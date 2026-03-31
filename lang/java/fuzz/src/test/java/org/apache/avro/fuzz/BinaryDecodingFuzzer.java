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
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;

/**
 * Fuzz tests for Avro binary decoding.
 *
 * <p>
 * Feeds arbitrary bytes through {@link BinaryDecoder} and
 * {@link GenericDatumReader} using several representative schemas to exercise
 * varint parsing, buffer management, nested record handling, and collection
 * size limit enforcement.
 * </p>
 */
class BinaryDecodingFuzzer {

  /** A record schema with multiple field types to exercise diverse code paths. */
  private static final Schema RECORD_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"type\":\"string\"}," + "{\"name\":\"value\",\"type\":\"double\"},"
          + "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},"
          + "{\"name\":\"metadata\",\"type\":{\"type\":\"map\",\"values\":\"string\"}},"
          + "{\"name\":\"optionalField\",\"type\":[\"null\",\"string\"]}" + "]}");

  /** A schema with nested records to test recursive decoding. */
  private static final Schema NESTED_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"Outer\",\"fields\":["
          + "{\"name\":\"inner\",\"type\":{\"type\":\"record\",\"name\":\"Inner\",\"fields\":["
          + "{\"name\":\"x\",\"type\":\"int\"}," + "{\"name\":\"y\",\"type\":\"int\"}" + "]}},"
          + "{\"name\":\"label\",\"type\":\"string\"}" + "]}");

  /** An enum schema. */
  private static final Schema ENUM_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"EnumRecord\",\"fields\":["
          + "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"Status\","
          + "\"symbols\":[\"ACTIVE\",\"INACTIVE\",\"PENDING\"]}}," + "{\"name\":\"count\",\"type\":\"int\"}" + "]}");

  @FuzzTest
  void fuzzBinaryDecodingRecord(byte[] data) {
    decodeWithSchema(data, RECORD_SCHEMA);
  }

  @FuzzTest
  void fuzzBinaryDecodingNested(byte[] data) {
    decodeWithSchema(data, NESTED_SCHEMA);
  }

  @FuzzTest
  void fuzzBinaryDecodingEnum(byte[] data) {
    decodeWithSchema(data, ENUM_SCHEMA);
  }

  private static void decodeWithSchema(byte[] data, Schema schema) {
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
      GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
      reader.read(null, decoder);
    } catch (IOException | AvroRuntimeException | ArrayIndexOutOfBoundsException | UnsupportedOperationException e) {
      // Expected for malformed binary data
      // UnsupportedOperationException: thrown by SystemLimitException for oversized
      // strings/collections
    }
  }
}
