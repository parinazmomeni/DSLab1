package client.tcp;

import java.io.IOException;
import java.net.ServerSocket;

import client.Client;
import util.Logger;

public class TcpListener implements Runnable {

	private Client client;
	private ServerSocket socket;
	private Logger logger = new Logger();

	public TcpListener(Client client, ServerSocket socket) {
		this.client = client;
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			while (client.isActive() && !socket.isClosed()) {
				TcpWorker tcpWorker = new TcpWorker(client, socket.accept());
				synchronized (client.getTcpWorkerList()) {
					client.getTcpWorkerList().add(tcpWorker);
				}
				client.getThreadPool().submit(tcpWorker);
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		synchronized (client.getTcpWorkerList()) {
			for (TcpWorker worker : client.getTcpWorkerList()) {
				worker.shutDown();
			}
		}
	}

}
