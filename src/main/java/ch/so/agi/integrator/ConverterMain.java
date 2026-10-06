package ch.so.agi.integrator;

import java.nio.file.Path;

/** Runs converter code in a separate JVM after its JUnit suite passed. */
public final class ConverterMain {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("class input output required");
    Object converter = Class.forName(args[0]).getConstructor().newInstance();
    if (!(converter instanceof CsvConverter c))
      throw new IllegalArgumentException("CsvConverter required");
    c.convert(Path.of(args[1]), Path.of(args[2]));
  }
}
