<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# AVRO-4247 Branch Review

## Scope

This review focused on the Java codec decompression-bomb fix and the write-path impact of changing `NonCopyingByteArrayOutputStream`.

The external PoC used for validation was `../avro-oom-compression-poc`, rebuilt against this branch's `org.apache.avro:avro:1.13.0-SNAPSHOT` artifact.

## Initial Findings

The original branch did not fully mitigate the exploit under default settings. The 50 MiB PoC still triggered `OutOfMemoryError` on constrained JVMs because the default decompression limit was 200 MiB.

The original limited stream also delegated writes to `ByteArrayOutputStream`, which could grow the backing array beyond the logical limit before the next write failed. This allowed `bzip2` and `xz` to still OOM under a low configured limit.

The original branch applied `capacityLimitedOutputStream(...)` to codec `compress()` methods, which made `org.apache.avro.limits.decompress.maxLength` affect normal writes. A 12 MiB incompressible deflate write failed when the decompression limit was set to 10 MiB.

## Implemented Fix

Compression paths now use the normal unlimited `NonCopyingByteArrayOutputStream` again.

Decompression paths still use `capacityLimitedOutputStream(...)`.

`NonCopyingByteArrayOutputStream` now performs its own bounded growth before copying bytes into the backing array, so a limited stream cannot over-allocate before throwing `SystemLimitException`.

The default decompression limit is now heap-aware: `min(200 MiB, Runtime.maxMemory() / 4)`. Users can still override it with `org.apache.avro.limits.decompress.maxLength`.

Regression coverage was added for `writeBytes(...)` and initial-capacity clamping on limited streams.

## Validation

Focused Avro tests passed with Maven build cache disabled:

```bash
mvn -Dmaven.build.cache.enabled=false -pl lang/java/avro -Dtest=org.apache.avro.util.NonCopyingByteArrayOutputStreamTest,org.apache.avro.file.TestAllCodecs,org.apache.avro.TestDataFile,org.apache.avro.TestDataFileConcat test -DskipITs
```

The PoC was rebuilt against the patched local Avro snapshot:

```bash
mvn clean package -DskipTests -Davro.version=1.13.0-SNAPSHOT
```

The multi-codec PoC no longer produced any OOM confirmations:

```bash
./run-all-codecs.sh
```

Result: `Vulnerable (OOM): 0`, `Not triggered: 5`, `Skipped: 0`.

Each codec failed with controlled `SystemLimitException` instead of `OutOfMemoryError`.

A low-limit write-path probe also succeeded after the fix. With `org.apache.avro.limits.decompress.maxLength=10485760`, writing a 12 MiB deflate block completed successfully.

## Notes

An install command including the root project failed Apache RAT because of pre-existing untracked files `AVRO-4247-analysis.md` and `REVIEW-AVRO-4247.md` without license headers. Those files were not modified or included in this change.

For validation only, the Avro module was installed with `-Drat.skip=true` after the focused tests had already passed.
