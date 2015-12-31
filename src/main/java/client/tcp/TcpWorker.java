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
		try (BufferedReader in = Streams.getBufferedReader(socket); 
				PrintWriter out = Streams.getPrintWriter(socket)) {
			String command = in.readLine();
			if (!client.isActive() && command == null) {
				return;
			}
			
			String[] msgParts = command.split(" ");
			if (msgParts.length < 2) {
				logger.error("Recieved strange message: "+command);
				return;
			}
			
			String messageHash = msgParts[0];
			String messageCommand = msgParts[1];
			String message = command.substring(messageHash.length()+1+messageCommand.length()+1);
			String controlHash = client.getHashMAC().getEncodedHash(messageCommand+" "+message);
			
			if (!messageHash.equals(controlHash)) {
				logger.error("Hash mismatch!!! Tampered message: \""+message+"\". Sending tampered warning to remote client.");
				out.println(client.getHashMAC().getEncodedHash("!tampered "+message) + " !tampered " + message);
			
			} else if (messageCommand.equals("!msg")) {
				logger.info(message);
				out.println(client.getHashMAC().getEncodedHash("!ack") + " !ack");
				
			} else if (messageCommand.equals("!tampered")) {
				logger.error("The recieving client reported that our message has been tampered!");
			
			} else if (messageCommand.equals("!ack")) {
				logger.info(messageCommand);
				
			} else {
				logger.error("Recieved strange message: "+command);
			}
			
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	public void shutDown() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
