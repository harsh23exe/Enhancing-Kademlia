package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

import java.net.InetAddress;

public record StoreRequest(byte[] messageId, NodeId senderId, InetAddress senderIp, int senderPort,
                          byte[] key, byte[] value) implements RpcRequest {
    @Override
    public MessageType type() {
        return MessageType.STORE;
    }
}
