package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class SerializedMcpTransportTest {
  @Test
  void concurrentResponsesWaitForEachAsyncEnqueueAndConnectionRemainsUsable() throws Exception {
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    var sent = new AtomicInteger();
    var ended = new AtomicInteger();
    var raw =
        new FakeTransport() {
          @Override
          public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.defer(
                () -> {
                  maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                  return Mono.delay(Duration.ofMillis(1))
                      .doOnNext(
                          ignored -> {
                            active.decrementAndGet();
                            sent.incrementAndGet();
                          })
                      .then();
                });
          }
        };
    var transport = new SerializedMcpTransport(raw, ended::incrementAndGet);
    try (var workers = Executors.newFixedThreadPool(16)) {
      var start = new CountDownLatch(1);
      var jobs = new java.util.ArrayList<Future<?>>();
      for (int i = 0; i < 200; i++) {
        int id = i;
        jobs.add(
            workers.submit(
                () -> {
                  start.await();
                  transport
                      .sendMessage(McpSchema.JSONRPCResponse.result(id, Json.map("ok", true)))
                      .block(Duration.ofSeconds(10));
                  return null;
                }));
      }
      start.countDown();
      for (var job : jobs) job.get(15, TimeUnit.SECONDS);
      assertEquals(200, sent.get());
      assertEquals(1, maximum.get());
      assertEquals(0, ended.get());
      transport
          .sendMessage(McpSchema.JSONRPCResponse.result(201, Json.map("ok", true)))
          .block(Duration.ofSeconds(2));
      assertEquals(201, sent.get());
    } finally {
      transport.close();
    }
    assertEquals(1, ended.get());
    assertEquals(1, raw.closes.get());
  }

  @Test
  void fatalEnqueueFailureReleasesQueuedSendersAndTerminatesProcessLifecycle() {
    var failure = Sinks.<Void>one();
    var ended = new AtomicInteger();
    var raw =
        new FakeTransport() {
          @Override
          public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return failure.asMono();
          }
        };
    var transport = new SerializedMcpTransport(raw, ended::incrementAndGet);
    var first = transport.sendMessage(McpSchema.JSONRPCResponse.result(1, Json.map())).toFuture();
    var second = transport.sendMessage(McpSchema.JSONRPCResponse.result(2, Json.map())).toFuture();
    failure.tryEmitError(new IllegalStateException("synthetic transport failure"));
    assertTrue(first.isCompletedExceptionally());
    assertTrue(second.isCompletedExceptionally());
    assertEquals(1, ended.get());
    assertEquals(1, raw.closes.get());
    assertThrows(
        IllegalStateException.class,
        () ->
            transport
                .sendMessage(McpSchema.JSONRPCResponse.result(3, Json.map()))
                .block(Duration.ofSeconds(1)));
    transport.close();
    assertEquals(1, ended.get());
  }

  private static class FakeTransport implements McpServerTransport {
    final AtomicInteger closes = new AtomicInteger();

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
      return Mono.empty();
    }

    @Override
    public Mono<Void> closeGracefully() {
      return Mono.fromRunnable(closes::incrementAndGet);
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      throw new UnsupportedOperationException();
    }
  }
}
