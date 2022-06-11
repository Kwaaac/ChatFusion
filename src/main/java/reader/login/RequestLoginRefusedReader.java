package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginRefusedReader implements Reader<Request> {
    private State state = State.WAIT;

    /**
     * Retrieves data from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing data
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
     * Gets the {@link Request} retrieved by the process method
     * @return the {@link Request} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
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
     * Resets the {@link Reader} to make it reusable
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
