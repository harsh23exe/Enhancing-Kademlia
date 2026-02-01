package com.kademlia.dht.storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ARCStorageTest {

    @Test
    void testPutAndGet() {
        ARCStorage storage = new ARCStorage(10);
        storage.put("k1".getBytes(), "v1".getBytes());
        Optional<byte[]> got = storage.get("k1".getBytes());
        assertTrue(got.isPresent());
        assertArrayEquals("v1".getBytes(), got.get());
    }

    @Test
    void testARCAdaptation() {
        ARCStorage storage = new ARCStorage(10);
        for (int i = 0; i < 10; i++) {
            storage.put(("k" + i).getBytes(), ("v" + i).getBytes());
        }
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 5; i++) {
                storage.get(("k" + i).getBytes());
            }
        }
        for (int i = 10; i < 15; i++) {
            storage.put(("k" + i).getBytes(), ("v" + i).getBytes());
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(storage.get(("k" + i).getBytes()).isPresent(), "k" + i + " should be present");
        }
    }
}
