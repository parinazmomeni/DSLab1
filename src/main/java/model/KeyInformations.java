package model;

import java.security.Key;

/**
 * Created by aigi on 05.01.2016.
 */
public class KeyInformations {

    private Key secretKey;
    private byte[] ivVector;
    private String privateKeyPath;
    private String publicKeyPath;

    public KeyInformations(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public KeyInformations(String privateKeyPath, String publicKeyPath) {
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
    }

    public Key getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(Key secretKey) {
        this.secretKey = secretKey;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public byte[] getIvVector() {
        return ivVector;
    }

    public void setIvVector(byte[] ivVector) {
        this.ivVector = ivVector;
    }
}
