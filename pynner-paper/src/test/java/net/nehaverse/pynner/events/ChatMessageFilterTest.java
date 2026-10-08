package net.nehaverse.pynner.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatMessageFilterTest {
  private Map<String, Object> rule(Object words) {
    return Map.of("kind", "event", "event", "async_chat", "message_contains", words);
  }

  @Test
  void blocksAnyListedSubstringButAllowsOrdinaryMessages() {
    var handler = rule(List.of("イキスギ", "お前やりませんねぇすぎぃ"));
    ChatMessageFilter.validate(handler);
    assertTrue(ChatMessageFilter.matches(handler, Map.of("message", "これはイキスギだ")));
    assertTrue(ChatMessageFilter.matches(handler, Map.of("message", "お前やりませんねぇすぎぃ！")));
    assertFalse(ChatMessageFilter.matches(handler, Map.of("message", "こんにちは")));
    assertFalse(ChatMessageFilter.matches(handler, Map.of()));
  }

  @Test
  void usesLiteralCaseSensitiveMatching() {
    var handler = rule(List.of("a.b"));
    assertTrue(ChatMessageFilter.matches(handler, Map.of("message", "a.b")));
    assertFalse(ChatMessageFilter.matches(handler, Map.of("message", "axb")));
    assertFalse(ChatMessageFilter.matches(handler, Map.of("message", "A.B")));
    assertTrue(ChatMessageFilter.matches(Map.of(), Map.of("message", "anything")));
  }

  @Test
  void rejectsInvalidRulesRatherThanCancellingEverything() {
    for (Object words : List.of(List.of(), List.of(""), List.of(1), "word"))
      assertThrows(IllegalArgumentException.class, () -> ChatMessageFilter.validate(rule(words)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ChatMessageFilter.validate(
                Map.of(
                    "kind", "event", "event", "player_join", "message_contains", List.of("word"))));
  }
}
