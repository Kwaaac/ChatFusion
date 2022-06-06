package main.java.request;

import main.java.Utils.StringChatFusion;

public sealed interface RequestLogin extends Request permits RequestLoginAnonymous {
    StringChatFusion getLogin();
}
