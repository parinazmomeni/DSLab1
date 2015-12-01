package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

	private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss.SSS");
		}
	};

	public void error(String msg) {
		String now = DATE_FORMAT.get().format(new Date());
		System.out.println(String.format("%s\t\t%s%s", now, "Error: ", msg));
	}

	public void info(String msg) {
		String now = DATE_FORMAT.get().format(new Date());
		System.out.println(String.format("%s\t\t%s%s", now, "INFO: ", msg));
	}

}
