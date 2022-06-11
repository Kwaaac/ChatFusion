package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestLoginAccepted(StringChatFusion serverName) implements Request {
    public RequestLoginAccepted {
        if (serverName.size() > 100) {
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        }
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_ACCEPTED;
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1/*Byte*/ + serverName.bufferLength();
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(serverName.encode()).flip();
    }
}
