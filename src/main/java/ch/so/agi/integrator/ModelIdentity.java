package ch.so.agi.integrator;

import java.util.*;
import java.util.regex.Pattern;

/** Read declaration tokens, never filenames of content-addressed internal blobs. */
final class ModelIdentity {
  private static final Pattern TOKENS =
      Pattern.compile(
          "(?s)!![^\\r\\n]*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|[A-Za-z][A-Za-z0-9_]*|[^\\s]");

  static void verify(String text, Map<String, Object> identity) {
    var tokens = new ArrayList<String>();
    var matcher = TOKENS.matcher(text);
    while (matcher.find()) {
      String token = matcher.group();
      if (!token.startsWith("!!") && !token.startsWith("/*")) tokens.add(token);
    }
    int model = tokens.indexOf("MODEL");
    if (model < 0
        || model + 1 >= tokens.size()
        || !tokens.get(model + 1).equals(identity.get("name")))
      throw new Problem(
          "model_identity", "Modellname und vorgesehener Themen-Dateiname stimmen nicht überein.");
    int end = tokens.subList(model, tokens.size()).indexOf("=") + model;
    if (end < model) throw new Problem("model_identity", "Modellkopf fehlt.");
    var header = tokens.subList(model, end);
    for (var field : Map.of("AT", "uri", "VERSION", "version").entrySet()) {
      int at = header.indexOf(field.getKey());
      if (at < 0
          || at + 1 >= header.size()
          || !header.get(at + 1).equals(Json.text(identity.get(field.getValue()))))
        throw new Problem(
            "model_identity",
            "Modellkopf weicht von der beauftragten Identität ab.",
            "field",
            field.getValue());
    }
  }
}
