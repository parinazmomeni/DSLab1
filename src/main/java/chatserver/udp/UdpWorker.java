package chatserver.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import chatserver.Chatserver;
import model.User;
import util.Logger;

public class UdpWorker implements Runnable {
	private Chatserver chatServer;
	private DatagramPacket packet;
	private DatagramSocket socket;
	private Logger logger;

	public UdpWorker(Chatserver chatServer, DatagramPacket packet, DatagramSocket socket) {
		this.chatServer = chatServer;
		this.packet = packet;
		this.socket = socket;
		this.logger = new Logger();
	}

	@Override
	public void run() {
		byte[] data = packet.getData();
		String out = new String(data);
		out = out.trim();

		String result = null;

		if (out.equals("!list")) {
			result = getOnlineUsers();
		} else {
			result = "Wrong command.";
		}

		InetAddress address = packet.getAddress();
		DatagramPacket p = new DatagramPacket(result.getBytes(), result.length(), address, packet.getPort());

		try {
			socket.send(p);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	private String getOnlineUsers() {

		String userList = "Online users:\n";

		synchronized (chatServer.getUsers()) {
			if (chatServer.getUsers().isEmpty()) {
				return userList;
			}

			for (User u : chatServer.getUsers().values()) {
				if (u.isOnline()) {
					userList += "* " + u.getUserName() + "\n";
				}
			}
		}

		return userList;
	}

}
