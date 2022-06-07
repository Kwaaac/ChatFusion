package main.java.reader.fusion;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestFusionRequestResponseReader implements Reader<Request> {
    private Byte status;
    private State state = State.WAIT_STATUS;

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

    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        if(status == 0)
            return RequestFactory.fusionRequestRefused();
        return RequestFactory.fusionRequestAccepted();
    }

    @Override
    public void reset() {
        state = State.WAIT_STATUS;
    }

    private enum State {
        DONE, WAIT_STATUS, ERROR
    }
}
