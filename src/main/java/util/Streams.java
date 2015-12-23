package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;

public class Streams {

	public static BufferedReader getBufferedReader(InputStream in){
		return new BufferedReader(new InputStreamReader(in));
	}
	
	public static BufferedReader getBufferedReader(Socket s) throws IOException{
		return getBufferedReader(s.getInputStream());
	}
		
	public static BufferedWriter getBufferedWriter(OutputStream out) {
		return new BufferedWriter(new OutputStreamWriter(out, Charset.defaultCharset()));
	}
	
	public static BufferedWriter getBufferedWriter(Socket s) throws IOException {
		return getBufferedWriter(s.getOutputStream());
	}
	
	public static PrintWriter getPrintWriter(OutputStream out) {
		return getPrintWriter(out, true);
	}
	
	public static PrintWriter getPrintWriter(OutputStream out, boolean append) {
		return new PrintWriter(out, append);
	}
	
	public static PrintWriter getPrintWriter(Socket s) throws IOException {
		return getPrintWriter(s.getOutputStream());
	}
	
	public static PrintWriter getPrintWriter(Socket s, boolean append) throws IOException {
		return getPrintWriter(s.getOutputStream(), append);
	}
	
}
