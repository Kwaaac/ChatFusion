package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginAcceptedReader implements Reader<Request> {
    String serverName;
    Reader<String> stringReader = new StringReader();
    private State state = State.WAIT_SERVER_NAME;

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
        if (state != State.DONE || serverName == null) {
            throw new IllegalStateException();
        }

        reset();
        return RequestFactory.loginAccepted(serverName);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAIT_SERVER_NAME;
    }

    private enum State {
        DONE, WAIT_SERVER_NAME, ERROR
    }
}
