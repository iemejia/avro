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

package org.apache.trevni;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link DecompressLimiter}. */
public class TestDecompressLimiter {

  @AfterEach
  void clearSystemProperty() {
    System.clearProperty(DecompressLimiter.MAX_LENGTH_PROPERTY);
  }

  // ---------- constructor ----------

  @Test
  void constructorRejectsZero() {
    assertThrows(IllegalArgumentException.class, () -> new DecompressLimiter(0));
  }

  @Test
  void constructorRejectsNegative() {
    assertThrows(IllegalArgumentException.class, () -> new DecompressLimiter(-1));
  }

  @Test
  void constructorAcceptsPositiveValue() {
    DecompressLimiter limiter = new DecompressLimiter(42);
    assertEquals(42, limiter.getMaxLength());
  }

  // ---------- fromSystemProperty ----------

  @Test
  void fromSystemPropertyDefaultIs200MB() {
    DecompressLimiter limiter = DecompressLimiter.fromSystemProperty();
    assertEquals(200L * 1024 * 1024, limiter.getMaxLength());
  }

  @Test
  void fromSystemPropertyReadsCustomValue() {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "5000");
    DecompressLimiter limiter = DecompressLimiter.fromSystemProperty();
    assertEquals(5000, limiter.getMaxLength());
  }

  @Test
  void fromSystemPropertyIgnoresZero() {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "0");
    DecompressLimiter limiter = DecompressLimiter.fromSystemProperty();
    assertEquals(200L * 1024 * 1024, limiter.getMaxLength());
  }

  @Test
  void fromSystemPropertyIgnoresNegativeValue() {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "-1");
    DecompressLimiter limiter = DecompressLimiter.fromSystemProperty();
    assertEquals(200L * 1024 * 1024, limiter.getMaxLength());
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "", "12.5", "9999999999999999999" })
  void fromSystemPropertyIgnoresUnparseableValues(String bad) {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, bad);
    DecompressLimiter limiter = DecompressLimiter.fromSystemProperty();
    assertEquals(200L * 1024 * 1024, limiter.getMaxLength());
  }

  // ---------- checkLimit ----------

  @Test
  void checkLimitPassesWhenWithinLimit() {
    DecompressLimiter limiter = new DecompressLimiter(10_000);
    assertDoesNotThrow(() -> limiter.checkLimit(9_999));
  }

  @Test
  void checkLimitPassesAtExactBoundary() {
    DecompressLimiter limiter = new DecompressLimiter(10_000);
    assertDoesNotThrow(() -> limiter.checkLimit(10_000));
  }

  @Test
  void checkLimitThrowsOneByteOverBoundary() {
    DecompressLimiter limiter = new DecompressLimiter(10_000);
    assertThrows(TrevniRuntimeException.class, () -> limiter.checkLimit(10_001));
  }

  @Test
  void checkLimitPassesForZeroSize() {
    DecompressLimiter limiter = new DecompressLimiter(1);
    assertDoesNotThrow(() -> limiter.checkLimit(0));
  }

  @Test
  void checkLimitExceptionContainsUsefulDetails() {
    DecompressLimiter limiter = new DecompressLimiter(1024);
    TrevniRuntimeException ex = assertThrows(TrevniRuntimeException.class, () -> limiter.checkLimit(2048));
    assertTrue(ex.getMessage().contains("2048"), "should contain actual size");
    assertTrue(ex.getMessage().contains("1024"), "should contain configured limit");
    assertTrue(ex.getMessage().contains(DecompressLimiter.MAX_LENGTH_PROPERTY), "should name the system property");
  }

  // ---------- boundedCopy ----------

  @Test
  void boundedCopyTransfersAllDataWithinLimit() throws IOException {
    DecompressLimiter limiter = new DecompressLimiter(100_000);
    byte[] data = generateData(50_000);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    limiter.boundedCopy(new ByteArrayInputStream(data), out);

    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  void boundedCopyThrowsWhenDataExceedsLimit() {
    DecompressLimiter limiter = new DecompressLimiter(1_000);
    byte[] data = generateData(5_000);

    assertThrows(TrevniRuntimeException.class,
        () -> limiter.boundedCopy(new ByteArrayInputStream(data), new ByteArrayOutputStream()));
  }

  @Test
  void boundedCopyPassesAtExactBoundary() throws IOException {
    int size = 10_000;
    DecompressLimiter limiter = new DecompressLimiter(size);
    byte[] data = generateData(size);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    limiter.boundedCopy(new ByteArrayInputStream(data), out);

    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  void boundedCopyHandlesEmptyStream() throws IOException {
    DecompressLimiter limiter = new DecompressLimiter(1);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    limiter.boundedCopy(new ByteArrayInputStream(new byte[0]), out);

    assertEquals(0, out.size());
  }

  // ---------- boundedInflate ----------

  @Test
  void boundedInflateDecompressesCorrectly() throws IOException {
    byte[] original = generateData(10_000);
    byte[] deflated = deflateRaw(original);
    DecompressLimiter limiter = new DecompressLimiter(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(deflated);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    limiter.boundedInflate(inflater, out);
    inflater.end();

    assertArrayEquals(original, out.toByteArray());
  }

  @Test
  void boundedInflateThrowsWhenOutputExceedsLimit() {
    byte[] original = generateData(10_000);
    byte[] deflated = deflateRaw(original);
    DecompressLimiter limiter = new DecompressLimiter(1_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(deflated);

    assertThrows(TrevniRuntimeException.class, () -> limiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    inflater.end();
  }

  @Test
  void boundedInflateRejectsTruncatedInput() {
    byte[] deflated = deflateRaw(generateData(10_000));
    byte[] truncated = new byte[deflated.length / 2];
    System.arraycopy(deflated, 0, truncated, 0, truncated.length);
    DecompressLimiter limiter = new DecompressLimiter(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(truncated);

    IOException ex = assertThrows(IOException.class,
        () -> limiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    assertTrue(ex.getMessage().contains("Invalid deflate data"));
    inflater.end();
  }

  @Test
  void boundedInflateRejectsCorruptData() {
    byte[] garbage = { 0x00, 0x01, 0x02, 0x03, (byte) 0xFF };
    DecompressLimiter limiter = new DecompressLimiter(100_000);

    Inflater inflater = new Inflater(true);
    inflater.setInput(garbage);

    IOException ex = assertThrows(IOException.class,
        () -> limiter.boundedInflate(inflater, new ByteArrayOutputStream()));
    assertTrue(ex.getMessage().contains("Invalid deflate data"));
    inflater.end();
  }

  // ---------- codec integration ----------
  //
  // Verify that every compressing Trevni codec actually delegates to
  // DecompressLimiter so the protection cannot be silently bypassed.
  //
  // The system property is set BEFORE creating codec instances because
  // the limit is captured at construction time (via Codec's no-arg
  // constructor calling DecompressLimiter.fromSystemProperty()).

  static Stream<Arguments> compressibleCodecTypes() {
    return Stream.of(Arguments.of("deflate"), Arguments.of("bzip2"), Arguments.of("snappy"));
  }

  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void codecRejectsDecompressionBomb(String codecName) throws IOException {
    // Set property BEFORE creating the codec so the limit is captured
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "1024");
    Codec codec = TestAllCodecs.getCodec(codecName);

    byte[] input = TestAllCodecs.generateTestData(10_000);
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    assertThrows(TrevniRuntimeException.class, () -> codec.decompress(compressed));
  }

  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void codecAllowsDataWithinLimit(String codecName) throws IOException {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, String.valueOf(100 * 1024));
    Codec codec = TestAllCodecs.getCodec(codecName);

    byte[] input = TestAllCodecs.generateTestData(50_000);
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    assertEquals(ByteBuffer.wrap(input), codec.decompress(compressed));
  }

  @Test
  void nullCodecBypassesLimit() throws IOException {
    System.setProperty(DecompressLimiter.MAX_LENGTH_PROPERTY, "1");
    Codec codec = TestAllCodecs.getCodec("null");

    byte[] input = TestAllCodecs.generateTestData(10_000);

    assertEquals(ByteBuffer.wrap(input), codec.decompress(codec.compress(ByteBuffer.wrap(input))));
  }

  // ---------- helpers ----------

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
