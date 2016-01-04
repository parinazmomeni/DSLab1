package chatserver.tcp;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import javax.crypto.spec.IvParameterSpec;

public class TcpWorker implements Runnable {
	private Chatserver chatServer;
	private Socket client;
	private Config config;
	private User currentUser;
	private String username;

	private final int WAITING_FOR_AUTHENTICATION = 0;
	private final int WAITING_FOR_CLIENTS_PROOF = 1;
	private final int AUTHENTICATED = 2;
	private final String B64 = "a-zA-Z0-9/+";
	private final Charset UTF_8 = Charset.forName("UTF-8");
	private int status = WAITING_FOR_AUTHENTICATION;

	private String serverChallenge;
	private String clientChallenge;

	private byte[] ivVector;
	private SecretKey secretKey;

	private Logger logger = new Logger();

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
			while (chatServer.isOnline() && command != null) {
				if(status == WAITING_FOR_AUTHENTICATION) {
					authenticate(command, out);
				} else if(status == WAITING_FOR_CLIENTS_PROOF) {
					proofClient(command, out);
				}else {
					command = decodeMessage(command);
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
			out.println(encodeMessage("Wrong command: incorrect number of arguments."));
			return;
		}

		String firstPartOfMessage = words[1];

		int index = command.indexOf(firstPartOfMessage);
		String message = command.substring(index);

		synchronized (chatServer.getUsers()) {
			if (chatServer.getUsers().size() == 1) {
				out.println(encodeMessage("There is no other user online."));
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
					SecretKey uKey = u.getSecretKey();
					byte[] uIVVector = u.getIvVector();
					writer.println(encodeMessage("!public " + currentUser.getUserName() + ": " + message, uKey, uIVVector));
					messageSent = true;
				}
			}

			if (!messageSent) {
				out.println(encodeMessage("There is no other user online."));
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
			out.println(encodeMessage("Wrong command: incorrect number of arguments."));
			return;
		}

		String[] addressPort = words[1].split(":");
		if (addressPort.length != 2) {
			out.println(encodeMessage("Wrong command: incorrect address format."));
			return;
		}

		String address = addressPort[0];
		String port = addressPort[1];

		synchronized (currentUser) {
			try {
				currentUser.setAddress(InetAddress.getByName(address));
			} catch (UnknownHostException e) {
				out.println(encodeMessage("IP address: " + address + " is unknown."));
				return;
			}

			try {
				currentUser.setPort(Integer.parseInt(port));
			} catch (NumberFormatException e) {
				out.println(encodeMessage("Port: " + port + " is not a number."));
				return;
			}
		}

		out.println(encodeMessage("Successfully registered address for " + currentUser.getUserName() + "."));
	}

	private void error(String command, PrintWriter out) {
		out.println(encodeMessage(command + " UNKNOWN COMMAND."));
	}

	private void lookup(String command, PrintWriter out) {

		// Check if user is logged in.
		if (currentUser == null) {
			out.println("Log in the user. You are not logged in.");
			return;
		}

		String[] words = command.split(" +");
		if (words.length != 2) {
			out.println(encodeMessage("Wrong command: incorrect number of arguments."));
			return;
		}

		String otherUser = words[1];

		Map<String, User> users = this.chatServer.getUsers();
		for (User user : users.values()) {
			if (user.getUserName().equals(otherUser)) {
				if (user.getAddress() != null) {
					out.println(encodeMessage("!address " + user.getAddress().getHostAddress() + ":" + user.getPort()));
					return;
				} else {
					out.println(encodeMessage(user.getUserName() + " has not registered a private address."));
					return;
				}
			}
		}

		out.println(encodeMessage(otherUser + " Wrong username or user not reachable."));
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
		status = WAITING_FOR_AUTHENTICATION;
		logger.info(username + " logged out.");

		out.println(encodeMessage("Successfully logged out."));
	}

	public void shutDown() {
		try {
			client.close();
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	private String decodeMessage(String msg){

		byte[] message = Base64.decode(msg.getBytes(UTF_8));

		// decrypt message with shared secret key
		Cipher cipher = null;
		byte[] decryptedMessage = null;
		try {
			cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey,new IvParameterSpec(ivVector));
			decryptedMessage = cipher.doFinal(message);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during decoding message.";
		}

		return new String(decryptedMessage, StandardCharsets.UTF_8);
	}

	private String encodeMessage(String msg){
		return encodeMessage(msg, secretKey, ivVector);
	}

	private String encodeMessage(String msg, SecretKey secKey, byte[] vector){

		// encrypt message with shared secret key
		Cipher cipher = null;
		byte[] encryptedMessage = null;
		try {
			cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, secKey,new IvParameterSpec(vector));
			encryptedMessage = cipher.doFinal(msg.getBytes(UTF_8));;
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during handshake for authentication";
		}

		byte[] base64encryptedMessage = Base64.encode(encryptedMessage);
		return new String(base64encryptedMessage, StandardCharsets.UTF_8);
	}

	private void authenticate(String command, PrintWriter out) {

		if(status == AUTHENTICATED) {
			out.println(encodeMessage("Already logged in."));
			return;
		}

		// decode challenge from Base64 format
		byte[] base64Message = Base64.decode(command.getBytes(UTF_8));

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
			out.println("!Error during handshake for authentification");
			return;
		}

		// decrypt every argument from Base64 format
		String str = new String(decryptedMessage, StandardCharsets.UTF_8);
		assert str.matches("!authenticate [\\w\\.]+["+B64+"]{43}="):"1st  message";

		String[] message = str.split(" ");
		clientChallenge = message[2];

		if(!str.startsWith("!authenticate")) {
			logger.error("Wrong starting argument");
			out.println("!Error: Wrong starting argument");
			return;
		}
		if(message.length != 3) {
			logger.error("Wrong number of arguments");
			out.println("!Error during handshake for authentication.");
			return;
		}
		if (!chatServer.getUsers().containsKey(message[1])) {
			logger.error("Wrong username");
			out.println("!Error: Wrong username.");
			return;
		}

		username = message[1];

		// generate a secure random number
		SecureRandom secureRandom = new SecureRandom();
		byte[] challengeServer = new byte[32];
		secureRandom.nextBytes(challengeServer);

		// generate an AES key
		KeyGenerator generator = null;
		try {
			generator = KeyGenerator.getInstance("AES");
		} catch (NoSuchAlgorithmException e) {
			logger.error(e.getMessage());
			out.println("!Error during authentication.");
			return;
		}
		generator.init(256);
		secretKey = generator.generateKey();

		// generate IV vector
		ivVector = new byte[16];
		secureRandom.nextBytes(ivVector);

		// get user's public key's path
		String userKeyFilepath = config.getString("keys.dir")+"\\"+username+".pub.pem";

		// encode everything into Base64 format
		byte[] base64ServerChallenge = Base64.encode(challengeServer);
		byte[] base64SecretKey = Base64.encode(secretKey.getEncoded());
		byte[] base64IVVector = Base64.encode(ivVector);

		serverChallenge = new String(base64ServerChallenge, StandardCharsets.UTF_8);

		String fullMessage = "!ok " + clientChallenge + " " +
				new String(base64ServerChallenge, StandardCharsets.UTF_8) + " " +
				new String(base64SecretKey, StandardCharsets.UTF_8) + " " +
				new String(base64IVVector, StandardCharsets.UTF_8);

		// initialize RSA cipher with user's public key
		// and encode full message
		byte[] encryptedMessage = null;
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(userKeyFilepath)));
			encryptedMessage = cipher.doFinal(fullMessage.getBytes(UTF_8));
		} catch (Exception e) {
			logger.error(e.getMessage());
			out.println("!Error during authentication.");
			return;
		}

		// encode full message to Base64 and send it
		byte[] base64EncryptedMessage = Base64.encode(encryptedMessage);
		out.println(new String(base64EncryptedMessage, StandardCharsets.UTF_8));

		logger.info("Sent challenge for handshake to " + username);
		status = WAITING_FOR_CLIENTS_PROOF;
		return;
	}

	private void proofClient(String command, PrintWriter out) {

		String decodedMessage = decodeMessage(command);

		if(!serverChallenge.equals(decodedMessage)) {
			logger.error("Client couldn't decode server's challenge");
			out.println(encodeMessage("!Error during authentication."));
			status = WAITING_FOR_AUTHENTICATION;
			return;
		}

		User user = chatServer.getUsers().get(username);
		synchronized (user) {
			if (user.isOnline()) {
				out.println("!Error: This user is in use somewhere else.");
				return;
			}
			currentUser = user;
			currentUser.setOnline(true);
			currentUser.setSocket(client);
			currentUser.setSecretKey(secretKey);
			currentUser.setIvVector(ivVector);

			status = AUTHENTICATED;
		}

		logger.info(username + " successfully authenticated");
		out.println(encodeMessage("Successfully logged in."));
		return;
	}
}
