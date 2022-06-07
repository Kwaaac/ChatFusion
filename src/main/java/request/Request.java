package main.java.request;

import main.java.BufferSerializable;
import main.java.OpCode;

/**
 * Record representing a request, a request has an opcode defining the kind of request and a buffer containing the request information
 * <p>
 * The buffer is in read-mode when get
 */
public sealed interface Request extends BufferSerializable permits RequestFusionChangeLeader, RequestFusionInit, RequestFusionInitFWD, RequestFusionInitKO, RequestFusionInitOK, RequestFusionMerge, RequestFusionRequest, RequestFusionRequestResponse, RequestLoginAccepted, RequestLoginAnonymous, RequestLoginPassword, RequestLoginRefused, RequestMessageFilePrivate, RequestMessagePrivate, RequestMessagePublic {
    OpCode getOpCode();

    enum ReadingState {
        READING_REQUEST, WAITING_FOR_REQUEST
    }
}
