package ch.so.agi.integrator;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.iox.*;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import java.io.File;

/** Runs in its own JVM so validator logging and global state cannot affect MCP stdout. */
public final class CsvValidationMain extends org.interlis2.validator.Validator {
  @Override
  protected IoxReader createReader(
      String filename,
      TransferDescription model,
      LogEventFactory errors,
      Settings settings,
      PipelinePool pool)
      throws IoxException {
    var reader = new CsvReader(new File(filename), settings);
    reader.setModel(model);
    reader.setFirstLineIsHeader(true);
    reader.setValueSeparator(';');
    reader.setValueDelimiter('"');
    return reader;
  }

  public static void main(String[] args) {
    if (args.length != 3) System.exit(2);
    var settings = new Settings();
    settings.setValue(SETTING_MODELNAMES, args[1]);
    settings.setValue(SETTING_ILIDIRS, args[2]);
    settings.setValue(CsvReader.ENCODING, "UTF-8");
    boolean valid = new CsvValidationMain().validate(new String[] {args[0]}, settings);
    System.out.println("INTEGRATOR_CSV_VALIDATED=" + valid);
    System.exit(valid ? 0 : 1);
  }
}
