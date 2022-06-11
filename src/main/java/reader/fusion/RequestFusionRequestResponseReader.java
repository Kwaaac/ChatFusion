package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestFusionRequestResponseReader implements Reader<Request> {
    private Byte status;
    private State state = State.WAIT_STATUS;

    /**
     * Retrieves data from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing data
     * @return the status of the buffer data recovery
     * @throws IllegalStateException if the state of the recovery is DONE or ERROR
     */
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if(state == State.WAIT_STATUS) {
            status = bb.get();
            bb.flip();
            state = State.DONE;
        }
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
        if(status == 0)
            return RequestFactory.fusionRequestRefused();
        return RequestFactory.fusionRequestAccepted();
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        state = State.WAIT_STATUS;
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_STATUS, ERROR
    }
}
