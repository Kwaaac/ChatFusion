# User Manual

## Client Side
As a client connected to a server, you can use several commands to communicate with other clients :

- /help

Display all commands the client can use.

- /w [server_destination_name] [login_client] [message]

Send a private message "message" to the client "login_client" on the server "server_destination_name"

- /wf [server_destination_name] [login_client] [filename_in_the_transfert_directory]

Send a file "filename_in_the_transfert_directory" to the client "login_client" on the server "server_destination_name"

Finally, you can send a message without using any command, just writing it. In this case, your message will be sent to every client connected to you mega-server.

## Server Side
As a server, you can also use several commands to manage connections with clients but other servers too :

- FUSION [IP_Address] [Port]

Start a server merge with the server using IP address and port informed in the command.
A merge can be accepted or refused depending on the clients connected to each.
If the merge is accepted, the two servers become a mega-server.

- INFO

Displays the number of clients and server connected.

- SHUTDOWN

Prevent new connections to the server but doesn't impact connected clients.

- SHUTDOWNNOW

Stop the server disconnecting connected clients.