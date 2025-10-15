package bdsql.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class PersistentStateStore {
    private static final String MAGIC = "RFTS";
    private static final int VERSION = 1;

    private final Path file;

    public record PersistentState(long currentTerm, String votedFor) {}

    public PersistentStateStore(Path dir) {
        Objects.requireNonNull(dir);
        this.file = dir.resolve("persist.meta");
    }

    public synchronized PersistentState read() throws IOException {
        if (!Files.exists(file)) {
            return new PersistentState(0L, null);
        }

        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(in))) {

            byte[] magicBytes = new byte[4];
            dis.readFully(magicBytes);
            String magic = new String(magicBytes, "UTF-8");
            if (!MAGIC.equals(magic)) {
                throw new IOException("Invalid persistent state file (bad magic)");
            }

            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported persistent state version: " + version);
            }

            long term = dis.readLong();
            int hasVoted = dis.readInt();
            String votedFor = null;
            if (hasVoted == 1) {
                int len = dis.readInt();
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                votedFor = new String(bytes, "UTF-8");
            }
            return new PersistentState(term, votedFor);
        }
    }

    public synchronized void write(long term, String votedFor) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(MAGIC.getBytes("UTF-8"));
            dos.writeInt(VERSION);
            dos.writeLong(term);
            if (votedFor != null) {
                dos.writeInt(1);
                byte[] vf = votedFor.getBytes("UTF-8");
                dos.writeInt(vf.length);
                dos.write(vf);
            } else {
                dos.writeInt(0);
            }
            dos.flush();
        }

        byte[] payload = baos.toByteArray();

        try (FileChannel ch = FileChannel.open(tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }

        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException amnse) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            try (FileChannel dirCh = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                dirCh.force(true);
            }
        } catch (Exception ignore) {
        }
    }
}
