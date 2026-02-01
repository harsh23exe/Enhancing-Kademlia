package com.kademlia.dht.protocol;

public record StoreResponse(byte[] messageId, boolean success) implements RpcResponse {}
