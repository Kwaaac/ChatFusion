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

    @Override
    public Request get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return RequestFactory.fusionMerge(serverName);
    }

    @Override
    public void reset() {
        state = State.WAIT_SERVER;
        stringReader.reset();
    }

    private enum State {
        DONE, WAIT_SERVER, ERROR
    }
}
