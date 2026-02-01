package com.kademlia.dht.node;

import java.math.BigInteger;
import java.net.InetAddress;

/**
 * Node entity: 160-bit ID, IP address, and port.
 */
public record Node(NodeId id, InetAddress ip, int port) {
    public int distanceTo(Node other) {
        return this.id.distanceTo(other.id);
    }

    public BigInteger xorDistance(Node other) {
        return this.id.xorDistance(other.id);
    }

    public boolean sameHome(Node other) {
        if (ip == null || other.ip() == null) return false;
        return this.ip.equals(other.ip()) && this.port == other.port();
    }

    @Override
    public String toString() {
        if (ip != null) {
            return ip.getHostAddress() + ":" + port;
        }
        return id.toString() + ":?";
    }
}
