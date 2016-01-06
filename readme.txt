Reflect about your solution!

Summary:

Code is stable and the specified points in the assignment are completed. I've tested my program with ANT manually, and all tests were successful.

LAB 2:

Stage 1 - Naming service and RMI

Stage 2 - Secure channel
For a connection with the server, the user first has to authenticate himself. The TcpWorker of the server checks the message and response accordingly, waiting for the last message. The client checks for a valid response, saves the key-information in his security (SecurityTool.java) and sends the server the server challenge, now encoded via AES. Lastly, the server check for the valid response, saves the given key-information for the shared key on the user (User.java) and his security (SecurityTool.java) and sends the client a success-message. From this time on, all message between client and server are over the established secure channel.
Encoding, decoding and sending through the secure channel is done by the SecurityTool, the needed filepaths and key-informations are stored in KeyPaths (KeyPaths.java) by the Client and the TcpWorker.
Error-Messages during authentication are send unencoded. If such an error occurs, the save connection will not be established and the client has to authenticate once more.
The "!login"-commando is deprecated and no longer used.

Stage 3 - Message Integrity
In Parinaz implementation communication between clients is done in the TcpWorker class. Therfore this class had to be changed for sending and receiving well-formed messages and for checking the hash validity. Additionally a class called HashMAC has been created that handles hashing of messages and base64 encoding of the hash string. The user is alerted via logger output if a message has been tampered with. Valid messages are outputted to the shell. 