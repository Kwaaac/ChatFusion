package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.InetSocketAddressReader;
import main.java.reader.Reader;
import main.java.request.Request;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RequestFusionRequestReader implements Reader<Request> {
    Reader<InetSocketAddress> addressReader = new InetSocketAddressReader();
    InetSocketAddress address;
    private State state = State.WAIT_ADDRESS;

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

        var addressStatus = addressReader.process(bb);
        if (addressStatus == ProcessStatus.DONE) {
            address = addressReader.get();
            addressReader.reset();
            state = State.DONE;
            return ProcessStatus.DONE;
        } else {
            return addressStatus;
        }
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
        return RequestFactory.fusionRequest(address);
    }

    /**
     * Resets the {@link Reader} to make it reusable
     */
    @Override
    public void reset() {
        addressReader.reset();
        state = State.WAIT_ADDRESS;

    }

    /**
     * The different possible states for the buffer data recovery
     */
    private enum State {
        DONE, WAIT_ADDRESS, ERROR
    }
}



