package main.java.request;

import main.java.BufferSerializable;
import main.java.OpCode;

import java.nio.ByteBuffer;

/**
 * Record representing a request, a request has an opcode defining the kind of request and a buffer containing the request information
 * <p>
 * The buffer is in read-mode when get
 */
public sealed interface Request extends BufferSerializable permits RequestMessage, RequestLogin {
    OpCode getOpCode();
}
