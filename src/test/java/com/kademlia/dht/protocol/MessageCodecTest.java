package com.kademlia.dht.protocol;

import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.util.Digest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    @Test
    void testPingRequestRoundTrip() throws Exception {
        MessageCodec codec = new MessageCodec();
        PingRequest req = new PingRequest(
                new byte[]{1, 2, 3, 4},
                new NodeId(Digest.hash("test")),
                InetAddress.getByName("127.0.0.1"),
                8468
        );
        byte[] encoded = codec.encode(req);
        RpcMessage decoded = codec.decode(encoded);
        assertTrue(decoded instanceof PingRequest);
        PingRequest decodedReq = (PingRequest) decoded;
        assertArrayEquals(req.messageId(), decodedReq.messageId());
        assertEquals(req.senderPort(), decodedReq.senderPort());
    }

    @Test
    void testPingResponseRoundTrip() throws Exception {
        MessageCodec codec = new MessageCodec();
        PingResponse resp = new PingResponse(
                new byte[]{1, 2, 3, 4},
                new NodeId(Digest.hash("node"))
        );
        byte[] encoded = codec.encode(resp);
        RpcMessage decoded = codec.decode(encoded);
        assertTrue(decoded instanceof PingResponse);
        PingResponse decodedResp = (PingResponse) decoded;
        assertArrayEquals(resp.messageId(), decodedResp.messageId());
        assertEquals(resp.nodeId(), decodedResp.nodeId());
    }
}
