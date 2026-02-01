package com.kademlia.dht.storage;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract storage interface for key-value store with TTL and eviction.
 */
public interface IStorage {
    void put(byte[] key, byte[] value);

    Optional<byte[]> get(byte[] key);

    Iterator<Map.Entry<byte[], byte[]>> iterator();

    void cull();
}
