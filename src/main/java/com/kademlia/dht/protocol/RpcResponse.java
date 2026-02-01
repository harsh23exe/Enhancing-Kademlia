package com.kademlia.dht.protocol;

/**
 * RPC response: has messageId to match request.
 */
public sealed interface RpcResponse extends RpcMessage permits PingResponse, StoreResponse, FindNodeResponse, FindValueResponse {
    byte[] messageId();
}
