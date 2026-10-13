package ch.so.agi.integrator;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpServerTransport;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Serializes response enqueueing, not tool execution, for the SDK's stdio transport. */
final class SerializedMcpTransport implements McpServerTransport {
  private record Pending(JSONRPCMessage message, Sinks.One<Void> completion) {}

  private final McpServerTransport delegate;
  private final Runnable onClose;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
  private final FluxSink<Pending> outbound;
  private final Disposable drain;

  SerializedMcpTransport(McpServerTransport delegate, Runnable onClose) {
    this.delegate = delegate;
    this.onClose = onClose;
    var sink = new AtomicReference<FluxSink<Pending>>();
    // Flux.create serializes concurrent producers. concatMap also waits for an async
    // enqueue to finish before starting the next one. SDK 2.0.1's tryEmitNext does neither.
    drain =
        Flux.<Pending>create(sink::set)
            .concatMap(
                entry ->
                    Mono.defer(() -> delegate.sendMessage(entry.message()))
                        .doOnSuccess(
                            ignored -> {
                              pending.remove(entry);
                              entry.completion().tryEmitEmpty();
                            }))
            .subscribe(ignored -> {}, this::terminate);
    outbound = sink.get();
  }

  @Override
  public Mono<Void> sendMessage(JSONRPCMessage message) {
    return Mono.defer(
        () -> {
          var entry = new Pending(message, Sinks.one());
          pending.add(entry);
          if (closed.get()) {
            pending.remove(entry);
            return Mono.error(new IllegalStateException("MCP transport is closed"));
          }
          outbound.next(entry);
          return entry.completion().asMono();
        });
  }

  private void terminate(Throwable error) {
    if (!closed.compareAndSet(false, true)) return;
    try {
      pending.forEach(entry -> entry.completion().tryEmitError(error));
      pending.clear();
      outbound.complete();
      drain.dispose();
      delegate.close();
    } finally {
      // The main process must also exit on transport failure, not just stdin EOF.
      onClose.run();
    }
  }

  @Override
  public void close() {
    terminate(new IllegalStateException("MCP transport is closed"));
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.fromRunnable(this::close);
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return delegate.unmarshalFrom(data, typeRef);
  }

  @Override
  public List<String> protocolVersions() {
    return delegate.protocolVersions();
  }
}
