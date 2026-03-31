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
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;

/**
 * Fuzz tests for Avro binary decoding.
 *
 * <p>
 * Feeds arbitrary bytes through {@link BinaryDecoder} and
 * {@link GenericDatumReader} using a compound schema that exercises varint
 * parsing, buffer management, nested records, enums, arrays, maps, unions, and
 * collection size limit enforcement in a single target.
 * </p>
 */
class BinaryDecodingFuzzer {

  /**
   * A compound schema that nests records, enums, arrays, maps, and unions to
   * maximise code coverage from a single fuzz target.
   */
  private static final Schema SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"Root\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"type\":\"string\"}," + "{\"name\":\"value\",\"type\":\"double\"},"
          + "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},"
          + "{\"name\":\"metadata\",\"type\":{\"type\":\"map\",\"values\":\"string\"}},"
          + "{\"name\":\"optionalField\",\"type\":[\"null\",\"string\"]},"
          + "{\"name\":\"inner\",\"type\":{\"type\":\"record\",\"name\":\"Inner\",\"fields\":["
          + "{\"name\":\"x\",\"type\":\"int\"},{\"name\":\"y\",\"type\":\"int\"}]}},"
          + "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"Status\","
          + "\"symbols\":[\"ACTIVE\",\"INACTIVE\",\"PENDING\"]}}," + "{\"name\":\"count\",\"type\":\"int\"}" + "]}");

  @FuzzTest
  void fuzzBinaryDecoding(byte[] data) {
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(SCHEMA);
      reader.read(null, decoder);
    } catch (Exception e) {
      // Expected for malformed binary data
    }
  }
}
