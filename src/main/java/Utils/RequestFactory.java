package main.java.Utils;

import main.java.OpCode;
import main.java.request.*;
import main.java.wrapper.InetIpv4ChatFusion;
import main.java.wrapper.StringChatFusion;

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
    public static Request loginPassword(String login, String password) {
        var strLogin = new StringChatFusion(login);
        var strPassword = new StringChatFusion(password);

        return new RequestLoginPassword(strLogin, strPassword);
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request loginRefused() {
        // FIXME
        return null;
        //return new RecordRequest(OpCode.LOGIN_REFUSED, ByteBuffer.allocate(0));
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
    public static Request publicMessage(String serverName, String login, String message) {
        return new RequestMessagePublic(new StringChatFusion(serverName), new StringChatFusion(login), new StringChatFusion(message));
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

        //return new RecordRequest(OpCode.FUSION_INIT, buffer.flip());
        return null;
    }

    /**
     * TODO
     *
     * @return
     */
    public static Request fusionInitKO() {
        return new RequestFusionInitKO();
    }

    public static Request fusionInitOK(String serverName, InetSocketAddress address, int nbMember, String... names) {
        // FIXME
        // var requestInit = fusionInit(serverName, address, nbMember, names);
        // return new RecordRequest(OpCode.FUSION_INIT_OK, requestInit.buffer());
        return null;
    }

    /**
     * TODO
     *
     * @param address
     * @return
     */
    public static Request fusionInitForward(InetSocketAddress address) {
        // FIXME
        return null;
        //return new RecordRequest(OpCode.FUSION_INIT_FWD, InetSocketAddressConverter.encodeInetSocketAddress(address));
    }

    public static Request fusionMerge(String serverName) {
        // FIXME
        return null;
        //return new RecordRequest(OpCode.FUSION_MERGE, new StringChatFusion(serverName).encode());
    }

    public static Request fusionChangeLeader(InetSocketAddress addressLeader) {
        return new RequestFusionChangeLeader(new InetIpv4ChatFusion(addressLeader));
    }

    public static Request fusionRequest(InetSocketAddress addressServer) {
        return new RequestFusionRequest(new InetIpv4ChatFusion(addressServer));
    }

    public static Request fusionRequestAccepted() {
        // FIXME
        return null;
        //return new RecordRequest(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 1));
    }

    public static Request fusionRequestRefused() {
        // FIXME
        return null;
        // return new RecordRequest(OpCode.FUSION_REQUEST_RESPONSE, ByteBuffer.allocate(1).put((byte) 0));
    }
}
