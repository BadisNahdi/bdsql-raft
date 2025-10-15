package bdsql.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class WriteAheadLog implements Closeable {
    private final Path walFile;
    private final FileChannel channel;
    private long lastIndex = 0L;
    private long lastTerm = 0L;

    public record WALRecord(long index, long term, byte[] data) {}

    public WriteAheadLog(Path dir) throws IOException {
        Files.createDirectories(dir);
        this.walFile = dir.resolve("wal.log");
        this.channel = FileChannel.open(walFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        recover();
    }

    private void recover() throws IOException {
        long pos = 0;
        ByteBuffer intBuf = ByteBuffer.allocate(4);
        while (pos < channel.size()) {
            intBuf.clear();
            channel.position(pos);
            int bytesRead = channel.read(intBuf);
            if (bytesRead < 4) break;
            intBuf.flip();
            int recLen = intBuf.getInt();
            if (recLen <= 0) {
                channel.truncate(pos);
                break;
            }
            long headerSize = 4 + recLen;
            if (pos + headerSize > channel.size()) {
                channel.truncate(pos);
                break;
            }
            ByteBuffer payload = ByteBuffer.allocate(recLen);
            channel.position(pos + 4);
            channel.read(payload);
            payload.flip();
            long term = payload.getLong();
            long index = payload.getLong();
            byte[] data = new byte[recLen - 16];
            if (data.length > 0) payload.get(data);
            lastIndex = index;
            lastTerm = term;
            pos += headerSize;
        }
        channel.position(channel.size());
    }

    public synchronized long append(long term, byte[] data) throws IOException {
        long expectedIndex = lastIndex + 1;
        return appendWithExpectedIndex(expectedIndex, term, data);
    }

    public synchronized long appendWithExpectedIndex(long expectedIndex, long term, byte[] data) throws IOException {
        if (expectedIndex != lastIndex + 1) {
            throw new IOException("WAL append index mismatch. expected " + (lastIndex + 1) + " but got " + expectedIndex);
        }
        int recLen = 8 + 8 + (data == null ? 0 : data.length);
        ByteBuffer buf = ByteBuffer.allocate(4 + recLen);
        buf.putInt(recLen);
        buf.putLong(term);
        buf.putLong(expectedIndex);
        if (data != null && data.length > 0) buf.put(data);
        buf.flip();
        while (buf.hasRemaining()) channel.write(buf);
        channel.force(true);
        lastIndex = expectedIndex;
        lastTerm = term;
        return lastIndex;
    }

    public synchronized List<WALRecord> readAll() throws IOException {
        List<WALRecord> out = new ArrayList<>();
        long pos = 0;
        ByteBuffer intBuf = ByteBuffer.allocate(4);
        while (pos < channel.size()) {
            intBuf.clear();
            channel.position(pos);
            int r = channel.read(intBuf);
            if (r < 4) break;
            intBuf.flip();
            int recLen = intBuf.getInt();
            if (recLen <= 0) break;
            if (pos + 4 + recLen > channel.size()) break;
            ByteBuffer payload = ByteBuffer.allocate(recLen);
            channel.position(pos + 4);
            channel.read(payload);
            payload.flip();
            long term = payload.getLong();
            long index = payload.getLong();
            byte[] data = new byte[recLen - 16];
            if (data.length > 0) payload.get(data);
            out.add(new WALRecord(index, term, data));
            pos += 4 + recLen;
        }
        return out;
    }


    public synchronized void truncateSuffixFrom(long indexExclusive) throws IOException {
        if (indexExclusive < 0) {
            channel.truncate(0);
            channel.force(true);
            lastIndex = 0;
            lastTerm = 0;
            channel.position(0);
            return;
        }
        long pos = 0;
        long truncatePos = 0;
        ByteBuffer intBuf = ByteBuffer.allocate(4);
        while (pos < channel.size()) {
            intBuf.clear();
            channel.position(pos);
            int r = channel.read(intBuf);
            if (r < 4) break;
            intBuf.flip();
            int recLen = intBuf.getInt();
            if (recLen <= 0 || pos + 4 + recLen > channel.size()) break;
            ByteBuffer payload = ByteBuffer.allocate(recLen);
            channel.position(pos + 4);
            channel.read(payload);
            payload.flip();
            long term = payload.getLong();
            long index = payload.getLong();
            if (index <= indexExclusive) {
                truncatePos = pos + 4 + recLen;
            } else {
                break;
            }
            pos = pos + 4 + recLen;
        }
        channel.truncate(truncatePos);
        channel.force(true);
        if (truncatePos == 0) {
            lastIndex = 0;
            lastTerm = 0;
            channel.position(0);
        } else {
            long readPos = 0;
            long li = 0;
            long lt = 0;
            pos = 0;
            while (pos < truncatePos) {
                intBuf.clear();
                channel.position(pos);
                channel.read(intBuf);
                intBuf.flip();
                int recLen = intBuf.getInt();
                ByteBuffer payload = ByteBuffer.allocate(recLen);
                channel.position(pos + 4);
                channel.read(payload);
                payload.flip();
                lt = payload.getLong();
                li = payload.getLong();
                pos = pos + 4 + recLen;
            }
            lastIndex = li;
            lastTerm = lt;
            channel.position(channel.size());
        }
    }

    public synchronized long getLastIndex() {
        return lastIndex;
    }

    public synchronized long getLastTerm() {
        return lastTerm;
    }

    @Override
    public synchronized void close() throws IOException {
        channel.close();
    }
}
