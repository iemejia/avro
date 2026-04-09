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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import org.apache.avro.AvroRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that all compression codecs enforce the decompression size limit,
 * preventing decompression bomb (zip bomb) denial-of-service attacks.
 *
 * <p>
 * The limit is controlled by the system property
 * {@code org.apache.avro.limits.decompress.maxLength}.
 */
public class TestDecompressionBomb {

  @AfterEach
  void resetDecompressLimit() {
    System.clearProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY);
    Codec.resetLimit();
  }

  /**
   * All codecs that perform actual compression/decompression. The null codec is
   * excluded because it passes data through unchanged and cannot amplify size.
   */
  static Stream<Arguments> compressibleCodecTypes() {
    return Stream.of(Arguments.of("deflate"), Arguments.of("bzip2"), Arguments.of("xz"), Arguments.of("zstandard"),
        Arguments.of("snappy"));
  }

  /**
   * Verify that decompression succeeds when the decompressed output is within the
   * configured limit.
   */
  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void decompressWithinLimitSucceeds(String codecName) throws IOException {
    // Set limit to 100KB - comfortably above our 50KB test data
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, String.valueOf(100 * 1024));
    Codec.resetLimit();

    byte[] input = TestAllCodecs.generateTestData(50_000);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    ByteBuffer decompressed = codec.decompress(compressed);
    assertEquals(ByteBuffer.wrap(input), decompressed);
  }

  /**
   * Verify that decompression throws {@link AvroRuntimeException} when the
   * decompressed output exceeds the configured limit. This is the core
   * decompression bomb protection test.
   */
  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void decompressExceedingLimitThrows(String codecName) throws IOException {
    // Set a very small limit: 1KB
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, "1024");
    Codec.resetLimit();

    // Compress 10KB of highly compressible data. Decompression will produce
    // 10KB of output which exceeds the 1KB limit.
    byte[] input = TestAllCodecs.generateTestData(10_000);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    AvroRuntimeException ex = assertThrows(AvroRuntimeException.class, () -> codec.decompress(compressed));

    assertTrue(ex.getMessage().contains("exceeds maximum allowed size"),
        "Expected message about exceeding limit, got: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("1024"),
        "Expected message to contain the configured limit '1024', got: " + ex.getMessage());
    assertTrue(ex.getMessage().contains(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY),
        "Expected message to reference the system property, got: " + ex.getMessage());
  }

  /**
   * Verify that the null codec is not affected by the decompression limit. The
   * null codec simply returns the input buffer without any decompression, so it
   * cannot be exploited as a decompression bomb.
   */
  @Test
  void nullCodecNotAffectedByLimit() throws IOException {
    // Set limit to just 1 byte - extremely restrictive
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, "1");
    Codec.resetLimit();

    byte[] input = TestAllCodecs.generateTestData(10_000);
    Codec codec = CodecFactory.fromString("null").createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    // NullCodec passes data through unchanged - should not throw
    ByteBuffer decompressed = codec.decompress(compressed);
    assertEquals(ByteBuffer.wrap(input), decompressed);
  }

  /**
   * Verify that the default limit (200MB) allows normal-sized data to be
   * decompressed without errors when no system property is set.
   */
  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void defaultLimitAllowsNormalData(String codecName) throws IOException {
    // Ensure no custom property is set - default 200MB limit applies
    System.clearProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY);
    Codec.resetLimit();

    byte[] input = TestAllCodecs.generateTestData(500_000);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    ByteBuffer decompressed = codec.decompress(compressed);
    assertEquals(ByteBuffer.wrap(input), decompressed);
  }

  /**
   * Verify that the limit is precise: data exactly at the limit succeeds, data
   * one byte over fails. Uses a known-size input so the decompressed size is
   * predictable.
   */
  @ParameterizedTest
  @MethodSource("compressibleCodecTypes")
  void limitBoundaryBehavior(String codecName) throws IOException {
    int dataSize = 10_000;
    byte[] input = TestAllCodecs.generateTestData(dataSize);
    Codec codec = CodecFactory.fromString(codecName).createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    // With limit exactly at data size, decompression should succeed
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, String.valueOf(dataSize));
    Codec.resetLimit();
    ByteBuffer decompressed = codec.decompress(compressed);
    assertEquals(ByteBuffer.wrap(input), decompressed);

    // With limit one byte below data size, decompression should fail
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, String.valueOf(dataSize - 1));
    Codec.resetLimit();

    // Need to rewind the compressed buffer for codecs that consumed it
    compressed.rewind();
    assertThrows(AvroRuntimeException.class, () -> codec.decompress(compressed));
  }

  /**
   * Verify that a custom system property value is respected and that the limit
   * can be changed at runtime via {@link Codec#resetLimit()}.
   */
  @Test
  void customLimitFromSystemProperty() throws IOException {
    byte[] input = TestAllCodecs.generateTestData(5_000);
    Codec codec = CodecFactory.fromString("deflate").createInstance();
    ByteBuffer compressed = codec.compress(ByteBuffer.wrap(input));

    // With a 2KB limit, 5KB data should fail
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, "2048");
    Codec.resetLimit();
    assertThrows(AvroRuntimeException.class, () -> codec.decompress(compressed));

    // Increase limit to 10KB - same data should now succeed
    System.setProperty(Codec.MAX_DECOMPRESS_LENGTH_PROPERTY, "10240");
    Codec.resetLimit();
    compressed.rewind();
    ByteBuffer decompressed = codec.decompress(compressed);
    assertEquals(ByteBuffer.wrap(input), decompressed);
  }
}
