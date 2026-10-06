package ch.so.agi.integrator;

import java.nio.file.Path;

/** Topic converter contract: immutable input; UTF-8 semicolon CSV output at the supplied path. */
public interface CsvConverter {
  void convert(Path input, Path output) throws Exception;
}
