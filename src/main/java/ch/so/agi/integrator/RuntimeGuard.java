package ch.so.agi.integrator;

import java.nio.channels.*;
import java.nio.file.*;

/** Cross-process lock, also safe for concurrent workflows within one JVM. */
final class RuntimeGuard implements AutoCloseable {
  final FileChannel channel;
  final FileLock lock;

  private RuntimeGuard(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  static RuntimeGuard acquire(Path file, int seconds) {
    FileChannel channel = null;
    try {
      Files.createDirectories(file.getParent());
      channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
      while (System.nanoTime() < deadline) {
        try {
          FileLock lock = channel.tryLock();
          if (lock != null) return new RuntimeGuard(channel, lock);
        } catch (OverlappingFileLockException ignored) {
        }
        Thread.sleep(100);
      }
      throw new Problem("runtime_locked", "Andere Runtime-Operation laeuft noch.");
    } catch (Exception e) {
      if (channel != null)
        try {
          channel.close();
        } catch (Exception ignored) {
        }
      if (e instanceof Problem p) throw p;
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Problem("runtime_lock_failed", "Runtime-Sperre nicht verfuegbar.");
    }
  }

  @Override
  public void close() {
    try {
      lock.release();
    } catch (Exception ignored) {
    }
    try {
      channel.close();
    } catch (Exception ignored) {
    }
  }
}
