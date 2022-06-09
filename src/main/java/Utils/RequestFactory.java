package main.java.Utils;

import main.java.OpCode;
import main.java.request.*;
import main.java.wrapper.InetIpv4ChatFusion;
import main.java.wrapper.StringChatFusion;

import java.net.InetSocketAddress;
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
        return new RequestLoginRefused();
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
     * @param serverSrc
     * @param serverDst
     * @param loginSrc
     * @param loginDst
     * @param message
     * @return
     */
    public static Request privateMessage(String serverSrc, String serverDst, String loginSrc, String loginDst, String message) {
        return new RequestMessagePrivate(new StringChatFusion(serverSrc), new StringChatFusion(serverSrc), new StringChatFusion(loginSrc), new StringChatFusion(loginDst), new StringChatFusion(message));
    }


    public static Request privateFile(String strServerSrc, String strServerDst, String strLoginSrc, String strLoginDst, String strFilename, int nbBlocksMax, int blockSize, byte[] block) {
        var serverSrc = new StringChatFusion(strServerSrc);
        var serverDst = new StringChatFusion(strServerDst);
        var loginSrc = new StringChatFusion(strLoginSrc);
        var loginDst = new StringChatFusion(strLoginDst);
        var filename = new StringChatFusion(strFilename);

        return new RequestMessageFilePrivate(serverSrc, serverDst, loginSrc, loginDst, filename, nbBlocksMax, blockSize, block);
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
        var scfServerName = new StringChatFusion(serverName);
        var acfAddress = new InetIpv4ChatFusion(address);
        var lstNames = Arrays.stream(names).map(StringChatFusion::new).toList();

        return new RequestFusionInit(scfServerName, acfAddress, nbMembers, lstNames);
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
        return new RequestFusionInitOK(new StringChatFusion(serverName), new InetIpv4ChatFusion(address), nbMember, Arrays.stream(names).map(StringChatFusion::new).toList());
    }

    /**
     * TODO
     *
     * @param address
     * @return
     */
    public static Request fusionInitForward(InetSocketAddress address) {
        return new RequestFusionInitFWD(new InetIpv4ChatFusion(address));
    }

    public static Request fusionMerge(String serverName) {
        return new RequestFusionMerge(new StringChatFusion(serverName));
    }

    public static Request fusionChangeLeader(InetSocketAddress addressLeader) {
        return new RequestFusionChangeLeader(new InetIpv4ChatFusion(addressLeader));
    }

    public static Request fusionRequest(InetSocketAddress addressServer) {
        return new RequestFusionRequest(new InetIpv4ChatFusion(addressServer));
    }

    public static Request fusionRequestAccepted() {
        return new RequestFusionRequestResponse((byte) 1);
    }

    public static Request fusionRequestRefused() {
        return new RequestFusionRequestResponse((byte) 0);
    }
}
