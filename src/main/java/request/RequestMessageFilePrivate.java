package main.java.request;

import main.java.OpCode;
import main.java.wrapper.StringChatFusion;

import java.nio.ByteBuffer;

public record RequestMessageFilePrivate(StringChatFusion serverSrc, StringChatFusion loginSrc,
                                        StringChatFusion serverDst, StringChatFusion loginDst,
                                        StringChatFusion filename, int nbBlocksMax, int blockSize,
                                        byte[] block) implements Request {
    public RequestMessageFilePrivate {
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
        if (blockSize > 5000) {
            throw new IllegalArgumentException("block size superior than 5000 bytes");
        }

        if (filename.size() > 30) {
            throw new IllegalArgumentException("filename length superior than 30 UTF8 characters");
        }
    }

    @Override
    public int bufferLength() {
        return 1 + serverDst.bufferLength() + serverSrc.bufferLength() + loginDst.bufferLength() + loginSrc.bufferLength() + filename.bufferLength() + Integer.BYTES * 2 + blockSize;
    }

    @Override
    public ByteBuffer encode() {
        return ByteBuffer.allocate(getOpCode().getOpCode()) // OpCode
                .put(serverSrc.encode()) // server source
                .put(loginSrc.encode()) // login source
                .put(serverDst.encode()) // server destination
                .put(loginDst.encode()) // login destination
                .put(filename.encode()) // filename
                .putInt(nbBlocksMax) // nbr max of block to send
                .putInt(blockSize) // size of block
                .put(block) // content of file
                .flip();
    }

    @Override
    public OpCode getOpCode() {
        return OpCode.FILE_PRIVATE;
    }
}
