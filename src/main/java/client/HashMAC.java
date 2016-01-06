package client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * Does hashing and base64 encoding of messages.
 * @author Stefan
 *
 */
public class HashMAC {
	private static final String algorithm = "HmacSHA256";
	private static final Charset charset = StandardCharsets.UTF_8;
	private Mac messageAuthentificationCode;
	
	/**
	 * Reads the secret key file and initializes the hashing algorithm.
	 * @param pathToKeyFile
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public HashMAC(String pathToKeyFile) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
		this(Files.readAllBytes(new File(pathToKeyFile).toPath()));
	}
	
	/**
	 * Reads the secret key file and initializes the hashing algorithm.
	 * @param secret
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 */
	public HashMAC(byte[] secret) throws InvalidKeyException, NoSuchAlgorithmException {
		messageAuthentificationCode = Mac.getInstance(algorithm);
		messageAuthentificationCode.init(new SecretKeySpec(secret, algorithm));
	}
	
	/**
	 * Creates and encodes the hash.
	 * @param msg the message to create the hash from
	 * @return hash in base64
	 */
	public String getEncodedHash(String msg) {
		return DatatypeConverter.printBase64Binary(messageAuthentificationCode.doFinal(msg.getBytes(charset)));
	}
	
}
