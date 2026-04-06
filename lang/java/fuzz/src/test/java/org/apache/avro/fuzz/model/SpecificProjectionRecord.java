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

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class SpecificProjectionRecord extends SpecificRecordBase implements SpecificRecord {
  public static final Schema SCHEMA$ = new Schema.Parser().parse(
      "{\"type\":\"record\",\"name\":\"SpecificProjectionRecord\",\"namespace\":\"org.apache.avro.fuzz.model\",\"fields\":["
          + "{\"name\":\"id\",\"type\":\"int\"}," + "{\"name\":\"name\",\"type\":\"string\"},"
          + "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},"
          + "{\"name\":\"status\",\"type\":[\"null\",{\"type\":\"enum\",\"name\":\"SpecificProjectionEnum\",\"symbols\":[\"ALPHA\",\"BETA\",\"GAMMA\"]}],\"default\":null}]}");

  private int id;
  private String name;
  private List<String> tags;
  private SpecificProjectionEnum status;

  public SpecificProjectionRecord() {
  }

  public SpecificProjectionRecord(int id, String name, List<String> tags, SpecificProjectionEnum status) {
    this.id = id;
    this.name = name;
    this.tags = tags;
    this.status = status;
  }

  public static Schema getClassSchema() {
    return SCHEMA$;
  }

  @Override
  public Schema getSchema() {
    return SCHEMA$;
  }

  @Override
  public Object get(int field) {
    switch (field) {
    case 0:
      return id;
    case 1:
      return name;
    case 2:
      return tags;
    case 3:
      return status;
    default:
      throw new AvroRuntimeException("Bad index: " + field);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void put(int field, Object value) {
    switch (field) {
    case 0:
      id = (Integer) value;
      break;
    case 1:
      name = value == null ? null : value.toString();
      break;
    case 2:
      tags = normalizeStrings((List<?>) value);
      break;
    case 3:
      status = (SpecificProjectionEnum) value;
      break;
    default:
      throw new AvroRuntimeException("Bad index: " + field);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SpecificProjectionRecord)) {
      return false;
    }
    SpecificProjectionRecord that = (SpecificProjectionRecord) other;
    return id == that.id && Objects.equals(name, that.name) && Objects.equals(tags, that.tags) && status == that.status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, tags, status);
  }

  private static List<String> normalizeStrings(List<?> values) {
    if (values == null) {
      return null;
    }

    List<String> normalized = new ArrayList<>(values.size());
    for (Object value : values) {
      normalized.add(value == null ? null : value.toString());
    }
    return normalized;
  }
}
