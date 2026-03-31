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
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Fuzz tests for Avro JSON decoding.
 *
 * <p>
 * Feeds arbitrary JSON strings through {@link JsonDecoder} with various schemas
 * to test the JSON-to-Avro deserialization path, including Jackson JSON
 * parsing, type coercion, union resolution, and error handling for malformed
 * data.
 * </p>
 */
class JsonDecodingFuzzer {

  /** A record schema with diverse field types. */
  private static final Schema RECORD_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"type\":\"string\"}," + "{\"name\":\"active\",\"type\":\"boolean\"},"
          + "{\"name\":\"scores\",\"type\":{\"type\":\"array\",\"items\":\"double\"}},"
          + "{\"name\":\"props\",\"type\":{\"type\":\"map\",\"values\":\"string\"}},"
          + "{\"name\":\"extra\",\"type\":[\"null\",\"string\",\"long\"]}" + "]}");

  /** A simpler schema for basic field decoding. */
  private static final Schema SIMPLE_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"Simple\",\"fields\":[" + "{\"name\":\"x\",\"type\":\"int\"},"
          + "{\"name\":\"y\",\"type\":\"string\"}" + "]}");

  @FuzzTest
  void fuzzJsonDecodingRecord(byte[] data) {
    decodeJsonWithSchema(data, RECORD_SCHEMA);
  }

  @FuzzTest
  void fuzzJsonDecodingSimple(byte[] data) {
    decodeJsonWithSchema(data, SIMPLE_SCHEMA);
  }

  private static void decodeJsonWithSchema(byte[] data, Schema schema) {
    String jsonData = new String(data, StandardCharsets.UTF_8);
    try {
      JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, jsonData);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
      reader.read(null, decoder);
    } catch (IOException | AvroRuntimeException | UnsupportedOperationException e) {
      // Expected for malformed JSON or type mismatches
      // UnsupportedOperationException: thrown by SystemLimitException for oversized
      // strings/collections
    }
  }
}
