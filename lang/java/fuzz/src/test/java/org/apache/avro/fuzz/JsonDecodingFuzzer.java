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
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.nio.charset.StandardCharsets;

/**
 * Fuzz tests for Avro JSON decoding.
 *
 * <p>
 * Feeds arbitrary JSON strings through {@link JsonDecoder} with a compound
 * schema to test the JSON-to-Avro deserialization path, including Jackson JSON
 * parsing, type coercion, union resolution, and error handling for malformed
 * data.
 * </p>
 */
class JsonDecodingFuzzer {

  /**
   * A compound schema with diverse field types: primitives, arrays, maps, and
   * unions. This is a superset of the previous separate record and simple
   * schemas.
   */
  private static final Schema SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"type\":\"string\"}," + "{\"name\":\"x\",\"type\":\"int\"},"
          + "{\"name\":\"active\",\"type\":\"boolean\"},"
          + "{\"name\":\"scores\",\"type\":{\"type\":\"array\",\"items\":\"double\"}},"
          + "{\"name\":\"props\",\"type\":{\"type\":\"map\",\"values\":\"string\"}},"
          + "{\"name\":\"extra\",\"type\":[\"null\",\"string\",\"long\"]}" + "]}");

  @FuzzTest
  void fuzzJsonDecoding(byte[] data) {
    String jsonData = new String(data, StandardCharsets.UTF_8);
    try {
      JsonDecoder decoder = DecoderFactory.get().jsonDecoder(SCHEMA, jsonData);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(SCHEMA);
      reader.read(null, decoder);
    } catch (Exception e) {
      // Expected for malformed JSON or type mismatches
    }
  }
}
