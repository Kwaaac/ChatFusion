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

    @Override
    public int bufferLength() {
        return 1 + Integer.BYTES * 5 + serverDst.bufferLength() + serverSrc.bufferLength() + loginDst.bufferLength() + loginSrc.bufferLength() + message.bufferLength();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(getOpCode().getOpCode())
                .put(serverSrc.encode())
                .put(loginSrc.encode())
                .put(serverDst.encode())
                .put(loginDst.encode())
                .put(message.encode())
                .flip();
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.PRIVATE_MESSAGE;
    }
}
