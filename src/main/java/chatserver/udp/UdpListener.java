package chatserver.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chatserver.Chatserver;
import util.Logger;

public class UdpListener implements Runnable {
	private DatagramSocket socket;
	private Chatserver chatserver;
	private Logger logger;

	public UdpListener(Chatserver chatserver, DatagramSocket socket) {
		this.chatserver = chatserver;
		this.socket = socket;
		this.logger = new Logger();
	}

	@Override
	public void run() {
		ExecutorService threadPool = Executors.newCachedThreadPool();

		try {
			while (chatserver.isOnline()) {
				DatagramPacket packet = new DatagramPacket(new byte[32], 32);
				try {
					socket.receive(packet);
					UdpWorker udpWorker = new UdpWorker(chatserver, packet, socket);
					threadPool.execute(udpWorker);

				} catch (SocketTimeoutException e) {
					logger.error("Timeout Exception occured");
					break;
				}
			}

		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		threadPool.shutdown();
	}

}
