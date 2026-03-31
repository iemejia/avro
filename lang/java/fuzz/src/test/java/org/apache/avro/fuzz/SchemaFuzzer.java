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

import java.nio.charset.StandardCharsets;

/**
 * Fuzz tests for Avro schema parsing.
 *
 * <p>
 * Targets {@link Schema#parse(String, boolean)} with arbitrary input and name
 * validation enabled. Any exception is treated as expected behavior for invalid
 * input; only JVM-level errors (e.g. {@link StackOverflowError}) escape and are
 * reported as real findings.
 * </p>
 */
class SchemaFuzzer {

  @FuzzTest
  void fuzzSchemaParse(byte[] data) {
    String schemaJson = new String(data, StandardCharsets.UTF_8);
    try {
      Schema.parse(schemaJson, true);
    } catch (Exception e) {
      // Expected for invalid schema input
    }
  }
}
