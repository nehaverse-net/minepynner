package net.nehaverse.pynner.bridge;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.nehaverse.pynner.protocol.Frames;

public final class RuntimeSession implements AutoCloseable {
  public final long generation;
  public final String token;
  public volatile Process process;
  public volatile long heartbeat = System.nanoTime();
  public volatile long completed;
  public volatile long progressAt = System.nanoTime();
  public volatile int pythonQueue;
  public volatile boolean active;
  public volatile boolean closed;
  public volatile Map<String, Object> manifest;
  private final Map<String, Channel> channels = new ConcurrentHashMap<>();
  private final ExecutorService executor;

  public RuntimeSession(long generation, String token, ExecutorService executor) {
    this.generation = generation;
    this.token = token;
    this.executor = executor;
  }

  public synchronized void attach(String name, Socket socket) throws IOException {
    if (closed || !Set.of("control", "events").contains(name) || channels.containsKey(name))
      throw new IOException("Invalid or duplicate channel");
    Channel channel = new Channel(socket);
    channels.put(name, channel);
    executor.submit(channel::run);
    send(
        name,
        "welcome",
        Map.of("capabilities", List.of("paper-1.21.11", "protocol-1", "async-events")));
  }

  public boolean connected() {
    return channels.size() == 2;
  }

  public boolean send(String channel, String type, Map<String, Object> payload) {
    Channel target = channels.get(channel);
    if (closed || target == null) return false;
    try {
      return target.offer(Frames.encode(Frames.envelope(type, generation, payload)));
    } catch (IOException exception) {
      return false;
    }
  }

  public void retire() {
    active = false;
    send("control", "shutdown", Map.of());
    executor.submit(
        () -> {
          try {
            if (process != null) process.waitFor(2, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          close();
        });
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    active = false;
    channels.values().forEach(Channel::close);
    if (process != null && process.isAlive()) process.destroyForcibly();
  }

  private final class Channel {
    private final Socket socket;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(256);
    private final AtomicLong bytes = new AtomicLong();

    private Channel(Socket socket) {
      this.socket = socket;
    }

    boolean offer(byte[] frame) {
      if (bytes.addAndGet(frame.length) > 8 * 1024 * 1024) {
        bytes.addAndGet(-frame.length);
        return false;
      }
      if (!queue.offer(frame)) {
        bytes.addAndGet(-frame.length);
        return false;
      }
      return true;
    }

    void run() {
      try {
        while (!closed) {
          byte[] frame = queue.poll(1, TimeUnit.SECONDS);
          if (frame == null) continue;
          bytes.addAndGet(-frame.length);
          socket.getOutputStream().write(frame);
          socket.getOutputStream().flush();
        }
      } catch (IOException exception) {
        close();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }

    void close() {
      try {
        socket.close();
      } catch (IOException ignored) {
        /* Already closing. */
      }
    }
  }
}
