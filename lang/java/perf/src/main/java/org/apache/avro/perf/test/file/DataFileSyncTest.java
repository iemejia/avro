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

import org.apache.avro.Schema;
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class DataFileSyncTest {

  private static final int RECORD_COUNT = 12000;
  private static final String RECORD_SCHEMA = "{ \"type\": \"record\", \"name\": \"SyncRecord\", \"fields\": ["
      + "{ \"name\": \"id\", \"type\": \"long\" }," + "{ \"name\": \"payload\", \"type\": \"string\" } ] }";

  @Benchmark
  @OperationsPerInvocation(4)
  public void syncToNextBlock(final ScanState state, final Blackhole blackhole) throws Exception {
    for (long position : state.positions) {
      state.reader.sync(position);
      blackhole.consume(state.reader.previousSync());
    }
  }

  @State(Scope.Thread)
  public static class ScanState {
    private final Schema schema;

    private byte[] data;
    private long[] positions;
    private DataFileReader<GenericRecord> reader;

    public ScanState() {
      this.schema = new Schema.Parser().parse(RECORD_SCHEMA);
    }

    @Setup(Level.Trial)
    public void doSetupTrial() throws Exception {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
        writer.setSyncInterval(128);
        writer.create(schema, out);

        for (int i = 0; i < RECORD_COUNT; i++) {
          final GenericRecord record = new GenericData.Record(schema);
          record.put("id", (long) i);
          record.put("payload", "payload-" + i + "-abcdefghijklmnop");
          writer.append(record);
        }
      }

      this.data = out.toByteArray();
      this.positions = new long[] { data.length / 8L, data.length / 3L, data.length / 2L, (data.length * 3L) / 4L };
      this.reader = new DataFileReader<>(new SeekableByteArrayInput(data), new GenericDatumReader<>(schema));
    }

    @TearDown(Level.Trial)
    public void doTearDownTrial() throws Exception {
      if (reader != null) {
        reader.close();
      }
    }
  }
}
