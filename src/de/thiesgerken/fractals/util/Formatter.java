package de.thiesgerken.fractals.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Formatter {

	public static String formatSize(long size) {
		if (size >= 1024 * 1024 * 1024)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) size / (1024 * 1024 * 1024)) + "GiB";
		else if (size >= 1024 * 1024)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) size / (1024 * 1024)) + "MiB";
		else if (size >= 1024)
			return new DecimalFormat("#", new DecimalFormatSymbols(Locale.ROOT)).format((double) size / 1024) + "KiB";
		else
			return size + "B";
	}

	public static String formatInt(int value) {
		if (value >= 1000000000)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / (1000000000)) + "G";
		else if (value >= 1000000)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / (1000000)) + "M";
		else if (value >= 1000)
			return new DecimalFormat("#", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / 1000) + "k";
		else
			return Integer.toString(value);
	}
	
	public static String formatIntBase2(int value) {
		if (value >= 1024*1024*1024)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / (1024*1024*1024)) + "G";
		else if (value >= 1024*1024)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / (1024*1024)) + "M";
		else if (value >= 1024)
			return new DecimalFormat("#", new DecimalFormatSymbols(Locale.ROOT)).format((double) value / 1024) + "k";
		else
			return Integer.toString(value);
	}
	public static String formatTime(long time) {
		if (time >= 1E9)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format(time / 1E9) + "s";
		else if (time >= 1E6)
			return new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT)).format(time / 1E6) + "ms";
		else if (time >= 1E3)
			return new DecimalFormat("#", new DecimalFormatSymbols(Locale.ROOT)).format(time / 1E3) + "µs";
		else
			return time + "ns";
	}

	public static int parseInt(String val) throws NumberFormatException {
		double factor = 1;

		if (val.endsWith("K") || val.endsWith("k")) {
			val = val.substring(0, val.length() - 1);
			factor = 1024;
		} else if (val.endsWith("M") || val.endsWith("m")) {
			val = val.substring(0, val.length() - 1);
			factor = 1024 * 1024;
		}

		return (int) (factor * Double.parseDouble(val));
	}

	public static Size parseSize(String val) throws Exception {
		String[] splits = val.split("x");
		int w, h;

		if (splits.length != 2)
			throw new Exception();

		w = parseInt(splits[0]);
		h = parseInt(splits[1]);

		if (w <= 0 || h <= 0)
			throw new Exception();

		return new Size(w, h);
	}
}
