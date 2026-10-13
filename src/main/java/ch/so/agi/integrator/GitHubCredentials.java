package ch.so.agi.integrator;

import java.io.*;
import java.util.*;

/** Git credential protocol; token is supplied only over the helper's private stdout pipe. */
final class GitHubCredentials {
  static int run(List<String> args) throws IOException {
    if (!args.equals(List.of("get"))) return 0;
    var fields = new HashMap<String, String>();
    var reader =
        new BufferedReader(
            new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null && !line.isEmpty()) {
      int eq = line.indexOf('=');
      if (eq > 0) fields.put(line.substring(0, eq), line.substring(eq + 1));
    }
    if ("https".equals(fields.get("protocol")) && "github.com".equals(fields.get("host"))) {
      System.out.println("username=x-access-token");
      System.out.println("password=" + GitHub.token());
      System.out.println();
    }
    return 0;
  }

  static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  static Map<String, String> environment() {
    String helper =
        "!"
            + quote(JavaRuntime.java())
            + " -cp "
            + quote(JavaRuntime.classpath())
            + " ch.so.agi.integrator.Main git-credential";
    return Map.of(
        "GH_TOKEN",
        GitHub.token(),
        "GIT_TERMINAL_PROMPT",
        "0",
        "GIT_CONFIG_COUNT",
        "2",
        "GIT_CONFIG_KEY_0",
        "credential.https://github.com.helper",
        "GIT_CONFIG_VALUE_0",
        "",
        "GIT_CONFIG_KEY_1",
        "credential.https://github.com.helper",
        "GIT_CONFIG_VALUE_1",
        helper);
  }
}
