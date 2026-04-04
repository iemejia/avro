# Fuzz Testing Report for Apache Avro Java

## Overview

`lang/java/fuzz/` is a Jazzer-based fuzzing module for the Java Avro implementation.

Current coverage includes 4 fuzz classes and 6 fuzz targets:

- schema parsing via `SchemaParser`
- buffered binary decoding
- direct binary decoding
- JSON decoding
- seekable container-file decoding
- streaming container-file decoding

The module is wired into `lang/java/pom.xml` and can be run in regression or continuous-fuzzing mode through Maven.

## Bugs Found

### Bug 1: `NullPointerException` in schema parsing

- **Severity**: Medium
- **Area**: `ParseContext.resolve()`
- **Impact**: malformed schema input could escape as `NullPointerException` instead of an Avro parse/type exception
- **Status**: Fixed

### Bug 2: Denial of service via impossible binary/container lengths

- **Severity**: High
- **Area**: `BinaryDecoder` string/bytes/array/map length handling
- **Impact**: tiny hostile inputs could trigger huge allocations before EOF was detected
- **Status**: Fixed

## Current Module Improvements

The fuzz module has been strengthened beyond the initial implementation:

- fuzzers now catch only expected malformed-input failures
- unexpected runtime exceptions are no longer suppressed
- schema fuzzing uses `SchemaParser` instead of the deprecated `Schema.parse(...)` entry point
- binary coverage now includes both buffered and direct decoder paths
- binary decoding uses writer/reader schema pairs to exercise aliases, defaults, recursion, fixed, and logical types
- regression corpora now include valid seeds in addition to historical crash reproducers
- container-file corpora now cover both `fuzzDataFileReader` and `fuzzDataFileStream`
- module dependency alignment now follows `${junit5.version}` from the parent POM

## BinaryDecoder Fix Notes

The BinaryDecoder hardening works by validating requested lengths against known remaining bytes before allocating buffers.

Key points:

- `ByteArrayByteSource.remainingBytes()` returns an exact remaining count
- stream-backed buffered decoders use buffered bytes plus `InputStream.available()`
- `ensureAvailableBytes(int length)` throws `EOFException` when the source is known to be too short
- direct decoders are exercised separately because they do not use the same buffered `ByteSource` path

## Regression Corpus

Checked-in regression inputs now include:

- historical `crash-*` reproducers for known findings
- valid schema text seeds
- valid JSON record seeds
- valid binary datum seeds
- valid object container file seeds for both reader and stream targets

This makes `mvn test -pl fuzz` useful as both a regression suite and a shallow successful-path smoke test.

## Verification

Verified locally with:

```bash
mvn test -pl fuzz
```

The module builds, replays the regression corpus, and runs all fuzz targets successfully.
