package main.java;

import main.java.reader.Reader;
import main.java.reader.fusion.RequestFusionInitReader;
import main.java.reader.login.RequestLoginAcceptedReader;
import main.java.reader.login.RequestLoginAnonymousReader;
import main.java.reader.login.RequestLoginPasswordReader;
import main.java.reader.message.RequestMessagePublicReader;
import main.java.request.Request;

import java.util.HashMap;
import java.util.Optional;

public enum OpCode {
    LOGIN_ANONYMOUS(0, new RequestLoginAnonymousReader()), LOGIN_PASSWORD(1, new RequestLoginPasswordReader()), LOGIN_ACCEPTED(2, new RequestLoginAcceptedReader()), LOGIN_REFUSED(3, null),

    MESSAGE(4, new RequestMessagePublicReader()), PRIVATE_MESSAGE(5, null), FILE_PRIVATE(6, null),

    FUSION_INIT(8, new RequestFusionInitReader()), FUSION_INIT_OK(9, null), FUSION_INIT_KO(10, null), FUSION_INIT_FWD(11, null), FUSION_REQUEST(12, null), FUSION_REQUEST_RESPONSE(13, null), FUSION_CHANGE_LEADER(14, null), FUSION_MERGE(15, null),

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