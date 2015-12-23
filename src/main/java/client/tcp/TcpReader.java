package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import client.Client;
import util.Logger;
import util.Streams;

public class TcpReader implements Runnable {

	private Socket socket;
	private Client client;
	private String response;
	private Logger logger = new Logger();
	private Object lock = new Object();

	public TcpReader(Client client, Socket socket) {
		this.client = client;
		this.socket = socket;
	}

	@Override
	public void run() {
		try(BufferedReader reader = Streams.getBufferedReader(socket);) {
			String tmp = reader.readLine();
			while (client.isActive() && tmp != null) {
				synchronized (lock) {
					if (tmp.startsWith("!public")) {
						client.setLastMsg(tmp.substring(8).trim());
						response = tmp.substring(8).trim();
					} else {
						response = tmp.trim();
					}

					logger.info(response);
				}

				tmp = reader.readLine();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	public String getResponse() {
		return response;
	}

	public Object getLock() {
		return lock;
	}

}
