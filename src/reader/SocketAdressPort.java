package reader;

import java.net.SocketAddress;

public record SocketAdressPort(SocketAddress address, int port) {
}
