package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import client.tcp.TcpListener;
import client.tcp.TcpReader;
import client.tcp.TcpWorker;
import client.udp.UdpReader;
import org.bouncycastle.util.encoders.Base64;
import util.ComponentFactory;
import util.Config;
import util.Keys;
import util.Logger;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class Client implements IClientCli, Runnable {

	private PrintWriter tcpOutputStream;

	private Socket tcpSocket;
	private DatagramSocket udpSocket;
	private ServerSocket srvSocket;

	private Shell shell;

	private boolean active = false;

	private Config config;
	private String hostname;
	private String lastMsg;

	private int tcpPort;
	private int udpPort;

	private List<TcpWorker> tcpWorkerList = Collections.synchronizedList(new ArrayList<TcpWorker>());
	private ExecutorService threadPool;

	private Logger logger;

	private Thread tcpReaderThread;
	private Thread udpReaderThread;

	private TcpReader tcpReader;
	private UdpReader udpReader;

	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		this.config = config;
		hostname = config.getString("chatserver.host");
		tcpPort = config.getInt("chatserver.tcp.port");
		udpPort = config.getInt("chatserver.udp.port");

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);

		threadPool = Executors.newCachedThreadPool();
		logger = new Logger();

		lastMsg = "";
	}

	private void acquirePorts() throws IOException {
		// TCP, UPD sockets
		tcpSocket = new Socket(hostname, tcpPort);
		udpSocket = new DatagramSocket();

		// TCP Output Stream
		tcpOutputStream = new PrintWriter(tcpSocket.getOutputStream(), true);
	}

	@Override
	public void run() {
		try {
			acquirePorts();
			active = true;

			this.tcpReader = new TcpReader(this, tcpSocket);
			this.udpReader = new UdpReader(this, udpSocket);

			this.tcpReaderThread = new Thread(tcpReader);
			this.udpReaderThread = new Thread(udpReader);

			tcpReaderThread.start();
			udpReaderThread.start();

			threadPool.execute(shell);

			logger.info("Client startet ...");

			threadPool.shutdown();
		} catch (UnknownHostException e) {
			logger.error("Hostname: " + hostname + "is unknown.");
		} catch (IOException e) {
			logger.error("Could not connect to ChatServer.");
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		tcpOutputStream.println("!login" + " " + username + " " + password);
		return "";
	}

	@Override
	@Command
	public String logout() throws IOException {
		tcpOutputStream.println("!logout");
		return "";
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		tcpOutputStream.println("!send" + " " + message);
		return "";
	}

	@Override
	@Command
	public String list() throws IOException {
		String data = "!list";
		InetAddress address = InetAddress.getByName(hostname);
		DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), address, udpPort);
		udpSocket.send(packet);
		return "";
	}

	@Override
	@Command
	public String msg(String receiver, String message) throws IOException {

		String address = lookup(receiver);
		if (!address.startsWith("!address")) {
			return "Error occured receiving address of client.";
		}

		address = address.substring(9);

		String[] words = address.split(":");
		if (words.length != 2) {
			return "Wrong address format.";
		}

		Socket s = null;
		try {
			s = new Socket(words[0], Integer.parseInt(words[1]));
			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			PrintWriter out = new PrintWriter(s.getOutputStream(), true);

			out.println("msg " + message);

			String response = in.readLine();

			if (response == null) {
				s.close();
				return "Error occured during client to client communication";
			}

			return response;

		} catch (IOException e) {
			return e.getMessage();
		} catch (NumberFormatException e) {
			return "Could not convert port: " + words[1] + " to integer.";
		} finally {
			s.close();
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {

		tcpOutputStream.println("!lookup" + " " + username);
		return tcpReader.getResponse();
	}

	@Override
	@Command
	public String register(String address) throws IOException {

		if (srvSocket != null) {
			srvSocket.close();
		}

		String[] addressPort = address.split(":");
		if (addressPort.length != 2) {
			return "Wrong command: incorrect address format.";
		}

		String port = addressPort[1];

		int p;
		try {
			p = Integer.parseInt(port);
		} catch (NumberFormatException e) {
			logger.error("Port: " + port + " is not a number.");
			return "Port number is invalid or already in use.";
		}

		srvSocket = new ServerSocket(p);
		new Thread(new TcpListener(this, srvSocket)).start();

		tcpOutputStream.println("!register" + " " + address);

		return "";
	}

	public void setLastMsg(String message) {
		lastMsg = message;
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		return lastMsg;
	}

	@Override
	@Command
	public String exit() throws IOException {
		try {
			logout();
			cleanUp();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error occured during Client shutdown.";
		}

		return "Client stopped";
	}

	private void cleanUp() throws IOException {
		if (tcpSocket != null) {
			tcpSocket.close();
		}

		if (udpSocket != null) {
			udpSocket.close();
		}

		if (srvSocket != null) {
			srvSocket.close();
		}

		shell.close();

		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public List<TcpWorker> getTcpWorkerList() {
		return tcpWorkerList;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		ComponentFactory factory = new ComponentFactory();
		IClientCli client = factory.createClient("Client", System.in, System.out);
		new Thread((Runnable) client).start();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {

		// generates a 32 byte secure random number
		SecureRandom secureRandom = new SecureRandom();
		final byte[] challenge = new byte[32];
		secureRandom.nextBytes(challenge);

		// encode challenge into Base64 format
		byte[] base64Challenge = Base64.encode(challenge);

		// generate full message
		String message = "!authenticate " + username + " ";
		byte[] messageByte = message.getBytes(Charset.forName("UTF-8"));
		byte[] fullMessage = new byte[base64Challenge.length + messageByte.length];
		for (int i = 0; i < fullMessage.length; ++i)
		{
			fullMessage[i] = i < messageByte.length ? messageByte[i] : base64Challenge[i - base64Challenge.length];
		}

		// initialize RSA cipher with chatserver's public key
		// and encode full message
		Cipher cipher = null;
		byte[] encryptedMessage = null;
		String chatServeKeyFilepath = config.getString("chatserver.key");
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(chatServeKeyFilepath)));
			encryptedMessage = cipher.doFinal(fullMessage);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during authentication";
		}

		byte[] base64EncryptedMessage = Base64.encode(encryptedMessage);
		tcpOutputStream.println(base64EncryptedMessage);
		return null;
	}
}
