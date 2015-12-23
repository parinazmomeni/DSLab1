package chatserver.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;

import chatserver.Chatserver;
import model.User;
import util.Logger;
import util.Streams;

public class TcpWorker implements Runnable {
	private Chatserver chatServer;
	private Socket client;
	private User currentUser;
	private String username;
	private Logger logger = new Logger();

	public TcpWorker(Chatserver chatServer, Socket client) {
		this.chatServer = chatServer;
		this.client = client;
	}

	@Override
	public void run() {
		try (BufferedReader bufferedReader = Streams.getBufferedReader(client); 
				PrintWriter out = Streams.getPrintWriter(client)) {

			String command = bufferedReader.readLine();
			while (chatServer.isOnline() && command != null) {
				if (command.startsWith("!login")) {
					login(command, out);
				} else if (command.startsWith("!logout")) {
					logout(out);
				} else if (command.startsWith("!send")) {
					send(command, out);
				} else if (command.startsWith("!lookup")) {
					lookup(command, out);
				} else if (command.startsWith("!register")) {
					register(command, out);
				} else {
					error(command, out);
				}

				command = bufferedReader.readLine();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());

		} finally {
			try {
				client.close();
				if (currentUser != null) {
					synchronized (currentUser) {
						currentUser.setOnline(false);
						currentUser.setSocket(null);
					}
					currentUser = null;
				}
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}

		logger.info("Ended communication with client " + (username == null ? "" : username));
	}

	private void send(String command, PrintWriter out) throws IOException {

		// Check if user is logged in.
		if (currentUser == null) {
			out.println("Log in the user. You are not logged in.");
			return;
		}

		String[] words = command.split(" +");
		if (words.length < 2) {
			out.println("Wrong command: incorrect number of arguments.");
			return;
		}

		String firstPartOfMessage = words[1];

		int index = command.indexOf(firstPartOfMessage);
		String message = command.substring(index);

		synchronized (chatServer.getUsers()) {
			if (chatServer.getUsers().size() == 1) {
				out.println("There is no other user online.");
				return;
			}

			boolean messageSent = false;
			for (User u : chatServer.getUsers().values()) {

				// Skip the sender of the message
				if (u.getUserName().equals(currentUser.getUserName())) {
					continue;
				}

				if (u.isOnline()) {
					Socket tmp = u.getSocket();
					PrintWriter writer = new PrintWriter(tmp.getOutputStream(), true);

					writer.println("!public " + currentUser.getUserName() + ": " + message);
					messageSent = true;
				}
			}

			if (!messageSent) {
				out.println("There is no other user online.");
			}
		}

	}

	private void register(String command, PrintWriter out) {

		// Check if user is logged in.
		if (currentUser == null) {
			out.println("Log in the user. You are not logged in.");
			return;
		}

		String[] words = command.split(" +");
		if (words.length != 2) {
			out.println("Wrong command: incorrect number of arguments.");
			return;
		}

		String[] addressPort = words[1].split(":");
		if (addressPort.length != 2) {
			out.println("Wrong command: incorrect address format.");
			return;
		}

		String address = addressPort[0];
		String port = addressPort[1];

		synchronized (currentUser) {
			try {
				currentUser.setAddress(InetAddress.getByName(address));
			} catch (UnknownHostException e) {
				out.println("IP address: " + address + " is unknown.");
				return;
			}

			try {
				currentUser.setPort(Integer.parseInt(port));
			} catch (NumberFormatException e) {
				out.println("Port: " + port + " is not a number.");
				return;
			}
		}

		out.println("Successfully registered address for " + currentUser.getUserName() + ".");
	}

	private void error(String command, PrintWriter out) {
		out.println(command + " UNKNOWN COMMAND.");
	}

	private void lookup(String command, PrintWriter out) {

		// Check if user is logged in.
		if (currentUser == null) {
			out.println("Log in the user. You are not logged in.");
			return;
		}

		String[] words = command.split(" +");
		if (words.length != 2) {
			out.println("Wrong command: incorrect number of arguments.");
			return;
		}

		String otherUser = words[1];

		Map<String, User> users = this.chatServer.getUsers();
		for (User user : users.values()) {
			if (user.getUserName().equals(otherUser)) {
				if (user.getAddress() != null) {
					out.println("!address " + user.getAddress().getHostAddress() + ":" + user.getPort());
					return;
				} else {
					out.println(user.getUserName() + " has not registered a private address.");
					return;
				}
			}
		}

		out.println(otherUser + " Wrong username or user not reachable.");
	}

	private void login(String command, PrintWriter out) {

		// Check if user is already logged in.
		if (currentUser != null) {
			out.println("You are already logged in.");
			return;
		}

		String[] words = command.split(" +");

		if (words.length != 3) {
			out.println("Wrong command: incorrect number of arguments.");
			return;
		}

		String username = words[1];
		String password = words[2];

		if (!chatServer.getUsers().containsKey(username)) {
			out.println("Wrong username or password.");
			return;
		}

		User user = chatServer.getUsers().get(username);
		if (!user.getPassword().equals(password)) {
			out.println("Wrong username or password.");
			return;
		}

		synchronized (user) {
			if (user.isOnline()) {
				out.println("This user is in use somewhere else.");
				return;
			}

			currentUser = user;
			currentUser.setOnline(true);
			currentUser.setSocket(client);

			this.username = user.getUserName();
		}

		out.println("Successfully logged in.");
	}

	private void logout(PrintWriter out) {

		// Check if user is logged in.
		if (currentUser == null) {
			out.println("Log in the user. You are not logged in.");
			return;
		}

		synchronized (currentUser) {
			currentUser.setOnline(false);
			currentUser.setSocket(null);
		}

		currentUser = null;

		out.println("Successfully logged out.");
	}

	public void shutDown() {
		try {
			client.close();
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

}
