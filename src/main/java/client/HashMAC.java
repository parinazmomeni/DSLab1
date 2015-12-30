package client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class HashMAC {
	private static final String algorithm = "HmacSHA256";
	private static final Charset charset = Charset.forName("UTF-8");
	private Mac messageAuthentificationCode;
	
	public HashMAC(String pathToKeyFile) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
		messageAuthentificationCode = Mac.getInstance(algorithm);
		messageAuthentificationCode.init(new SecretKeySpec(getSecretKey(pathToKeyFile), algorithm));
	}
	
	private byte[] getSecretKey(String pathToKeyFile) throws IOException{
		return Files.readAllBytes(new File(pathToKeyFile).toPath());
	}
	
	public String getEncodedHash(String msg) {
		return DatatypeConverter.printBase64Binary(messageAuthentificationCode.doFinal(msg.getBytes(charset)));
	}
	
}
