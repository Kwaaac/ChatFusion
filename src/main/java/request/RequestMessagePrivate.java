package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestMessagePrivate(StringChatFusion serverSrc,
                                    StringChatFusion loginSrc,
                                    StringChatFusion serverDst,
                                    StringChatFusion loginDst,
                                    StringChatFusion message) implements Request {

    public RequestMessagePrivate {
        if (serverSrc.size() > 100) {
            throw new IllegalArgumentException("Server_src length superior than 100 UTF8 characters");
        }
        if (serverDst.size() > 100) {
            throw new IllegalArgumentException("Server_dst length superior than 100 UTF8 characters");
        }
        if (loginSrc.size() > 30) {
            throw new IllegalArgumentException("login_src length superior than 30 UTF8 characters");
        }
        if (loginDst.size() > 30) {
            throw new IllegalArgumentException("login_dst length superior than 30 UTF8 characters");
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
        return 1 + serverSrc.bufferLength() + loginSrc.bufferLength() + serverDst.bufferLength() + loginDst.bufferLength() + message.bufferLength();
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength())
                .put(getOpCode().getOpCode())
                .put(serverSrc.encode())
                .put(loginSrc.encode())
                .put(serverDst.encode())
                .put(loginDst.encode())
                .put(message.encode())
                .flip();
    }

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.PRIVATE_MESSAGE;
    }
}
