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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RoundTripFuzzer {

  /**
   * OSS-Fuzz entry point. Multiplexes between binary, JSON, and DataFile
   * round-trip modes using the first byte of fuzz input.
   */
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    int mode = data.consumeInt(0, 2);
    GenericRecord record = FuzzSupport.buildRoundTripRecord(data);
    try {
      switch (mode) {
      case 0:
        roundTripBinary(record);
        break;
      case 1:
        roundTripJson(record);
        break;
      default:
        roundTripDataFile(record);
        break;
      }
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IOException in round-trip", e);
    }
  }

  // --- JUnit @FuzzTest entry points for local fuzzing ---

  @FuzzTest
  void fuzzBinaryRoundTrip(FuzzedDataProvider data) throws IOException {
    roundTripBinary(FuzzSupport.buildRoundTripRecord(data));
  }

  @FuzzTest
  void fuzzJsonRoundTrip(FuzzedDataProvider data) throws IOException {
    roundTripJson(FuzzSupport.buildRoundTripRecord(data));
  }

  @FuzzTest
  void fuzzDataFileRoundTrip(FuzzedDataProvider data) throws IOException {
    roundTripDataFile(FuzzSupport.buildRoundTripRecord(data));
  }

  // --- Shared round-trip logic ---

  private static void roundTripBinary(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    writer.write(record, encoder);
    encoder.flush();

    byte[] encoded = output.toByteArray();
    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    GenericRecord decoded = reader.read(null, decoder);

    assertSemanticallyEqual(record, decoded);
    if (!decoder.isEnd()) {
      throw new AssertionError("Binary roundtrip left trailing bytes unread");
    }
  }

  private static void roundTripJson(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    JsonEncoder encoder = EncoderFactory.get().jsonEncoder(FuzzSupport.ROUND_TRIP_SCHEMA, output);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    writer.write(record, encoder);
    encoder.flush();

    byte[] encoded = output.toByteArray();
    JsonDecoder decoder = DecoderFactory.get().jsonDecoder(FuzzSupport.ROUND_TRIP_SCHEMA,
        new ByteArrayInputStream(encoded));
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.ROUND_TRIP_SCHEMA);
    GenericRecord decoded = reader.read(null, decoder);

    assertSemanticallyEqual(record, decoded);
  }

  private static void roundTripDataFile(GenericRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(
        new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA))) {
      writer.create(FuzzSupport.ROUND_TRIP_SCHEMA, output);
      writer.append(record);
    }

    byte[] encoded = output.toByteArray();
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
