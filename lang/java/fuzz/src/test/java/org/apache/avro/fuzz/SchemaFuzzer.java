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
import org.apache.avro.SchemaParser;

/**
 * Fuzz tests for Avro schema parsing.
 *
 * <p>
 * Targets {@link SchemaParser#parse(CharSequence)} with mostly structured
 * input. Expected parse failures are swallowed, but unexpected runtime failures
 * are allowed to escape so Jazzer can report them as real findings.
 * </p>
 */
class SchemaFuzzer {

  @FuzzTest
  void fuzzSchemaParse(FuzzedDataProvider data) {
    String schemaJson = FuzzSupport.buildSchemaInput(data);
    try {
      new SchemaParser().parse(schemaJson).mainSchema();
    } catch (RuntimeException e) {
      if (!FuzzSupport.isExpectedSchemaFailure(e)) {
        throw e;
      }
    }
  }
}
