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
package org.apache.avro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.apache.avro.file.DataFileConstants;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for bugs found by fuzz testing. Each test verifies that
 * malformed input produces a clear, descriptive exception rather than an
 * unexpected internal error (NPE, ArrayIndexOutOfBoundsException, etc.).
 */
class TestFuzzFindings {

  /**
   * FastReaderBuilder.createUnionReader must throw AvroRuntimeException with a
   * descriptive message when the binary data contains a union index that exceeds
   * the number of branches in the union schema.
   */
  @Test
  void outOfBoundsUnionIndexThrowsAvroRuntimeException() throws IOException {
    // Schema: a record with a union field ["null", "int"] (2 branches, valid indices: 0,1)
    Schema unionSchema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.NULL),
        Schema.create(Schema.Type.INT)));
    Schema recordSchema = Schema.createRecord("TestRecord", null, "test", false);
    recordSchema.setFields(Arrays.asList(new Schema.Field("value", unionSchema, null, null)));

    // Write a valid record first to get proper binary encoding structure
    ByteArrayOutputStream validOut = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(validOut, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(recordSchema);
    GenericRecord record = new GenericRecordBuilder(recordSchema).set("value", 42).build();
    writer.write(record, encoder);
    encoder.flush();

    // Now craft malformed binary: write a union index of 16 (way out of bounds)
    // Avro encodes union index as a varint. Index 16 encodes as byte 0x20.
    byte[] malformed = new byte[] { 0x20, 0x00 };

    // Read with FastReaderBuilder enabled
    GenericData data = new GenericData();
    data.setFastReaderEnabled(true);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(recordSchema, recordSchema, data);

    AvroRuntimeException ex = assertThrows(AvroRuntimeException.class,
        () -> reader.read(null, DecoderFactory.get().binaryDecoder(malformed, null)));
    assertTrue(ex.getMessage().contains("Union index"), "Expected message about union index, got: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("out of bounds"),
        "Expected 'out of bounds' in message, got: " + ex.getMessage());
  }

  /**
   * Verify that a valid union index (0 for null, 1 for int) still works after the
   * bounds check was added.
   */
  @Test
  void validUnionIndexStillWorks() throws IOException {
    Schema unionSchema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.NULL),
        Schema.create(Schema.Type.INT)));
    Schema recordSchema = Schema.createRecord("TestRecord", null, "test", false);
    recordSchema.setFields(Arrays.asList(new Schema.Field("value", unionSchema, null, null)));

    // Write with index 1 (int branch), value = 42
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(recordSchema);
    GenericRecord record = new GenericRecordBuilder(recordSchema).set("value", 42).build();
    writer.write(record, encoder);
    encoder.flush();

    GenericData data = new GenericData();
    data.setFastReaderEnabled(true);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(recordSchema, recordSchema, data);
    GenericRecord result = reader.read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null));
    assertEquals(42, result.get("value"));
  }

  /**
   * Negative union index should also be caught by the bounds check.
   */
  @Test
  void negativeUnionIndexThrowsAvroRuntimeException() throws IOException {
    Schema unionSchema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.NULL),
        Schema.create(Schema.Type.INT)));
    Schema recordSchema = Schema.createRecord("TestRecord", null, "test", false);
    recordSchema.setFields(Arrays.asList(new Schema.Field("value", unionSchema, null, null)));

    // Avro varint for -1 is 0x01 (zigzag: (0 >>> 1) ^ -(0 & 1) = 0 for 0x00,
    // and -1 encodes as 0x01). Let's use raw bytes for index = -1.
    // zigzag(-1) = 1, so byte is 0x01. But wait, index 1 is valid for ["null","int"].
    // zigzag(-2) = 3, so byte is 0x03. That's also potentially valid.
    // Actually, readIndex() calls readInt() which uses zigzag decoding.
    // zigzag(0x20) = 16 (positive). For a negative, we need an odd encoded value
    // that decodes to negative: e.g. byte 0x01 decodes to -1 via zigzag.
    // Nope: zigzag decoding: (n >>> 1) ^ -(n & 1). So 0x01 -> (0) ^ (-1) = -1.
    // But readInt reads varints, not single bytes directly...
    // Actually for single-byte varints (value 0-127): readInt returns byte value
    // then zigzag-decodes: (1 >>> 1) ^ -(1 & 1) = 0 ^ -1 = -1.
    // Wait no — readIndex calls readInt which does varint + zigzag.
    // Byte 0x01 -> varint value 1 -> zigzag decode -> -1. No wait...
    // Actually BinaryDecoder.readInt reads a zigzag-encoded varint.
    // For value = -1: zigzag encode = (-1 << 1) ^ (-1 >> 31) = -2 ^ -1 = 1.
    // So single byte 0x01 is varint 1, which zigzag-decodes to -1.
    byte[] malformed = new byte[] { 0x01 };

    GenericData data = new GenericData();
    data.setFastReaderEnabled(true);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(recordSchema, recordSchema, data);

    AvroRuntimeException ex = assertThrows(AvroRuntimeException.class,
        () -> reader.read(null, DecoderFactory.get().binaryDecoder(malformed, null)));
    assertTrue(ex.getMessage().contains("Union index"), "Expected message about union index, got: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("-1"), "Expected negative index in message, got: " + ex.getMessage());
  }

  /**
   * DataFileStream must throw IOException (not NPE) when the container header
   * metadata is missing the required "avro.schema" entry.
   */
  @Test
  void missingSchemaMetadataThrowsIOException() throws IOException {
    // Construct a minimal Avro container file header without the schema key:
    // Magic (4 bytes) + metadata map (with some other key) + sync marker (16 bytes)
    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
    BinaryEncoder enc = EncoderFactory.get().binaryEncoder(headerBytes, null);

    // Write magic
    headerBytes.write(DataFileConstants.MAGIC);

    // Write metadata map with 1 entry (not "avro.schema")
    enc = EncoderFactory.get().binaryEncoder(headerBytes, enc);
    enc.writeMapStart();
    enc.setItemCount(1);
    enc.startItem();
    enc.writeString("avro.codec");
    enc.writeBytes("null".getBytes());
    enc.writeMapEnd();

    // Write sync marker (16 arbitrary bytes)
    byte[] sync = new byte[DataFileConstants.SYNC_SIZE];
    Arrays.fill(sync, (byte) 0x42);
    enc.writeFixed(sync);
    enc.flush();

    byte[] data = headerBytes.toByteArray();

    IOException ex = assertThrows(IOException.class,
        () -> new DataFileStream<>(new ByteArrayInputStream(data), new GenericDatumReader<>()));
    assertTrue(ex.getMessage().contains("missing schema"),
        "Expected 'missing schema' in message, got: " + ex.getMessage());
  }

  /**
   * DataFileStream should work normally when the schema metadata is present.
   */
  @Test
  void validContainerHeaderWithSchemaSucceeds() {
    assertDoesNotThrow(() -> {
      Schema schema = Schema.create(Schema.Type.STRING);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      GenericDatumWriter<CharSequence> writer = new GenericDatumWriter<>(schema);
      org.apache.avro.file.DataFileWriter<CharSequence> dfw = new org.apache.avro.file.DataFileWriter<>(writer);
      dfw.create(schema, out);
      dfw.append("hello");
      dfw.close();

      DataFileStream<CharSequence> stream = new DataFileStream<>(new ByteArrayInputStream(out.toByteArray()),
          new GenericDatumReader<>(schema));
      assertEquals("hello", stream.next().toString());
      stream.close();
    });
  }

  /**
   * ParseContext.resolve must throw AvroTypeException (not NPE) when a named
   * schema reference cannot be resolved.
   */
  @Test
  void unresolvedSchemaReferenceThrowsAvroTypeException() {
    // Parse a schema that references a type name that doesn't exist.
    // A union referencing an undefined named type will trigger resolve().
    String schemaJson = "{\"type\":\"record\",\"name\":\"Outer\",\"fields\":["
        + "{\"name\":\"ref\",\"type\":\"NonExistentType\"}" + "]}";

    AvroTypeException ex = assertThrows(AvroTypeException.class, () -> new SchemaParser().parse(schemaJson).mainSchema(),
        "Should throw AvroTypeException for unresolved schema reference");
    assertTrue(ex.getMessage().contains("Unknown") || ex.getMessage().contains("Undefined"),
        "Expected 'Unknown' or 'Undefined' in message, got: " + ex.getMessage());
  }

  /**
   * FastReaderBuilder.createEnumReader must throw AvroRuntimeException with a
   * descriptive message when the binary data contains an enum index that exceeds
   * the number of symbols in the enum schema.
   */
  @Test
  void outOfBoundsEnumIndexThrowsAvroRuntimeException() throws IOException {
    Schema enumSchema = Schema.createEnum("Color", null, "test", Arrays.asList("RED", "GREEN", "BLUE"));
    Schema recordSchema = Schema.createRecord("TestRecord", null, "test", false);
    recordSchema.setFields(Arrays.asList(new Schema.Field("color", enumSchema, null, null)));

    // Enum index 32 encoded as zigzag varint: zigzag(32) = 64 = 0x40
    byte[] malformed = new byte[] { 0x40 };

    GenericData data = new GenericData();
    data.setFastReaderEnabled(true);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(recordSchema, recordSchema, data);

    AvroRuntimeException ex = assertThrows(AvroRuntimeException.class,
        () -> reader.read(null, DecoderFactory.get().binaryDecoder(malformed, null)));
    assertTrue(ex.getMessage().contains("Enum index"), "Expected message about enum index, got: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("out of bounds"),
        "Expected 'out of bounds' in message, got: " + ex.getMessage());
  }

  /**
   * Valid enum indices should still work after the bounds check was added.
   */
  @Test
  void validEnumIndexStillWorks() throws IOException {
    Schema enumSchema = Schema.createEnum("Color", null, "test", Arrays.asList("RED", "GREEN", "BLUE"));
    Schema recordSchema = Schema.createRecord("TestRecord", null, "test", false);
    recordSchema.setFields(Arrays.asList(new Schema.Field("color", enumSchema, null, null)));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(recordSchema);
    GenericRecord record = new GenericRecordBuilder(recordSchema).set("color",
        new org.apache.avro.generic.GenericData.EnumSymbol(enumSchema, "GREEN")).build();
    writer.write(record, encoder);
    encoder.flush();

    GenericData data = new GenericData();
    data.setFastReaderEnabled(true);
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(recordSchema, recordSchema, data);
    GenericRecord result = reader.read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null));
    assertEquals("GREEN", result.get("color").toString());
  }
}
