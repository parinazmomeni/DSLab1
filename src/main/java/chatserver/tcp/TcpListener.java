package chatserver.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chatserver.Chatserver;
import util.Logger;

public class TcpListener implements Runnable {

	private ServerSocket socket;
	private Chatserver chatServer;
	private Logger logger;

	public TcpListener(Chatserver chatServer, ServerSocket socket) {
		this.chatServer = chatServer;
		this.socket = socket;
		this.logger = new Logger();
	}

	@Override
	public void run() {
		ExecutorService threadPool = Executors.newCachedThreadPool();

		try {
			while (chatServer.isOnline()) {
				try {
					Socket client = socket.accept();

					TcpWorker tcpWorker = new TcpWorker(chatServer, client);
					chatServer.getTcpWorkerList().add(tcpWorker);
					threadPool.execute(tcpWorker);

				} catch (SocketTimeoutException e) {
					logger.error("Timeout Exception occured");
					break;
				}
			}

		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		for (TcpWorker worker : chatServer.getTcpWorkerList()) {
			worker.shutDown();
		}

		threadPool.shutdown();
	}

}
