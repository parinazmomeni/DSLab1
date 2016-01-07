package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;
import util.Logger;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, INameserver, Runnable {

    private String componentName;
    private Config config;

    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;
    private ExecutorService threadPool;
    private Registry registry;
    private Logger logger;

    private String registryHost;
    private int registryPort;
    private String rootID;
    private String domain;

    private boolean isRoot;

    private static final String VALID_DOMAIN = "([a-zA-Z]|\\.)+";

    private Map<String, INameserver> servers;
    private Map<String, String> addresses;

    /**
     * @param componentName the name of the component - represented in the prompt
     * @param config the configuration to use
     * @param userRequestStream the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Nameserver(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        registryHost = this.config.getString("registry.host");
        registryPort = this.config.getInt("registry.port");
        rootID = this.config.getString("root_id");

        isRoot = false;
        try {
            domain = this.config.getString("domain");
        } catch (MissingResourceException ex) {
            isRoot = true;
        }

        shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
        shell.register(this);

        logger = new Logger();
        threadPool = Executors.newCachedThreadPool();

        servers = Collections.synchronizedMap(new TreeMap<String, INameserver>());
        addresses = Collections.synchronizedMap(new TreeMap<String, String>());
    }

    @Override
    public void run() {

        if (isRoot) {
            try {
                // Create and export the registry instance on localhost at the
                // specified port
                LocateRegistry.createRegistry(registryPort);

                // Get reference to created registry
                registry = LocateRegistry.getRegistry(registryHost, registryPort);

                // Create a stub for this root nameserver
                INameserver stub = (INameserver) UnicastRemoteObject.exportObject(this, 0);

                // Bind stub to the name of the root nameserver
                registry.rebind(rootID, stub);

            } catch (RemoteException ex) {
                logger.error(ex.getMessage());
                return;
            }

        } else {
            try {
                // Get reference to registry
                Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

                // Lookup root namerserver
                INameserver rootServer = (INameserver) registry.lookup(rootID);

                // Create a stub for this nameserver
                INameserver stub = (INameserver) UnicastRemoteObject.exportObject(this, 0);

                // Register new nameserver
                rootServer.registerNameserver(domain, stub, stub);

            } catch (RemoteException ex) {
                logger.error(ex.getMessage());
                return;
            } catch (NotBoundException ex) {
                logger.error(ex.getMessage());
                return;
            } catch (AlreadyRegisteredException ex) {
                logger.error(ex.getMessage());
                return;
            } catch (InvalidDomainException ex) {
                logger.error(ex.getMessage());
                return;
            }
        }

        threadPool.execute(shell);
        threadPool.shutdown();

    }

    @Override
    @Command
    public String nameservers() throws IOException {
        StringBuffer buffer = new StringBuffer();

        synchronized (servers) {
            Set<String> keys = servers.keySet();

            if (keys.isEmpty()) {
                buffer.append("No Nameservers");
            } else {
                for (String s : keys) {
                    buffer.append(s).append("\n");
                }
            }
        }

        return buffer.toString();
    }

    @Override
    @Command
    public String addresses() throws IOException {
        StringBuffer buffer = new StringBuffer();

        synchronized (addresses) {
            if (addresses.isEmpty()) {
                buffer.append("No addresses");
            } else {
                for (String key : addresses.keySet()) {
                    buffer.append(key).append(" ").append(addresses.get(key)).append("\n");
                }
            }
        }

        return buffer.toString();
    }

    @Override
    @Command
    public String exit() throws IOException {

        shell.close();

        if (isRoot) {
            try {
                // Unbind stub
                registry.unbind(rootID);
            } catch (Exception ex) {
                logger.error("Error while unbinding stub: " + ex.getMessage());
            }
        }

        try {
            // Unexport remote object
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ex) {
            logger.error("Error while unexporting object: " + ex.getMessage());
        }

        return "Shutting down the nameserver";
    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        checkDomain(username);

        String[] tokens = username.split("\\.");

        // Register user in this zone
        if (tokens.length == 1) {
            synchronized (addresses) {
                if (addresses.containsKey(username.toLowerCase())) {
                    throw new AlreadyRegisteredException("The username: " + username + " has been already registered in this zone: " + domain);
                } else {
                    addresses.put(username.toLowerCase(), address);
                    logger.info("Registering user: " + username.toLowerCase() + " in zone: " + domain);
                }
            }
        }

        // Forward user to next zone
        if (tokens.length > 1) {
            String forwardZone = tokens[tokens.length - 1];

            INameserver server = servers.get(forwardZone.toLowerCase());
            if (server == null) {
                throw new InvalidDomainException("Can't forward user to requested zone: " + forwardZone);
            }

            int indexOfForwardZone = username.lastIndexOf(forwardZone);

            server.registerUser(username.substring(0, indexOfForwardZone - 1), address);
        }

    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {

        if (zone == null || zone.isEmpty()) {
            return null;
        }

        logger.info("Chatserver requested nameserver for domain: " + domain);
        return servers.get(zone.toLowerCase());
    }

    @Override
    public String lookup(String username) throws RemoteException {

        logger.info(username + " requested by Chatserver");

        System.out.println(addresses.keySet().toString());

        if (username == null || username.isEmpty()) {
            return null;
        }

        return addresses.get(username.toLowerCase());
    }

    @Override
    public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        checkDomain(domain);

        String[] tokens = domain.split("\\.");

        // Register sub-domain
        if (tokens.length == 1) {
            synchronized (servers) {
                if (servers.containsKey(domain.toLowerCase())) {
                    throw new AlreadyRegisteredException("The domain: " + domain + " has been already registered by a nameserver");
                } else {
                    servers.put(domain.toLowerCase(), nameserver);
                    logger.info("Registering nameserver for zone: " + domain.toLowerCase());
                }
            }
        }

        // Forward domain to next zone
        if (tokens.length > 1) {
            String forwardZone = tokens[tokens.length - 1];

            INameserver server = servers.get(forwardZone.toLowerCase());
            if (server == null) {
                throw new InvalidDomainException("Can't forward domain to requested zone: " + forwardZone);
            }

            int indexOfForwardZone = domain.lastIndexOf(forwardZone);

            server.registerNameserver(domain.substring(0, indexOfForwardZone - 1), nameserver, nameserverForChatserver);
        }
    }

    private void checkDomain(String domain) throws InvalidDomainException {
        if (domain == null || domain.isEmpty() || !domain.matches(VALID_DOMAIN)) {
            throw new InvalidDomainException("Domain name: " + domain + " is not valid");
        }
    }

    /**
     * @param args * the first argument is the name of the {@link Nameserver} component
     */
    public static void main(String[] args) {
        Nameserver nameserver = new Nameserver(args[0], new Config(args[0]), System.in, System.out);
        new Thread(nameserver).start();
    }
}