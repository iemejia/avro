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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Interface for compression codecs. */
abstract class Codec {

  private static final Logger LOG = LoggerFactory.getLogger(Codec.class);

  static final String MAX_DECOMPRESS_LENGTH_PROPERTY = "org.apache.avro.limits.decompress.maxLength";
  private static final long DEFAULT_MAX_DECOMPRESS_LENGTH = 200L * 1024 * 1024; // 200MB default limit

  private static volatile long maxDecompressLength;

  private static final int DECOMPRESS_BUFFER_SIZE = 8192;

  static {
    resetLimit();
  }

  /** Re-read the decompression limit from the system property. */
  // VisibleForTesting
  static void resetLimit() {
    String prop = System.getProperty(MAX_DECOMPRESS_LENGTH_PROPERTY);
    long limit = DEFAULT_MAX_DECOMPRESS_LENGTH;
    if (prop != null) {
      try {
        long parsed = Long.parseLong(prop);
        if (parsed <= 0) {
          LOG.warn("Invalid value '{}' for property '{}': must be positive. Using default: {}", prop,
              MAX_DECOMPRESS_LENGTH_PROPERTY, DEFAULT_MAX_DECOMPRESS_LENGTH);
        } else {
          limit = parsed;
        }
      } catch (NumberFormatException e) {
        LOG.warn("Could not parse property '{}' value '{}'. Using default: {}", MAX_DECOMPRESS_LENGTH_PROPERTY, prop,
            DEFAULT_MAX_DECOMPRESS_LENGTH);
      }
    }
    maxDecompressLength = limit;
  }

  public static Codec get(MetaData meta) {
    String name = meta.getCodec();
    if (name == null || "null".equals(name))
      return new NullCodec();
    else if ("deflate".equals(name))
      return new DeflateCodec();
    else if ("snappy".equals(name))
      return new SnappyCodec();
    else if ("bzip2".equals(name))
      return new BZip2Codec();
    else
      throw new TrevniRuntimeException("Unknown codec: " + name);
  }

  /** Compress data */
  abstract ByteBuffer compress(ByteBuffer uncompressedData) throws IOException;

  /** Decompress data */
  abstract ByteBuffer decompress(ByteBuffer compressedData) throws IOException;

  // Codecs often reference the array inside a ByteBuffer. Compute the offset
  // to the start of data correctly in the case that our ByteBuffer
  // is a slice() of another.
  protected static int computeOffset(ByteBuffer data) {
    return data.arrayOffset() + data.position();
  }

  /**
   * Throws a {@link TrevniRuntimeException} if the decompressed size exceeds the
   * configured maximum. The limit can be configured via the system property
   * {@code org.apache.avro.limits.decompress.maxLength}.
   *
   * @param size the current decompressed size in bytes
   */
  static void checkDecompressLimit(long size) {
    if (size > maxDecompressLength) {
      throw new TrevniRuntimeException(
          "Decompressed size " + size + " (bytes) exceeds maximum allowed size " + maxDecompressLength
              + ". This can be configured by setting the system property '" + MAX_DECOMPRESS_LENGTH_PROPERTY + "'");
    }
  }

  /**
   * Copies data from an {@link InputStream} to an {@link OutputStream} while
   * enforcing the decompression size limit. This is the primary utility for
   * stream-based codecs to guard against decompression bombs.
   *
   * @param in  the decompression input stream
   * @param out the output stream to write decompressed data to
   * @throws IOException            if an I/O error occurs
   * @throws TrevniRuntimeException if the decompressed size exceeds the limit
   */
  static void boundedCopy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[DECOMPRESS_BUFFER_SIZE];
    long totalBytes = 0;
    int len;
    while ((len = in.read(buffer)) != -1) {
      totalBytes += len;
      checkDecompressLimit(totalBytes);
      out.write(buffer, 0, len);
    }
  }

  /**
   * Inflates data into the provided output stream while enforcing the
   * decompression size limit and rejecting truncated or no-progress input.
   */
  static void boundedInflate(Inflater inflater, OutputStream out) throws IOException {
    byte[] buffer = new byte[DECOMPRESS_BUFFER_SIZE];
    long totalBytes = 0;

    try {
      while (true) {
        int len = inflater.inflate(buffer);
        if (len > 0) {
          totalBytes += len;
          checkDecompressLimit(totalBytes);
          out.write(buffer, 0, len);
          continue;
        }
        if (inflater.finished()) {
          break;
        }
        if (inflater.needsDictionary()) {
          throw new IOException("Invalid deflate data: dictionary required");
        }
        if (inflater.needsInput()) {
          throw new IOException("Invalid deflate data: truncated input");
        }
        throw new IOException("Invalid deflate data: unable to make progress");
      }
    } catch (DataFormatException e) {
      throw new IOException("Invalid deflate data", e);
    }
  }
}
