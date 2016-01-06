package util;

import model.KeyInformations;
import model.User;
import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Created by aigi on 04.01.2016.
 */
public class SecurityTool {

    private Logger logger = new Logger();

    private PrintWriter out;
    private KeyInformations keyPaths;

    public SecurityTool(PrintWriter out, KeyInformations keyPaths) {
        this.keyPaths = keyPaths;
        this.out = out;
    }

    public String decode(String msg, String algorithm) {
        switch (algorithm) {
            case "RSA":
                return decodeRSA(msg);
            case "AES":
                return decodeAES(msg);
            default:
                return null;
        }
    }

    public String encode(String msg, String algorithm) {
        switch (algorithm) {
            case "RSA":
                return encodeRSA(msg);
            case "AES":
                return encodeAES(msg);
            default:
                return null;
        }
    }

    /**
     * This method first decodes a Base64-String and decodes the bytes further
     * via RSA algorithm and the use of the private key, saved in the keyPaths
     * @param msg the message to decode
     * @return a decoded UTF-8 String
     */
    private String decodeRSA(String msg) {

        // decode challenge from Base64 format
        byte[] message = Base64.decode(msg);

        // decrypt message with private key
        byte[] decodedMessage = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(keyPaths.getPrivateKeyPath())));
            decodedMessage = cipher.doFinal(message);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "!Error: No valid private key for this user exists. Authentication failed.";
        }

        return new String(decodedMessage, StandardCharsets.UTF_8);
    }

    /**
     * This method first decodes a Base64-String and decodes the bytes further
     * via AES algorithm and the use of the shared key, saved in the keyPaths
     * @param msg the message to decode
     * @return a decoded UTF-8 String
     */
    private String decodeAES(String msg) {

        // decode challenge from Base64 format
        byte[] message = Base64.decode(msg);

        // decrypt message with shared secret key
        byte[] decodedMessage = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyPaths.getSecretKey(),new IvParameterSpec(keyPaths.getIvVector()));
            decodedMessage = cipher.doFinal(message);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "!Error during decoding message";
        }

        return new String(decodedMessage, StandardCharsets.UTF_8);
    }

    public void setKeyPaths(KeyInformations keyPaths) {
        this.keyPaths = keyPaths;
    }

    public void setIvVector(byte[] ivVector) {
        this.keyPaths.setIvVector(ivVector);
    }

    public void setSecretKey(SecretKey secretKey) {
        this.keyPaths.setSecretKey(secretKey);
    }

    /**
     * This method first encodes the given message via AES algorithm
     * and the shared key and ivVector, saved in keyPaths. After that it
     * encodes the bytes in a Base64 String and returns it
     * @param msg the message to encode
     * @return an encoded UTF-8 String
     */
    private String encodeAES(String msg) {

        // initialize AES cipher with shared secret key and encode full message
        byte[] encryptedMessage = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyPaths.getSecretKey(), new IvParameterSpec(keyPaths.getIvVector()));
            encryptedMessage = cipher.doFinal(msg.getBytes(Charset.forName("UTF-8")));
        }catch (Exception e) {
            logger.error(e.getMessage());
            return "!Error during encoding message";
        }

        // encode in Base64 format
        byte[] base64encryptedMessage = Base64.encode(encryptedMessage);

        return new String(base64encryptedMessage,StandardCharsets.UTF_8);
    }

    /**
     * This method first encodes the given message via RSA algorithm
     * and the public key saved in keyPaths. After that it
     * encodes the bytes in a Base64 String and returns it
     * @param msg the message to encode
     * @return an encoded UTF-8 String
     */
    private String encodeRSA(String msg) {

        // initialize RSA cipher with public key and encode full message
        byte[] encryptedMessage = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(keyPaths.getPublicKeyPath())));
            encryptedMessage = cipher.doFinal(msg.getBytes(Charset.forName("UTF-8")));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "Error during authentication";
        }

        // encode in Base64 format
        byte[] base64encryptedMessage = Base64.encode(encryptedMessage);

        return new String(base64encryptedMessage,StandardCharsets.UTF_8);
    }

    /**
     * Writes an AES-encoded message to a Stream
     * @param msg the message to encode and send
     */
    public void println(String msg) {
        out.println(encode(msg, "AES"));
    }

    /**
     * Writes an RSA-encoded message to a Stream
     * @param msg the message to encode and send
     */
    public void printlnRSA(String msg) {
        out.println(encode(msg, "RSA"));
    }

    /**
     * Encodes and sends a message via AES-information, saved in the
     * given user, with the given PrintWriter to the user
     * @param writer the PrintWriter to use to write the encoded message
     * @param msg the message to encode and send
     * @param user the user to receive the message
     */
    public void printlnTo(PrintWriter writer, String msg, User user) {

        // initialize RSA cipher with public key and encode full message
        byte[] encryptedMessage = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, user.getSecretKey(), new IvParameterSpec(user.getIvVector()));
            encryptedMessage = cipher.doFinal(msg.getBytes(Charset.forName("UTF-8")));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        // encode in Base64 format
        byte[] base64encryptedMessage = Base64.encode(encryptedMessage);
        writer.println(new String(base64encryptedMessage, StandardCharsets.UTF_8));
    }
}

