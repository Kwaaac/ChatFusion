package main.java.request;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginPassword(OpCode code, StringChatFusion login) implements RequestLogin {
    @Override
    public int bufferLength() {
        return 1 + login.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put((byte) code.getOpCode()).put(login.encode());
    }

    @Override
    public OpCode getOpCode() {
        return code;
    }

    @Override
    public StringChatFusion getLogin() {
        return login;
    }
}
