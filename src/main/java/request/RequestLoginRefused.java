package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestLoginRefused() implements Request {

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1; // OpCode
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).flip(); // OpCode
    }

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     *
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.LOGIN_REFUSED;
    }

}
