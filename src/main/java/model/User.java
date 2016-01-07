package model;

import java.net.InetAddress;
import java.net.Socket;
import java.security.Key;

public class User {

    private String userName;
    private String password;
    private Key secretKey;
    private byte[] ivVector;
    private boolean online;

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

    public String toString() {
        //return String.format("%1$-7s %2$-10s %3$s%4$d\n", userName, onlineStatus()); -> throws an error!!!
        return userName + " " + onlineStatus();
    }

    public void setSocket(Socket client) {
        this.client = client;
    }

    public Socket getSocket() {
        return this.client;
    }

    public Key getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(Key secretKey) {
        this.secretKey = secretKey;
    }

    public byte[] getIvVector() {
        return ivVector;
    }

    public void setIvVector(byte[] ivVector) {
        this.ivVector = ivVector;
    }
}
