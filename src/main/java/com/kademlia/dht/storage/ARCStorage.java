package com.kademlia.dht.storage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptive Replacement Cache: T1 (recent), T2 (frequent), B1/B2 (ghost lists).
 */
public class ARCStorage implements IStorage {
    private final int capacity;
    private int p;
    private final LinkedHashMap<ByteArray, StorageEntry> T1 = new LinkedHashMap<>();
    private final LinkedHashMap<ByteArray, StorageEntry> T2 = new LinkedHashMap<>();
    private final LinkedHashMap<ByteArray, StorageEntry> B1 = new LinkedHashMap<>();
    private final LinkedHashMap<ByteArray, StorageEntry> B2 = new LinkedHashMap<>();

    public ARCStorage(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.p = 0;
    }

    @Override
    public synchronized void put(byte[] key, byte[] value) {
        ByteArray k = new ByteArray(key);
        StorageEntry entry = new StorageEntry(System.nanoTime(), value);

        if (T1.containsKey(k) || T2.containsKey(k)) {
            T1.remove(k);
            T2.put(k, entry);
            return;
        }

        if (B1.containsKey(k)) {
            p = Math.min(capacity, p + Math.max(B2.size() / Math.max(1, B1.size()), 1));
            B1.remove(k);
            T2.put(k, entry);
        } else if (B2.containsKey(k)) {
            p = Math.max(0, p - Math.max(B1.size() / Math.max(1, B2.size()), 1));
            B2.remove(k);
            T2.put(k, entry);
        } else {
            if (T1.size() + T2.size() >= capacity) {
                replace(k);
            }
            T1.put(k, entry);
        }
    }

    private void replace(ByteArray k) {
        if (T1.size() >= 1 && (T1.size() > p || (B2.containsKey(k) && T1.size() == p))) {
            ByteArray evict = T1.keySet().iterator().next();
            T1.remove(evict);
            B1.put(evict, new StorageEntry(System.nanoTime(), null));
        } else {
            ByteArray evict = T2.keySet().iterator().next();
            T2.remove(evict);
            B2.put(evict, new StorageEntry(System.nanoTime(), null));
        }
        while (B1.size() + B2.size() > capacity) {
            if (B1.size() > 0) {
                B1.remove(B1.keySet().iterator().next());
            } else {
                B2.remove(B2.keySet().iterator().next());
            }
        }
    }

    @Override
    public synchronized Optional<byte[]> get(byte[] key) {
        ByteArray k = new ByteArray(key);
        StorageEntry entry = T1.remove(k);
        if (entry != null) {
            T2.put(k, entry);
            return Optional.of(entry.value());
        }
        entry = T2.get(k);
        if (entry != null) {
            T2.remove(k);
            T2.put(k, entry);
            return Optional.of(entry.value());
        }
        return Optional.empty();
    }

    @Override
    public Iterator<Map.Entry<byte[], byte[]>> iterator() {
        Map<ByteArray, StorageEntry> copy;
        synchronized (this) {
            copy = new LinkedHashMap<>();
            copy.putAll(T1);
            copy.putAll(T2);
        }
        return copy.entrySet().stream()
                .filter(e -> e.getValue().value() != null)
                .map(e -> Map.<byte[], byte[]>entry(e.getKey().getBytes(), e.getValue().value()))
                .iterator();
    }

    @Override
    public void cull() {
        // ARC is in-memory only; no TTL cull. Optional: evict oldest from B1/B2
    }
}
