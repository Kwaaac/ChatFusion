package main.java.reader.message;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestMessagePublicReader implements Reader<Request> {
    private final Reader<String> stringReader = new StringReader();
    private String serverName;
    private String login;
    private String msg;
    private State state = State.WAIT_SERVER_NAME;

    /**
     * Retrieves datas from the bytebuffer and stores them
     * @param bb the bytebuffer containing datas
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
                        case WAIT_SERVER_NAME -> {
                            serverName = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_LOGIN;
                        }
                        case WAIT_LOGIN -> {
                            login = stringReader.get();
                            stringReader.reset();
                            state = State.WAIT_MSG;
                        }
                        case WAIT_MSG -> {
                            msg = stringReader.get();
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
     * Get the request associated with the reader
     * @return the request associated with the reader
     * @throws IllegalStateException if the state of the recovery isn't DONE or the serverName is null
     */
    @Override
    public Request get() {
        if (this.state != State.DONE) {
            throw new IllegalStateException();
        } else {
            return RequestFactory.publicMessage(serverName, login, msg);
        }
    }

    /**
     * Reset the reader to make it reusable
     */
    @Override
    public void reset() {
        this.state = State.WAIT_SERVER_NAME;
        stringReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_SERVER_NAME, WAIT_LOGIN, WAIT_MSG, ERROR
    }
}