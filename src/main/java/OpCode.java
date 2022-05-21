package main.java;

import java.util.HashMap;
import java.util.Optional;

public enum OpCode {
    LOGIN_ANONYMOUS(0),
    LOGIN_PASSWORD(1),
    LOGIN_ACCEPTED(2),
    LOGIN_REFUSED(3),

    MESSAGE(4),
    PRIVATE_MESSAGE(5),
    FILE_PRIVATE(6),

    FUSION_INIT(8),
    FUSION_INIT_OK(9),
    FUSION_INIT_KO(10),
    FUSION_INIT_FWD(11),
    FUSION_REQUEST(12),
    FUSION_REQUEST_RESP(13),
    FUSION_CHANGE_LEADER(14),
    FUSION_MERGE(15);


    private static final HashMap<Integer, OpCode> codeMap = new HashMap<>();

    static {
        for (var conn : OpCode.values()) {
            codeMap.put(conn.opCode, conn);
        }
    }

    private final int opCode;


    OpCode(int opCode) {
        this.opCode = opCode;
    }

    public static Optional<OpCode> getOpCodeFromInt(int opcode) {
        var conn = codeMap.get(opcode);
        return conn == null ? Optional.empty() : Optional.of(conn);
    }

}