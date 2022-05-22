package main.java.Utils;

import main.java.OpCode;
import main.java.reader.Message;
import main.java.reader.Request;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    /**
     * TODO
     *
     * @param serverName Name of the leaderServer
     * @param address    Address of the leaderServer
     * @param nbMembers  Number of server in the Mega-Server
     * @param names      Names of each server in the Mega-Server
     * @return A {@link OpCode#FUSION_INIT} request
     */
    public static Request fusionInit(String serverName, InetSocketAddress address, int nbMembers, String... names) {
        var strServer = new StringChatFusion(serverName);
        var bufferAddress = InetSocketAddressConverter.encodeInetSocketAddress(address);
        var listServerFusion = Arrays.stream(names).map(StringChatFusion::new).toList();
        var strLength = strServer.bufferLength();
        var addressLength = bufferAddress.remaining();
        var namesLength = listServerFusion.stream().mapToInt(StringChatFusion::bufferLength).sum();

        var buffer = ByteBuffer.allocate(strLength + addressLength + Integer.BYTES + namesLength);

        buffer.put(strServer.encode()).put(bufferAddress);
        buffer.putInt(nbMembers);
        listServerFusion.stream().map(StringChatFusion::encode).forEach(buffer::put);

        return new Request(OpCode.FUSION_INIT, buffer.flip());
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request fusionInitKO() {
        return new Request(OpCode.FUSION_INIT_KO, ByteBuffer.allocate(0));
    }

    public static Request fusionInitOK(String serverName, InetSocketAddress address, int nbMember, String... names) {
        var requestInit = fusionInit(serverName, address, nbMember, names);
        return new Request(OpCode.FUSION_INIT_OK, requestInit.buffer());
    }

    /**
     * TODO
     *
     * @param address
     * @return
     */
    public static Request fusionInitForward(InetSocketAddress address) {
        return new Request(OpCode.FUSION_INIT_FWD, InetSocketAddressConverter.encodeInetSocketAddress(address));
    }

    public static Request fusionMerge(String serverName) {
        return new Request(OpCode.FUSION_MERGE, new StringChatFusion(serverName).encode());
    }

    public static Request fusionChangeLeader(InetSocketAddress addressLeader) {
        return new Request(OpCode.FUSION_CHANGE_LEADER, InetSocketAddressConverter.encodeInetSocketAddress(addressLeader));
    }

    public static Request fusionRequest(InetSocketAddress addressServer) {
        return new Request(OpCode.FUSION_REQUEST, InetSocketAddressConverter.encodeInetSocketAddress(addressServer));
    }

    public static Request fusionRequestAccepted() {
        return new Request(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 1));
    }

    public static Request fusionRequestRefused() {
        return new Request(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 0));
    }
}
