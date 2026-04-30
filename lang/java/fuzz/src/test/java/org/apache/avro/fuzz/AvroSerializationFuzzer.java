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
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.hadoop.io.AvroSerialization;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapred.AvroValue;
import org.apache.avro.mapred.AvroWrapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.Serializer;
import org.apache.hadoop.util.ReflectionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Fuzz tests for the Hadoop {@link AvroSerialization} framework.
 *
 * <p>
 * Exercises the full Avro MapReduce serialization path: configuring schemas on
 * {@link Configuration}, obtaining {@link Serializer} and {@link Deserializer}
 * instances via {@link AvroSerialization}, and round-tripping Avro records
 * through the Hadoop serialization framework as both {@link AvroKey} and
 * {@link AvroValue}.
 * </p>
 *
 * <p>
 * Also tests raw deserialization of arbitrary bytes to find crashes in the
 * decode path when the binary payload is malformed.
 * </p>
 */
public class AvroSerializationFuzzer {

  /**
   * OSS-Fuzz entry point. Multiplexes between key/value round-trip and raw
   * deserialization modes.
   */
  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    int mode = data.consumeInt(0, 2);
    try {
      switch (mode) {
      case 0:
        roundTripKey(FuzzSupport.buildRoundTripRecord(data));
        break;
      case 1:
        roundTripValue(FuzzSupport.buildRoundTripRecord(data));
        break;
      default:
        deserializeRawBytes(data.consumeRemainingAsBytes());
        break;
      }
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IOException in serialization fuzz", e);
    }
  }

  // --- JUnit @FuzzTest entry points for local fuzzing ---

  @FuzzTest
  void fuzzAvroKeyRoundTrip(FuzzedDataProvider data) throws IOException {
    roundTripKey(FuzzSupport.buildRoundTripRecord(data));
  }

  @FuzzTest
  void fuzzAvroValueRoundTrip(FuzzedDataProvider data) throws IOException {
    roundTripValue(FuzzSupport.buildRoundTripRecord(data));
  }

  @FuzzTest
  void fuzzAvroDeserialization(byte[] data) throws IOException {
    deserializeRawBytes(data);
  }

  // --- Shared logic ---

  @SuppressWarnings("unchecked")
  private static void roundTripKey(GenericRecord record) throws IOException {
    Configuration conf = buildConfiguration(FuzzSupport.ROUND_TRIP_SCHEMA);

    AvroSerialization<GenericRecord> serialization = ReflectionUtils.newInstance(AvroSerialization.class, conf);
    Class<AvroWrapper<GenericRecord>> keyClass = (Class<AvroWrapper<GenericRecord>>) (Class<?>) AvroKey.class;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serializer<AvroWrapper<GenericRecord>> serializer = serialization.getSerializer(keyClass);
    serializer.open(baos);
    serializer.serialize(new AvroKey<>(record));
    serializer.close();

    byte[] bytes = baos.toByteArray();

    Deserializer<AvroWrapper<GenericRecord>> deserializer = serialization.getDeserializer(keyClass);
    deserializer.open(new ByteArrayInputStream(bytes));
    AvroKey<GenericRecord> deserialized = (AvroKey<GenericRecord>) deserializer.deserialize(null);
    deserializer.close();

    if (!FuzzSupport.roundTripRecordsEqual(record, deserialized.datum())) {
      throw new AssertionError("AvroKey serialization round-trip changed semantic value");
    }
  }

  @SuppressWarnings("unchecked")
  private static void roundTripValue(GenericRecord record) throws IOException {
    Configuration conf = buildConfiguration(FuzzSupport.ROUND_TRIP_SCHEMA);

    AvroSerialization<GenericRecord> serialization = ReflectionUtils.newInstance(AvroSerialization.class, conf);
    Class<AvroWrapper<GenericRecord>> valueClass = (Class<AvroWrapper<GenericRecord>>) (Class<?>) AvroValue.class;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serializer<AvroWrapper<GenericRecord>> serializer = serialization.getSerializer(valueClass);
    serializer.open(baos);
    serializer.serialize(new AvroValue<>(record));
    serializer.close();

    byte[] bytes = baos.toByteArray();

    Deserializer<AvroWrapper<GenericRecord>> deserializer = serialization.getDeserializer(valueClass);
    deserializer.open(new ByteArrayInputStream(bytes));
    AvroValue<GenericRecord> deserialized = (AvroValue<GenericRecord>) deserializer.deserialize(null);
    deserializer.close();

    if (!FuzzSupport.roundTripRecordsEqual(record, deserialized.datum())) {
      throw new AssertionError("AvroValue serialization round-trip changed semantic value");
    }
  }

  @SuppressWarnings("unchecked")
  private static void deserializeRawBytes(byte[] data) throws IOException {
    Configuration conf = buildConfiguration(FuzzSupport.ROUND_TRIP_SCHEMA);

    AvroSerialization<GenericRecord> serialization = ReflectionUtils.newInstance(AvroSerialization.class, conf);
    Class<AvroWrapper<GenericRecord>> keyClass = (Class<AvroWrapper<GenericRecord>>) (Class<?>) AvroKey.class;

    Deserializer<AvroWrapper<GenericRecord>> deserializer = serialization.getDeserializer(keyClass);
    try {
      deserializer.open(new ByteArrayInputStream(data));
      deserializer.deserialize(null);
      deserializer.close();
    } catch (IOException e) {
      // Expected for malformed binary data
    } catch (RuntimeException e) {
      if (!isExpectedSerializationFailure(e)) {
        throw e;
      }
    }
  }

  private static Configuration buildConfiguration(Schema schema) {
    Configuration conf = new Configuration(false);
    AvroSerialization.addToConfiguration(conf);
    AvroSerialization.setKeyWriterSchema(conf, schema);
    AvroSerialization.setValueWriterSchema(conf, schema);
    return conf;
  }

  private static boolean isExpectedSerializationFailure(RuntimeException e) {
    return e instanceof AvroRuntimeException || FuzzSupport.isExpectedDecodingFailure(e);
  }
}
