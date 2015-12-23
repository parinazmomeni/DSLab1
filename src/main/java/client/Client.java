package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

	private String hostname;
	private String lastMsg = "";

	private int tcpPort;
	private int udpPort;

	private List<TcpWorker> tcpWorkerList = Collections.synchronizedList(new ArrayList<TcpWorker>());
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	private Logger logger = new Logger();

	private Thread tcpReaderThread;
	private Thread udpReaderThread;

	private TcpReader tcpReader;
	private UdpReader udpReader;

	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		hostname = config.getString("chatserver.host");
		tcpPort = config.getInt("chatserver.tcp.port");
		udpPort = config.getInt("chatserver.udp.port");

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
	}

	private void acquirePorts() throws IOException {
		// TCP, UPD sockets
		tcpSocket = new Socket(hostname, tcpPort);
		udpSocket = new DatagramSocket();

		// TCP Output Stream
		tcpOutputStream = Streams.getPrintWriter(tcpSocket);
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
			BufferedReader in = Streams.getBufferedReader(s);
			PrintWriter out = Streams.getPrintWriter(s);

			out.println("msg " + message);

			String response = in.readLine();

			if (response == null && s != null) {
				s.close();
				return "Error occured during client to client communication";
			}

			return response;

		} catch (IOException e) {
			return e.getMessage();
		} catch (NumberFormatException e) {
			return "Could not convert port: " + words[1] + " to integer.";
		} finally {
			if ( s!= null) s.close();
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
		// TODO Auto-generated method stub
		return null;
	}
}
