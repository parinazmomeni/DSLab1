package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import client.Client;
import util.Logger;

public class TcpWorker implements Runnable {

	private Client client;
	private Socket socket;
	private Logger logger;

	public TcpWorker(Client client, Socket socket) {
		this.client = client;
		this.socket = socket;
		this.logger = new Logger();
	}

	@Override
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

			String command = in.readLine();
			if (client.isActive() && command != null) {

				String[] words = command.split(" +");

				if (words.length < 2) {
					out.println("Wrong command: incorrect number of arguments");
					return;
				}

				String message = command.substring(4).trim();
				logger.info(message);

				out.println("!ack");
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
