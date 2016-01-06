package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import util.SecurityTool;
import client.tcp.TcpListener;
import client.tcp.TcpReader;
import client.tcp.TcpWorker;
import client.udp.UdpReader;
import model.KeyInformations;
import org.bouncycastle.util.encoders.Base64;
import util.ComponentFactory;
import util.Config;
import util.Logger;
import util.Streams;

import javax.crypto.spec.SecretKeySpec;

public class Client implements IClientCli, Runnable {

	private final int WAITING_FOR_AUTHENTICATION = 0;
	private final int AUTHENTICATED = 2;
	private final String B64 = "a-zA-Z0-9/+";

	private int status = WAITING_FOR_AUTHENTICATION;
	private PrintWriter tcpOutputStream;

	private Socket tcpSocket;
	private DatagramSocket udpSocket;
	private ServerSocket srvSocket;

	private Logger logger = new Logger();
	private Config config;
	private Shell shell;

	private boolean active = false;

	private String lastMsg = "";

	private List<TcpWorker> tcpWorkerList = Collections.synchronizedList(new ArrayList<TcpWorker>());
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	private Thread tcpReaderThread;
	private Thread udpReaderThread;

	private TcpReader tcpReader;
	private UdpReader udpReader;

	private HashMAC hashMAC; //hashing helper

	private SecurityTool security;

	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		this.config = config;
		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		this.shell.register(this);
	}

	private void acquirePorts() throws IOException {
		// TCP, UPD sockets
		tcpSocket = new Socket(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
		udpSocket = new DatagramSocket();

		// TCP Output Stream
		tcpOutputStream = Streams.getPrintWriter(tcpSocket);
	}

	public HashMAC getHashMAC() {
		return hashMAC;
	}

	@Override
	public void run() {
		try {

			hashMAC = new HashMAC(config.getString("hmac.key"));

			acquirePorts();

			active = true;

			tcpReader = new TcpReader(this, tcpSocket);
			udpReader = new UdpReader(this, udpSocket);

			tcpReaderThread = new Thread(tcpReader);
			udpReaderThread = new Thread(udpReader);

			tcpReaderThread.start();
			udpReaderThread.start();

			threadPool.execute(shell);

			logger.info("Client started ...");

		} catch (UnknownHostException e) {
			logger.error("Hostname: " + config.getString("chatserver.host") + "is unknown.");
		} catch (IOException e) {
			logger.error("Could not connect to ChatServer.");
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	public ExecutorService getThreadPool(){
		return threadPool;
	}
	
	public Shell getShell() {
		return shell;
	}
	
	@Override
	public String login(String username, String password) throws IOException {
		security.println("!login" + " " + username + " " + password);
		return "";
	}

	@Override
	@Command
	public String logout() throws IOException {
		if(status!=AUTHENTICATED) return "You have to authenticate yourself first.";

		try {
			security.println("!logout");
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during encoding message.";
		}

		String response = getResponse();
		tcpReader.setStatus(WAITING_FOR_AUTHENTICATION);
		status = WAITING_FOR_AUTHENTICATION;
		return response;
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if(status!=AUTHENTICATED) return "You have to authenticate yourself first.";

		try {
			security.println("!send" + " " + message);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during encoding message.";
		}
		return "";
	}

	@Override
	@Command
	public String list() throws IOException {
		String data = "!list";
		InetAddress address = InetAddress.getByName(config.getString("chatserver.host"));
		DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), address, config.getInt("chatserver.udp.port"));
		udpSocket.send(packet);
		return "";
	}

	@Override
	@Command
	public String msg(String receiver, String message) throws IOException {
		if(status!=AUTHENTICATED) return "You have to authenticate yourself first.";

		String address = lookup(receiver.trim());
		if (!address.startsWith("!address")) {
			return "Error occured receiving address of client. Got: "+address;
		}

		String[] hostAndPort = address.split(" ")[1].split(":");
		if (hostAndPort.length != 2) {
			return "Wrong address format.";
		}

		String host = hostAndPort[0];
		int port = 0;
		try{
			port = new Integer(hostAndPort[1].trim());
		} catch (NumberFormatException e) {
			return "Could not convert port: " + hostAndPort[1].trim() + " to integer.";
		}

		TcpWorker worker = new TcpWorker(this, new Socket(host, port));
		String response = worker.sendAndRecieve("!msg", message);
		worker.shutDown();
		if (response == null) {
			logger.error("Error occured during client to client communication!");
			return null;
		} else {
			return receiver + " replied with " +response;
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException{
		if(status!=AUTHENTICATED) return "You have to authenticate yourself first.";

		tcpReader.clearResponse();
		try{
			security.println("!lookup" + " " + username);
		} catch (Exception e) {
			logger.exception(e);
			return "Error during encoding message.";
		}

		String address = "";

		try {
			for (int count=0; count<10; count++){ //this is workaround 
				address = tcpReader.getResponse();
				if (address.startsWith("!address")) {
					tcpReader.clearResponse();
					return address;
				} else {
					Thread.sleep(250);
				}
			}
		} catch (InterruptedException e) {
			logger.exception(e);
			tcpReader.clearResponse();
		}
		
		return address;
	}

	@Override
	@Command
	public String register(String address) throws IOException {
		if(status!=AUTHENTICATED) return "You have to authenticate yourself first.";

		if (srvSocket != null) {
			srvSocket.close();
		}

		String[] addressPort = address.split(":");
		if (addressPort.length != 2) {
			return "Wrong command: incorrect address format.";
		}

		try {
			srvSocket = new ServerSocket(Integer.parseInt(addressPort[1]));
			new Thread(new TcpListener(this, srvSocket)).start();
		} catch (NumberFormatException e) {
			logger.error("Port: " + addressPort[1] + " is not a number.");
			return "Port: " + addressPort[1] + " is not a number.";
		}
		
		try{
			security.println("!register" + " " + address);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return "Error during encoding message.";
		}
		
		return getResponse();
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
		threadPool.shutdown();

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
	@Command
	public String authenticate(String username) throws IOException {

		if(status==AUTHENTICATED) return "You are already logged in";

		// generate and save security tool with key paths
		KeyInformations keyPaths = new KeyInformations(
				config.getString("keys.dir")+File.separator+username+".pem",
				config.getString("chatserver.key")
		);
		security = new SecurityTool(tcpOutputStream, keyPaths);
		tcpReader.setSecurity(security);

		// generates a 32 byte secure random number for challenge
		SecureRandom secureRandom = new SecureRandom();
		final byte[] challenge = new byte[32];
		secureRandom.nextBytes(challenge);

		// encode challenge into Base64 format
		byte[] base64Challenge = Base64.encode(challenge);

		// generate full message and send it
		security.printlnRSA("!authenticate " + username + " " + new String(base64Challenge, StandardCharsets.UTF_8));

		// get server's response with server challenge and AES information and compare to original client challenge
		String response = getResponse();

		if(response.startsWith("!Error")) {
			return response;
		}

		try {
			response = security.decode(response,"RSA");
		} catch (Exception e) {
			logger.exception(e);
			return null;
		}
		assert response.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}=="):"2nd  message";

		String[] message = response.split(" ");
		if(!message[1].equals(new String(base64Challenge, StandardCharsets.UTF_8)) || !message[0].equals("!ok")) {
			tcpOutputStream.println("!Error: Wrong challenge response. Authentication failed.");
			return "Can't assure safe connection to server. Authentication failed.";
		}

		// save shared key and vector for further use and respond with encoded server challenge
		security.setSecretKey(new SecretKeySpec(Base64.decode(message[3].getBytes(StandardCharsets.UTF_8)), "AES"));
		security.setIvVector(Base64.decode(message[4].getBytes(StandardCharsets.UTF_8)));
		security.println(message[2]);

		// get server's response with log in status
		try {
			response = security.decode(getResponse(), "AES");
		} catch (Exception e) {
			logger.exception(e);
			return null;
		}

		if(response.startsWith("!Error")) {
			return response;
		}

		tcpReader.setStatus(AUTHENTICATED);
		status = AUTHENTICATED;
		return response;
	}

	/**
	 * this method checks for a response from the tcpReader
	 * @return the response as String
	 */
	private String getResponse() {
		String response = "";
		try {
			while (true) {
				response = tcpReader.getResponse();
				if (!response.equals("")) {
					break;
				} else {
					Thread.sleep(250);
				}
			}
		} catch (InterruptedException e) {
			logger.error(e.getMessage());
		}
		tcpReader.clearResponse();
		return response;
	}
}
