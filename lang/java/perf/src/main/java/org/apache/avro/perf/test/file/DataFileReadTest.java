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

package org.apache.avro.perf.test.file;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class DataFileReadTest {

  private static final int RECORD_COUNT = 2000;
  private static final int MAP_SIZE = 8;
  private static final String RECORD_SCHEMA = "{ \"type\": \"record\", \"name\": \"FileRecord\", \"fields\": ["
      + "{ \"name\": \"id\", \"type\": \"long\" },"
      + "{ \"name\": \"values\", \"type\": { \"type\": \"map\", \"values\": \"long\" } } ] }";

  @Benchmark
  @OperationsPerInvocation(RECORD_COUNT)
  public void readAll(final ReadState state, final Blackhole blackhole) throws Exception {
    long sum = 0L;
    GenericRecord reuse = state.reuse;

    while (state.reader.hasNext()) {
      reuse = state.reader.next(reuse);
      sum += (Long) reuse.get("id");
    }

    state.reuse = reuse;
    blackhole.consume(sum);
  }

  @State(Scope.Thread)
  public static class ReadState {
    private final Schema schema;

    @Param({ "null", "deflate" })
    private String codecName;

    private byte[] data;
    private DataFileReader<GenericRecord> reader;
    private GenericRecord reuse;

    public ReadState() {
      this.schema = new Schema.Parser().parse(RECORD_SCHEMA);
    }

    @Setup(Level.Trial)
    public void doSetupTrial() throws Exception {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
        writer.setSyncInterval(256);
        writer.setCodec(CodecFactory.fromString(codecName));
        writer.create(schema, out);

        for (int i = 0; i < RECORD_COUNT; i++) {
          final GenericRecord record = new GenericData.Record(schema);
          final Map<String, Long> values = new LinkedHashMap<>();
          for (int j = 0; j < MAP_SIZE; j++) {
            values.put("key-" + j, (long) (i + j));
          }
          record.put("id", (long) i);
          record.put("values", values);
          writer.append(record);
        }
      }

      this.data = out.toByteArray();
    }

    @Setup(Level.Invocation)
    public void doSetupInvocation() throws Exception {
      this.reader = new DataFileReader<>(new SeekableByteArrayInput(data), new GenericDatumReader<>(schema));
    }

    @TearDown(Level.Invocation)
    public void doTearDownInvocation() throws Exception {
      if (reader != null) {
        reader.close();
      }
    }
  }
}
