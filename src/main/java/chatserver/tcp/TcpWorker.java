package chatserver.tcp;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import chatserver.Chatserver;
import model.KeyInformations;
import model.User;
import nameserver.INameserver;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import org.bouncycastle.util.encoders.Base64;
import util.*;

import javax.crypto.KeyGenerator;

public class TcpWorker implements Runnable {
    private Chatserver chatServer;
    private Socket client;
    private Config config;
    private User currentUser;
    private String username;

    private SecurityTool security;
    private KeyInformations keyPaths;

    private final int WAITING_FOR_AUTHENTICATION = 0;
    private final int WAITING_FOR_CLIENTS_PROOF = 1;
    private final int AUTHENTICATED = 2;
    private final String B64 = "a-zA-Z0-9/+";
    private int status = WAITING_FOR_AUTHENTICATION;

    private String serverChallenge;
    private String clientChallenge;

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

            initiateSecurity(out);

            String command = bufferedReader.readLine();
            while (chatServer.isOnline() && command != null) {
                if (status == WAITING_FOR_AUTHENTICATION) {
                    authenticate(command, out);
                } else if (status == WAITING_FOR_CLIENTS_PROOF) {
                    proofClient(command);
                } else {
                    command = security.decode(command, "AES");
                    if (command.startsWith("!authenticate")) {
                        authenticate(command, out);
                    } else if (command.startsWith("!logout")) {
                        logout();
                    } else if (command.startsWith("!send")) {
                        send(command);
                    } else if (command.startsWith("!lookup")) {
                        lookup(command);
                    } else if (command.startsWith("!register")) {
                        register(command);
                    } else {
                        error(command, out);
                    }
                }

                command = bufferedReader.readLine();
            }
        } catch (Exception e) {
            logger.exception(e);

        } finally {
            try {
                client.close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }

            if (currentUser != null) {
                synchronized (currentUser) {
                    currentUser.setOnline(false);
                    currentUser.setSocket(null);
                }
                currentUser = null;
            }
        }

        logger.info("Ended communication with client " + (username == null ? "" : username));
    }

    private void send(String command) throws IOException {

        // Check if user is logged in.
        if (currentUser == null) {
            security.println("Log in the user. You are not logged in.");
            return;
        }

        String[] words = command.split(" +");
        if (words.length < 2) {
            security.println("Wrong command: incorrect number of arguments.");
            return;
        }

        String firstPartOfMessage = words[1];

        int index = command.indexOf(firstPartOfMessage);
        String message = command.substring(index);

        synchronized (chatServer.getUsers()) {
            if (chatServer.getUsers().size() == 1) {
                security.println("There is no other user online.");
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
                    security.printlnTo(writer, ("!public " + currentUser.getUserName() + ": " + message), u);
                    messageSent = true;
                }
            }

            if (!messageSent) {
                security.println("There is no other user online.");
            }
        }

    }

    private void register(String command) {

        String[] words = command.split(" +");
        if (words.length != 2) {
            security.println("Wrong command: incorrect number of arguments.");
            return;
        }

        String[] addressPort = words[1].split(":");
        if (addressPort.length != 2) {
            security.println("Wrong command: incorrect address format.");
            return;
        }

        String address = addressPort[0];
        String port = addressPort[1];

        try {
            InetAddress.getByName(address);
        } catch (UnknownHostException ex) {
            security.println("IP address: " + address + " is unknown.");
            return;
        }

        try {
            Integer.parseInt(port);
        } catch (NumberFormatException ex) {
            security.println("Port: " + port + " is not a number.");
            return;
        }

        try {
            chatServer.getRootServer().registerUser(currentUser.getUserName(), words[1]);
        } catch (RemoteException ex) {
            security.println("Could not register address due to communication error with nameserver");
            return;
        } catch (AlreadyRegisteredException ex) {
            security.println("User has already registered an address");
            return;
        } catch (InvalidDomainException ex) {
            security.println("Username does not contain valid zone information");
            return;
        }

        security.println("Successfully registered address for " + currentUser.getUserName() + ".");
    }

    private void error(String command, PrintWriter out) {
        security.println(command + " UNKNOWN COMMAND.");
    }

    private void lookup(String command) {

        String[] words = command.split(" +");
        if (words.length != 2) {
            security.println("Wrong command: incorrect number of arguments.");
            return;
        }

        String otherUser = words[1];

        if (!otherUser.matches("([a-zA-Z]|\\.)+")) {
            security.println("Wrong command: Username does not contain valid zone information.");
            return;
        }

        String[] tokens = otherUser.split("\\.");

        // Username must consist of name + one zone at least e.g. alice.at
        if (tokens.length < 2) {
            security.println("Wrong command: Username does not contain valid zone information.");
            return;
        }

        INameserverForChatserver server = chatServer.getRootServer();
        for (int i = tokens.length - 1; i > 0; i--) {

            try {
                server = server.getNameserver(tokens[i]);
            } catch (RemoteException ex) {
                security.println("Could not lookup address of user due to communication error with nameserver");
                return;
            }

            if (server == null) {
                security.println("Wrong command: Username does not contain valid zone information");
                return;
            }
        }

        String address = null;
        try {
            address = server.lookup(tokens[0]);

            if (address == null) {
                security.println(otherUser + ": Wrong username or user not reachable.");
                return;
            }

        } catch (RemoteException ex) {
            security.println("Could not lookup address of user due to communication error with nameserver");
            return;
        }

        security.println(address);

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

    private void logout() {

        // Check if user is logged in.
        if (currentUser == null) {
            security.println("Log in the user. You are not logged in.");
            return;
        }

        synchronized (currentUser) {
            currentUser.setOnline(false);
            currentUser.setSocket(null);
        }

        currentUser = null;
        status = WAITING_FOR_AUTHENTICATION;
        logger.info(username + " logged out.");

        security.println("Successfully logged out.");
    }

    public void shutDown() {
        try {
            client.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void authenticate(String command, PrintWriter out) {

        if (status == AUTHENTICATED) {
            security.println("Already logged in.");
            return;
        }

        // decrypt every argument from Base64 format
        String str;
        try {
            str = security.decode(command, "RSA");
        } catch (Exception e) {
            logger.exception(e);
            out.println("!Error during authentication.");
            return;
        }

        assert str.matches("!authenticate [\\w\\.]+[" + B64 + "]{43}=") : "1st  message";
        String[] message = str.split(" ");
        clientChallenge = message[2];

        // error handling
        if (!chatServer.getUsers().containsKey(message[1])) {
            logger.error("Wrong username: " + message[1] + "; Message: " + str);
            out.println("!Error: Wrong username.");
            return;
        }

        if ((chatServer.getUsers().get(message[1])).isOnline()) {
            logger.error("Username already in use.");
            out.println("!Error: This user is in use somewhere else.");
            return;
        }

        username = message[1];
        keyPaths.setPublicKeyPath(config.getString("keys.dir") + File.separator + username + ".pub.pem");

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
        Key key = generator.generateKey();
        keyPaths.setSecretKey(key);

        // generate IV vector
        byte[] ivVector = new byte[16];
        secureRandom.nextBytes(ivVector);
        keyPaths.setIvVector(ivVector);

        // encode everything into Base64 format
        byte[] base64ServerChallenge = Base64.encode(challengeServer);
        byte[] base64SecretKey = Base64.encode(key.getEncoded());
        byte[] base64IVVector = Base64.encode(ivVector);

        serverChallenge = new String(base64ServerChallenge, StandardCharsets.UTF_8);

        // generate full message and send
        String fullMessage = "!ok " + clientChallenge + " " +
                new String(base64ServerChallenge, StandardCharsets.UTF_8) + " " +
                new String(base64SecretKey, StandardCharsets.UTF_8) + " " +
                new String(base64IVVector, StandardCharsets.UTF_8);
        security.printlnRSA(fullMessage);

        logger.info("Sent challenge for handshake to " + username);
        status = WAITING_FOR_CLIENTS_PROOF;
        return;
    }

    private void proofClient(String command) {

        if (command.startsWith("!Error")) {
            logger.error("Authentication error. Server couldn't decode client's challenge.");
            status = WAITING_FOR_AUTHENTICATION;
            return;
        }

        // read response and compare to original server challenge
        String response;
        try {
            response = security.decode(command, "AES");
        } catch (Exception e) {
            logger.exception(e);
            return;
        }

        if (!serverChallenge.equals(response)) {
            logger.error("Authentication error. Client couldn't decode server's challenge.");
            security.println("!Error during authentication.");
            status = WAITING_FOR_AUTHENTICATION;
            return;
        }

        // save user information
        User user = chatServer.getUsers().get(username);
        synchronized (user) {
            currentUser = user;
            currentUser.setOnline(true);
            currentUser.setSocket(client);
            currentUser.setSecretKey(keyPaths.getSecretKey());
            currentUser.setIvVector(keyPaths.getIvVector());

            status = AUTHENTICATED;
        }

        logger.info(username + " successfully authenticated");
        security.println("Successfully logged in.");
        return;
    }

    private void initiateSecurity(PrintWriter out) {
        keyPaths = new KeyInformations(config.getString("key"));
        security = new SecurityTool(out, keyPaths);
    }
}