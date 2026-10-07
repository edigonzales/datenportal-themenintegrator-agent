package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;

class EofStdioTransportTest {
  @Test
  void readsUtf8ContentAndMessagesLargerThan64KiB() throws Exception {
    String data = "ä".repeat(90000);
    var reader = new StringReader(data + "\r\n{}\n");
    assertEquals(data, EofStdioTransport.readLine(reader));
    assertEquals("{}", EofStdioTransport.readLine(reader));
    assertNull(EofStdioTransport.readLine(reader));
  }

  @Test
  void rejectsUnboundedProtocolLines() {
    assertThrows(
        IOException.class,
        () -> EofStdioTransport.readLine(new StringReader("x".repeat(16 * 1024 * 1024 + 1))));
  }
}
