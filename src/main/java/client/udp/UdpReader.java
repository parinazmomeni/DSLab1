package client.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import client.Client;
import util.Logger;

public class UdpReader implements Runnable {

	private DatagramSocket udpSocket;
	private Client client;
	private Logger logger;

	public UdpReader(Client client, DatagramSocket udpSocket) {
		this.client = client;
		this.udpSocket = udpSocket;
		this.logger = new Logger();
	}

	@Override
	public void run() {
		try {
			DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

			while (client.isActive()) {
				udpSocket.receive(packet);

				String response = new String(packet.getData(), 0, packet.getLength());
				logger.info(response);
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

	}

}
