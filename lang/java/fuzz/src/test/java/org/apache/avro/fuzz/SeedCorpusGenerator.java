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

import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class SeedCorpusGenerator {
  private static final Path RESOURCE_ROOT = Path.of("src/test/resources/org/apache/avro/fuzz");
  private static final org.apache.avro.Schema NODE_SCHEMA = FuzzSupport.BINARY_WRITER_SCHEMA.getField("node").schema();

  @Test
  void generateSeeds() throws IOException {
    writeBytes("BinaryDecodingFuzzerInputs/fuzzBinaryDecoding/seed-valid-record.bin", writeBinaryRecord());
    writeBytes("BinaryDecodingFuzzerInputs/fuzzDirectBinaryDecoding/seed-valid-record.bin", writeBinaryRecord());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileReader/seed-valid-container.avro", writeContainerFile());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileStream/seed-valid-container.avro", writeContainerFile());
  }

  private static byte[] writeBinaryRecord() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.BINARY_WRITER_SCHEMA);
    writer.write(sampleBinaryRecord(), encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeContainerFile() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    GenericDatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(FuzzSupport.BINARY_WRITER_SCHEMA);
    try (DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(datumWriter)) {
      fileWriter.create(FuzzSupport.BINARY_WRITER_SCHEMA, output);
      fileWriter.append(sampleBinaryRecord());
    }
    return output.toByteArray();
  }

  private static GenericRecord sampleBinaryRecord() {
    GenericRecord nodeTail = new GenericData.Record(NODE_SCHEMA);
    nodeTail.put("value", "tail");
    nodeTail.put("next", null);

    GenericRecord nodeHead = new GenericData.Record(NODE_SCHEMA);
    nodeHead.put("value", "head");
    nodeHead.put("next", nodeTail);

    GenericRecord record = new GenericData.Record(FuzzSupport.BINARY_WRITER_SCHEMA);
    record.put("id", 7L);
    record.put("legacyName", "seed");
    record.put("createdDate", 12);
    record.put("payload", java.nio.ByteBuffer.wrap("payload".getBytes(StandardCharsets.UTF_8)));
    record.put("hash",
        new GenericData.Fixed(FuzzSupport.BINARY_WRITER_SCHEMA.getField("hash").schema(), new byte[] { 1, 2, 3, 4 }));
    record.put("node", nodeHead);
    return record;
  }

  private static void writeBytes(String relativePath, byte[] data) throws IOException {
    Path output = RESOURCE_ROOT.resolve(relativePath);
    Files.createDirectories(output.getParent());
    Files.write(output, data);
  }
}
