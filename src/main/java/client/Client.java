package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
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
import util.ComponentFactory;
import util.Config;
import util.Logger;
import util.Streams;

public class Client implements IClientCli, Runnable {

	private PrintWriter tcpOutputStream;

	private Socket tcpSocket;
	private DatagramSocket udpSocket;
	private ServerSocket srvSocket;

	private Shell shell;

	private boolean active = false;

	private String lastMsg = "";
	
	private Config config;

	private List<TcpWorker> tcpWorkerList = Collections.synchronizedList(new ArrayList<TcpWorker>());
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	private Logger logger = new Logger();

	private Thread tcpReaderThread;
	private Thread udpReaderThread;

	private TcpReader tcpReader;
	private UdpReader udpReader;
	
	private HashMAC hashMAC;

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

			logger.info("Client startet ...");

			threadPool.shutdown();
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
		InetAddress address = InetAddress.getByName(config.getString("chatserver.host"));
		DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), address, config.getInt("chatserver.udp.port"));
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
		
		Socket s = new Socket(host, port);
		BufferedReader in = Streams.getBufferedReader(s);
		PrintWriter out = Streams.getPrintWriter(s);
		out.println(hashMAC.getEncodedHash("!msg "+message) + " !msg " + message);
		String response = in.readLine();
		out.close();
		in.close();
		s.close();
		if (response == null) {
			return "Error occured during client to client communication";
		} else {
			return response;
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

		try {
			srvSocket = new ServerSocket(Integer.parseInt(addressPort[1]));
			new Thread(new TcpListener(this, srvSocket)).start();
		} catch (NumberFormatException e) {
			logger.error("Port: " + addressPort[1] + " is not a number.");
			return "Port: " + addressPort[1] + " is not a number.";
		}
		
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
		// TODO Auto-generated method stub
		return null;
	}
}
