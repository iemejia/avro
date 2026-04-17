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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable guard against decompression bomb (zip bomb) attacks. Enforces an
 * upper bound on decompressed output size.
 *
 * <p>
 * Instances are immutable and therefore unconditionally thread-safe. The limit
 * is captured once at construction time and never changes.
 *
 * <p>
 * The factory method {@link #fromSystemProperty()} reads the system property
 * {@value #MAX_LENGTH_PROPERTY} and returns an instance with that limit (or the
 * default of 200&nbsp;MB when the property is absent or invalid).
 */
final class DecompressLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(DecompressLimiter.class);

  /** System property that controls the maximum decompressed size in bytes. */
  static final String MAX_LENGTH_PROPERTY = "org.apache.avro.limits.decompress.maxLength";

  private static final long DEFAULT_MAX_LENGTH = 200L * 1024 * 1024; // 200 MB

  private static final int BUFFER_SIZE = 8192;

  /** The maximum decompressed size in bytes enforced by this instance. */
  private final long maxLength;

  /**
   * Creates a limiter with the given maximum decompressed size.
   *
   * @param maxLength the maximum number of decompressed bytes allowed; must be
   *                  positive
   * @throws IllegalArgumentException if {@code maxLength} is not positive
   */
  DecompressLimiter(long maxLength) {
    if (maxLength <= 0) {
      throw new IllegalArgumentException("maxLength must be positive, got: " + maxLength);
    }
    this.maxLength = maxLength;
  }

  /**
   * Creates a {@code DecompressLimiter} by reading the system property
   * {@value #MAX_LENGTH_PROPERTY}. If the property is absent, not a valid
   * positive long, or otherwise unparseable, the default limit of 200&nbsp;MB is
   * used.
   *
   * @return a new immutable {@code DecompressLimiter}
   */
  static DecompressLimiter fromSystemProperty() {
    String prop = System.getProperty(MAX_LENGTH_PROPERTY);
    long limit = DEFAULT_MAX_LENGTH;
    if (prop != null) {
      try {
        long parsed = Long.parseLong(prop);
        if (parsed <= 0) {
          LOG.warn("Invalid value '{}' for property '{}': must be positive. Using default: {}", prop,
              MAX_LENGTH_PROPERTY, DEFAULT_MAX_LENGTH);
        } else {
          limit = parsed;
        }
      } catch (NumberFormatException e) {
        LOG.warn("Could not parse property '{}' value '{}'. Using default: {}", MAX_LENGTH_PROPERTY, prop,
            DEFAULT_MAX_LENGTH);
      }
    }
    return new DecompressLimiter(limit);
  }

  /**
   * Returns the maximum decompressed size enforced by this instance.
   *
   * @return the limit in bytes
   */
  long getMaxLength() {
    return maxLength;
  }

  /**
   * Throws a {@link TrevniRuntimeException} if {@code size} exceeds the
   * configured maximum. Suitable for codecs that know the output size before
   * decompression (e.g.&nbsp;Snappy).
   *
   * @param size the (expected) decompressed size in bytes
   */
  void checkLimit(long size) {
    checkAgainst(size, maxLength);
  }

  /**
   * Copies data from a decompression {@link InputStream} to an
   * {@link OutputStream}, aborting if the total bytes written exceed the
   * configured limit.
   *
   * @param in  the decompression input stream
   * @param out the output stream to write decompressed data to
   * @throws IOException            if an I/O error occurs
   * @throws TrevniRuntimeException if the decompressed size exceeds the limit
   */
  void boundedCopy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long totalBytes = 0;
    int len;
    while ((len = in.read(buffer)) != -1) {
      totalBytes += len;
      checkAgainst(totalBytes, maxLength);
      out.write(buffer, 0, len);
    }
  }

  /**
   * Inflates data into the provided output stream while enforcing the
   * decompression size limit and rejecting truncated or corrupt input.
   *
   * @param inflater a {@link Inflater} whose input has already been set
   * @param out      the output stream to write inflated data to
   * @throws IOException            if the deflate data is invalid or truncated
   * @throws TrevniRuntimeException if the decompressed size exceeds the limit
   */
  void boundedInflate(Inflater inflater, OutputStream out) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long totalBytes = 0;

    try {
      while (true) {
        int len = inflater.inflate(buffer);
        if (len > 0) {
          totalBytes += len;
          checkAgainst(totalBytes, maxLength);
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

  // ---- internal ----

  private static void checkAgainst(long size, long limit) {
    if (size > limit) {
      throw new TrevniRuntimeException("Decompressed size " + size + " (bytes) exceeds maximum allowed size " + limit
          + ". This can be configured by setting the system property '" + MAX_LENGTH_PROPERTY + "'");
    }
  }
}
