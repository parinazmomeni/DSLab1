package client.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

		ExecutorService threadPool = Executors.newCachedThreadPool();

		try {
			while (client.isActive()) {
				TcpWorker tcpWorker = new TcpWorker(client, socket.accept());
				synchronized (client.getTcpWorkerList()) {
					client.getTcpWorkerList().add(tcpWorker);
				}
				threadPool.execute(tcpWorker);
			}

		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		synchronized (client.getTcpWorkerList()) {
			for (TcpWorker worker : client.getTcpWorkerList()) {
				worker.shutDown();
			}
		}

		threadPool.shutdown();
	}

}
