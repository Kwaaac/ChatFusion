package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginPasswordReader implements Reader<Request> {
    private final StringReader stringReader = new StringReader();
    private String login;
    private String password;
    private State state = State.WAIT_LOGIN;

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
        for( ; ; ) {
            var status = stringReader.process(bb);
            switch (status) {
                case DONE -> {
                    if(state == State.WAIT_LOGIN) {
                        login = stringReader.get();
                        stringReader.reset();
                        state = State.WAIT_PSWD;
                        break;
                    }
                    password = stringReader.get();
                    state = State.DONE;
                    return ProcessStatus.DONE;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case REFILL -> {
                    return ProcessStatus.REFILL;
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
        if(state != State.DONE) {
            throw new IllegalStateException();
        }
        return RequestFactory.loginPassword(login, password);
    }

    /**
     * Reset the reader to make it reusable
     */
    @Override
    public void reset() {
        state = State.WAIT_LOGIN;
        stringReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_LOGIN, WAIT_PSWD, ERROR
    }
}
