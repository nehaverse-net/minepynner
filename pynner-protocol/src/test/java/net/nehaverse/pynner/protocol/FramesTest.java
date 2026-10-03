package net.nehaverse.pynner.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FramesTest {
  @Test
  void rejectsNonFiniteNumbers() {
    assertThrows(IOException.class, () -> Frames.encode(Map.of("value", Double.NaN)));
  }

  @Test
  void roundTrip() throws Exception {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("name", "炎の剣");
    payload.put("health", 12.5);
    payload.put("null", null);
    payload.put("values", List.of(true, -5L));
    var message = Frames.envelope("event", 7, payload);
    var decoded = Frames.read(new ByteArrayInputStream(Frames.encode(message)));
    assertEquals(1L, decoded.get("version"));
    assertEquals(7L, decoded.get("generation"));
    assertEquals(payload, decoded.get("payload"));
  }

  @Test
  void rejectsInvalidSize() {
    assertThrows(
        IOException.class, () -> Frames.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 0})));
  }

  @Test
  void rejectsTruncatedPayload() {
    assertThrows(
        EOFException.class,
        () -> Frames.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 10, 1})));
  }

  @Test
  void rejectsOversizedPayload() {
    assertThrows(
        IOException.class, () -> Frames.encode(Map.of("data", "x".repeat(Frames.MAX_FRAME))));
  }

  @Test
  void rejectsDeepNesting() throws Exception {
    Object nested = "leaf";
    for (int depth = 0; depth < 40; depth++) nested = List.of(nested);
    byte[] frame = Frames.encode(Map.of("nested", nested));
    assertThrows(IOException.class, () -> Frames.read(new ByteArrayInputStream(frame)));
  }
}
