package com.kademlia.dht.storage;

/**
 * Timestamped value for TTL and eviction.
 */
public record StorageEntry(long timestamp, byte[] value) {
    public StorageEntry {
        if (value != null) {
            value = value.clone();
        }
    }

    public byte[] value() {
        return value == null ? null : value.clone();
    }
}
