package main.java.reader.login;

import main.java.Utils.RequestFactory;
import main.java.reader.Reader;
import main.java.reader.StringReader;
import main.java.request.Request;

import java.nio.ByteBuffer;

public class RequestLoginPasswordReader implements Reader<Request> {
    private final StringReader stringReader = new StringReader();
    private String login;
    private String password;
    private State state = State.WAIT_LOGIN;
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (this.state == State.DONE || this.state == State.ERROR) {
            throw new IllegalStateException();
        }
        for( ; ; ) {
            var status = stringReader.process(bb);
            switch (status) {
                case DONE -> {
                    if(state == State.WAIT_LOGIN) {
                        login = stringReader.get();
                        stringReader.reset();
                        state = State.WAIT_PSWD;
                        break;
                    }
                    password = stringReader.get();
                    state = State.DONE;
                    return ProcessStatus.DONE;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
            }
        }
    }

    @Override
    public Request get() {
        if(state != State.DONE) {
            throw new IllegalStateException();
        }
        return RequestFactory.loginPassword(login, password);
    }

    @Override
    public void reset() {
        state = State.WAIT_LOGIN;
        stringReader.reset();
    }

    private enum State {
        DONE, WAIT_LOGIN, WAIT_PSWD, ERROR
    }
}
