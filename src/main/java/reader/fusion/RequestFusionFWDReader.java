package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.InetSocketAddressReader;
import main.java.reader.Reader;
import main.java.request.Request;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RequestFusionFWDReader implements Reader<Request>{
    Reader<InetSocketAddress> addressReader = new InetSocketAddressReader();
    InetSocketAddress address;
    private State state = State.WAIT_ADDRESS;

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

    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.fusionInitForward(address);
    }

    @Override
    public void reset() {
        addressReader.reset();
        state = State.WAIT_ADDRESS;

    }

    private enum State {
        DONE, WAIT_ADDRESS, ERROR
    }
}
