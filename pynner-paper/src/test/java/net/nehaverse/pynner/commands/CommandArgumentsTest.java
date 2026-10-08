package net.nehaverse.pynner.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CommandArgumentsTest {
  @Test
  void validatesTypesChoicesAndInclusiveRanges() {
    var spec =
        Map.<String, Object>of(
            "name",
            "seconds",
            "type",
            "int",
            "min",
            1,
            "max",
            60,
            "choices",
            List.of(1L, 60L)); // MessagePack integers decode as Long.
    assertEquals(1, CommandArguments.parse(spec, "1"));
    assertEquals(60, CommandArguments.parse(spec, "60"));
    assertEquals(
        "choices",
        assertThrows(CommandArguments.InputError.class, () -> CommandArguments.parse(spec, "2"))
            .code);
    assertEquals(
        "invalid",
        assertThrows(CommandArguments.InputError.class, () -> CommandArguments.parse(spec, "oops"))
            .code);
    var range = Map.<String, Object>of("name", "seconds", "type", "float", "min", 1, "max", 60);
    assertEquals(
        "range",
        assertThrows(CommandArguments.InputError.class, () -> CommandArguments.parse(range, "60.1"))
            .code);
    for (String invalid : List.of("NaN", "Infinity", "-Infinity"))
      assertEquals(
          "invalid",
          assertThrows(
                  CommandArguments.InputError.class, () -> CommandArguments.parse(range, invalid))
              .code);
  }

  @Test
  void choicesAreCaseSensitiveAndBooleansAreTyped() {
    var text = Map.<String, Object>of("name", "mode", "type", "str", "choices", List.of("rain"));
    assertEquals("rain", CommandArguments.parse(text, "rain"));
    assertThrows(CommandArguments.InputError.class, () -> CommandArguments.parse(text, "RAIN"));
    var flag = Map.<String, Object>of("name", "flag", "type", "bool", "choices", List.of(true));
    assertEquals(true, CommandArguments.parse(flag, "TRUE"));
    assertThrows(CommandArguments.InputError.class, () -> CommandArguments.parse(flag, "false"));
  }

  @Test
  void errorMessagesUseSpecificOverridesWithoutRecursiveInputSubstitution() {
    var handler =
        Map.<String, Object>of(
            "name",
            "weather",
            "error_messages",
            Map.of(
                "range", "generic", "seconds.range", "{argument}:{value}:{min}:{max}:{command}"));
    var spec = Map.<String, Object>of("name", "seconds", "min", 1, "max", 60);
    assertEquals(
        "seconds:{max}:1:60:weather",
        CommandArguments.message(handler, "range", spec, "{max}", "fallback"));
    assertEquals("fallback", CommandArguments.message(handler, "missing", spec, "", "fallback"));
  }
}
