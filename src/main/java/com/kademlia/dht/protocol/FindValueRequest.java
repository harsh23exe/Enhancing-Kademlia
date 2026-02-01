package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

import java.net.InetAddress;

public record FindValueRequest(byte[] messageId, NodeId senderId, InetAddress senderIp, int senderPort,
                               byte[] key) implements RpcRequest {
    @Override
    public MessageType type() {
        return MessageType.FIND_VALUE;
    }
}
