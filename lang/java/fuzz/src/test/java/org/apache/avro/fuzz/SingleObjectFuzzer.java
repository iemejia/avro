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

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.MissingSchemaException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

class SingleObjectFuzzer {
  @FuzzTest
  void fuzzSingleObjectRoundTrip(FuzzedDataProvider data) throws IOException {
    GenericRecord record = FuzzSupport.buildRoundTripRecord(data);
    BinaryMessageEncoder<GenericRecord> encoder = new BinaryMessageEncoder<>(GenericData.get(),
        FuzzSupport.ROUND_TRIP_SCHEMA);
    BinaryMessageDecoder<GenericRecord> decoder = new BinaryMessageDecoder<>(GenericData.get(),
        FuzzSupport.ROUND_TRIP_SCHEMA);

    ByteBuffer encoded = encoder.encode(record);
    GenericRecord decoded = decoder.decode(encoded);

    if (!FuzzSupport.roundTripRecordsEqual(record, decoded)) {
      throw new AssertionError("Single-object roundtrip changed semantic value");
    }
  }

  @FuzzTest
  void fuzzSingleObjectDecoding(byte[] data) throws IOException {
    BinaryMessageDecoder<GenericRecord> decoder = new BinaryMessageDecoder<>(GenericData.get(),
        FuzzSupport.ROUND_TRIP_SCHEMA);
    try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
      decoder.decode(input, null);
      if (input.available() > 0) {
        throw new AssertionError("Single-object decoder left trailing bytes unread");
      }
    } catch (BadHeaderException | MissingSchemaException e) {
      // Expected for malformed or unknown single-object payloads.
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }
}
