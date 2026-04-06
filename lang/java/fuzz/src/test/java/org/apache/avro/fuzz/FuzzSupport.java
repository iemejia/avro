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
package org.apache.avro.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.SystemLimitException;
import org.apache.avro.UnresolvedUnionException;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FuzzSupport {
  static final Schema BINARY_WRITER_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"WriterRoot\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"legacyName\",\"type\":\"string\"},"
          + "{\"name\":\"createdDate\",\"type\":{\"type\":\"int\",\"logicalType\":\"date\"}},"
          + "{\"name\":\"payload\",\"type\":\"bytes\"},"
          + "{\"name\":\"hash\",\"type\":{\"type\":\"fixed\",\"name\":\"Hash4\",\"size\":4}},"
          + "{\"name\":\"node\",\"type\":{\"type\":\"record\",\"name\":\"Node\",\"fields\":["
          + "{\"name\":\"value\",\"type\":\"string\"},"
          + "{\"name\":\"next\",\"type\":[\"null\",\"Node\"],\"default\":null}]}}]}");

  static final Schema BINARY_READER_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"ReaderRoot\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"aliases\":[\"legacyName\"],\"type\":\"string\"},"
          + "{\"name\":\"createdDate\",\"type\":{\"type\":\"int\",\"logicalType\":\"date\"}},"
          + "{\"name\":\"payload\",\"type\":\"bytes\"},"
          + "{\"name\":\"hash\",\"type\":{\"type\":\"fixed\",\"name\":\"Hash4\",\"size\":4}},"
          + "{\"name\":\"node\",\"type\":{\"type\":\"record\",\"name\":\"Node\",\"fields\":["
          + "{\"name\":\"value\",\"type\":\"string\"},"
          + "{\"name\":\"next\",\"type\":[\"null\",\"Node\"],\"default\":null}]}},"
          + "{\"name\":\"status\",\"type\":\"string\",\"default\":\"new\"}]}");

  static final Schema JSON_SCHEMA = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"JsonRoot\",\"fields\":["
      + "{\"name\":\"id\",\"type\":\"long\"}," + "{\"name\":\"name\",\"type\":\"string\"},"
      + "{\"name\":\"createdDate\",\"type\":{\"type\":\"int\",\"logicalType\":\"date\"}},"
      + "{\"name\":\"active\",\"type\":\"boolean\"},"
      + "{\"name\":\"scores\",\"type\":{\"type\":\"array\",\"items\":\"double\"}},"
      + "{\"name\":\"props\",\"type\":{\"type\":\"map\",\"values\":\"string\"}},"
      + "{\"name\":\"extra\",\"type\":[\"null\",\"string\",\"long\"],\"default\":null},"
      + "{\"name\":\"inner\",\"type\":{\"type\":\"record\",\"name\":\"Inner\",\"fields\":["
      + "{\"name\":\"x\",\"type\":\"int\"}," + "{\"name\":\"y\",\"type\":\"int\"}]}}]}");

  static final Schema ROUND_TRIP_SCHEMA = new Schema.Parser()
      .parse("{\"type\":\"record\",\"name\":\"RoundTripRoot\",\"fields\":[" + "{\"name\":\"id\",\"type\":\"long\"},"
          + "{\"name\":\"name\",\"type\":\"string\"},"
          + "{\"name\":\"createdDate\",\"type\":{\"type\":\"int\",\"logicalType\":\"date\"}},"
          + "{\"name\":\"active\",\"type\":\"boolean\"}," + "{\"name\":\"score\",\"type\":\"double\"},"
          + "{\"name\":\"payload\",\"type\":\"bytes\"},"
          + "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},"
          + "{\"name\":\"counts\",\"type\":{\"type\":\"map\",\"values\":\"long\"}},"
          + "{\"name\":\"choice\",\"type\":[\"null\",\"string\",\"long\"],\"default\":null},"
          + "{\"name\":\"hash\",\"type\":{\"type\":\"fixed\",\"name\":\"RoundTripHash\",\"size\":4}},"
          + "{\"name\":\"inner\",\"type\":{\"type\":\"record\",\"name\":\"RoundTripInner\",\"fields\":["
          + "{\"name\":\"x\",\"type\":\"int\"}," + "{\"name\":\"y\",\"type\":\"int\"}]}},"
          + "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"RoundTripStatus\",\"symbols\":[\"NEW\",\"READY\",\"DONE\"]}}]}");

  private FuzzSupport() {
  }

  static boolean isExpectedSchemaFailure(RuntimeException exception) {
    return exception instanceof SchemaParseException || exception instanceof AvroTypeException
        || exception instanceof IllegalArgumentException;
  }

  static boolean isExpectedDecodingFailure(RuntimeException exception) {
    return exception instanceof AvroTypeException || exception instanceof SystemLimitException
        || exception instanceof UnresolvedUnionException || isExpectedDecodingIllegalArgument(exception)
        || isExpectedUnsupportedOperation(exception) || isExpectedAvroRuntimeFailure(exception);
  }

  static InputStream shortReadStream(byte[] data) {
    return new FilterInputStream(new ByteArrayInputStream(data)) {
      @Override
      public int read(byte[] buffer, int off, int len) throws IOException {
        if (len <= 0) {
          return super.read(buffer, off, len);
        }
        return super.read(buffer, off, Math.min(len, 3));
      }
    };
  }

  static String buildSchemaInput(FuzzedDataProvider data) {
    int mode = data.consumeInt(0, 3);
    String name = toIdentifier(data.consumeString(16), "SeedRecord");
    String fieldName = toIdentifier(data.consumeString(16), "field");
    String remaining = data.consumeRemainingAsString();

    switch (mode) {
    case 0:
      return remaining;
    case 1:
      return "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[" + remaining + "]}";
    case 2:
      return "[\"null\"," + remaining + "]";
    default:
      return "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"" + fieldName + "\",\"type\":"
          + remaining + "}]}";
    }
  }

  static String buildJsonInput(FuzzedDataProvider data) {
    int mode = data.consumeInt(0, 2);
    if (mode == 0) {
      return data.consumeRemainingAsString();
    }

    String name = escapeJson(data.consumeString(24));
    String mapKey = toIdentifier(data.consumeString(12), "k");
    String mapValue = escapeJson(data.consumeString(24));
    String extraString = escapeJson(data.consumeString(16));
    double scoreOne = boundedScore(data.consumeInt());
    double scoreTwo = boundedScore(data.consumeInt());
    long id = sanitizeLong(data.consumeLong());
    int createdDate = Math.abs(data.consumeInt(0, 36500));
    boolean active = data.consumeBoolean();
    int x = data.consumeInt();
    int y = data.consumeInt();

    if (mode == 1) {
      return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"createdDate\":" + createdDate + ",\"active\":" + active
          + ",\"scores\":[" + scoreOne + "," + scoreTwo + "]" + ",\"props\":{\"" + mapKey + "\":\"" + mapValue
          + "\"},\"extra\":{\"string\":\"" + extraString + "\"},\"inner\":{\"x\":" + x + ",\"y\":" + y + "}}";
    }

    String trailing = escapeJson(data.consumeRemainingAsString());
    return "{\"id\":" + id + ",\"name\":\"" + name + trailing + "\",\"createdDate\":" + createdDate + ",\"active\":"
        + active + ",\"scores\":[" + scoreOne + "]" + ",\"props\":{\"" + mapKey + "\":\"" + mapValue
        + "\"},\"extra\":null,\"inner\":{\"x\":" + x + ",\"y\":" + y + "}}";
  }

  static GenericRecord buildRoundTripRecord(FuzzedDataProvider data) {
    GenericRecord record = new GenericData.Record(ROUND_TRIP_SCHEMA);
    record.put("id", sanitizeLong(data.consumeLong()));
    record.put("name", safeString(data.consumeString(24), "roundtrip"));
    record.put("createdDate", data.consumeInt(0, 36500));
    record.put("active", data.consumeBoolean());
    record.put("score", boundedScore(data.consumeInt()));
    record.put("payload", ByteBuffer.wrap(data.consumeBytes(data.consumeInt(0, 16))));
    record.put("tags", buildStringList(data));
    record.put("counts", buildCountMap(data));
    record.put("choice", buildUnionValue(data));
    record.put("hash", new GenericData.Fixed(ROUND_TRIP_SCHEMA.getField("hash").schema(), buildFixedBytes(data)));
    record.put("inner", buildInnerRecord(data));
    record.put("status", new GenericData.EnumSymbol(ROUND_TRIP_SCHEMA.getField("status").schema(),
        ROUND_TRIP_SCHEMA.getField("status").schema().getEnumSymbols().get(data.consumeInt(0, 2))));
    return record;
  }

  static String safeString(String value, String fallback) {
    return value.isEmpty() ? fallback : value;
  }

  static boolean semanticEquals(Object left, Object right) {
    return Objects.equals(normalizeDatum(left), normalizeDatum(right));
  }

  static boolean roundTripRecordsEqual(GenericRecord left, GenericRecord right) {
    for (Schema.Field field : ROUND_TRIP_SCHEMA.getFields()) {
      if (!semanticEquals(left.get(field.name()), right.get(field.name()))) {
        return false;
      }
    }
    return true;
  }

  private static double boundedScore(int value) {
    return (value % 1000) / 10.0d;
  }

  private static GenericRecord buildInnerRecord(FuzzedDataProvider data) {
    GenericRecord inner = new GenericData.Record(ROUND_TRIP_SCHEMA.getField("inner").schema());
    inner.put("x", data.consumeInt());
    inner.put("y", data.consumeInt());
    return inner;
  }

  private static List<String> buildStringList(FuzzedDataProvider data) {
    List<String> list = new ArrayList<>();
    int size = data.consumeInt(0, 4);
    for (int i = 0; i < size; i++) {
      list.add(safeString(data.consumeString(16), "tag" + i));
    }
    return list;
  }

  private static Map<String, Long> buildCountMap(FuzzedDataProvider data) {
    Map<String, Long> map = new LinkedHashMap<>();
    int size = data.consumeInt(0, 4);
    for (int i = 0; i < size; i++) {
      map.put(safeString(toIdentifier(data.consumeString(12), "k" + i), "k" + i), sanitizeLong(data.consumeLong()));
    }
    return map;
  }

  private static Object buildUnionValue(FuzzedDataProvider data) {
    switch (data.consumeInt(0, 2)) {
    case 0:
      return null;
    case 1:
      return safeString(data.consumeString(16), "choice");
    default:
      return sanitizeLong(data.consumeLong());
    }
  }

  private static byte[] buildFixedBytes(FuzzedDataProvider data) {
    byte[] bytes = data.consumeBytes(4);
    if (bytes.length == 4) {
      return bytes;
    }
    byte[] fixed = new byte[4];
    System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, fixed.length));
    return fixed;
  }

  private static Object normalizeDatum(Object datum) {
    if (datum == null) {
      return null;
    }
    if (datum instanceof CharSequence) {
      return datum.toString();
    }
    if (datum instanceof GenericEnumSymbol<?> || datum instanceof Enum<?>) {
      return datum.toString();
    }
    if (datum instanceof ByteBuffer) {
      return normalizeBytes((ByteBuffer) datum);
    }
    if (datum instanceof GenericFixed) {
      return normalizeBytes(ByteBuffer.wrap(((GenericFixed) datum).bytes()));
    }
    if (datum instanceof GenericRecord) {
      GenericRecord record = (GenericRecord) datum;
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (Schema.Field field : record.getSchema().getFields()) {
        normalized.put(field.name(), normalizeDatum(record.get(field.name())));
      }
      return normalized;
    }
    if (datum instanceof List<?>) {
      List<Object> normalized = new ArrayList<>();
      for (Object value : (List<?>) datum) {
        normalized.add(normalizeDatum(value));
      }
      return normalized;
    }
    if (datum instanceof Map<?, ?>) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) datum).entrySet()) {
        normalized.put(String.valueOf(normalizeDatum(entry.getKey())), normalizeDatum(entry.getValue()));
      }
      return normalized;
    }
    if (datum instanceof Double || datum instanceof Float) {
      return ((Number) datum).doubleValue();
    }
    if (datum instanceof Number) {
      return ((Number) datum).longValue();
    }
    return datum;
  }

  private static List<Integer> normalizeBytes(ByteBuffer buffer) {
    ByteBuffer copy = buffer.duplicate();
    List<Integer> bytes = new ArrayList<>(copy.remaining());
    while (copy.hasRemaining()) {
      bytes.add(copy.get() & 0xff);
    }
    return bytes;
  }

  private static boolean isExpectedAvroRuntimeFailure(RuntimeException exception) {
    if (!(exception instanceof AvroRuntimeException)) {
      return false;
    }

    String message = exception.getMessage();
    if (message == null) {
      return false;
    }

    return message.startsWith("Malformed data.") || message.startsWith("Unknown datum type")
        || message.startsWith("Not an array") || message.startsWith("Not a map") || message.startsWith("No match for ");
  }

  private static boolean isExpectedDecodingIllegalArgument(RuntimeException exception) {
    if (!(exception instanceof IllegalArgumentException)) {
      return false;
    }

    String message = exception.getMessage();
    if (message == null) {
      return false;
    }

    return message.contains("Invalid UTF-8") || message.contains("fromIndex") || message.contains("toIndex")
        || message.contains("length") || message.contains("bound");
  }

  private static boolean isExpectedUnsupportedOperation(RuntimeException exception) {
    if (!(exception instanceof UnsupportedOperationException)) {
      return false;
    }

    String message = exception.getMessage();
    return message != null && message.startsWith("Cannot read ");
  }

  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      switch (current) {
      case '\\':
        escaped.append("\\\\");
        break;
      case '"':
        escaped.append("\\\"");
        break;
      case '\b':
        escaped.append("\\b");
        break;
      case '\f':
        escaped.append("\\f");
        break;
      case '\n':
        escaped.append("\\n");
        break;
      case '\r':
        escaped.append("\\r");
        break;
      case '\t':
        escaped.append("\\t");
        break;
      default:
        if (current < 0x20) {
          escaped.append(String.format("\\u%04x", (int) current));
        } else {
          escaped.append(current);
        }
      }
    }
    return escaped.toString();
  }

  private static long sanitizeLong(long value) {
    return value == Long.MIN_VALUE ? 0L : Math.abs(value);
  }

  private static String toIdentifier(String value, String fallback) {
    StringBuilder identifier = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (Character.isLetterOrDigit(current) || current == '_') {
        identifier.append(current);
      }
    }

    if (identifier.length() == 0) {
      return fallback;
    }
    if (!Character.isLetter(identifier.charAt(0)) && identifier.charAt(0) != '_') {
      identifier.insert(0, 'N');
    }
    return identifier.toString();
  }
}
