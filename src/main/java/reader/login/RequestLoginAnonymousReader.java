package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginAnonymousReader implements Reader<Request> {
    String login;
    Reader<String> stringReader = new StringReader();
    private State state = State.WAIT_LOGIN;

    /**
     * Retrieves datas from the bytebuffer and stores them
     * @param bb the bytebuffer containing datas
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var status = stringReader.process(bb);
        return switch (status) {
            case DONE -> {
                login = stringReader.get();
                state = State.DONE;
                yield ProcessStatus.DONE;
            }
            case ERROR -> {
                state = State.ERROR;
                yield ProcessStatus.ERROR;
            }
            case REFILL -> ProcessStatus.REFILL;
        };
    }

    /**
     * Get the request associated with the reader
     * @return the request associated with the reader
     * @throws IllegalStateException if the state of the recovery isn't DONE or the serverName is null
     */
    @Override
    public Request get() {
        if (state != State.DONE || login == null) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.loginAnonymous(login);
    }

    /**
     * Reset the reader to make it reusable
     */
    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAIT_LOGIN;
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_LOGIN, ERROR
    }
}
