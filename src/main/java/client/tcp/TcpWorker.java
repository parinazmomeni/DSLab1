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
			
			if (command.contains(" !msg ")) {
				String[] hashAndMsg = command.split(" !msg ");
				if (hashAndMsg.length != 2) {
					logger.error("Recieved strange command: "+command);
					return;
				}
				
				String messageHash = hashAndMsg[0];
				String message = hashAndMsg[1];
				String controlHash = client.getHashMAC().getEncodedHash("!msg "+message);
					
				if (messageHash.equals(controlHash)) {
					logger.info(hashAndMsg[1]);
					out.println("!ack");
					
				} else {
					logger.error("Hash mismatch!!! Tampered message: \""+message+"\"");
					out.println(client.getHashMAC().getEncodedHash("!tampered "+message) + " !tampered " + message);
				}
				
			} else if (command.contains(" !tampered ")) {
				logger.error("The recieving client reported that our message has been tampered!");
			
			} else {
				logger.info(command);
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
