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

package org.apache.avro.perf.test.message;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.perf.test.BasicState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

public class BinaryMessageDecodeTest {

  private static final String RECORD_SCHEMA = "{ \"type\": \"record\", \"name\": \"MessageRecord\", \"fields\": ["
      + "{ \"name\": \"id\", \"type\": \"long\" }," + "{ \"name\": \"name\", \"type\": \"string\" },"
      + "{ \"name\": \"payload\", \"type\": \"bytes\" } ] }";

  @Benchmark
  @OperationsPerInvocation(BasicState.BATCH_SIZE)
  public void decodeByteArray(final DecodeState state, final Blackhole blackhole) throws Exception {
    GenericRecord reuse = state.reuse;

    for (byte[] encoded : state.messages) {
      reuse = state.decoder.decode(encoded, reuse);
      blackhole.consume(reuse);
    }

    state.reuse = reuse;
  }

  @Benchmark
  @OperationsPerInvocation(BasicState.BATCH_SIZE)
  public void decodeChunkedStream(final DecodeState state, final Blackhole blackhole) throws Exception {
    GenericRecord reuse = state.reuse;

    for (byte[] encoded : state.messages) {
      reuse = state.decoder.decode(new ChunkedByteArrayInputStream(encoded, 4), reuse);
      blackhole.consume(reuse);
    }

    state.reuse = reuse;
  }

  @State(Scope.Thread)
  public static class DecodeState extends BasicState {

    private final Schema schema;
    private final GenericData model;

    private BinaryMessageDecoder<GenericRecord> decoder;
    private byte[][] messages;
    private GenericRecord reuse;

    public DecodeState() {
      super();
      this.schema = new Schema.Parser().parse(RECORD_SCHEMA);
      this.model = GenericData.get();
    }

    @Setup(Level.Trial)
    public void doSetupTrial() throws Exception {
      final BinaryMessageEncoder<GenericRecord> encoder = new BinaryMessageEncoder<>(model, schema, false);
      this.decoder = new BinaryMessageDecoder<>(model, schema);
      this.messages = new byte[getBatchSize()][];

      for (int i = 0; i < messages.length; i++) {
        final GenericRecord record = new GenericData.Record(schema);
        record.put("id", (long) i);
        record.put("name", "user-" + i);
        record.put("payload", ByteBuffer.wrap(new byte[] { (byte) i, (byte) (i + 1), (byte) (i + 2), (byte) (i + 3) }));

        final ByteBuffer encoded = encoder.encode(record);
        final byte[] message = new byte[encoded.remaining()];
        encoded.get(message);
        messages[i] = message;
      }

      this.reuse = new GenericData.Record(schema);
    }
  }

  private static final class ChunkedByteArrayInputStream extends ByteArrayInputStream {
    private final int maxChunkSize;

    private ChunkedByteArrayInputStream(byte[] data, int maxChunkSize) {
      super(data);
      this.maxChunkSize = maxChunkSize;
    }

    @Override
    public synchronized int read(byte[] bytes, int off, int len) {
      return super.read(bytes, off, Math.min(len, maxChunkSize));
    }
  }
}
