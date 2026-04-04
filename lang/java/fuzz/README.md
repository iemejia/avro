# Apache Avro Java Fuzzing

This module hosts Jazzer-based fuzz tests for the Java Avro implementation.

## Targets

| Class | Target | Purpose |
|---|---|---|
| `SchemaFuzzer` | `fuzzSchemaParse` | Exercises `SchemaParser` with structured and arbitrary schema text |
| `BinaryDecodingFuzzer` | `fuzzBinaryDecoding` | Exercises buffered binary decoding and writer/reader schema resolution |
| `BinaryDecodingFuzzer` | `fuzzDirectBinaryDecoding` | Exercises direct binary decoding on `InputStream`-backed inputs |
| `JsonDecodingFuzzer` | `fuzzJsonDecoding` | Exercises JSON decoding with unions, maps, arrays, and logical types |
| `DataFileReaderFuzzer` | `fuzzDataFileReader` | Exercises seekable object container file parsing |
| `DataFileReaderFuzzer` | `fuzzDataFileStream` | Exercises streaming object container file parsing |

## Design Notes

- Targets only suppress exceptions that are expected from malformed Avro input.
- Unexpected runtime failures are allowed to escape so Jazzer reports them.
- The checked-in corpus includes both historical crash reproducers and valid seeds.
- Valid seeds help regression mode reach successful decode paths instead of replaying only prior failures.

## Running

Run from `lang/java/`:

```bash
# Replay the checked-in regression corpus only.
mvn test -pl fuzz

# Run continuous fuzzing for 60 seconds per target.
mvn test -pl fuzz -Pfuzz

# Run one target continuously.
mvn test -pl fuzz -Pfuzz -Dtest=BinaryDecodingFuzzer#fuzzDirectBinaryDecoding

# Equivalent environment-variable toggle.
JAZZER_FUZZ=1 mvn test -pl fuzz
```

## Corpus Layout

Regression inputs live under `src/test/resources/org/apache/avro/fuzz/<Class>Inputs/<target>/`.

- `crash-*` files are preserved reproducers for previously discovered failures.
- `seed-*` files are valid inputs generated from Avro's own encoders.

## Maintenance

- If a fuzz target is renamed, rename its corpus directory to match.
- Add new valid seeds whenever a target starts covering a new feature area.
- Keep dependency versions aligned with the parent POM rather than hard-coding them here.
