package net.nehaverse.pynner.protocol;

import java.io.*;
import java.util.*;
import org.msgpack.core.*;

/** Length-prefixed, bounded MessagePack maps. No Java object deserialization. */
public final class Frames {
  public static final int MAX_FRAME = 1_048_576;

  private Frames() {}

  public static byte[] encode(Map<String, Object> message) throws IOException {
    try (var packer = MessagePack.newDefaultBufferPacker()) {
      pack(packer, message);
      byte[] payload = packer.toByteArray();
      if (payload.length > MAX_FRAME) throw new IOException("Frame exceeds 1 MiB");
      var output = new ByteArrayOutputStream();
      var data = new DataOutputStream(output);
      data.writeInt(payload.length);
      data.write(payload);
      return output.toByteArray();
    }
  }

  public static Map<String, Object> read(InputStream input) throws IOException {
    var data = new DataInputStream(input);
    int length = data.readInt();
    if (length <= 0 || length > MAX_FRAME) throw new IOException("Invalid frame size");
    byte[] payload = data.readNBytes(length);
    if (payload.length != length) throw new EOFException("Truncated frame");
    try (var unpacker = MessagePack.newDefaultUnpacker(payload)) {
      Object decoded = unpack(unpacker, 0);
      if (!(decoded instanceof Map<?, ?>) || unpacker.hasNext())
        throw new IOException("Expected one map");
      return map(decoded);
    }
  }

  private static Object unpack(MessageUnpacker reader, int depth) throws IOException {
    if (depth > 32) throw new IOException("Excessive nesting");
    return switch (reader.getNextFormat().getValueType()) {
      case NIL -> {
        reader.unpackNil();
        yield null;
      }
      case BOOLEAN -> reader.unpackBoolean();
      case INTEGER -> reader.unpackLong();
      case FLOAT -> {
        double value = reader.unpackDouble();
        if (!Double.isFinite(value)) throw new IOException("Non-finite number");
        yield value;
      }
      case STRING -> reader.unpackString();
      case BINARY -> {
        int size = reader.unpackBinaryHeader();
        if (size > MAX_FRAME) throw new IOException("Binary too large");
        yield reader.readPayload(size);
      }
      case ARRAY -> {
        int size = reader.unpackArrayHeader();
        if (size > 16384) throw new IOException("Array too large");
        var list = new ArrayList<Object>(size);
        for (int i = 0; i < size; i++) list.add(unpack(reader, depth + 1));
        yield list;
      }
      case MAP -> {
        int size = reader.unpackMapHeader();
        if (size > 4096) throw new IOException("Map too large");
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < size; i++) {
          String key = reader.unpackString();
          if (map.containsKey(key)) throw new IOException("Duplicate key");
          map.put(key, unpack(reader, depth + 1));
        }
        yield map;
      }
      default -> throw new IOException("Unsupported MessagePack type");
    };
  }

  private static void pack(MessagePacker packer, Object value) throws IOException {
    if (value == null) packer.packNil();
    else if (value instanceof String text) packer.packString(text);
    else if (value instanceof Boolean flag) packer.packBoolean(flag);
    else if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) throw new IOException("Non-finite number");
      packer.packDouble(number);
    } else if (value instanceof Number number) packer.packLong(number.longValue());
    else if (value instanceof byte[] bytes) {
      packer.packBinaryHeader(bytes.length);
      packer.writePayload(bytes);
    } else if (value instanceof Map<?, ?> map) {
      packer.packMapHeader(map.size());
      for (var entry : map.entrySet()) {
        packer.packString(entry.getKey().toString());
        pack(packer, entry.getValue());
      }
    } else if (value instanceof Collection<?> list) {
      packer.packArrayHeader(list.size());
      for (Object element : list) pack(packer, element);
    } else throw new IOException("Unsupported value: " + value.getClass());
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  public static String text(Map<String, Object> map, String key, String fallback) {
    Object value = map.get(key);
    return value == null ? fallback : value.toString();
  }

  public static double number(Map<String, Object> map, String key, double fallback) {
    Object value = map.get(key);
    double result = value instanceof Number n ? n.doubleValue() : fallback;
    if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite " + key);
    return result;
  }

  public static boolean flag(Map<String, Object> map, String key, boolean fallback) {
    return map.get(key) instanceof Boolean b ? b : fallback;
  }

  public static Map<String, Object> envelope(
      String type, long generation, Map<String, Object> payload) {
    return Map.of("version", 1, "type", type, "generation", generation, "payload", payload);
  }
}
