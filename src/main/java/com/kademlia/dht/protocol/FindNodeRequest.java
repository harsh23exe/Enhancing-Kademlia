package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

import java.net.InetAddress;

public record FindNodeRequest(byte[] messageId, NodeId senderId, InetAddress senderIp, int senderPort,
                              NodeId targetId) implements RpcRequest {
    @Override
    public MessageType type() {
        return MessageType.FIND_NODE;
    }
}
