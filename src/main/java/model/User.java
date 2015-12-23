package model;

import java.net.InetAddress;
import java.net.Socket;

public class User {

    private String userName;
    private String password;
    private boolean online;

    private InetAddress address;
    private int port;
    private Socket client;

    public User(String userName, String password) {
        this.userName = userName;
        this.password = password;
        this.online = false;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String onlineStatus() {
        if (isOnline()) {
            return "online";
        } else {
            return "offline";
        }
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String toString() {
        return String.format("%1$-7s %2$-10s %3$s%4$d\n", userName, onlineStatus());
    }

    public void setSocket(Socket client) {
        this.client = client;
    }

    public Socket getSocket() {
        return this.client;
    }
}
