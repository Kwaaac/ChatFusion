package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginPassword(StringChatFusion login, StringChatFusion password) implements Request {
    public RequestLoginPassword {
        if(login.size() > 30)
            throw new IllegalArgumentException("Login length superior than 30 UTF8 characters");
        if(password.size() > 30)
            throw new IllegalArgumentException("Password length superior than 30 UTF8 characters");
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1 + login.bufferLength() + password.bufferLength();
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(login.encode()).put(password.encode()).flip();
    }

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_PASSWORD;
    }
}
