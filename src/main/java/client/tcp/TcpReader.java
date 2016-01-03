package client.tcp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;

import client.Client;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;
import util.Logger;
import util.Streams;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class TcpReader implements Runnable {

	private final int WAITING_FOR_AUTHORIZATION = 0;
	private final int AUTHORIZED = 1;
	private final String B64 = "a-zA-Z0-9/+";

	private Socket socket;
	private Client client;
	private String response;
	private Config config;

	private int status = WAITING_FOR_AUTHORIZATION;
	private byte[] clientChallenge;
	private SecretKey secretKey;
	private byte[] ivVector;

	private Logger logger = new Logger();
	private Object lock = new Object();

	public TcpReader(Client client, Socket socket, Config config) {
		this.client = client;
		this.socket = socket;
		this.config = config;
	}

	@Override
	public void run() {
		try(BufferedReader reader = Streams.getBufferedReader(socket);) {
			String tmp = reader.readLine();
			while (client.isActive() && tmp != null) {
				synchronized (lock) {
					if (status == AUTHORIZED) {
						if (tmp.startsWith("!public")) {
							client.setLastMsg(tmp.substring(8).trim());
							response = tmp.substring(8).trim();
						} else {
							response = tmp.trim();
						}
					}else{
						response = decipherMessage(tmp.getBytes(Charset.forName("UTF-8")));
					}
					logger.info(response);
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

	private String decipherMessage(byte[] msg) {

		// decode challenge from Base64 format
		byte[] base64Message = Base64.decode(msg);

		// decrypt message with client's private key
		Cipher cipher = null;
		byte[] decryptedMessage = null;
		String clientKeyFilepath = config.getString("key.dir")+"\\"+client.getUserName()+".pem";
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(clientKeyFilepath)));
			decryptedMessage = cipher.doFinal(base64Message);
		} catch (Exception e) {
			return "No valid private key for this user exists. Authentication failed.";
		}

		// decrypt every argument from Base64 format
		String str = new String(decryptedMessage, StandardCharsets.UTF_8);
		assert str.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}=="):"2nd  message";

		String[] message = str.split(" ");
		if(message[1].getBytes(Charset.forName("UTF-8")) != clientChallenge) {
			return "Can't assure safe connection to server. Authentication failed.";
		}

		String serverChallenge = message[2];
		byte[] secretKey = Base64.decode(message[3].getBytes(Charset.forName("UTF-8")));
		byte[] ivVector = Base64.decode(message[4].getBytes(Charset.forName("UTF-8")));

		setIvVector(ivVector);
		setSecretKey(new SecretKeySpec(secretKey, 0, secretKey.length, "AES"));

		return "!"+serverChallenge;
	}

	private String decodeMessage(String msg) {
		return null;
	}

	private void setIvVector(byte[] ivVector) {
		this.ivVector = ivVector;
		client.setIvVector(ivVector);
	}

	private void setSecretKey(SecretKey secretKey) {
		this.secretKey = secretKey;
		client.setSecretKey(secretKey);
	}
}
