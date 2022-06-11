package main.java.request;

import main.java.OpCode;
import main.java.wrapper.InetIpv4ChatFusion;

import java.nio.ByteBuffer;

public record RequestFusionRequest(InetIpv4ChatFusion address) implements Request {

    /**
     * Gets the {@link OpCode} associated with the {@link Request}
     * @return the {@link OpCode} associated with the {@link Request}
     */
    @Override
    public OpCode getOpCode() {
        return OpCode.FUSION_REQUEST;
    }

    /**
     * Returns the length of a {@link ByteBuffer} containing the {@link Request} data
     * @return the length of the {@link ByteBuffer}
     */
    @Override
    public int bufferLength() {
        return 1/*Byte*/ + address.bufferLength();
    }

    /**
     * Encodes the necessary data and puts it in the {@link ByteBuffer}
     * @return the {@link ByteBuffer} filled
     */
    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()).put(address.encode()).flip();
    }
}
