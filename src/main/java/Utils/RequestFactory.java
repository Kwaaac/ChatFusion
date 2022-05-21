package main.java.Utils;

import main.java.OpCode;
import main.java.reader.Request;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RequestFactory {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    /**
     * //TODO
     *
     * @return
     */
    public static Request loginAnonymous(String login) {
        var loginLength = login.getBytes(UTF8).length;
        var buffer = ByteBuffer.allocate(loginLength + Integer.BYTES);
        buffer.putInt(loginLength).put(UTF8.encode(login));

        return new Request(OpCode.LOGIN_ANONYMOUS, buffer.flip());
    }

    /**
     * //TODO
     *
     * @return
     */
    public static Request loginPassword(String login, String password) {
        var loginLength = login.getBytes(UTF8).length;
        var pwdLength = password.getBytes(UTF8).length;

        var buffer = ByteBuffer.allocate(Integer.BYTES * 2 + loginLength + pwdLength);
        buffer.putInt(loginLength).put(UTF8.encode(login)).putInt(pwdLength).put(UTF8.encode(password));

        return new Request(OpCode.LOGIN_PASSWORD, buffer.flip());
    }

    /**
     * //TODO
     *
     * @return
     */
    public static Request loginRefused() {
        return new Request(OpCode.LOGIN_REFUSED, ByteBuffer.allocate(0));
    }

    /**
     * //TODO
     *
     * @return
     */
    public static Request loginAccepted(String serverName) {
        var serverNameLength = serverName.getBytes(UTF8).length;
        var buffer = ByteBuffer.allocate(serverNameLength + Integer.BYTES);
        buffer.putInt(serverNameLength).put(UTF8.encode(serverName));

        return new Request(OpCode.LOGIN_ACCEPTED, buffer.flip());
    }

}
