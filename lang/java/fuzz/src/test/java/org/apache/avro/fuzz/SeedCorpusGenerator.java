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
import org.apache.avro.fuzz.model.ReflectRoundTripRecord;
import org.apache.avro.fuzz.model.SpecificProjectionEnum;
import org.apache.avro.fuzz.model.SpecificProjectionRecord;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
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
    writeBytes("BinaryDecodingFuzzerInputs/fuzzBinaryDecoding/seed-valid-record-single-node.bin",
        writeBinaryRecordWithSingleNode());
    writeBytes("BinaryDecodingFuzzerInputs/fuzzDirectBinaryDecoding/seed-valid-record.bin", writeBinaryRecord());
    writeBytes("BinaryDecodingFuzzerInputs/fuzzDirectBinaryDecoding/seed-valid-record-single-node.bin",
        writeBinaryRecordWithSingleNode());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileReader/seed-valid-container.avro", writeContainerFile());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileReaderWithResolution/seed-valid-container.avro",
        writeContainerFile());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileStream/seed-valid-container.avro", writeContainerFile());
    writeBytes("DataFileReaderFuzzerInputs/fuzzDataFileStreamWithResolution/seed-valid-container.avro",
        writeContainerFile());
    writeBytes("RoundTripFuzzerInputs/fuzzBinaryRoundTrip/seed-valid-roundtrip.bin", writeRoundTripBinary());
    writeBytes("RoundTripFuzzerInputs/fuzzJsonRoundTrip/seed-valid-roundtrip.json", writeRoundTripJson());
    writeBytes("RoundTripFuzzerInputs/fuzzDataFileRoundTrip/seed-valid-roundtrip.avro", writeRoundTripContainer());
    writeBytes("ProjectionFuzzerInputs/fuzzSpecificProjection/seed-valid-specific.bin",
        writeSpecificProjectionRecord());
    writeBytes("ProjectionFuzzerInputs/fuzzReflectProjection/seed-valid-reflect.bin", writeReflectProjectionRecord());
    writeBytes("SingleObjectFuzzerInputs/fuzzSingleObjectDecoding/seed-valid-single-object.bin",
        writeSingleObjectRecord());
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

  private static byte[] writeBinaryRecordWithSingleNode() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(FuzzSupport.BINARY_WRITER_SCHEMA);
    writer.write(sampleBinaryRecordWithSingleNode(), encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeRoundTripBinary() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    new GenericDatumWriter<GenericRecord>(FuzzSupport.ROUND_TRIP_SCHEMA).write(sampleRoundTripRecord(), encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeRoundTripJson() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    JsonEncoder encoder = EncoderFactory.get().jsonEncoder(FuzzSupport.ROUND_TRIP_SCHEMA, output);
    new GenericDatumWriter<GenericRecord>(FuzzSupport.ROUND_TRIP_SCHEMA).write(sampleRoundTripRecord(), encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeRoundTripContainer() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(
        new GenericDatumWriter<>(FuzzSupport.ROUND_TRIP_SCHEMA))) {
      writer.create(FuzzSupport.ROUND_TRIP_SCHEMA, output);
      writer.append(sampleRoundTripRecord());
    }
    return output.toByteArray();
  }

  private static byte[] writeSpecificProjectionRecord() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    new SpecificDatumWriter<SpecificProjectionRecord>(SpecificProjectionRecord.class).write(sampleSpecificRecord(),
        encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeReflectProjectionRecord() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    new ReflectDatumWriter<ReflectRoundTripRecord>(ReflectRoundTripRecord.class, ReflectData.AllowNull.get())
        .write(sampleReflectRecord(), encoder);
    encoder.flush();
    return output.toByteArray();
  }

  private static byte[] writeSingleObjectRecord() throws IOException {
    return toArray(
        new BinaryMessageEncoder<>(GenericData.get(), FuzzSupport.ROUND_TRIP_SCHEMA).encode(sampleRoundTripRecord()));
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

  private static GenericRecord sampleBinaryRecordWithSingleNode() {
    GenericRecord node = new GenericData.Record(NODE_SCHEMA);
    node.put("value", "solo");
    node.put("next", null);

    GenericRecord record = new GenericData.Record(FuzzSupport.BINARY_WRITER_SCHEMA);
    record.put("id", 11L);
    record.put("legacyName", "seed-single");
    record.put("createdDate", 1);
    record.put("payload", java.nio.ByteBuffer.wrap(new byte[0]));
    record.put("hash",
        new GenericData.Fixed(FuzzSupport.BINARY_WRITER_SCHEMA.getField("hash").schema(), new byte[] { 9, 8, 7, 6 }));
    record.put("node", node);
    return record;
  }

  private static GenericRecord sampleRoundTripRecord() {
    GenericRecord record = new GenericData.Record(FuzzSupport.ROUND_TRIP_SCHEMA);
    record.put("id", 19L);
    record.put("name", "roundtrip");
    record.put("createdDate", 42);
    record.put("active", true);
    record.put("score", 12.3d);
    record.put("payload", java.nio.ByteBuffer.wrap("blob".getBytes(StandardCharsets.UTF_8)));
    record.put("tags", java.util.List.of("alpha", "beta"));
    record.put("counts", java.util.Map.of("x", 1L, "y", 2L));
    record.put("choice", "choice");
    record.put("hash",
        new GenericData.Fixed(FuzzSupport.ROUND_TRIP_SCHEMA.getField("hash").schema(), new byte[] { 4, 3, 2, 1 }));
    GenericRecord inner = new GenericData.Record(FuzzSupport.ROUND_TRIP_SCHEMA.getField("inner").schema());
    inner.put("x", 7);
    inner.put("y", 8);
    record.put("inner", inner);
    record.put("status",
        new GenericData.EnumSymbol(FuzzSupport.ROUND_TRIP_SCHEMA.getField("status").schema(), "READY"));
    return record;
  }

  private static SpecificProjectionRecord sampleSpecificRecord() {
    return new SpecificProjectionRecord(9, "specific", java.util.List.of("one", "two"), SpecificProjectionEnum.BETA);
  }

  private static ReflectRoundTripRecord sampleReflectRecord() {
    ReflectRoundTripRecord record = new ReflectRoundTripRecord();
    record.id = 5L;
    record.name = "reflect";
    record.tags = java.util.List.of("first", "second");
    record.counts = java.util.Map.of("left", 1L, "right", 2L);
    record.alias = "seen";
    return record;
  }

  private static byte[] toArray(java.nio.ByteBuffer buffer) {
    java.nio.ByteBuffer copy = buffer.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  private static void writeBytes(String relativePath, byte[] data) throws IOException {
    Path output = RESOURCE_ROOT.resolve(relativePath);
    Files.createDirectories(output.getParent());
    Files.write(output, data);
  }
}
