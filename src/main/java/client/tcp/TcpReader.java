package client.tcp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import cli.Shell;
import client.Client;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;
import util.Logger;
import util.Streams;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TcpReader implements Runnable {

	private final int WAITING_FOR_AUTHENTICATION = 0;
	private final int WAITING_FOR_FINAL_RESPONSE = 1;
	private final int AUTHENTICATED = 2;
	private final String B64 = "a-zA-Z0-9/+";

	private Socket socket;
	private Client client;
	private String response;
	private Config config;
	private Shell shell;

	private int status = WAITING_FOR_AUTHENTICATION;
	private byte[] clientChallenge;
	private SecretKey secretKey;
	private byte[] ivVector;

	private Logger logger = new Logger();
	private Object lock = new Object();

	public TcpReader(Client client, Socket socket, Config config, Shell shell) {
		this.client = client;
		this.socket = socket;
		this.config = config;
		this.shell = shell;
	}

	@Override
	public void run() {
		try(BufferedReader reader = Streams.getBufferedReader(socket);) {
			String tmp = reader.readLine();
			while (client.isActive()) {
				synchronized (lock) {
					while(status != AUTHENTICATED) {
						if(status == WAITING_FOR_AUTHENTICATION) {
							response = decipherMessage(tmp);
						}else{
							response = decodeMessage(tmp);
						}
						tmp = reader.readLine();
					}
					String decodedTmp = decodeMessage(tmp);
					if (decodedTmp.startsWith("!public")) {
						client.setLastMsg(decodedTmp.substring(8).trim());
						shell.writeLine(decodedTmp.substring(8).trim());
					} else {
						if(response.startsWith("Successfully logged out")) setStatus(WAITING_FOR_AUTHENTICATION);
						response = decodedTmp.trim();
					}
				}
				tmp = reader.readLine();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	public String getResponse() {
		return response;
	}

	public void clearResponse() {
		response = "";
	}

	public Object getLock() {
		return lock;
	}

	public void setChallenge(byte[] challenge) { clientChallenge = challenge; }

	private String decipherMessage(String msg) {

		if(msg.startsWith("!Error")) return msg.substring(1);

		// decode challenge from Base64 format
		byte[] base64Message = Base64.decode(msg.getBytes(Charset.forName("UTF-8")));

		// decrypt message with client's private key
		Cipher cipher = null;
		byte[] decryptedMessage = null;
		String clientKeyFilepath = config.getString("keys.dir")+"\\"+client.getUserName()+".pem";

		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(clientKeyFilepath)));
			decryptedMessage = cipher.doFinal(base64Message);
		} catch (Exception e) {
			logger.error(e.getClass() + ": " + e.getMessage());
			return "Error: No valid private key for this user exists. Authentication failed.";
		}

		// decrypt every argument from Base64 format
		String str = new String(decryptedMessage, StandardCharsets.UTF_8);
		assert str.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}=="):"2nd  message";

		String[] message = str.split(" ");
		if(!message[1].equals(new String(clientChallenge, StandardCharsets.UTF_8))) {
			return "Can't assure safe connection to server. Authentication failed.";
		}

		String serverChallenge = message[2];
		byte[] secretKey = Base64.decode(message[3].getBytes(Charset.forName("UTF-8")));
		byte[] ivVector = Base64.decode(message[4].getBytes(Charset.forName("UTF-8")));

		setIvVector(ivVector);
		setSecretKey(new SecretKeySpec(secretKey, "AES"));

		return "!"+serverChallenge;
	}

	private String decodeMessage(String msg) {

		byte[] message = Base64.decode(msg);

		// decrypt message with shared secret key
		Cipher cipher = null;
		byte[] decryptedMessage = null;
		try {
			cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey,new IvParameterSpec(ivVector));
			decryptedMessage = cipher.doFinal(message);
		} catch (Exception e) {
			logger.error(e.getClass() + ": " + e.getMessage());
			return "Error during handshake for authentification";
		}

		return new String(decryptedMessage, StandardCharsets.UTF_8);
	}

	private void setIvVector(byte[] ivVector) {
		this.ivVector = ivVector;
		client.setIvVector(ivVector);
	}

	private void setSecretKey(SecretKey secretKey) {
		this.secretKey = secretKey;
		client.setSecretKey(secretKey);
	}

	public void setStatus(int status) { this.status = status; }
	public int getStatus() { return status; }
}
