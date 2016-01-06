package client.tcp;

import java.io.BufferedReader;
import java.net.Socket;

import client.Client;
import util.SecurityTool;
import util.Logger;
import util.Streams;

public class TcpReader implements Runnable {

	private final int WAITING_FOR_AUTHENTICATION = 0;
	private final int AUTHENTICATED = 2;

	private Socket socket;
	private Client client;
	private String response = "";
	private SecurityTool security;

	private int status = WAITING_FOR_AUTHENTICATION;

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
					if (status != AUTHENTICATED) {
						response = tmp;
					} else {
						String decodedTmp = security.decode(tmp, "AES");
						// !public msg will be transfered to the shell immediately
						if (decodedTmp.startsWith("!public")) {
							client.setLastMsg(decodedTmp.substring(8).trim());
							client.getShell().writeLine(decodedTmp.substring(8).trim());
						} else {
							if (response.startsWith("Successfully logged out")) setStatus(WAITING_FOR_AUTHENTICATION);
							response = decodedTmp.trim();
						}
					}
				}
				tmp = reader.readLine();
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	public String getResponse() {
		return response;
	}

	public void clearResponse() {
		response = "";
	}

	public Object getLock() {
		return lock;
	}

	public void setStatus(int status) { this.status = status; }
	public void setSecurity(SecurityTool security) { this.security = security; }
}
