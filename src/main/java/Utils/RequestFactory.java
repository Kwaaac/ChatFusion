package main.java.Utils;

import main.java.OpCode;
import main.java.reader.Message;
import main.java.reader.Request;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RequestFactory {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    /**
     * TODO
     *
     * @return
     */
    public static Request loginAnonymous(String login) {
        return new Request(OpCode.LOGIN_ANONYMOUS, new StringChatFusion(login).encode());
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request loginPassword(String login, String password) {
        var strLogin = new StringChatFusion(login);
        var strPassword = new StringChatFusion(password);

        var buffer = ByteBuffer.allocate(strLogin.bufferLength() + strPassword.bufferLength());
        buffer.put(strLogin.encode()).put(strPassword.encode());

        return new Request(OpCode.LOGIN_PASSWORD, buffer.flip());
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request loginRefused() {
        return new Request(OpCode.LOGIN_REFUSED, ByteBuffer.allocate(0));
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request loginAccepted(String serverName) {
        return new Request(OpCode.LOGIN_ACCEPTED, new StringChatFusion(serverName).encode());
    }

    /**
     * TODO
     *
     * @param serverName
     * @param message
     * @return
     */
    public static Request publicMessage(String serverName, Message message) {
        var strServerName = new StringChatFusion(serverName);

        var buffer = ByteBuffer.allocate(strServerName.bufferLength() + message.bufferLength());

        buffer.put(strServerName.encode()).put(message.encode());

        return new Request(OpCode.MESSAGE, buffer.flip());
    }
}