package com.kademlia.dht.node;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 160-bit node identifier (20 bytes). Supports XOR distance for Kademlia routing.
 */
public final class NodeId {
    public static final int SIZE_BYTES = 20;
    public static final int SIZE_BITS = 160;

    private final byte[] id;

    public NodeId(byte[] id) {
        if (id == null || id.length != SIZE_BYTES) {
            throw new IllegalArgumentException("NodeId must be exactly " + SIZE_BYTES + " bytes");
        }
        this.id = id.clone();
    }

    public byte[] getBytes() {
        return id.clone();
    }

    public BigInteger toBigInteger() {
        return new BigInteger(1, id);
    }

    /**
     * XOR distance: smaller return value means closer (same ID = 0, differing at MSB = 159).
     * Used for ordering "K closest" (min-heap of distance).
     */
    public int distanceTo(NodeId other) {
        if (this.equals(other)) {
            return 0;
        }
        for (int i = 0; i < SIZE_BYTES; i++) {
            int xor = (this.id[i] ^ other.id[i]) & 0xFF;
            if (xor != 0) {
                int bitPosition = i * 8 + (7 - Integer.numberOfLeadingZeros(xor));
                return bitPosition + 1;
            }
        }
        return 0;
    }

    /**
     * XOR distance as BigInteger. Smaller value means closer in XOR metric.
     */
    public BigInteger xorDistance(NodeId other) {
        byte[] xor = new byte[SIZE_BYTES];
        for (int i = 0; i < SIZE_BYTES; i++) {
            xor[i] = (byte) (this.id[i] ^ other.id[i]);
        }
        return new BigInteger(1, xor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeId nodeId = (NodeId) o;
        return Arrays.equals(id, nodeId.id);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public String toString() {
        return toBigInteger().toString(16);
    }
}
