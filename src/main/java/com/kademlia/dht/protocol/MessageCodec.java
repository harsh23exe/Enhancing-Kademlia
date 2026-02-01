package com.kademlia.dht.protocol;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binary serialization for RPC messages. Format: [type:1][messageId:4][payload...]
 */
public class MessageCodec {

    private static final int NODE_ID_LEN = 20;
    private static final int MESSAGE_ID_LEN = 4;
    private static final int IPV4_LEN = 4;

    public byte[] encode(RpcMessage msg) throws IOException {
        if (msg instanceof RpcRequest req) {
            return encodeRequest(req);
        }
        return encodeResponse((RpcResponse) msg);
    }

    private byte[] encodeRequest(RpcRequest msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(msg.type().getCode());
        writeMessageId(dos, msg.messageId());
        switch (msg) {
            case PingRequest req -> writeSender(dos, req.senderId(), req.senderIp(), req.senderPort());
            case StoreRequest req -> {
                writeSender(dos, req.senderId(), req.senderIp(), req.senderPort());
                dos.writeInt(req.key().length);
                dos.write(req.key());
                dos.writeInt(req.value().length);
                dos.write(req.value());
            }
            case FindNodeRequest req -> {
                writeSender(dos, req.senderId(), req.senderIp(), req.senderPort());
                dos.write(req.targetId().getBytes());
            }
            case FindValueRequest req -> {
                writeSender(dos, req.senderId(), req.senderIp(), req.senderPort());
                dos.writeInt(req.key().length);
                dos.write(req.key());
            }
        }
        return baos.toByteArray();
    }

    private byte[] encodeResponse(RpcResponse msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(msg instanceof PingResponse ? MessageType.PING_RESPONSE.getCode()
                : msg instanceof StoreResponse ? MessageType.STORE_RESPONSE.getCode()
                : msg instanceof FindNodeResponse ? MessageType.FIND_NODE_RESPONSE.getCode()
                : MessageType.FIND_VALUE_RESPONSE.getCode());
        writeMessageId(dos, msg.messageId());
        switch (msg) {
            case PingResponse r -> dos.write(r.nodeId().getBytes());
            case StoreResponse r -> dos.writeBoolean(r.success());
            case FindNodeResponse r -> {
                dos.writeInt(r.nodes().size());
                for (Node n : r.nodes()) {
                    writeNode(dos, n);
                }
            }
            case FindValueResponse r -> {
                if (r.value().isPresent()) {
                    dos.writeBoolean(true);
                    byte[] v = r.value().get();
                    dos.writeInt(v.length);
                    dos.write(v);
                } else {
                    dos.writeBoolean(false);
                    dos.writeInt(r.nodes().size());
                    for (Node n : r.nodes()) {
                        writeNode(dos, n);
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    public RpcMessage decode(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        MessageType type = MessageType.fromCode(dis.readByte());
        byte[] msgId = readMessageId(dis);
        return (RpcMessage) switch (type) {
            case PING -> new PingRequest(msgId, readNodeId(dis), readInetAddress(dis), dis.readUnsignedShort());
            case STORE -> new StoreRequest(msgId, readNodeId(dis), readInetAddress(dis), dis.readUnsignedShort(),
                    readBytes(dis, dis.readInt()), readBytes(dis, dis.readInt()));
            case FIND_NODE -> new FindNodeRequest(msgId, readNodeId(dis), readInetAddress(dis), dis.readUnsignedShort(),
                    readNodeId(dis));
            case FIND_VALUE -> new FindValueRequest(msgId, readNodeId(dis), readInetAddress(dis), dis.readUnsignedShort(),
                    readBytes(dis, dis.readInt()));
            case PING_RESPONSE -> new PingResponse(msgId, new NodeId(dis.readNBytes(NODE_ID_LEN)));
            case STORE_RESPONSE -> new StoreResponse(msgId, dis.readBoolean());
            case FIND_NODE_RESPONSE -> new FindNodeResponse(msgId, readNodeList(dis));
            case FIND_VALUE_RESPONSE -> {
                boolean hasValue = dis.readBoolean();
                if (hasValue) {
                    yield new FindValueResponse(msgId, Optional.of(readBytes(dis, dis.readInt())), List.of());
                } else {
                    yield new FindValueResponse(msgId, Optional.empty(), readNodeList(dis));
                }
            }
        };
    }

    private static void writeMessageId(DataOutputStream dos, byte[] messageId) throws IOException {
        dos.write(messageId.length >= MESSAGE_ID_LEN ? messageId : pad(messageId, MESSAGE_ID_LEN));
    }

    private static byte[] readMessageId(DataInputStream dis) throws IOException {
        return dis.readNBytes(MESSAGE_ID_LEN);
    }

    private static void writeSender(DataOutputStream dos, NodeId senderId, InetAddress senderIp, int senderPort) throws IOException {
        dos.write(senderId.getBytes());
        dos.write(senderIp.getAddress().length >= IPV4_LEN ? senderIp.getAddress() : pad(senderIp.getAddress(), IPV4_LEN));
        dos.writeShort(senderPort & 0xFFFF);
    }

    private static void writeNode(DataOutputStream dos, Node node) throws IOException {
        dos.write(node.id().getBytes());
        byte[] addr = node.ip().getAddress();
        dos.write(addr.length >= IPV4_LEN ? addr : pad(addr, IPV4_LEN));
        dos.writeShort(node.port() & 0xFFFF);
    }

    private static NodeId readNodeId(DataInputStream dis) throws IOException {
        return new NodeId(dis.readNBytes(NODE_ID_LEN));
    }

    private static InetAddress readInetAddress(DataInputStream dis) throws IOException {
        return InetAddress.getByAddress(dis.readNBytes(IPV4_LEN));
    }

    private static byte[] readBytes(DataInputStream dis, int len) throws IOException {
        return dis.readNBytes(len);
    }

    private static List<Node> readNodeList(DataInputStream dis) throws IOException {
        int n = dis.readInt();
        List<Node> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            NodeId id = new NodeId(dis.readNBytes(NODE_ID_LEN));
            InetAddress ip = readInetAddress(dis);
            int port = dis.readUnsignedShort();
            list.add(new Node(id, ip, port));
        }
        return list;
    }

    private static byte[] pad(byte[] b, int len) {
        if (b.length >= len) return b;
        byte[] out = new byte[len];
        System.arraycopy(b, 0, out, len - b.length, b.length);
        return out;
    }
}
