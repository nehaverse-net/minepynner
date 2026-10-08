package net.nehaverse.pynner.commands;

import java.util.*;
import net.nehaverse.pynner.protocol.Frames;

/** Pure argument validation and message formatting, independent of command senders. */
public final class CommandArguments {
  private CommandArguments() {}

  public static final class InputError extends IllegalArgumentException {
    public final String code;
    public final Map<String, Object> spec;
    public final String value;

    public InputError(String code, Map<String, Object> spec, String value, String detail) {
      super(detail);
      this.code = code;
      this.spec = spec;
      this.value = value;
    }
  }

  public static Object parse(Map<String, Object> spec, String raw) {
    Object value;
    try {
      value =
          switch (Frames.text(spec, "type", "str")) {
            case "int" -> Integer.parseInt(raw);
            case "float" -> {
              double number = Double.parseDouble(raw);
              if (!Double.isFinite(number)) throw new IllegalArgumentException("Invalid number");
              yield number;
            }
            case "bool" -> {
              if (!List.of("true", "false").contains(raw.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Use true or false");
              yield Boolean.parseBoolean(raw);
            }
            default -> raw;
          };
    } catch (IllegalArgumentException error) {
      throw new InputError("invalid", spec, raw, "Invalid " + spec.get("name") + ": " + raw);
    }
    if (spec.get("choices") instanceof List<?> choices
        && choices.stream().noneMatch(choice -> equivalent(choice, value)))
      throw new InputError("choices", spec, raw, "Choose " + choices + " for " + spec.get("name"));
    if (value instanceof Number number) {
      double parsed = number.doubleValue();
      if ((spec.get("min") instanceof Number min && parsed < min.doubleValue())
          || (spec.get("max") instanceof Number max && parsed > max.doubleValue()))
        throw new InputError("range", spec, raw, "Out of range: " + spec.get("name"));
    }
    return value;
  }

  private static boolean equivalent(Object left, Object right) {
    if (left instanceof Number a && right instanceof Number b)
      return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    return Objects.equals(left, right);
  }

  public static String message(
      Map<String, Object> handler,
      String code,
      Map<String, Object> spec,
      String value,
      String fallback) {
    var messages =
        handler.get("error_messages") instanceof Map<?, ?>
            ? Frames.map(handler.get("error_messages"))
            : Map.<String, Object>of();
    String argument = Frames.text(spec, "name", "");
    Object template = messages.getOrDefault(argument + "." + code, messages.get(code));
    if (template == null) return fallback;
    var replacements = new LinkedHashMap<String, String>();
    replacements.put("command", Frames.text(handler, "name", ""));
    replacements.put("argument", argument);
    replacements.put("value", value);
    replacements.put("min", Objects.toString(spec.get("min"), ""));
    replacements.put("max", Objects.toString(spec.get("max"), ""));
    replacements.put(
        "choices",
        spec.get("choices") instanceof List<?> choices
            ? String.join(", ", choices.stream().map(Object::toString).toList())
            : "");
    replacements.put("detail", fallback);
    // Substitute only template tokens, never recursively interpret player input.
    var matcher =
        java.util.regex.Pattern.compile("\\{(command|argument|value|min|max|choices|detail)\\}")
            .matcher(template.toString());
    var result = new StringBuilder();
    while (matcher.find())
      matcher.appendReplacement(
          result, java.util.regex.Matcher.quoteReplacement(replacements.get(matcher.group(1))));
    matcher.appendTail(result);
    return result.toString();
  }
}
