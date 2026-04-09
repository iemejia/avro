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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.avro.AvroRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for Avro-supported compression codecs for data files.
 *
 * Note that Codec objects may maintain internal state (e.g. buffers) and are
 * not thread safe.
 */
public abstract class Codec {

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

  /** Name of the codec; written to the file's metadata. */
  public abstract String getName();

  /** Compresses the input data */
  public abstract ByteBuffer compress(ByteBuffer uncompressedData) throws IOException;

  /** Decompress the data */
  public abstract ByteBuffer decompress(ByteBuffer compressedData) throws IOException;

  /**
   * Codecs must implement an equals() method. Two codecs, A and B are equal if:
   * the result of A and B decompressing content compressed by A is the same AND
   * the result of A and B decompressing content compressed by B is the same
   **/
  @Override
  public abstract boolean equals(Object other);

  /**
   * Codecs must implement a hashCode() method that is consistent with equals().
   */
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return getName();
  }

  // Codecs often reference the array inside a ByteBuffer. Compute the offset
  // to the start of data correctly in the case that our ByteBuffer
  // is a slice() of another.
  protected static int computeOffset(ByteBuffer data) {
    return data.arrayOffset() + data.position();
  }

  /**
   * Throws an {@link AvroRuntimeException} if the decompressed size exceeds the
   * configured maximum. The limit can be configured via the system property
   * {@code org.apache.avro.limits.decompress.maxLength}.
   *
   * @param size the current decompressed size in bytes
   */
  protected static void checkDecompressLimit(long size) {
    if (size > maxDecompressLength) {
      throw new AvroRuntimeException(
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
   * @throws IOException          if an I/O error occurs
   * @throws AvroRuntimeException if the decompressed size exceeds the limit
   */
  protected static void boundedCopy(InputStream in, OutputStream out) throws IOException {
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
  protected static void boundedInflate(Inflater inflater, OutputStream out) throws IOException {
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
