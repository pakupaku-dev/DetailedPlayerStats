package jp.kamei.detailedstats;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local write-ahead journal. MySQL failures leave replayable files on disk. */
public final class Journal implements AutoCloseable {
    private final Path directory;
    private FileChannel channel;
    private Path current;
    private int count;
    public Journal(Path directory) throws IOException {
        this.directory = directory; Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".open")).toList()) {
                // Only the final partial line can result from an interrupted append.
                byte[] bytes = Files.readAllBytes(file);
                int end = bytes.length;
                while (end > 0 && bytes[end - 1] != '\n') end--;
                if (end != bytes.length) Files.write(file, Arrays.copyOf(bytes, end));
                Files.move(file, ready(file), StandardCopyOption.ATOMIC_MOVE);
            }
        }
    }
    private static Path ready(Path p) { return p.resolveSibling(p.getFileName().toString().replace(".open", ".jsonl")); }
    public synchronized void append(JsonObject record) throws IOException {
        if (channel == null) {
            current = directory.resolve(UUID.randomUUID() + ".open");
            channel = FileChannel.open(current, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(record.toString() + "\n");
        while (bytes.hasRemaining()) channel.write(bytes);
        count++;
        if (count >= 500) rotate();
    }
    public synchronized void rotate() throws IOException {
        if (channel == null) return;
        channel.force(true); channel.close(); channel = null;
        Files.move(current, ready(current), StandardCopyOption.ATOMIC_MOVE); count = 0;
    }
    public List<Path> pending() throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList();
        }
    }
    @Override public void close() throws IOException { rotate(); }
}
