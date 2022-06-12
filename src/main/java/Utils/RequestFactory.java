package main.java.Utils;

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
     * Creates a {@link RequestLoginAnonymous} from the given login
     * @param login the login of the {@link main.java.client.ClientChatFusion} asking the connection
     * @return the {@link RequestLoginAnonymous} created
     */
    public static Request loginAnonymous(String login) {
        return new RequestLoginAnonymous(new StringChatFusion(login));
    }

    /**
     * Creates a {@link RequestLoginPassword} from the given arguments
     * @param login the login of the {@link main.java.client.ClientChatFusion} asking the connection
     * @param password the password of the {@link main.java.client.ClientChatFusion} asking the connection
     * @return the {@link RequestLoginPassword} created
     */
    public static Request loginPassword(String login, String password) {
        var strLogin = new StringChatFusion(login);
        var strPassword = new StringChatFusion(password);

        return new RequestLoginPassword(strLogin, strPassword);
    }

    /**
     * Creates a {@link RequestLoginRefused}
     * @return the {@link RequestLoginRefused} created
     */
    public static Request loginRefused() {
        return new RequestLoginRefused();
    }

    /**
     * Creates a {@link RequestLoginAccepted} from the given server name
     * @param serverName the name of the {@link main.java.server.ServerChatFusion} accepting the connection
     * @return the {@link RequestLoginAccepted} created
     */
    public static Request loginAccepted(String serverName) {
        return new RequestLoginAccepted(new StringChatFusion(serverName));
    }

    /**
     * Creates a {@link RequestMessagePublic} from the given arguments
     * @param serverName the name of the {@link main.java.server.ServerChatFusion} source of the message
     * @param login the name of the sender of the message
     * @param message the message sent by the sender
     * @return the {@link RequestMessagePublic} created
     */
    public static Request publicMessage(String serverName, String login, String message) {
        return new RequestMessagePublic(new StringChatFusion(serverName), new StringChatFusion(login), new StringChatFusion(message));
    }

    /**
     * Creates a {@link RequestMessagePrivate} from the given arguments
     * @param serverSrc the name of the {@link main.java.server.ServerChatFusion} source
     * @param serverDst the name of the {@link main.java.server.ServerChatFusion} destination
     * @param loginSrc the name of the {@link main.java.client.ClientChatFusion} source
     * @param loginDst the name of the {@link main.java.client.ClientChatFusion} destination
     * @param message the message sent by the {@link main.java.client.ClientChatFusion} source
     * @return the {@link RequestMessagePrivate} created
     */
    public static Request privateMessage(String serverSrc, String loginSrc, String serverDst, String loginDst, String message) {
        return new RequestMessagePrivate(new StringChatFusion(serverSrc), new StringChatFusion(loginSrc), new StringChatFusion(serverDst), new StringChatFusion(loginDst), new StringChatFusion(message));
    }

    /**
     * Creates a {@link RequestMessageFilePrivate} from the given arguments
     * @param strServerDst the name of the {@link main.java.server.ServerChatFusion} source
     * @param strLoginSrc the name of the {@link main.java.server.ServerChatFusion} destination
     * @param strLoginDst the name of the {@link main.java.client.ClientChatFusion} source
     * @param strFilename the name of the {@link main.java.client.ClientChatFusion} destination
     * @param nbBlocksMax number of blocks in the split file
     * @param blockSize the size of the blocks
     * @param block a block of the split file
     * @return the {@link RequestMessageFilePrivate} created
     */
    public static Request privateFile(String strServerSrc, String strServerDst, String strLoginSrc, String strLoginDst, String strFilename, int nbBlocksMax, int blockSize, byte[] block) {
        var serverSrc = new StringChatFusion(strServerSrc);
        var serverDst = new StringChatFusion(strServerDst);
        var loginSrc = new StringChatFusion(strLoginSrc);
        var loginDst = new StringChatFusion(strLoginDst);
        var filename = new StringChatFusion(strFilename);

        return new RequestMessageFilePrivate(serverSrc, serverDst, loginSrc, loginDst, filename, nbBlocksMax, blockSize, block);
    }

    /**
     * Creates a {@link RequestFusionInit} from the given arguments
     * @param serverName Name of the leaderServer
     * @param address    Address of the leaderServer
     * @param nbMembers  Number of server in the Mega-Server
     * @param names      Names of each server in the Mega-Server
     * @return the {@link RequestFusionInit} created
     */
    public static Request fusionInit(String serverName, InetSocketAddress address, int nbMembers, String... names) {
        var scfServerName = new StringChatFusion(serverName);
        var acfAddress = new InetIpv4ChatFusion(address);
        var lstNames = Arrays.stream(names).map(StringChatFusion::new).toList();

        return new RequestFusionInit(scfServerName, acfAddress, nbMembers, lstNames);
    }

    /**
     * Creates a {@link RequestFusionInitKO}
     * @return the {@link RequestFusionInitKO} created
     */
    public static Request fusionInitKO() {
        return new RequestFusionInitKO();
    }

    /**
     * Creates a {@link RequestFusionInitOK} from the given arguments
     * @param serverName the name of the {@link main.java.server.ServerChatFusion} receiving the merge request
     * @param address the address of the {@link main.java.server.ServerChatFusion} receiving the merge request
     * @param nbMember the number of clients connected to the {@link main.java.server.ServerChatFusion} receiving the merge request
     * @param names the names of the clients connected to the {@link main.java.server.ServerChatFusion} receiving the merge request
     * @return the  {@link RequestFusionInitOK} created
     */
    public static Request fusionInitOK(String serverName, InetSocketAddress address, int nbMember, String... names) {
        return new RequestFusionInitOK(new StringChatFusion(serverName), new InetIpv4ChatFusion(address), nbMember, Arrays.stream(names).map(StringChatFusion::new).toList());
    }

    /**
     * Creates a {@link RequestFusionInitFWD} from the given address
     * @param address the address of the leader of the Mega-Server
     * @return the {@link RequestFusionInitFWD} created
     */
    public static Request fusionInitForward(InetSocketAddress address) {
        return new RequestFusionInitFWD(new InetIpv4ChatFusion(address));
    }

    /**
     * Creates a {@link RequestFusionMerge} from the given name
     * @param serverName the name of the {@link main.java.server.ServerChatFusion} who's connecting to the leader
     * @return the {@link RequestFusionMerge} created
     */
    public static Request fusionMerge(String serverName) {
        return new RequestFusionMerge(new StringChatFusion(serverName));
    }

    /**
     * Creates a {@link RequestFusionChangeLeader} from the given address
     * @param addressLeader the {@link InetSocketAddress} of the new leader
     * @return the {@link RequestFusionChangeLeader} created
     */
    public static Request fusionChangeLeader(InetSocketAddress addressLeader) {
        return new RequestFusionChangeLeader(new InetIpv4ChatFusion(addressLeader));
    }

    /**
     * Creates a {@link RequestFusionRequest} from the given address
     * @param addressServer the address of the {@link main.java.server.ServerChatFusion} asking the merge
     * @return the {@link RequestFusionRequest} created
     */
    public static Request fusionRequest(InetSocketAddress addressServer) {
        return new RequestFusionRequest(new InetIpv4ChatFusion(addressServer));
    }

    /**
     * Creates a {@link RequestFusionRequestResponse} from the given address
     * @return the {@link RequestFusionRequestResponse} created
     */
    public static Request fusionRequestAccepted() {
        return new RequestFusionRequestResponse((byte) 1);
    }

    /**
     * Creates a {@link RequestFusionRequestResponse} from the given address
     * @return the {@link RequestFusionRequestResponse} created
     */
    public static Request fusionRequestRefused() {
        return new RequestFusionRequestResponse((byte) 0);
    }
}
