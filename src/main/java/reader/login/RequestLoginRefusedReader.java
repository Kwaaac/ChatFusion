package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginRefusedReader implements Reader<Request> {
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
        return RequestFactory.loginRefused();
    }

    @Override
    public void reset() {
        state = State.WAIT;
    }

    private enum State {
        DONE, WAIT
    }
}
