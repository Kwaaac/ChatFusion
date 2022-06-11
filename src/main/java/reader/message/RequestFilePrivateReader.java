package main.java.reader.message;

import main.java.Utils.RequestFactory;
import main.java.reader.IntReader;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestFilePrivateReader implements Reader<Request> {
    private final Reader<String> stringReader = new StringReader();
    private final Reader<Integer> intReader = new IntReader();
    private String serverSrc;
    private String serverDst;
    private String loginSrc;
    private String loginDst;
    private String filename;
    private int nbBlockMax;
    private int blockSize;
    private ByteBuffer internalBuffer;

    private State state = State.WAIT_SERVER_SRC;

    /**
     * Retrieves data from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (this.state == State.DONE || this.state == State.ERROR) {
            throw new IllegalStateException();
        }

        while (state != State.WAIT_BLOCK) {
            ProcessStatus status;
            if (state == State.WAIT_NB_BLOCK_MAX || state == State.WAIT_BLOCK_SIZE) {
                status = intReader.process(bb);
            } else {
                status = stringReader.process(bb);
            }
            switch (status) {
                case DONE -> {
                    switch (state) {
                        case WAIT_SERVER_SRC -> {
                            serverSrc = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_LOGIN_SRC;
                        }
                        case WAIT_LOGIN_SRC -> {
                            loginSrc = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_SERVER_DST;
                        }
                        case WAIT_SERVER_DST -> {
                            serverDst = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_LOGIN_DST;
                        }
                        case WAIT_LOGIN_DST -> {
                            loginDst = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_FILENAME;
                        }
                        case WAIT_FILENAME -> {
                            filename = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_NB_BLOCK_MAX;
                        }
                        case WAIT_NB_BLOCK_MAX -> {
                            nbBlockMax = intReader.get();
                            intReader.reset();
                            state = State.WAIT_BLOCK_SIZE;
                        }
                        case WAIT_BLOCK_SIZE -> {
                            blockSize = intReader.get();
                            internalBuffer = ByteBuffer.allocate(blockSize);
                            intReader.reset();
                            state = State.WAIT_BLOCK;
                        }
                    }
                }

                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    this.state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
            }
        }

        bb.flip();
        try {
            if (bb.remaining() <= this.internalBuffer.remaining()) {
                this.internalBuffer.put(bb);
            } else {
                int oldLimit = bb.limit();
                var remaining = this.internalBuffer.remaining();
                var delta = bb.position() == 0 ? 0 : blockSize - bb.position() + 1;
                bb.limit(remaining + delta);
                this.internalBuffer.put(bb);
                bb.limit(oldLimit);
            }
        } finally {
            bb.compact();
        }

        if (this.internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        } else {
            this.internalBuffer.flip();
            this.state = State.DONE;
            return ProcessStatus.DONE;
        }
    }

    /**
     * Gets the {@link Request} retrieved by the process method
     * @return the {@link Request} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    @Override
    public Request get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return RequestFactory.privateFile(serverSrc, loginSrc, serverDst, loginDst, filename, nbBlockMax, blockSize, internalBuffer.array());
        }
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        this.state = State.WAIT_SERVER_SRC;
        stringReader.reset();
        intReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_SERVER_SRC, WAIT_SERVER_DST, WAIT_LOGIN_SRC, WAIT_LOGIN_DST, WAIT_FILENAME, WAIT_NB_BLOCK_MAX, WAIT_BLOCK_SIZE, WAIT_BLOCK, ERROR
    }
}
