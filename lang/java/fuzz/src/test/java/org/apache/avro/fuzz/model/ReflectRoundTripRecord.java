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
package org.apache.avro.fuzz.model;

import org.apache.avro.reflect.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReflectRoundTripRecord {
  public long id;
  public String name;
  public List<String> tags;
  public Map<String, Long> counts;
  @Nullable
  public String alias;

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ReflectRoundTripRecord)) {
      return false;
    }
    ReflectRoundTripRecord that = (ReflectRoundTripRecord) other;
    return id == that.id && Objects.equals(name, that.name) && Objects.equals(tags, that.tags)
        && Objects.equals(counts, that.counts) && Objects.equals(alias, that.alias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, tags, counts, alias);
  }
}
