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

package org.apache.avro.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.avro.AvroRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link DecompressLimiter}. */
public class TestDecompressLimiter {

  @AfterEach
  void resetToDefault() {
    System.clearProperty(DecompressLimiter.MAX_LENGTH_PROPERTY);
    DecompressLimiter.resetLimit();
  }

  // ---------- resetLimit ----------

  @Test
  void defaultLimitIs200MB() {
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(200L * 1024 * 1024));
    assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(200L * 1024 * 1024 + 1));
  }

  @Test
  void resetLimitReadsCustomProperty() {
    setLimit(5000);
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(5000));
    assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(5001));
  }

  @Test
  void resetLimitIgnoresZero() {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "0");
    DecompressLimiter.resetLimit();
    // Should fall back to default 200 MB
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(200L * 1024 * 1024));
  }

  @Test
  void resetLimitIgnoresNegativeValue() {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "-1");
    DecompressLimiter.resetLimit();
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(200L * 1024 * 1024));
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "", "12.5", "9999999999999999999" })
  void resetLimitIgnoresUnparseableValues(String bad) {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, bad);
    DecompressLimiter.resetLimit();
    // Should fall back to default 200 MB
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(200L * 1024 * 1024));
  }

  @Test
  void resetLimitCanBeCalledRepeatedly() {
    setLimit(1000);
    assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(1001));

    setLimit(2000);
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(1001));
    assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(2001));
  }

  // ---------- checkLimit ----------

  @Test
  void checkLimitPassesWhenWithinLimit() {
    setLimit(10_000);
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(9_999));
  }

  @Test
  void checkLimitPassesAtExactBoundary() {
    setLimit(10_000);
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(10_000));
  }

  @Test
  void checkLimitThrowsOneByteOverBoundary() {
    setLimit(10_000);
    assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(10_001));
  }

  @Test
  void checkLimitPassesForZeroSize() {
    setLimit(1);
    assertDoesNotThrow(() -> DecompressLimiter.checkLimit(0));
  }

  @Test
  void checkLimitExceptionContainsUsefulDetails() {
    setLimit(1024);
    AvroRuntimeException ex = assertThrows(AvroRuntimeException.class, () -> DecompressLimiter.checkLimit(2048));
    assertTrue(ex.getMessage().contains("2048"), "should contain actual size");
    assertTrue(ex.getMessage().contains("1024"), "should contain configured limit");
    assertTrue(ex.getMessage().contains(DecompressLimiter.MAX_LENGTH_PROPERTY), "should name the system property");
  }

  // ---------- boundedCopy ----------

  @Test
  void boundedCopyTransfersAllDataWithinLimit() throws IOException {
    setLimit(100_000);
    byte[] data = generateData(50_000);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    DecompressLimiter.boundedCopy(new ByteArrayInputStream(data), out);

    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  void boundedCopyThrowsWhenDataExceedsLimit() {
    setLimit(1_000);
    byte[] data = generateData(5_000);

    assertThrows(AvroRuntimeException.class,
        () -> DecompressLimiter.boundedCopy(new ByteArrayInputStream(data), new ByteArrayOutputStream()));
  }

  @Test
  void boundedCopyPassesAtExactBoundary() throws IOException {
    int size = 10_000;
    setLimit(size);
    byte[] data = generateData(size);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    DecompressLimiter.boundedCopy(new ByteArrayInputStream(data), out);

    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  void boundedCopyHandlesEmptyStream() throws IOException {
    setLimit(1);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    DecompressLimiter.boundedCopy(new ByteArrayInputStream(new byte[0]), out);

    assertEquals(0, out.size());
  }

  // ---------- boundedInflate ----------

  @Test
  void boundedInflateDecompressesCorrectly() throws IOException {
    byte[] original = generateData(10_000);
    byte[] deflated = deflateRaw(original);
    setLimit(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(deflated);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    DecompressLimiter.boundedInflate(inflater, out);
    inflater.end();

    assertArrayEquals(original, out.toByteArray());
  }

  @Test
  void boundedInflateThrowsWhenOutputExceedsLimit() {
    byte[] original = generateData(10_000);
    byte[] deflated = deflateRaw(original);
    setLimit(1_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(deflated);

    assertThrows(AvroRuntimeException.class,
        () -> DecompressLimiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    inflater.end();
  }

  @Test
  void boundedInflateRejectsTruncatedInput() {
    byte[] deflated = deflateRaw(generateData(10_000));
    byte[] truncated = new byte[deflated.length / 2];
    System.arraycopy(deflated, 0, truncated, 0, truncated.length);
    setLimit(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(truncated);

    IOException ex = assertThrows(IOException.class,
        () -> DecompressLimiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    assertTrue(ex.getMessage().contains("Invalid deflate data"));
    inflater.end();
  }

  @Test
  void boundedInflateRejectsCorruptData() {
    byte[] garbage = { 0x00, 0x01, 0x02, 0x03, (byte) 0xFF };
    setLimit(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(garbage);

    IOException ex = assertThrows(IOException.class,
        () -> DecompressLimiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    assertTrue(ex.getMessage().contains("Invalid deflate data"));
    inflater.end();
  }

  // ---------- codec integration ----------
  //
  // Verify that every compressing Avro codec actually delegates to
  // DecompressLimiter so the protection cannot be silently bypassed.

  static Stream<Arguments> compressibleCodecTypes() {
    return Stream.of(Arguments.of("deflate"), Arguments.of("bzip2"), Arguments.of("xz"), Arguments.of("zstandard"),
        Arguments.of("snappy"));
  }

  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void codecRejectsDecompressionBomb(String codecName) throws IOException {
    setLimit(1024);

    byte[] input = TestAllCodecs.generateTestData(10_000);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    assertThrows(AvroRuntimeException.class, () -> codec.decompress(compressed));
  }

  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void codecAllowsDataWithinLimit(String codecName) throws IOException {
    setLimit(100 * 1024);

    byte[] input = TestAllCodecs.generateTestData(50_000);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    assertEquals(ByteBuffer.wrap(input), codec.decompress(compressed));
  }

  @Test
  void nullCodecBypassesLimit() throws IOException {
    setLimit(1);

    byte[] input = TestAllCodecs.generateTestData(10_000);
    Codec codec = CodecFactory.fromString("null").createInstance();

    assertEquals(ByteBuffer.wrap(input), codec.decompress(codec.compress(ByteBuffer.wrap(input))));
  }

  // ---------- helpers ----------

  private static void setLimit(long limit) {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, String.valueOf(limit));
    DecompressLimiter.resetLimit();
  }

  private static byte[] generateData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) (65 + i % 10);
    }
    return data;
  }

  /** Produces raw deflate (RFC 1951, no zlib wrapper) output. */
  private static byte[] deflateRaw(byte[] data) {
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    deflater.setInput(data);
    deflater.finish();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    while (!deflater.finished()) {
      int len = deflater.deflate(buf);
      baos.write(buf, 0, len);
    }
    deflater.end();
    return baos.toByteArray();
  }
}
