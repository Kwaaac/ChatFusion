package main.java.request;

import main.java.OpCode;

import java.nio.ByteBuffer;

public record RequestLoginRefused() implements Request {
        @Override

        public int bufferLength() {
            return 1; // OpCode
        }

        @Override
        public ByteBuffer encode() {
            return ByteBuffer.allocate(bufferLength()).put(getOpCode().getOpCode()); // OpCode
        }

        @Override
        public OpCode getOpCode() {
            return OpCode.LOGIN_REFUSED;
        }

}
