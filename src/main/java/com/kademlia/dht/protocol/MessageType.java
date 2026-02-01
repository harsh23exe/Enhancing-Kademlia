package com.kademlia.dht.protocol;

/**
 * RPC message type codes for wire format.
 */
public enum MessageType {
    PING(0x01),
    STORE(0x02),
    FIND_NODE(0x03),
    FIND_VALUE(0x04),
    PING_RESPONSE(0x11),
    STORE_RESPONSE(0x12),
    FIND_NODE_RESPONSE(0x13),
    FIND_VALUE_RESPONSE(0x14);

    private final byte code;

    MessageType(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    private static final MessageType[] BY_CODE = new MessageType[256];
    static {
        for (MessageType t : values()) {
            BY_CODE[t.code & 0xFF] = t;
        }
    }

    public static MessageType fromCode(byte code) {
        MessageType t = BY_CODE[code & 0xFF];
        if (t == null) throw new IllegalArgumentException("Unknown message type: " + (code & 0xFF));
        return t;
    }
}
