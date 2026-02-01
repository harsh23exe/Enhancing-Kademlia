package com.kademlia.dht.protocol;

import com.kademlia.dht.node.Node;

import java.util.List;

public record FindNodeResponse(byte[] messageId, List<Node> nodes) implements RpcResponse {}
