package main.java.reader;

import main.java.OpCode;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Record representing a request, a request has an opcode defining the kind of request and a buffer containing the request information
 * <p>
 * The buffer is in read-mode
 *
 * @param code
 * @param buffer
 */
public record Request(OpCode code, ByteBuffer buffer) {

    /**
     * @return In bytes, the length of the request
     */
    public int length() {
        return Integer.BYTES + buffer().remaining();
    }

    /**
     * encode the request into a ByteBuffer, the buffer is in read mode
     *
     * @return ByteBuffer containing the request in read mode
     */
    public ByteBuffer encode() {
        return ByteBuffer.allocate(length()).putInt(code.getOpCode()).put(buffer).flip();
    }
}
