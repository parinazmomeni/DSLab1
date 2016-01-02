package chatserver.tcp;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import chatserver.Chatserver;
import model.User;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;
import util.Logger;
import util.Streams;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class TcpWorker implements Runnable {
	private Chatserver chatServer;
	private Socket client;
	private Config config;
	private User currentUser;
	private String username;
	private Logger logger = new Logger();
	final String B64 = "a-zA-Z0-9/+";

	public TcpWorker(Chatserver chatServer, Socket client, Config config) {
		this.chatServer = chatServer;
		this.client = client;
		this.config = config;
	}

	@Override
	public void run() {
		try (BufferedReader bufferedReader = Streams.getBufferedReader(client);
				PrintWriter out = Streams.getPrintWriter(client)) {

			String command = bufferedReader.readLine();
			logger.info("Command: " + command);
			command = decryptMessage(command.getBytes());

			while (chatServer.isOnline() && command != null) {
				//if (command.startsWith("!login")) {
				//	login(command, out);
				if (command.startsWith("!authenticate")) {
					authenticate(command, out);
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
				command = decryptMessage(command.getBytes());
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

	private String decryptMessage(byte[] msg){

		logger.info("Decrypt message");
		logger.info(new String(msg, StandardCharsets.UTF_8));
		// decode challenge from Base64 format
		byte[] base64Message = Base64.decode(msg);
		String firstMessage = new String(base64Message, StandardCharsets.UTF_8);
		assert firstMessage.matches("!authenticate [\\w\\.]+["+B64+"]{43}="):"1st  message ";

		// decrypt message with chatserver's private key
		Cipher cipher = null;
		byte[] decryptedMessage = null;
		String chatserverKeyFilepath = config.getString("key");
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(chatserverKeyFilepath)));
			decryptedMessage = cipher.doFinal(base64Message);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during handshake for authentification";
		}

		// decrypt every argument from Base64 format
		String str = new String(decryptedMessage, StandardCharsets.UTF_8);
		logger.info(str);
		String[] message = str.split(" ");
		String finalMessage = message[0] + " " + message[1];

		//for(int i = 1; i < message.length; ++i) {
		logger.info(message[2]);
			byte[] base64Decrypt = Base64.decode(message[2]);
			str = new String(base64Decrypt, StandardCharsets.UTF_8);
		logger.info(str);
			finalMessage += " " + str;
			logger.info(finalMessage);
		//}

		return finalMessage;
	}

	private void authenticate(String command, PrintWriter out) {

		logger.info("Authentification of 1st message");
		String[] message = command.split(" ");
		if(message.length != 3) {
			logger.error("Wrong number of arguments");
			out.println("Problem with authentification.");
			return;
		}

		if (!chatServer.getUsers().containsKey(message[1])) {
			logger.error("Wrong username");
			out.println("Wrong username.");
			return;
		}

		String user = message[1];

		String returnMessage = "!ok ";
		byte[] challengeClient = message[2].getBytes(Charset.forName("UTF-8"));

		// generate a secure random numbers
		SecureRandom secureRandom = new SecureRandom();
		byte[] challengeServer = new byte[32];
		secureRandom.nextBytes(challengeServer);

		logger.info("cl-C: " + new String(challengeClient, StandardCharsets.UTF_8));
		logger.info("s-C : " + new String(challengeServer, StandardCharsets.UTF_8));

		// generate an AES key
		KeyGenerator generator = null;
		try {
			generator = KeyGenerator.getInstance("AES");
		} catch (NoSuchAlgorithmException e) {
			logger.error(e.getMessage());
			out.println("Error during authentification.");
			return;
		}
		generator.init(256);
		SecretKey key = generator.generateKey();

		// generate IV vector
		byte[] vectorIV = new byte[16];
		secureRandom.nextBytes(vectorIV);

		// get user's public key
		String userKeyFilepath = config.getString("keys.dir")+"\\"+user+".pub.pem";
		Key userPublicKey = null;
		try {
			userPublicKey = Keys.readPrivatePEM(new File(userKeyFilepath));
		} catch (IOException e) {
			logger.error(e.getMessage());
			out.println("Error during authentification.");
			return;
		}

		// encode everything into Base64 format
		byte[] base64ServerChallenge = Base64.encode(challengeServer);
		logger.info(new String(base64ServerChallenge,StandardCharsets.UTF_8));
		byte[] base64ClientChallenge = Base64.encode(challengeClient);
		logger.info(new String(base64ClientChallenge,StandardCharsets.UTF_8));
		byte[] base64SecretKey = Base64.encode(key.getEncoded());
		byte[] base64IVVector = Base64.encode(vectorIV);
		logger.info(new String(base64IVVector,StandardCharsets.UTF_8));

		String fullMessage = "!ok " + new String(base64ClientChallenge,StandardCharsets.UTF_8) + " " +
				new String(base64ServerChallenge, StandardCharsets.UTF_8) + " " +
				new String(base64SecretKey, StandardCharsets.UTF_8) + " " +
				new String(base64IVVector, StandardCharsets.UTF_8);
		byte[] fullMessageByte = fullMessage.getBytes();

		// initialize RSA cipher with user's public key
		// and encode full message
		Cipher cipher = null;
		byte[] encryptedMessage = null;
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, userPublicKey);
			encryptedMessage = cipher.doFinal(fullMessageByte);
		} catch (Exception e) {
			logger.error(e.getMessage());
			out.println("Error during authentification.");
			return;
		}

		// encode full message to Base64 and send it
		byte[] base64EncryptedMessage = Base64.encode(encryptedMessage);
		out.println(base64EncryptedMessage);
		return;
	}
}
