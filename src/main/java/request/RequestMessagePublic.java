package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestMessagePublic(StringChatFusion serverName, StringChatFusion login,
                                   StringChatFusion message) implements Request {
    public RequestMessagePublic {
        if (serverName.size() > 100) {
            throw new IllegalArgumentException("severName length superior than 100 UTF8 characters");
        }
        if (login.size() > 30) {
            throw new IllegalArgumentException("login length superior than 30 UTF8 characters");
        }
        if (message.size() > 1024) {
            throw new IllegalArgumentException("message length superior than 1024 UTF8 characters");
        }
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1 + Integer.BYTES * 3 + serverName.bufferLength() + login.bufferLength() + message.bufferLength();
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()) // OpCode
                .put(serverName.encode()) // Server Name size + Server name
                .put(login.encode()) // Login size + login
                .put(message.encode()).flip(); // message size + message

    }

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.MESSAGE;
    }
}
