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
import org.apache.avro.fuzz.model.ReflectRoundTripRecord;
import org.apache.avro.fuzz.model.SpecificProjectionEnum;
import org.apache.avro.fuzz.model.SpecificProjectionRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuzz tests for Avro schema projection (reading data with a different schema
 * than used to write it), covering both Specific and Reflect APIs.
 */
public class ProjectionFuzzer {
  private static final ReflectData REFLECT_DATA = ReflectData.get();

  /**
   * OSS-Fuzz entry point. Multiplexes between Specific and Reflect projection
   * modes using the first byte of fuzz input.
   */
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    int mode = data.consumeInt(0, 1);
    try {
      switch (mode) {
      case 0:
        specificProjection(buildSpecificRecord(data));
        break;
      default:
        reflectProjection(buildReflectRecord(data));
        break;
      }
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IOException in projection fuzz", e);
    }
  }

  // --- JUnit @FuzzTest entry points for local fuzzing ---

  @FuzzTest
  void fuzzSpecificProjection(FuzzedDataProvider data) throws IOException {
    specificProjection(buildSpecificRecord(data));
  }

  @FuzzTest
  void fuzzReflectProjection(FuzzedDataProvider data) throws IOException {
    reflectProjection(buildReflectRecord(data));
  }

  // --- Shared projection logic ---

  private static void specificProjection(SpecificProjectionRecord record) throws IOException {
    byte[] encoded = encodeSpecific(record);

    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
    SpecificDatumReader<SpecificProjectionRecord> reader = new SpecificDatumReader<>(SpecificProjectionRecord.class);
    SpecificProjectionRecord decoded = reader.read(null, decoder);

    if (!specificRecordsEqual(record, decoded)) {
      throw new AssertionError("Specific projection changed semantic value");
    }
    if (!decoder.isEnd()) {
      throw new AssertionError("Specific projection left trailing bytes unread");
    }
  }

  private static void reflectProjection(ReflectRoundTripRecord record) throws IOException {
    byte[] encoded = encodeReflect(record);

    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
    ReflectDatumReader<ReflectRoundTripRecord> reader = new ReflectDatumReader<>(ReflectRoundTripRecord.class);
    ReflectRoundTripRecord decoded = reader.read(null, decoder);

    if (!record.equals(decoded)) {
      throw new AssertionError("Reflect projection changed semantic value");
    }
    if (!decoder.isEnd()) {
      throw new AssertionError("Reflect projection left trailing bytes unread");
    }
  }

  // --- Encoding helpers ---

  private static byte[] encodeSpecific(SpecificProjectionRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    SpecificDatumWriter<SpecificProjectionRecord> writer = new SpecificDatumWriter<>(SpecificProjectionRecord.class);
    writer.write(record, encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] encodeReflect(ReflectRoundTripRecord record) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    ReflectDatumWriter<ReflectRoundTripRecord> writer = new ReflectDatumWriter<>(ReflectRoundTripRecord.class,
        REFLECT_DATA);
    writer.write(record, encoder);
    encoder.flush();
    return output.toByteArray();
  }

  // --- Record builders ---

  private static SpecificProjectionRecord buildSpecificRecord(FuzzedDataProvider data) {
    List<String> nicknames = new ArrayList<>();
    int nicknameCount = data.consumeInt(0, 3);
    for (int i = 0; i < nicknameCount; i++) {
      nicknames.add(FuzzSupport.safeString(data.consumeString(12), "nick" + i));
    }

    SpecificProjectionEnum typeEnum = data.consumeBoolean() ? null
        : SpecificProjectionEnum.values()[data.consumeInt(0, SpecificProjectionEnum.values().length - 1)];
    return new SpecificProjectionRecord(data.consumeInt(), FuzzSupport.safeString(data.consumeString(24), "name"),
        nicknames, typeEnum);
  }

  private static ReflectRoundTripRecord buildReflectRecord(FuzzedDataProvider data) {
    ReflectRoundTripRecord record = new ReflectRoundTripRecord();
    record.id = data.consumeLong();
    record.name = FuzzSupport.safeString(data.consumeString(24), "reflect");
    record.tags = new ArrayList<>();
    for (int i = 0, size = data.consumeInt(0, 3); i < size; i++) {
      record.tags.add(FuzzSupport.safeString(data.consumeString(16), "tag" + i));
    }
    record.counts = new LinkedHashMap<>();
    for (int i = 0, size = data.consumeInt(0, 3); i < size; i++) {
      record.counts.put(FuzzSupport.safeString(data.consumeString(12), "k" + i), data.consumeLong());
    }
    record.alias = data.consumeBoolean() ? null : FuzzSupport.safeString(data.consumeString(16), "alias");
    return record;
  }

  private static boolean specificRecordsEqual(SpecificProjectionRecord left, SpecificProjectionRecord right) {
    return FuzzSupport.semanticEquals(left.get(0), right.get(0))
        && FuzzSupport.semanticEquals(left.get(1), right.get(1))
        && FuzzSupport.semanticEquals(left.get(2), right.get(2))
        && FuzzSupport.semanticEquals(left.get(3), right.get(3));
  }
}
