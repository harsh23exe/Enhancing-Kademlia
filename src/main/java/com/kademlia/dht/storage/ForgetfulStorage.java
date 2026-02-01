package com.kademlia.dht.storage;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-based storage with automatic eviction on get and via cull().
 */
public class ForgetfulStorage implements IStorage {
    private final ConcurrentHashMap<ByteArray, StorageEntry> data = new ConcurrentHashMap<>();
    private final long ttlNanos;

    public ForgetfulStorage(long ttlSeconds) {
        this.ttlNanos = ttlSeconds * 1_000_000_000L;
    }

    @Override
    public void put(byte[] key, byte[] value) {
        data.put(new ByteArray(key), new StorageEntry(System.nanoTime(), value));
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        StorageEntry entry = data.get(new ByteArray(key));
        if (entry == null) return Optional.empty();
        if (System.nanoTime() - entry.timestamp() > ttlNanos) {
            data.remove(new ByteArray(key));
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public Iterator<Map.Entry<byte[], byte[]>> iterator() {
        return new Iterator<>() {
            private final Iterator<Map.Entry<ByteArray, StorageEntry>> it = data.entrySet().iterator();
            private long now = System.nanoTime();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<byte[], byte[]> next() {
                Map.Entry<ByteArray, StorageEntry> e = it.next();
                StorageEntry entry = e.getValue();
                if (now - entry.timestamp() > ttlNanos) {
                    throw new ConcurrentModificationException("expired during iteration");
                }
                return Map.entry(e.getKey().getBytes(), entry.value());
            }
        };
    }

    @Override
    public void cull() {
        long now = System.nanoTime();
        data.entrySet().removeIf(e -> now - e.getValue().timestamp() > ttlNanos);
    }
}
