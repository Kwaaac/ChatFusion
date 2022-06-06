package main.java.reader;

import main.java.OpCode;
import main.java.Utils.StringChatFusion;
import main.java.request.Request;
import main.java.request.RequestLoginAnonymous;

import java.nio.ByteBuffer;

public class RequestLoginAnonymousReader implements Reader<Request> {
    @Override
    public ProcessStatus process(ByteBuffer bb, int maxValue) {
        return null;
    }

    @Override
    public Request get() {
        return new RequestLoginAnonymous(OpCode.LOGIN_ANONYMOUS, new StringChatFusion("bob"));
    }

    @Override
    public void reset() {

    }
}
