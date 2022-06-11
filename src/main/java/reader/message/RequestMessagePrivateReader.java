package main.java.reader.message;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestMessagePrivateReader implements Reader<Request> {
    private final Reader<String> stringReader = new StringReader();
    private String serverSrc;
    private String serverDst;
    private String loginSrc;
    private String loginDst;
    private String message;
    private State state = State.WAIT_SERVER_SRC;

    /**
     * Retrieves datas from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing datas
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (this.state == State.DONE || this.state == State.ERROR) {
            throw new IllegalStateException();
        }

        for (; ; ) {
            var status = stringReader.process(bb);
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
                            state = State.WAIT_MSG;
                        }
                        case WAIT_MSG -> {
                            message = stringReader.get();
                            this.state = State.DONE;
                            return ProcessStatus.DONE;
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
            return RequestFactory.privateMessage(serverSrc, loginSrc, serverDst, loginDst, message);
        }
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        this.state = State.WAIT_SERVER_SRC;
        stringReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_SERVER_SRC, WAIT_SERVER_DST, WAIT_LOGIN_SRC, WAIT_LOGIN_DST, WAIT_MSG, ERROR
    }
}
