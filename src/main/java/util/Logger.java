package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

//	private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
//		@Override
//		protected DateFormat initialValue() {
//			return new SimpleDateFormat("HH:mm:ss.SSS");
//		}
//	};  ----> Sorry aber das macht wenig Sinn für jeden Thread ein eigenes statisches dateformat zu haben
//				da kannst du gleich für jedes Logger Objekt ein eigenes DateFormat instanzieren und dir den Overhead sparen
	
	private static final boolean isDebug = false;
	private final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
	private final String MSG_FORMAT = "%s\t\t%s: %s";
	
	private String format(String level, String msg) {
		return String.format(MSG_FORMAT, DATE_FORMAT.format(new Date()), level, msg);
	}

	public void error(String msg) {
		System.err.println(format("Error", msg));
	}

	public void info(String msg) {
		System.out.println(format("Info", msg));
	}
	
	public void debug(String msg) {
		if (isDebug) System.out.println(format("Debug", msg));
	}
	
	public void exception(Exception e) {
		System.err.println(format("Exception", e.getClass().getSimpleName()+" - "+e.getMessage()));
		e.printStackTrace();
	}

}
