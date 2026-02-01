package com.kademlia.dht.storage;

import java.util.Arrays;

/**
 * Wrapper for byte[] to use as map key (equals/hashCode).
 */
public final class ByteArray {
    private final byte[] data;
    private final int hash;

    public ByteArray(byte[] data) {
        this.data = data == null ? new byte[0] : data.clone();
        this.hash = Arrays.hashCode(this.data);
    }

    public byte[] getBytes() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteArray byteArray = (ByteArray) o;
        return Arrays.equals(data, byteArray.data);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
