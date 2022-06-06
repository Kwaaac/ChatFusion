package main.java.reader;

import main.java.BufferSerializable;
import main.java.OpCode;

import java.nio.ByteBuffer;

/**
 * Record representing a request, a request has an opcode defining the kind of request and a buffer containing the request information
 * <p>
 * The buffer is in read-mode
 *
 * @param code
 * @param buffer
 */
public record RecordRequest(OpCode code, ByteBuffer buffer) implements BufferSerializable {

    @Override
    public int bufferLength() {
        return Integer.BYTES + buffer().remaining();
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(bufferLength()).putInt(code.getOpCode()).put(buffer).flip();
    }
}
