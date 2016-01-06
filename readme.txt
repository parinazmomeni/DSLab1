Reflect about your solution!

Summary:

Code is stable and the specified points in the assignment are completed. I've tested my program with ANT manually, and all tests were successful.

LAB 2:

Stage 1 - Naming service and RMI

Stage 2 - Secure channel

Stage 3 - Message Integrity
In Parinaz implementation communication between clients is done in the TcpWorker class. Therfore this class had to be changed for sending and receiving well-formed messages and for checking the hash validity. Additionally a class called HashMAC has been created that handles hashing of messages and base64 encoding of the hash string. The user is alerted via logger output if a message has been tampered with. Valid messages are outputted to the shell. 