package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;

public record PingResponse(byte[] messageId, NodeId nodeId) implements RpcResponse {}
