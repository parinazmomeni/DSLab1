package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chatserver.tcp.TcpListener;
import chatserver.tcp.TcpWorker;
import chatserver.udp.UdpListener;
import cli.Command;
import cli.Shell;
import model.User;
import nameserver.INameserver;
import nameserver.INameserverForChatserver;
import util.ComponentFactory;
import util.Config;
import util.Logger;

public class Chatserver implements IChatserverCli, Runnable {

    private Logger logger = new Logger();
    private Shell shell;
    private Config config;

    private boolean online = true;
    private boolean active = true;

    private ExecutorService threadPool;

    private Map<String, User> users = Collections.synchronizedMap(new TreeMap<String, User>());
    private List<TcpWorker> tcpWorkerList = Collections.synchronizedList(new ArrayList<TcpWorker>());

    private int tcpPort;
    private int udpPort;

    private String registryHost;
    private int registryPort;
    private String rootID;

    private ServerSocket tcpSocket;
    private DatagramSocket udpSocket;

    private TcpListener tcpListener;
    private UdpListener udpListener;

    private INameserverForChatserver rootServer;

    public Chatserver(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
        this.config = config;

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);

        threadPool = Executors.newCachedThreadPool();

        try {
            tcpPort = config.getInt("tcp.port");
            udpPort = config.getInt("udp.port");

            registryHost = config.getString("registry.host");
            registryPort = config.getInt("registry.port");
            rootID = config.getString("root_id");

            // Get reference to registry
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

            // Lookup root namerserver
            rootServer = (INameserverForChatserver) registry.lookup(rootID);

        } catch (Exception e) {
            logger.error(e.getMessage());
            active = false;
        }
    }

    private void readUserProperties() throws MissingResourceException {
        Config userConfig = new Config("user");
        Set<String> keys = userConfig.listKeys();

        for (String key : keys) {
            int index = key.lastIndexOf(".password");
            String username = key.substring(0, index);
            users.put(username, new User(username, userConfig.getString(key)));
        }
    }

    private void aquirePorts() throws IOException {
        // Create TCP Socket
        tcpSocket = new ServerSocket(tcpPort);

        // Create UDP Socket
        udpSocket = new DatagramSocket(udpPort);
    }

    private void createListeners() {
        // Create TCP Listener
        tcpListener = new TcpListener(this, tcpSocket, config);

        // Create UDP Listener
        udpListener = new UdpListener(this, udpSocket);
    }

    @Override
    public void run() {

        // If Server is not active, something went wrong and we don't
        // start up the Server and the other Threads.
        if (active) {
            try {
                // Users credentials
                readUserProperties();

                // TCP, UDP ports
                aquirePorts();

                // TCP, UDP Listeners
                createListeners();

                // Start threads
                threadPool.execute(tcpListener);
                threadPool.execute(udpListener);
                threadPool.execute(shell);

                logger.info("ChatServer started ...");

                threadPool.shutdown();

            } catch (MissingResourceException e) {
                logger.error("user.properties file not found.");
            } catch (BindException e) {
                logger.error("UDP port: " + udpPort + " or TCP port: " + tcpPort + " are already in use.");
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    @Override
    @Command
    public String users() throws IOException {

        String list = "";
        synchronized (users) {
            if (users.isEmpty()) {
                list = "User list is empty!";
            }

            int num = 1;
            for (User u : users.values()) {
                list += num + "." + " " + u.getUserName() + " ";
                num++;
                if (u.isOnline()) {
                    list += "online \n";
                } else {
                    list += "offline \n";
                }
            }
        }
        return list;
    }

    @Override
    @Command
    public String exit() throws IOException {
        try {
            shell.close();
            setOffline();

            udpSocket.close();
            tcpSocket.close();

        } catch (Exception e) {
            logger.error(e.getMessage());
            return "Error occured druing ChatServer shutdown.";
        }

        return "Shutting down ChatServer ...";
    }

    public boolean isOnline() {
        return online;
    }

    public void setOffline() {
        this.online = false;
    }

    public Map<String, User> getUsers() {
        return users;
    }

    public List<TcpWorker> getTcpWorkerList() {
        return tcpWorkerList;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public Config getConfig() {
        return config;
    }

    public INameserverForChatserver getRootServer() {
        return rootServer;
    }

    public static void main(String[] args) throws Exception {
        ComponentFactory factory = new ComponentFactory();

        IChatserverCli chatsever = factory.createChatserver("Server", System.in, System.out);

        // Start Server
        new Thread((Runnable) chatsever).start();
    }
}
