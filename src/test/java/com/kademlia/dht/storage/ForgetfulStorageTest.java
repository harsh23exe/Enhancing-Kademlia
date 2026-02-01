package com.kademlia.dht.storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ForgetfulStorageTest {

    @Test
    void testPutAndGet() {
        ForgetfulStorage storage = new ForgetfulStorage(3600);
        byte[] key = "key".getBytes();
        byte[] value = "value".getBytes();
        storage.put(key, value);
        Optional<byte[]> got = storage.get(key);
        assertTrue(got.isPresent());
        assertArrayEquals(value, got.get());
    }

    @Test
    void testTTLEviction() throws InterruptedException {
        ForgetfulStorage storage = new ForgetfulStorage(1);
        storage.put("key".getBytes(), "value".getBytes());
        assertTrue(storage.get("key".getBytes()).isPresent());
        Thread.sleep(1100);
        storage.cull();
        assertFalse(storage.get("key".getBytes()).isPresent());
    }

    @Test
    void testGetMissing() {
        ForgetfulStorage storage = new ForgetfulStorage(3600);
        assertTrue(storage.get("missing".getBytes()).isEmpty());
    }
}
