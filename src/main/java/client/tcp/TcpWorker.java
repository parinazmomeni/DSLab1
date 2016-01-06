package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import client.Client;
import util.Logger;
import util.Streams;

public class TcpWorker implements Runnable {

	private final String regexCheck = "[a-zA-Z0-9/+]{43}= [\\s[^\\s]]+";
	private Logger logger = new Logger();
	private Client client;
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;

	public TcpWorker(Client client, Socket socket) throws IOException {
		this.client = client;
		this.socket = socket;
		this.in = Streams.getBufferedReader(socket);
		this.out = Streams.getPrintWriter(socket);
	}
	
	
	/**
	 * Is used after a client has registered itself and a connection to the client's 
	 * serversocket has been established by another client.
	 */
	@Override
	public void run() {
		String message = receive();
		if (message != null) {
			try {
				client.getShell().writeLine(socket.getInetAddress().getHostName() + " sent: " + message);
			} catch (IOException e) {
				logger.exception(e);
				logger.info(message);
			}
		}
		shutDown();
	}
	
	/**
	 * Listens for a message in the input stream. Returns the message or !ack if a message has been
	 * received and HMAC is correct, otherwise null. If the HMAC is not correct then the user will be
	 * alerted through logging output and a !tampered message will be sent to the other client.
	 * @return the message, !ack or null
	 */
	public String receive() {
		String command;
		
		try {
			command = in.readLine();
		} catch (IOException e) {
			logger.exception(e);
			return null;
		}
		
		logger.debug("Incoming: " + command);
		
		if (!client.isActive() || command == null) {
			return null;
		}
		
		String[] msgParts = command.split(" ");
		if (msgParts.length < 2) {
			logger.error("Recieved strange message: "+command);
			return null;
		}
		
		//get hash, command and message out of the incoming string
		String messageHash = msgParts[0];
		String messageCommand = msgParts[1];
		String messageCommandAndBody = command.substring(messageHash.length()).trim();
		String controlHash = client.getHashMAC().getEncodedHash(messageCommandAndBody);
		String message = messageCommandAndBody.replaceFirst(messageCommand, "").trim();
		
		if (!messageHash.equals(controlHash)) {
			logger.error("Hash mismatch!!! Tampered message: \""+message+"\". Sending tampered warning to remote client.");
			send("!tampered", message);
		
		} else if (messageCommand.equals("!msg")) {
			send("!ack", "");
			return message;
			
		} else if (messageCommand.equals("!tampered")) {
			logger.error("The receiving client reported that our message has been tampered with!");
		
		} else if (messageCommand.equals("!ack")) {
			return messageCommand;
			
		} else {
			logger.error("Recieved strange message: "+command);
		}
			
		return null;
	}
	
	/**
	 * Sends a command and message to another chat client. Also generates the HMAC of the 
	 * command + message and prepends it.
	 * @param command = the command to send. e.g. !msg
	 * @param message = the message
	 */
	public void send(String command, String message) {
		String messageToRemote = (command + " " + message).trim();
		messageToRemote = client.getHashMAC().getEncodedHash(messageToRemote) + " " + messageToRemote;
		assert messageToRemote.matches(regexCheck);
		logger.debug("Outgoing: " + messageToRemote);
		out.println(messageToRemote);
	}
	
	/**
	 * Sends a message and then listens for an answer.
	 * @param command
	 * @param message
	 * @return
	 */
	public String sendAndRecieve(String command, String message) {
		send(command, message);
		return receive();
	}

	/**
	 * Closes IO and socket.
	 */
	public void shutDown() {
		try {
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			logger.debug(e.getMessage());
		}
	}

}
