package com.kademlia.dht.protocol;

/**
 * Sealed type for all RPC messages (requests and responses).
 */
public sealed interface RpcMessage permits RpcRequest, RpcResponse {
    byte[] messageId();
}
