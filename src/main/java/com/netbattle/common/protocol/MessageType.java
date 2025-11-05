// common/protocol/MessageType.java
package com.netbattle.common.protocol;

public enum MessageType {
    // Authentication
    LOGIN_REQUEST(0x01),
    LOGIN_RESPONSE(0x02),
    LOGOUT(0x03),
    
    // Game Actions
    JOIN_GAME(0x10),
    LEAVE_GAME(0x11),
    SUBMIT_FLAG(0x12),
    GET_CHALLENGE(0x13),
    
    // Real-time Updates (UDP)
    PLAYER_POSITION(0x20),
    FLAG_CAPTURED(0x21),
    SCORE_UPDATE(0x22),
    CHAT_MESSAGE(0x23),
    
    // Responses
    SUCCESS(0x30),
    ERROR(0x31),
    GAME_STATE(0x32);
    
    private final byte code;
    
    MessageType(int code) {
        this.code = (byte) code;
    }
    
    public byte getCode() {
        return code;
    }
}