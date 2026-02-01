package com.kademlia.dht.protocol;

import com.kademlia.dht.node.Node;

import java.util.List;
import java.util.Optional;

public record FindValueResponse(byte[] messageId, Optional<byte[]> value, List<Node> nodes) implements RpcResponse {}
