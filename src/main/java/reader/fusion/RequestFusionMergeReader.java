package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestFusionMergeReader implements Reader<Request> {
    private State state = State.WAIT_SERVER;
    private String serverName;
    private StringReader stringReader = new StringReader();

    /**
     * Retrieves datas from the {@link ByteBuffer} and stores them
     * @param bb the {@link ByteBuffer} containing datas
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
                serverName = stringReader.get();
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
     * Gets the {@link Request} retrieved by the process method
     * @return the {@link Request} associated with the {@link Reader}
     * @throws IllegalStateException If the process method is not DONE
     */
    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return RequestFactory.fusionMerge(serverName);
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        state = State.WAIT_SERVER;
        stringReader.reset();
    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_SERVER, ERROR
    }
}
