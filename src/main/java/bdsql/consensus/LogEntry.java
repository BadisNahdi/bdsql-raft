package bdsql.consensus;

public record LogEntry(long index, long term, byte[] data) {}
