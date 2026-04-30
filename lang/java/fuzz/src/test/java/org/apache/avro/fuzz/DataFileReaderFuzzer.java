/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro.fuzz;

import com.code_intelligence.jazzer.junit.FuzzTest;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Fuzz tests for Avro data file (container format) reading.
 *
 * <p>
 * Feeds arbitrary bytes through {@link DataFileReader} and
 * {@link DataFileStream} to test the full Avro container format parsing: magic
 * bytes, file header, metadata (including the embedded schema), sync markers,
 * codec decompression, and datum deserialization.
 * </p>
 *
 * <p>
 * Because the container format includes an embedded schema, malformed input can
 * fail in the IO, schema parsing, or decoding layers. Those expected failures
 * are swallowed, while likely bug-indicating runtime failures are rethrown.
 * </p>
 */
public class DataFileReaderFuzzer {

  /**
   * OSS-Fuzz entry point. Multiplexes between seekable/stream modes with and
   * without schema resolution using the first byte of fuzz input.
   */
  public static void fuzzerTestOneInput(byte[] data) {
    if (data.length < 1) {
      return;
    }
    int mode = data[0] & 0x03;
    byte[] payload = java.util.Arrays.copyOfRange(data, 1, data.length);

    switch (mode) {
    case 0:
      fuzzSeekable(payload);
      break;
    case 1:
      fuzzSeekableWithResolution(payload);
      break;
    case 2:
      fuzzStream(payload);
      break;
    default:
      fuzzStreamWithResolution(payload);
      break;
    }
  }

  private static void fuzzSeekable(byte[] data) {
    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableByteArrayInput(data),
        new GenericDatumReader<>())) {
      while (reader.hasNext()) {
        reader.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files.
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedContainerFailure(e)) {
        throw e;
      }
    } catch (OutOfMemoryError e) {
      // Fuzzed varint-encoded lengths can trigger huge allocations.
    }
  }

  private static void fuzzSeekableWithResolution(byte[] data) {
    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableByteArrayInput(data),
        new GenericDatumReader<>(null, FuzzSupport.BINARY_READER_SCHEMA))) {
      while (reader.hasNext()) {
        reader.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files.
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedContainerFailure(e)) {
        throw e;
      }
    } catch (OutOfMemoryError e) {
      // Fuzzed varint-encoded lengths can trigger huge allocations.
    }
  }

  private static void fuzzStream(byte[] data) {
    try (DataFileStream<GenericRecord> stream = new DataFileStream<>(new ByteArrayInputStream(data),
        new GenericDatumReader<>())) {
      while (stream.hasNext()) {
        stream.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files.
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedContainerFailure(e)) {
        throw e;
      }
    } catch (OutOfMemoryError e) {
      // Fuzzed varint-encoded lengths can trigger huge allocations.
    }
  }

  private static void fuzzStreamWithResolution(byte[] data) {
    try (DataFileStream<GenericRecord> stream = new DataFileStream<>(new ByteArrayInputStream(data),
        new GenericDatumReader<>(null, FuzzSupport.BINARY_READER_SCHEMA))) {
      while (stream.hasNext()) {
        stream.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files.
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedContainerFailure(e)) {
        throw e;
      }
    } catch (OutOfMemoryError e) {
      // Fuzzed varint-encoded lengths can trigger huge allocations.
    }
  }

  // --- JUnit @FuzzTest entry points for local fuzzing ---

  @FuzzTest
  void fuzzDataFileReader(byte[] data) {
    fuzzSeekable(data);
  }

  @FuzzTest
  void fuzzDataFileReaderWithResolution(byte[] data) {
    fuzzSeekableWithResolution(data);
  }

  @FuzzTest
  void fuzzDataFileStream(byte[] data) {
    fuzzStream(data);
  }

  @FuzzTest
  void fuzzDataFileStreamWithResolution(byte[] data) {
    fuzzStreamWithResolution(data);
  }
}
