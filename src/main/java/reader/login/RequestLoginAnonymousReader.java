package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginAnonymousReader implements Reader<Request> {
    String login;
    Reader<String> stringReader = new StringReader();
    private State state = State.WAIT_LOGIN;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var status = stringReader.process(bb);
        return switch (status) {
            case DONE -> {
                login = stringReader.get();
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
        if (state != State.DONE || login == null) {
            throw new IllegalStateException();
        }
        reset();
        return RequestFactory.loginAnonymous(login);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAIT_LOGIN;
    }

    private enum State {
        DONE, WAIT_LOGIN, ERROR
    }
}
