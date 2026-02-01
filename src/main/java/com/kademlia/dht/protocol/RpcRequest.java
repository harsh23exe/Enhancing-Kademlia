package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

import java.net.InetAddress;

/**
 * RPC request: has type, messageId, and sender info.
 */
public sealed interface RpcRequest extends RpcMessage permits PingRequest, StoreRequest, FindNodeRequest, FindValueRequest {
    MessageType type();
    byte[] messageId();
    NodeId senderId();
    InetAddress senderIp();
    int senderPort();
}
