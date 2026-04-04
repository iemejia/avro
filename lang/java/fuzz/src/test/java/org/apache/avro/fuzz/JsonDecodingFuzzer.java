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
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.io.IOException;

/**
 * Fuzz tests for Avro JSON decoding.
 *
 * <p>
 * Feeds mostly structured JSON strings through {@link JsonDecoder} to test the
 * JSON-to-Avro deserialization path, including Jackson JSON parsing, type
 * coercion, union resolution, logical types, and malformed-input handling.
 * </p>
 */
class JsonDecodingFuzzer {
  @FuzzTest
  void fuzzJsonDecoding(FuzzedDataProvider data) {
    String jsonData = FuzzSupport.buildJsonInput(data);
    try {
      JsonDecoder decoder = DecoderFactory.get().jsonDecoder(FuzzSupport.JSON_SCHEMA, jsonData);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(FuzzSupport.JSON_SCHEMA);
      reader.read(null, decoder);
    } catch (IOException e) {
      // Expected for malformed JSON input
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedDecodingFailure(e)) {
        throw e;
      }
    }
  }
}
