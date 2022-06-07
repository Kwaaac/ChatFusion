package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.InetSocketAddressReader;
import main.java.reader.IntReader;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RequestFusionInitKOReader implements Reader<Request> {
    private State state = State.WAIT;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE) {
            throw new IllegalStateException();
        }

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.fusionInitKO();
    }

    @Override
    public void reset() {
        state = State.WAIT;
    }

    private enum State {
        DONE, WAIT
    }
}
