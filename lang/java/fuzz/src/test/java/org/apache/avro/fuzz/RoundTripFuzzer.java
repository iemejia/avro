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
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

class RoundTripFuzzer {
  @FuzzTest
  void fuzzBinaryRoundTrip(FuzzedDataProvider data) throws IOException {
    GenericRecord record = FuzzSupport.buildRoundTripRecord(data);
    byte[] encoded = encodeBinary(record);

    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    GenericRecord decoded = reader.read(null, decoder);

    assertSemanticallyEqual(record, decoded);
    if (!decoder.isEnd()) {
      throw new AssertionError("Binary roundtrip left trailing bytes unread");
    }
  }

  @FuzzTest
  void fuzzJsonRoundTrip(FuzzedDataProvider data) throws IOException {
    GenericRecord record = FuzzSupport.buildRoundTripRecord(data);
    byte[] encoded = encodeJson(record);

    JsonDecoder decoder = DecoderFactory.get().jsonDecoder(FuzzSupport.ROUND_TRIP_SCHEMA,
        new java.io.ByteArrayInputStream(encoded));
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    GenericRecord decoded = reader.read(null, decoder);

    assertSemanticallyEqual(record, decoded);
  }

  @FuzzTest
  void fuzzDataFileRoundTrip(FuzzedDataProvider data) throws IOException {
    GenericRecord record = FuzzSupport.buildRoundTripRecord(data);
    byte[] encoded = encodeContainer(record);

    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(new SeekableByteArrayInput(encoded),
        new GenericDatumReader<>(FuzzSupport.ROUND_TRIP_SCHEMA))) {
      if (!reader.hasNext()) {
        throw new AssertionError("Container roundtrip lost the encoded datum");
      }
      GenericRecord decoded = reader.next();
      assertSemanticallyEqual(record, decoded);
      if (reader.hasNext()) {
        throw new AssertionError("Container roundtrip unexpectedly produced multiple data items");
      }
    }
  }

  private static byte[] encodeBinary(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    writer.write(record, encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] encodeJson(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    JsonEncoder encoder = EncoderFactory.get().jsonEncoder(FuzzSupport.ROUND_TRIP_SCHEMA, output);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    writer.write(record, encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] encodeContainer(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(
        new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA))) {
      writer.create(FuzzSupport.ROUND_TRIP_SCHEMA, output);
      writer.append(record);
    }
    return output.toByteArray();
  }

  private static void assertSemanticallyEqual(GenericRecord expected, GenericRecord actual) {
    if (!GenericData.get().validate(FuzzSupport.ROUND_TRIP_SCHEMA, actual)) {
      throw new AssertionError("Decoded roundtrip record does not validate against the schema");
    }
    if (!FuzzSupport.roundTripRecordsEqual(expected, actual)) {
      throw new AssertionError("Roundtrip record changed semantic value");
    }

    GenericRecord copied = GenericData.get().deepCopy(FuzzSupport.ROUND_TRIP_SCHEMA, actual);
    if (!FuzzSupport.roundTripRecordsEqual(actual, copied)) {
      throw new AssertionError("Deep copy changed roundtrip record semantics");
    }
  }
}
