# User Manual

## Client Side
As a client connected to a server, you can use several commands to communicate with other clients :

- /help



- /w [ServerDestination] [ClientDestination] [Message]
- /wf

## Server Side
As a server, you can also use several commands to manage connections with clients but other servers too :

- FUSION [IP_Address] [Port]

Start a server merge with the server using IP address and port informed in the command.
A merge can be accepted or refused depending on the clients connected to each.

- INFO

Displays the number of clients and server connected.

- SHUTDOWN

Prevent new connections to the server but doesn't impact connected clients.

- SHUTDOWNNOW

Stop the server disconnecting connected clients.