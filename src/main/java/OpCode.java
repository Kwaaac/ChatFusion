package main.java;

import main.java.reader.Reader;
import main.java.reader.fusion.*;
import main.java.reader.login.RequestLoginAcceptedReader;
import main.java.reader.login.RequestLoginAnonymousReader;
import main.java.reader.login.RequestLoginPasswordReader;
import main.java.reader.login.RequestLoginRefusedReader;
import main.java.reader.message.RequestMessagePrivateReader;
import main.java.reader.message.RequestMessagePublicReader;
import main.java.request.Request;

import java.util.HashMap;
import java.util.Optional;

public enum OpCode {
    LOGIN_ANONYMOUS(0, new RequestLoginAnonymousReader()), LOGIN_PASSWORD(1, new RequestLoginPasswordReader()), LOGIN_ACCEPTED(2, new RequestLoginAcceptedReader()), LOGIN_REFUSED(3, new RequestLoginRefusedReader()),

    MESSAGE(4, new RequestMessagePublicReader()), PRIVATE_MESSAGE(5, new RequestMessagePrivateReader()), FILE_PRIVATE(6, null),

    FUSION_INIT(8, new RequestFusionInitReader()), FUSION_INIT_OK(9, new RequestFusionInitOKReader()), FUSION_INIT_KO(10, new RequestFusionInitKOReader()), FUSION_INIT_FWD(11, new RequestFusionFWDReader()), FUSION_REQUEST(12, new RequestFusionRequestReader()), FUSION_REQUEST_RESPONSE(13, null), FUSION_CHANGE_LEADER(14, new RequestFusionChangeLeaderReader()), FUSION_MERGE(15, null),

    // Idle is used as a placeholder waiting for a new OpCode for clients and server
    IDLE(-1, null);


    private static final HashMap<Integer, OpCode> codeMap = new HashMap<>();

    static {
        for (var conn : OpCode.values()) {
            codeMap.put(conn.opCode, conn);
        }
    }

    private final int opCode;
    private final Reader<Request> requestReader;

    OpCode(int opCode, Reader<Request> requestReader) {
        this.opCode = opCode;
        this.requestReader = requestReader;
    }

    public static Optional<OpCode> getOpCodeFromByte(byte opcode) {
        var conn = codeMap.get((int) opcode);
        return conn == null ? Optional.empty() : Optional.of(conn);
    }

    public Reader<Request> getRequestReader() {
        requestReader.reset();
        return requestReader;
    }

    public byte getOpCode() {
        return (byte) opCode;
    }
}