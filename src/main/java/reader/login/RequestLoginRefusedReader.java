package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginRefusedReader implements Reader<Request> {
    private State state = State.WAIT;

    /**
     * Retrieves datas from the bytebuffer and stores them
     * @param bb the bytebuffer containing datas
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE) {
            throw new IllegalStateException();
        }

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    /**
     * Get the request associated with the reader
     * @return the request associated with the reader
     * @throws IllegalStateException if the state of the recovery isn't DONE or the serverName is null
     */
    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.loginRefused();
    }

    /**
     * Reset the reader to make it reusable
     */
    @Override
    public void reset() {
        state = State.WAIT;
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT
    }
}
