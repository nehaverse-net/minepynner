package net.nehaverse.pynner.events;

import java.util.List;
import java.util.Map;

/** A literal substring rule evaluated before chat delivery, without waiting for Python. */
public final class ChatMessageFilter {
  private ChatMessageFilter() {}

  public static void validate(Map<String, Object> handler) {
    Object words = handler.get("message_contains");
    if (words == null) return;
    if (!"event".equals(handler.get("kind")) || !"async_chat".equals(handler.get("event")))
      throw new IllegalArgumentException("message_contains is only supported for async_chat");
    if (!(words instanceof List<?> list)
        || list.isEmpty()
        || list.stream().anyMatch(word -> !(word instanceof String text) || text.isEmpty()))
      throw new IllegalArgumentException("message_contains requires at least one non-empty string");
  }

  public static boolean matches(Map<String, Object> handler, Map<String, Object> payload) {
    Object words = handler.get("message_contains");
    if (words == null) return true;
    if (!(payload.get("message") instanceof String message)) return false;
    for (Object word : (List<?>) words) {
      if (message.contains((String) word)) return true;
    }
    return false;
  }
}
