package de.thiesgerken.fractals.cli;

import static java.lang.System.out;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLPlatform;

import de.thiesgerken.commandlineparser.Argument;
import de.thiesgerken.commandlineparser.Command;
import de.thiesgerken.commandlineparser.CommandLineParser;
import de.thiesgerken.commandlineparser.ParseException;
import de.thiesgerken.commandlineparser.ValueArgument;
import de.thiesgerken.fractals.buddhabrot.BuddhabrotCLI;
import de.thiesgerken.fractals.multibrot.MultibrotCLI;
import de.thiesgerken.fractals.newton.NewtonCLI;
import de.thiesgerken.fractals.util.palettes.Palette;

public class Main {
	private static Logger logger;
	private static CommandLineParser parser;

	private static Command multibrotCommand;
	private static Command buddhabrotCommand;
	private static Command newtonCommand;

	private static Command helpCommand;
	private static Command infoCommand;
	private static Command listPalettesCommand;

	private static ValueArgument<CLDevice> infoDeviceArgument;
	private static ValueArgument<CLPlatform> infoPlatformArgument;

	private static MultibrotCLI multibrotCLI;
	private static BuddhabrotCLI buddhabrotCLI;
	private static NewtonCLI newtonCLI;

	public static final String VERSION = "0.1";
	public static final String YEAR = "2013";
	public static final int CONSOLEWIDTH = 79;

	public static void main(String[] args) {
		logger = Logger.getLogger("");

		for (Handler h : logger.getHandlers())
			logger.removeHandler(h);

		logger.addHandler(new Handler() {
			@Override
			public void close() {
			}

			@Override
			public void flush() {
				out.flush();
			}

			@Override
			public void publish(LogRecord record) {
				out.println((new SimpleDateFormat("HH:mm:ss.SSS")).format(new Date(record.getMillis())) + " - " + record.getMessage());
			}

		});

		multibrotCLI = new MultibrotCLI();
		buddhabrotCLI = new BuddhabrotCLI();
		newtonCLI = new NewtonCLI();

		parser = new CommandLineParser();

		helpCommand = new Command("help", "displays help on how to use this application.");
		multibrotCommand = new Command("multibrot", "creates fractals similar to the popular mandelbrot fractal.");
		buddhabrotCommand = new Command("buddhabrot", "(Experimental) creates buddhabrot images.");
		newtonCommand = new Command("newton", "(Experimental) creates newton fractals.");

		infoCommand = new Command("clinfo", "displays information about the opencl runtime environment, including available opencl-capable devices.");
		listPalettesCommand = new Command("listpalettes", "displays a list of all built-in palettes.");

		initializeArguments();

		multibrotCLI.initializeArguments();
		buddhabrotCLI.initializeArguments();
		newtonCLI.initializeArguments();

		parser.putCommand(helpCommand, null);
		parser.putCommand(listPalettesCommand, null);
		parser.putCommand(infoCommand, new Argument[] { infoDeviceArgument, infoPlatformArgument });
	
		parser.putCommand(multibrotCommand, multibrotCLI.getArguments());
		parser.putCommand(buddhabrotCommand, buddhabrotCLI.getArguments());
		parser.putCommand(newtonCommand, newtonCLI.getArguments());
		
		try {
			parser.parse(args);
		} catch (ParseException e) {
			out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
			return;
		}

		if (helpCommand.wasParsed())
			printHelp();
		else if (infoCommand.wasParsed())
			printCLInfo();
		else if (listPalettesCommand.wasParsed())
			printPaletteList();
		else if (multibrotCommand.wasParsed())
			multibrotCLI.doStuff();
		else if (buddhabrotCommand.wasParsed())
			buddhabrotCLI.doStuff();
		else if (newtonCommand.wasParsed())
			newtonCLI.doStuff();
	}

	private static void initializeArguments() {
		infoDeviceArgument = new ValueArgument<CLDevice>("device", "", false,
				"prints all available information about a specific device using its id (e.g. '0.0' for the first device)") {

			@Override
			protected CLDevice convert(String value) throws ParseException {
				String[] splits = value.split("\\.");
				int plat, dev;

				try {
					if (splits.length != 2)
						throw new Exception();

					plat = Integer.parseInt(splits[0]);
					dev = Integer.parseInt(splits[1]);

					if (plat < 0 || dev < 0)
						throw new Exception();
				} catch (Exception e) {
					throw new ParseException("value for argument --device must be in the form 'x.y', where x and y are positive integers.");
				}

				if (plat >= CLPlatform.listCLPlatforms().length)
					throw new ParseException("The requested platform with id '" + plat + "' does not exist.");

				if (dev >= CLPlatform.listCLPlatforms()[plat].listCLDevices().length)
					throw new ParseException("The requested device with id '" + plat + "." + dev + "' does not exist.");

				return CLPlatform.listCLPlatforms()[plat].listCLDevices()[dev];
			}
		};

		infoPlatformArgument = new ValueArgument<CLPlatform>("platform", "", false,
				"prints all available information about a specific platform using its id (e.g. '0' for the first platform)") {

			@Override
			protected CLPlatform convert(String value) throws ParseException {
				int plat;

				try {
					plat = Integer.parseInt(value);

					if (plat < 0)
						throw new Exception();
				} catch (Exception e) {
					throw new ParseException("value for argument --platform must be a non-negative integer.");
				}

				if (plat >= CLPlatform.listCLPlatforms().length)
					throw new ParseException("The requested platform with id '" + plat + "' does not exist.");

				return CLPlatform.listCLPlatforms()[plat];

			}
		};
	}

	private static void printCLInfo() {
		if (infoDeviceArgument.wasParsed() && infoPlatformArgument.wasParsed()) {
			out.println("Error: Using --device and --platform simultaneously is not possible.");
			return;
		}

		CLPlatform[] platforms = CLPlatform.listCLPlatforms();

		if (platforms.length == 0) {
			out.println("Error: No OpenCL platforms detected.");
			return;
		}

		if (infoDeviceArgument.wasParsed()) {
			out.println(CommandLineParser.printTable(infoDeviceArgument.getValue().getProperties(), CONSOLEWIDTH, 8, 3));
		} else if (infoPlatformArgument.wasParsed()) {
			out.println(CommandLineParser.printTable(infoPlatformArgument.getValue().getProperties(), CONSOLEWIDTH, 8, 2));
		} else {
			out.println("Available OpenCL platforms and devices:\n");

			for (int i = 0; i < platforms.length; i++) {
				if (i != 0)
					out.println();

				CLPlatform platform = platforms[i];

				out.println(" Platform " + i + ": " + platform.getName());

				CLDevice[] devices = platform.listCLDevices();

				for (int j = 0; j < devices.length; j++) {
					CLDevice device = devices[j];

					out.println("  |- Device " + i + "." + j + ": " + device.getName());

					TreeMap<String, String> data = new TreeMap<String, String>();

					data.put("|- Device Type    :", device.getProperties().get("CL_DEVICE_TYPE"));
					data.put("|- Driver version :", device.getDriverVersion());
					data.put("|- OpenCL version :", device.getCVersion().major + "." + device.getCVersion().minor);
					data.put("|- Double support :", device.isDoubleFPAvailable() ? "Yes" : "No");

					out.println(CommandLineParser.printTable(data, CONSOLEWIDTH, 8, 6));
				}
			}
		}
	}

	private static void printHelp() {
		out.println("JFractals " + VERSION + " (C) " + YEAR + " Thies Gerken <tgerken@math.uni-bremen.de>");
		out.println("Usage: jfractals (command) [arguments].\n\nAvailable commands:");
		out.println(parser.listCommands(CONSOLEWIDTH));

		for (Command cmd : new Command[] { null, helpCommand, multibrotCommand, buddhabrotCommand, newtonCommand, infoCommand }) {
			if (parser.getArguments(cmd).size() == 0)
				continue;

			if (cmd == null)
				out.println("\nGeneral arguments:");
			else
				out.println("\nAvailable arguments when using command '" + cmd.getName() + "':");

			out.println(parser.listArguments(CONSOLEWIDTH, cmd));
		}
	}

	private static void printPaletteList() {
		try {
			out.println("Info: cyclic palettes start and end with the same color, so that they can be cycled multiple times (via --pcycles) and phased (via --pphase). Normal palettes are rather colorful and dark palettes start with black. Note that the palette 'grey' (cyclic) can be loaded faster than the others, since it is generated from code and not loaded from an embedded image file.");
			out.println();
			out.println("   Name " + "   |   " + " Length");
			out.println("-----------|-------------");

			for (String palName : Palette.listPaletteNames()) {
				out.print(" " + palName);

				for (int i = 0; i < 10 - palName.length(); i++)
					out.print(" ");

				out.println("|     " + (new Palette(palName)).getLength());
			}
		} catch (Exception e) {
			out.println("Error loading palettes.");
			return;
		}
	}

}