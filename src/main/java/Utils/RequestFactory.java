package main.java.Utils;

import main.java.OpCode;
import main.java.reader.Message;
import main.java.reader.RecordRequest;
import main.java.request.Request;
import main.java.request.RequestLoginAccepted;
import main.java.request.RequestLoginAnonymous;

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
        return new RequestLoginAnonymous(new StringChatFusion(login));
    }

    /**
     * TODO
     *
     * @return
     */
    public static RecordRequest loginPassword(String login, String password) {
        var strLogin = new StringChatFusion(login);
        var strPassword = new StringChatFusion(password);

        var buffer = ByteBuffer.allocate(strLogin.bufferLength() + strPassword.bufferLength());
        buffer.put(strLogin.encode()).put(strPassword.encode());

        return new RecordRequest(OpCode.LOGIN_PASSWORD, buffer.flip());
    }

    /**
     * TODO
     *
     * @return
     */
    public static RecordRequest loginRefused() {
        return new RecordRequest(OpCode.LOGIN_REFUSED, ByteBuffer.allocate(0));
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request loginAccepted(String serverName) {
        return new RequestLoginAccepted(new StringChatFusion(serverName));
    }

    /**
     * TODO
     *
     * @param serverName
     * @param message
     * @return
     */
    public static RecordRequest publicMessage(String serverName, Message message) {
        var strServerName = new StringChatFusion(serverName);

        var buffer = ByteBuffer.allocate(strServerName.bufferLength() + message.bufferLength());

        buffer.put(strServerName.encode()).put(message.encode());

        return new RecordRequest(OpCode.MESSAGE, buffer.flip());
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
    public static RecordRequest fusionInit(String serverName, InetSocketAddress address, int nbMembers, String... names) {
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

        return new RecordRequest(OpCode.FUSION_INIT, buffer.flip());
    }

    /**
     * TODO
     *
     * @return
     */
    public static RecordRequest fusionInitKO() {
        return new RecordRequest(OpCode.FUSION_INIT_KO, ByteBuffer.allocate(0));
    }

    public static RecordRequest fusionInitOK(String serverName, InetSocketAddress address, int nbMember, String... names) {
        var requestInit = fusionInit(serverName, address, nbMember, names);
        return new RecordRequest(OpCode.FUSION_INIT_OK, requestInit.buffer());
    }

    /**
     * TODO
     *
     * @param address
     * @return
     */
    public static RecordRequest fusionInitForward(InetSocketAddress address) {
        return new RecordRequest(OpCode.FUSION_INIT_FWD, InetSocketAddressConverter.encodeInetSocketAddress(address));
    }

    public static RecordRequest fusionMerge(String serverName) {
        return new RecordRequest(OpCode.FUSION_MERGE, new StringChatFusion(serverName).encode());
    }

    public static RecordRequest fusionChangeLeader(InetSocketAddress addressLeader) {
        return new RecordRequest(OpCode.FUSION_CHANGE_LEADER, InetSocketAddressConverter.encodeInetSocketAddress(addressLeader));
    }

    public static RecordRequest fusionRequest(InetSocketAddress addressServer) {
        return new RecordRequest(OpCode.FUSION_REQUEST, InetSocketAddressConverter.encodeInetSocketAddress(addressServer));
    }

    public static RecordRequest fusionRequestAccepted() {
        return new RecordRequest(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 1));
    }

    public static RecordRequest fusionRequestRefused() {
        return new RecordRequest(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 0));
    }
}
