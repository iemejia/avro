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

package org.apache.avro.perf.test.generic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.perf.test.BasicState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

public class GenericFastMapTest {

  private static final int MAP_SIZE = 8;
  private static final String RECORD_SCHEMA = "{ \"type\": \"record\", \"name\": \"MapRecord\", \"fields\": ["
      + "{ \"name\": \"id\", \"type\": \"long\" },"
      + "{ \"name\": \"values\", \"type\": { \"type\": \"map\", \"values\": \"long\" } } ] }";

  @Benchmark
  @OperationsPerInvocation(BasicState.BATCH_SIZE)
  public void decode(final TestStateDecode state, final Blackhole blackhole) throws Exception {
    final Decoder decoder = state.decoder;
    Object reuse = state.reuse;

    for (int i = 0; i < state.getBatchSize(); i++) {
      reuse = state.reader.read(reuse, decoder);
      blackhole.consume(reuse);
    }

    state.reuse = reuse;
  }

  @State(Scope.Thread)
  public static class TestStateDecode extends BasicState {

    private final Schema schema;

    private byte[] testData;
    private Decoder decoder;
    private GenericDatumReader<Object> reader;
    private Object reuse;

    public TestStateDecode() {
      super();
      this.schema = new Schema.Parser().parse(RECORD_SCHEMA);
    }

    @Setup(Level.Trial)
    public void doSetupTrial() throws IOException {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final Encoder encoder = super.newEncoder(true, baos);

      for (int i = 0; i < getBatchSize(); i++) {
        encoder.writeLong(i);
        encoder.writeMapStart();
        encoder.setItemCount(MAP_SIZE);
        for (int j = 0; j < MAP_SIZE; j++) {
          encoder.startItem();
          encoder.writeString("key-" + j);
          encoder.writeLong(i + j);
        }
        encoder.writeMapEnd();
      }

      encoder.flush();
      this.testData = baos.toByteArray();
      this.reader = new GenericDatumReader<>(schema);
      this.reader.getData().setFastReaderEnabled(true);
      this.reuse = new GenericData.Record(schema);
      this.reuse = withReusableMap((GenericData.Record) this.reuse);
    }

    @Setup(Level.Invocation)
    public void doSetupInvocation() {
      this.decoder = super.newDecoder(this.testData);
    }

    private Object withReusableMap(GenericData.Record record) {
      final Map<String, Long> values = new LinkedHashMap<>();
      for (int i = 0; i < MAP_SIZE; i++) {
        values.put("key-" + i, 0L);
      }
      record.put("values", values);
      return record;
    }
  }
}
