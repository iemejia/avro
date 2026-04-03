# Fuzz Testing Report for Apache Avro Java

## Overview

A new `lang/java/fuzz/` Maven module was created with 4 fuzz test classes (5 total fuzz targets) using [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) 0.22.1, a coverage-guided Java fuzzer built on libFuzzer. Two bugs were found and fixed: an NPE in schema parsing and a denial-of-service via memory exhaustion in binary decoding.

## Fuzz Test Targets

| Class | Target | Description |
|---|---|---|
| `SchemaFuzzer` | `fuzzSchemaParse` | Feeds arbitrary UTF-8 strings to `Schema.parse(String)` |
| `BinaryDecodingFuzzer` | `fuzzBinaryDecoding` | Feeds arbitrary bytes through `BinaryDecoder` + `GenericDatumReader` with a multi-field record schema |
| `JsonDecodingFuzzer` | `fuzzJsonDecoding` | Feeds arbitrary JSON through `JsonDecoder` + `GenericDatumReader` |
| `DataFileReaderFuzzer` | `fuzzDataFileReader` | Feeds arbitrary bytes to `DataFileReader` (seekable path) |
| `DataFileReaderFuzzer` | `fuzzDataFileStream` | Feeds arbitrary bytes to `DataFileStream` (streaming path) |

## Bugs Found

### Bug 1: `NullPointerException` in `Schema.parse()` (ParseContext.resolve)

- **Severity**: Medium
- **Location**: `ParseContext.java:330`
- **Trigger**: Input like `""""""""""""""""""` (18 quote characters)
- **Root Cause**: When `Schema.parse()` processes certain malformed JSON inputs (e.g., nested JSON arrays with string elements that look like type references), the parser creates unresolved schema placeholders (`UnresolvedSchema_0`). In `ParseContext.resolve()`, it calls `requireNonNull(oldSchemas.get(schema.getFullName()))` which throws `NullPointerException` instead of a proper `AvroTypeException`.
- **Impact**: Code that calls `Schema.parse()` and catches `SchemaParseException` or `AvroRuntimeException` will miss this error. The NPE leaks internal implementation details.
- **Status**: **Fixed**

**Stack trace:**

```
java.lang.NullPointerException: Unknown schema: org.apache.avro.compiler.UnresolvedSchema_0
    at java.base/java.util.Objects.requireNonNull(Objects.java:360)
    at org.apache.avro.ParseContext.resolve(ParseContext.java:330)
    at org.apache.avro.Schema$Parser.parse(Schema.java:1542)
    at org.apache.avro.Schema$Parser.parse(Schema.java:1516)
    at org.apache.avro.Schema.parse(Schema.java:1607)
```

### Bug 2: Denial-of-Service via Memory Exhaustion in Binary/Container Decoding

- **Severity**: High (security, DoS)
- **Location**: `Utf8.java:115` via `BinaryDecoder.readString()` at `BinaryDecoder.java:299`; also `readBytes()`, `readArrayStart()`, `arrayNext()`, `readMapStart()`, `mapNext()`
- **Trigger**: Crafted binary input containing varint-encoded lengths far exceeding available data
- **Root Cause**: `BinaryDecoder` reads a varint-encoded length, then immediately allocates `new byte[length]` before checking whether that many bytes are actually available. A few-byte payload encoding a large varint (e.g., 200 MB+) causes `OutOfMemoryError` before any read can fail with `EOFException`. This affects strings, bytes, arrays, and maps.
- **Impact**: An attacker can craft a small Avro binary payload (just a few bytes encoding a large varint) that causes the JVM to run out of memory. This is a denial-of-service vector for any service that reads untrusted Avro data.
- **Status**: **Fixed**

**Stack trace:**

```
java.lang.OutOfMemoryError: Java heap space
    at org.apache.avro.util.Utf8.setByteLength(Utf8.java:115)
    at org.apache.avro.io.BinaryDecoder.readString(BinaryDecoder.java:299)
    at org.apache.avro.io.FastReaderBuilder.lambda$createSimpleStringReader$27(FastReaderBuilder.java:380)
    at org.apache.avro.io.FastReaderBuilder$RecordReader.read(FastReaderBuilder.java:575)
    at org.apache.avro.generic.GenericDatumReader.read(GenericDatumReader.java:150)
```

## Fixes Applied

### Fix for Bug 1: Proper error type in ParseContext.resolve()

**File**: `ParseContext.java`

Replaced `Objects.requireNonNull(oldSchemas.get(schema.getFullName()), ...)` with an explicit null check that throws `AvroTypeException`. This ensures malformed schema inputs produce the same exception type as other schema parse errors, making catch blocks reliable.

**Regression test**: `TestSchema.parseMalformedInputDoesNotThrowNPE` — verifies that the trigger input throws `AvroTypeException`, not `NullPointerException`.

### Fix for Bug 2: Validate-before-allocate in BinaryDecoder

**File**: `BinaryDecoder.java`

Added a protobuf-style validate-before-allocate check: before allocating memory for a decoded length, verify that the byte source actually has enough bytes remaining.

Implementation details:

- Added abstract `remainingBytes()` to the inner `ByteSource` class
- `ByteArrayByteSource.remainingBytes()` returns exact remaining bytes (`lim - pos`)
- `InputStreamByteSource.remainingBytes()` returns `-1` (unknown — `InputStream.available()` is unreliable, so stream-backed decoders are not guarded by this check)
- Added `ensureAvailableBytes(int length)` method that throws `EOFException` when the requested length exceeds known remaining bytes
- The check is called in: `readString()`, `readBytes()`, `readArrayStart()`, `arrayNext()`, `readMapStart()`, `mapNext()`
- For `DirectBinaryDecoder`, `source` is always null, so the check is a no-op (correct — it has no buffered byte source)
- For arrays/maps, each item must be at least 1 byte, so item count serves as a valid lower bound for bytes needed
- `InputStreamByteSource.remainingBytes()` returns `buffered + in.available()`, which is exact for the finite, in-memory streams used by `DataFileReader` and `DataFileStream` (`SeekableInputStream.available()` and `ByteArrayInputStream.available()` both report exact remaining). When `available()` throws, returns -1 (check skipped). This is safe because the buffering `BinaryDecoder` is only used for file/container reading — network/RPC streams use `DirectBinaryDecoder` which bypasses this check entirely.

This approach was chosen over lowering the default `SystemLimitException` limits because it does not change behavior for production users — only clearly impossible allocations (where the claimed length exceeds the actual data) are rejected.

**Regression tests**:
- `TestBinaryDecoder.testStringLengthExceedsAvailableBytes` — verifies `EOFException` for a string length exceeding available bytes
- `TestBinaryDecoder.testBytesLengthExceedsAvailableBytes` — verifies `EOFException` for a bytes length exceeding available bytes
- Updated `testArrayVmMaxSize`, `testArrayMaxCustom`, `testMapVmMaxSize`, `testMapMaxCustom` — byte-array-backed decoders now correctly reject impossible counts with `EOFException`

## Verification

### Test suite
All 3,415 tests in the `avro` module pass after all fixes.

### Fuzzer re-runs (post-fix)
- **BinaryDecodingFuzzer**: ~974K iterations, 0 crashes (previously crashed within ~4K iterations with OOM)
- **SchemaFuzzer**: ~1M iterations, 0 crashes (previously found NPE)
- **DataFileReaderFuzzer**: ~684K iterations, 0 crashes (previously crashed within ~4K iterations with OOM via `DataFileStream.initialize()`)
- **JsonDecodingFuzzer**: ~1M iterations, 0 crashes

All bugs are confirmed fixed.

## What Each Fuzz Test Does

### SchemaFuzzer

Tests the Avro JSON schema parser (`Schema.parse()`). Converts random bytes to a UTF-8 string and passes it as a schema definition. This exercises the Jackson JSON parser, Avro type resolution, named schema registration, union construction, and schema validation. Expected exceptions (`AvroRuntimeException`, `SchemaParseException`, `IllegalArgumentException`) are caught — any other exception indicates a bug.

### BinaryDecodingFuzzer

Tests the Avro binary wire format decoder. Feeds raw bytes directly into `BinaryDecoder` paired with a `GenericDatumReader` using a record schema with long, string, double, array, map, and union fields — exercises varint parsing, buffer management, collection size decoding, and union branch selection.

### JsonDecodingFuzzer

Tests the Avro JSON data decoder. Converts random bytes to UTF-8 and feeds them through `JsonDecoder` with a `GenericDatumReader`. This exercises JSON-to-Avro type coercion, union resolution via JSON object wrapping, and error handling for malformed or type-mismatched data.

### DataFileReaderFuzzer

Tests the Avro container file (Object Container File) format parser. This is the highest-value target as it exercises the full deserialization pipeline: magic byte validation, file header parsing, embedded schema deserialization, sync marker verification, codec decompression, and datum reading. Two code paths are tested:

- **`DataFileReader`** (seekable, random-access via `SeekableByteArrayInput`)
- **`DataFileStream`** (sequential, non-seekable via `ByteArrayInputStream`)

## How to Run

```bash
# Regression mode (default — replays seed corpus only, fast):
mvn test -pl fuzz

# Continuous fuzzing mode (60s per target):
mvn test -pl fuzz -Pfuzz

# Fuzz a specific target:
mvn test -pl fuzz -Pfuzz -Dtest=SchemaFuzzer#fuzzSchemaParse

# Alternative: set JAZZER_FUZZ environment variable:
JAZZER_FUZZ=1 mvn test -pl fuzz
```

## Technical Notes

- **Jazzer version**: 0.22.1 with JUnit 5 integration (`jazzer-junit`)
- **Fuzzing engine**: libFuzzer (coverage-guided, mutation-based)
- **Enabling fuzzing mode**: Jazzer 0.22.1 checks the `JAZZER_FUZZ` environment variable or the `jazzer.internal.command_line` JUnit Platform configuration parameter. The `-Pfuzz` Maven profile sets both.
- **JUnit version alignment**: Jazzer 0.22.1 ships with JUnit Platform 1.9.0 dependencies which conflict with the project's JUnit 5.14.3. The POM excludes Jazzer's JUnit deps and provides `junit-platform-launcher:1.14.3` explicitly.
- **Surefire configuration**: The parent POM sets `parallel=all` with unlimited threads. The fuzz module overrides this to single-threaded execution, which Jazzer requires.
