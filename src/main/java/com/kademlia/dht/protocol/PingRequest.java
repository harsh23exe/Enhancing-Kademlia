package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

import java.net.InetAddress;

public record PingRequest(byte[] messageId, NodeId senderId, InetAddress senderIp, int senderPort)
        implements RpcRequest {
    @Override
    public MessageType type() {
        return MessageType.PING;
    }
}
