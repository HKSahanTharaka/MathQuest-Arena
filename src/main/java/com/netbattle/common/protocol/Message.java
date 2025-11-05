// common/protocol/Message.java
package com.netbattle.common.protocol;

import java.io.*;
import java.nio.ByteBuffer;

public class Message implements Serializable {
    private MessageType type;
    private String sessionId;
    private long timestamp;
    private byte[] payload;
    
    public Message(MessageType type, String sessionId, byte[] payload) {
        this.type = type;
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload;
    }
    
    // Serialize to bytes for network transmission
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeByte(type.getCode());
        dos.writeUTF(sessionId != null ? sessionId : "");
        dos.writeLong(timestamp);
        dos.writeInt(payload != null ? payload.length : 0);
        if (payload != null) {
            dos.write(payload);
        }
        
        return baos.toByteArray();
    }
    
    // Deserialize from bytes
    public static Message fromBytes(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        byte typeCode = dis.readByte();
        String sessionId = dis.readUTF();
        long timestamp = dis.readLong();
        int payloadLength = dis.readInt();
        
        byte[] payload = null;
        if (payloadLength > 0) {
            payload = new byte[payloadLength];
            dis.readFully(payload);
        }
        
        // Find MessageType by code
        MessageType type = null;
        for (MessageType mt : MessageType.values()) {
            if (mt.getCode() == typeCode) {
                type = mt;
                break;
            }
        }
        
        Message msg = new Message(type, sessionId, payload);
        msg.timestamp = timestamp;
        return msg;
    }
    
    // Getters and setters
    public MessageType getType() { return type; }
    public String getSessionId() { return sessionId; }
    public long getTimestamp() { return timestamp; }
    public byte[] getPayload() { return payload; }
    
    public String getPayloadAsString() {
        return payload != null ? new String(payload) : "";
    }
}