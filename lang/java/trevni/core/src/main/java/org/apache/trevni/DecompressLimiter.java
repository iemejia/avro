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
 * Thread-safe guard against decompression bomb (zip bomb) attacks. Enforces a
 * configurable upper bound on decompressed output size.
 *
 * <p>
 * The maximum size is controlled by the system property
 * {@value #MAX_LENGTH_PROPERTY}. The default is 200&nbsp;MB.
 *
 * <p>
 * All methods are safe to call from any thread. The bounded I/O operations
 * ({@link #boundedCopy} and {@link #boundedInflate}) capture a snapshot of the
 * limit before they start, so a concurrent call to {@link #resetLimit()} cannot
 * cause inconsistent behaviour mid-operation.
 */
final class DecompressLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(DecompressLimiter.class);

  /** System property that controls the maximum decompressed size in bytes. */
  static final String MAX_LENGTH_PROPERTY = "org.apache.avro.limits.decompress.maxLength";

  private static final long DEFAULT_MAX_LENGTH = 200L * 1024 * 1024; // 200 MB

  private static final int BUFFER_SIZE = 8192;

  /**
   * Current limit. Declared volatile so that a write from any thread is
   * immediately visible to all other threads.
   */
  private static volatile long maxLength;

  static {
    resetLimit();
  }

  private DecompressLimiter() {
    // utility class -- not instantiable
  }

  /**
   * Re-read the decompression limit from the system property. Values that are not
   * positive longs are ignored (with a warning) and the default is used.
   */
  // VisibleForTesting
  static void resetLimit() {
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
    maxLength = limit;
  }

  /**
   * Throws a {@link TrevniRuntimeException} if {@code size} exceeds the
   * configured maximum. Suitable for codecs that know the output size before
   * decompression (e.g.&nbsp;Snappy).
   *
   * @param size the (expected) decompressed size in bytes
   */
  static void checkLimit(long size) {
    checkAgainst(size, maxLength);
  }

  /**
   * Copies data from a decompression {@link InputStream} to an
   * {@link OutputStream}, aborting if the total bytes written exceed the
   * configured limit.
   *
   * <p>
   * The limit is captured once at the start of the call so that a concurrent
   * {@link #resetLimit()} cannot cause the check to flip mid-stream.
   *
   * @param in  the decompression input stream
   * @param out the output stream to write decompressed data to
   * @throws IOException            if an I/O error occurs
   * @throws TrevniRuntimeException if the decompressed size exceeds the limit
   */
  static void boundedCopy(InputStream in, OutputStream out) throws IOException {
    final long limit = maxLength; // snapshot
    byte[] buffer = new byte[BUFFER_SIZE];
    long totalBytes = 0;
    int len;
    while ((len = in.read(buffer)) != -1) {
      totalBytes += len;
      checkAgainst(totalBytes, limit);
      out.write(buffer, 0, len);
    }
  }

  /**
   * Inflates data into the provided output stream while enforcing the
   * decompression size limit and rejecting truncated or corrupt input.
   *
   * <p>
   * The limit is captured once at the start of the call so that a concurrent
   * {@link #resetLimit()} cannot cause the check to flip mid-stream.
   *
   * @param inflater a {@link Inflater} whose input has already been set
   * @param out      the output stream to write inflated data to
   * @throws IOException            if the deflate data is invalid or truncated
   * @throws TrevniRuntimeException if the decompressed size exceeds the limit
   */
  static void boundedInflate(Inflater inflater, OutputStream out) throws IOException {
    final long limit = maxLength; // snapshot
    byte[] buffer = new byte[BUFFER_SIZE];
    long totalBytes = 0;

    try {
      while (true) {
        int len = inflater.inflate(buffer);
        if (len > 0) {
          totalBytes += len;
          checkAgainst(totalBytes, limit);
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
