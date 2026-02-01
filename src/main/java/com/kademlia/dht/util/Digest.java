package com.kademlia.dht.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-1 hashing for node IDs and key digests (160-bit).
 */
public final class Digest {
    private static final MessageDigest SHA1;

    static {
        try {
            SHA1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private Digest() {}

    public static byte[] hash(byte[] input) {
        synchronized (SHA1) {
            SHA1.reset();
            return SHA1.digest(input);
        }
    }

    public static byte[] hash(String input) {
        return hash(input.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns 20-byte SHA-1 digest for use as NodeId or key digest. */
    public static byte[] digest(String key) {
        return hash(key);
    }
}
