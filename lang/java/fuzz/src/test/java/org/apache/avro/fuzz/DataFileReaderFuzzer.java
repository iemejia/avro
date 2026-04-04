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
 */
class DataFileReaderFuzzer {

  /**
   * Fuzz the seekable {@link DataFileReader} path via
   * {@link SeekableByteArrayInput}, exercising random-access container file
   * reading including block seeking.
   */
  @FuzzTest
  void fuzzDataFileReader(byte[] data) {
    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableByteArrayInput(data),
        new GenericDatumReader<>())) {
      while (reader.hasNext()) {
        reader.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }

  @FuzzTest
  void fuzzDataFileReaderWithResolution(byte[] data) {
    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableByteArrayInput(data),
        new GenericDatumReader<>(null, FuzzSupport.BINARY_READER_SCHEMA))) {
      while (reader.hasNext()) {
        reader.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }

  /**
   * Fuzz the streaming {@link DataFileStream} path via
   * {@link ByteArrayInputStream}, exercising sequential (non-seekable) container
   * file reading.
   */
  @FuzzTest
  void fuzzDataFileStream(byte[] data) {
    try (DataFileStream<GenericRecord> stream = new DataFileStream<>(new ByteArrayInputStream(data),
        new GenericDatumReader<>())) {
      while (stream.hasNext()) {
        stream.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }

  @FuzzTest
  void fuzzDataFileStreamWithResolution(byte[] data) {
    try (DataFileStream<GenericRecord> stream = new DataFileStream<>(new ByteArrayInputStream(data),
        new GenericDatumReader<>(null, FuzzSupport.BINARY_READER_SCHEMA))) {
      while (stream.hasNext()) {
        stream.next();
      }
    } catch (IOException e) {
      // Expected for malformed container files
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }
}
