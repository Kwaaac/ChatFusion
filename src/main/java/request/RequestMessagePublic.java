package main.java.request;

import main.java.OpCode;
import main.java.request.RequestMessage;

import java.nio.ByteBuffer;

public record RequestMessagePublic() implements RequestMessage {
    @Override
    public OpCode getOpCode() {
        return null;
    }

    @Override
    public ByteBuffer getBuffer() {
        return null;
    }

    @Override
    public String getServer() {
        return null;
    }

    @Override
    public String getLogin() {
        return null;
    }
}
