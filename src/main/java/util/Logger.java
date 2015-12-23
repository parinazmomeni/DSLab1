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
	
	private final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

	public void error(String msg) {
		System.out.println(String.format("%s\t\t%s%s", DATE_FORMAT.format(new Date()), "Error: ", msg));
	}

	public void info(String msg) {
		System.out.println(String.format("%s\t\t%s%s", DATE_FORMAT.format(new Date()), "INFO: ", msg));
	}

}
