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
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Fuzz tests for Avro binary decoding.
 *
 * <p>
 * Feeds arbitrary bytes through {@link BinaryDecoder} and
 * {@link GenericDatumReader} using writer/reader schema pairs that exercise
 * varint parsing, schema resolution, aliases, defaults, fixed values, logical
 * types, and recursive records.
 * </p>
 */
class BinaryDecodingFuzzer {

  @FuzzTest
  void fuzzBinaryDecoding(byte[] data) {
    fuzz(data, false);
  }

  @FuzzTest
  void fuzzDirectBinaryDecoding(byte[] data) {
    fuzz(data, true);
  }

  private void fuzz(byte[] data, boolean direct) {
    try {
      BinaryDecoder decoder = direct ? DecoderFactory.get().directBinaryDecoder(new ByteArrayInputStream(data), null)
          : DecoderFactory.get().binaryDecoder(data, null);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.BINARY_WRITER_SCHEMA,
          FuzzSupport.BINARY_READER_SCHEMA);
      reader.read(null, decoder);
    } catch (IOException e) {
      // Expected for malformed binary data
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }
}
