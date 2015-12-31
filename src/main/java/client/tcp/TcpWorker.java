package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import client.Client;
import util.Logger;
import util.Streams;

public class TcpWorker implements Runnable {

	private Client client;
	private Socket socket;
	private Logger logger = new Logger();

	public TcpWorker(Client client, Socket socket) {
		this.client = client;
		this.socket = socket;
	}
	
	@Override
	public void run() {
		send(null);
	}
	
	public String send(String messageToRemote) {
		String response = null;
		
		try (BufferedReader in = Streams.getBufferedReader(socket); 
				PrintWriter out = Streams.getPrintWriter(socket)) {
			
			if (messageToRemote != null) {
				out.println(client.getHashMAC().getEncodedHash(messageToRemote) + " " + messageToRemote);
			}
			
			String command = in.readLine();
			if (!client.isActive() || command == null) {
				return null;
			}
			
			String[] msgParts = command.split(" ");
			if (msgParts.length < 2) {
				logger.error("Recieved strange message: "+command);
				return null;
			}
			
			String messageHash = msgParts[0];
			String messageCommand = msgParts[1];
			String messageCommandAndBody = command.substring(messageHash.length()).trim();
			String message = messageCommandAndBody.replaceFirst(messageCommand, "").trim();
			String controlHash = client.getHashMAC().getEncodedHash(messageCommandAndBody);
			
			if (!messageHash.equals(controlHash)) {
				logger.error("Hash mismatch!!! Tampered message: \""+message+"\". Sending tampered warning to remote client.");
				out.println(client.getHashMAC().getEncodedHash("!tampered "+message) + " !tampered " + message);
			
			} else if (messageCommand.equals("!msg")) {
				logger.info(message);
				out.println(client.getHashMAC().getEncodedHash("!ack") + " !ack");
				response = message;
				
			} else if (messageCommand.equals("!tampered")) {
				logger.error("The recieving client reported that our message has been tampered!");
			
			} else if (messageCommand.equals("!ack")) {
				logger.info(messageCommand);
				response = messageCommand;
				
			} else {
				logger.error("Recieved strange message: "+command);
			}
			
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
		
		return response;
	}

	public void shutDown() {
		try {
			socket.close();
		} catch (IOException e) {
			//e.printStackTrace();
		}
	}

}
