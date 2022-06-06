package main.java.request;

public sealed interface RequestMessage extends Request permits RequestMessagePublic, RequestMessagePrivate, RequestMessageFilePrivate {
    String getServer();
    String getLogin();
}
