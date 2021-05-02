package de.thiesgerken.fractals.newton;

import static java.lang.System.out;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import de.thiesgerken.commandlineparser.Argument;
import de.thiesgerken.commandlineparser.EnumArgument;
import de.thiesgerken.commandlineparser.ParseException;
import de.thiesgerken.commandlineparser.SwitchArgument;
import de.thiesgerken.commandlineparser.ValueArgument;
import de.thiesgerken.fractals.FractalCLI;
import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Rectangle;
import de.thiesgerken.fractals.util.Size;

public class NewtonCLI extends FractalCLI {
	private Logger logger = Logger.getLogger(NewtonCLI.class.getName());

	private EnumArgument formatArgument;
	private ValueArgument<String> outputArgument;
	private ValueArgument<String> functionArgument;
	private ValueArgument<String> derivativeArgument;
private ValueArgument<Integer> partSizeArgument;
	private ValueArgument<Integer> maxIterationsArgument;
	private ValueArgument<Double> epsilonArgument;
	private ValueArgument<Size> superSamplingArgument;
	private ValueArgument<Rectangle> areaArgument;
	private SwitchArgument saveCommandlineArgument;

	private boolean singleAreaMode;

	private static ArrayList<Rectangle> loadAreas() throws Exception {
		BufferedReader reader = null;
		ArrayList<Rectangle> areas = new ArrayList<Rectangle>();

		try {
			reader = new BufferedReader(new FileReader("areas.txt"));

			String line = reader.readLine();

			while (line != null) {
				if (!line.startsWith("#")) {
					String[] splits = line.split(", ");

					if (splits.length != 4)
						throw new Exception("Error at \"" + line + "\"");

					areas.add(new Rectangle(Double.parseDouble(splits[0]), Double.parseDouble(splits[1]), Double.parseDouble(splits[2]), Double
							.parseDouble(splits[3])));
				}

				line = reader.readLine();
			}
		} finally {
			if (reader != null)
				reader.close();
		}

		if (areas.size() == 0)
			throw new Exception("area file is empty.");

		return areas;
	}

	public static String generateArgumentString(Newton newton, String outputFile) {
		StringBuilder sb = new StringBuilder();

		sb.append("jfractals newton");

				sb.append(" -o \"" + outputFile + "\"");

		sb.append(" -s " + newton.getSize().toString());
		sb.append(" -i " + newton.getMaxIterations());
		sb.append(" -e " + newton.getEpsilon());
		sb.append(" -a \"" + newton.getArea().toString() + "\"");
		sb.append(" --supersampling " + newton.getSuperSampling().toString());
		sb.append(" --function \"" + newton.getFunction().toString() + "\"");
		sb.append(" --derivative \"" + newton.getDerivative().toString() + "\"");
		
		if (newton.use64bitFloats())
			sb.append(" --fp64");
	
		return sb.toString();
	}

	public void doStuff() {
		super.doStuff();

		Newton newton = new Newton();

		newton.setDevice(deviceArgument.getValue());
		newton.setUse64bitFloats(fp64Argument.wasParsed());

		if (sizeArgument.wasParsed())
			newton.setSize(sizeArgument.getValue());

		if (maxIterationsArgument.wasParsed())
			newton.setMaxIterations(maxIterationsArgument.getValue());

		if (epsilonArgument.wasParsed())
			newton.setEpsilon(epsilonArgument.getValue());

		if (superSamplingArgument.wasParsed())
			newton.setSuperSampling(superSamplingArgument.getValue());

		if ( functionArgument.wasParsed())
			newton.setFunction(functionArgument.getValue());
		
		if ( derivativeArgument.wasParsed())
			newton.setDerivative(derivativeArgument.getValue());
		
		if (areaArgument.wasParsed()) {
			newton.setArea(areaArgument.getValue());
			singleAreaMode = (areaArgument.getValue() != null);
		} else
			singleAreaMode = true;

		if (partSizeArgument.wasParsed())
			newton.setDesiredPartSize(partSizeArgument.getValue());

		String outputFormat;

		if (formatArgument.wasParsed())
			outputFormat = formatArgument.getValue();
		else {
			try {
				outputFormat = outputArgument.getValue().substring(outputArgument.getValue().lastIndexOf(".") + 1);
				formatArgument.parse(outputFormat);
			} catch (Exception ee) {
				out.println("Error: Could not infer a valid image format from the supplied output file name.");
				return;
			}
		}

		ArrayList<Rectangle> areas = new ArrayList<Rectangle>();

		try {
			if (singleAreaMode)
				areas.add(newton.getArea());
			else
				areas.addAll(loadAreas());
		} catch (Exception e) {
			out.println("Error: Could not load areas.");
			return;
		}

		try {
			logger.log(Level.INFO, "Command line arguments seem to be okay, begin rendering.");

			for (int areaIndex = 0; areaIndex < areas.size(); areaIndex++) {
				Rectangle area = areas.get(areaIndex);

				if (!singleAreaMode) {
					logger.log(Level.INFO, "Using Area '" + area + "'");
					newton.setArea(area);
				}

				logger.log(Level.INFO, "Creating image");

				BufferedImage image = newton.createImage();
				String filename = outputArgument.getValue();

				if (!singleAreaMode) {
					int indexOfExtension = filename.lastIndexOf(".");

					if (indexOfExtension == -1)
						filename = filename + "_area" + areaIndex;
					else
						filename = filename.substring(0, indexOfExtension) + "_area" + areaIndex + filename.substring(indexOfExtension, filename.length());

				}

				logger.log(Level.INFO, "Saving image to '" + filename + "', format '" + outputFormat + "'.");

				if (!ImageIO.write(image, outputFormat, new File(filename)))
					throw new Exception("An error occured while saving the image.");

				if (saveCommandlineArgument.wasParsed()) {
					logger.log(Level.INFO, "Saving rendering command to '" + filename + ".command.txt'.");
					saveCommandLine(filename, newton);
				}
			}
		} catch (Exception e) {
			out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
			return;
		} finally {
			newton.release();
		}
	}

	public Argument[] getArguments() {
		return new Argument[] { deviceArgument, fp64Argument, sizeArgument, outputArgument, formatArgument, partSizeArgument, maxIterationsArgument,
				epsilonArgument, superSamplingArgument, areaArgument, saveCommandlineArgument, functionArgument, derivativeArgument };
	}

	private static void saveCommandLine(String outputFile, Newton newton) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(outputFile + ".command.txt"));
			writer.write(generateArgumentString(newton,  outputFile));
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	public void initializeArguments() {
		super.initializeArguments();

		partSizeArgument = new ValueArgument<Integer>(
				"partsize",
				"",
				false,
				"Maximum amount of pixels that are calculated simultaneously, eg. '8kx8k', '8000x8000', '64000000' or '64M'. Note that the actual parts might be bigger, because the desired size must be divisible by the picture width. The picture is never divided into parts if this value is set to zero. Defaults to 0.") {
			@Override
			protected Integer convert(String value) throws ParseException {
				try {
					Size s = Formatter.parseSize(value);
					return s.getWidth() * s.getHeight();
				} catch (Exception e) {
				}

				try {
					int val = Formatter.parseInt(value);
					if (val < 0)
						throw new Exception();

					return val;
				} catch (Exception e) {
					throw new ParseException("value for argument --partsize is invalid.");
				}
			}
		};

		outputArgument = new ValueArgument<String>(
				"output",
				"o",
				true,
				"Output file name, e.g. 'mandelbrot.png'. Must end with a valid image format extension, e.g. 'bmp', 'jpg', 'jpeg', 'png' or 'gif', unless --format is specified.") {
			@Override
			protected String convert(String value) throws ParseException {
				if (value == null || value.isEmpty())
					throw new ParseException("Output file name must not be empty!");

				return value;
			}
		};

		functionArgument = new ValueArgument<String>(
				"function",
				"",
				false,
				"Function in z in Newton's method. Must be specified in OpenCL-Syntax. Additional available functions: cmul, cdiv, cinv, cpow, cexp, csin, ccos, carg. Default is \"cpow(z,3)-(fp2)(1,0)\".") {
			@Override
			protected String convert(String value) throws ParseException {
				if (value == null || value.isEmpty())
					throw new ParseException("function must not be empty!");

				return value;
			}
		};
		
		derivativeArgument = new ValueArgument<String>(
				"derivative",
				"",
				false,
				"Derivative as a function in z in Newton's method. Must be specified in the same manner as --function. Default value is \"3*cpow(z,2)\".") {
			@Override
			protected String convert(String value) throws ParseException {
				if (value == null || value.isEmpty())
					throw new ParseException("derivative must not be empty!");

				return value;
			}
		};
		
		formatArgument = new EnumArgument("format", "f", false, "Output file format. This value has a higher priority than the output file name extension.",
				new String[] { "png", "bmp", "gif", "jpg", "jpeg" });

		maxIterationsArgument = new ValueArgument<Integer>("maxiter", "i", false, "Maximum iteration count. Must be a positive integer. Defaults to '120'.") {
			@Override
			protected Integer convert(String value) throws ParseException {
				try {
					int val = Formatter.parseInt(value);

					if (val <= 0)
						throw new Exception();

					return val;
				} catch (Exception e) {
					throw new ParseException("value for argument --maxiter must be a positive integer.");
				}
			}
		};

		epsilonArgument = new ValueArgument<Double>("epsilon", "e", false, "Iteration epsilon as a positive float. Defaults to '1E-6'.") {
			@Override
			protected Double convert(String value) throws ParseException {
				try {
					Double val = Double.parseDouble(value);

					if (val <= 0)
						throw new Exception();

					return val;
				} catch (Exception e) {
					throw new ParseException("value for argument --bailout must be a positive float.");
				}
			}
		};

		superSamplingArgument = new ValueArgument<Size>("supersampling", "", false,
				"Amount of subpixels per pixel in the form 'wxh' for positive integers w and h or a single square number. Defaults to '1'.") {
			@Override
			protected Size convert(String value) throws ParseException {
				try {
					Size s = Formatter.parseSize(value);
					return s;
				} catch (Exception e) {
				}

				try {
					int val = Formatter.parseInt(value);
					int sval = (int) Math.sqrt(val);
					if (val <= 0 || sval * sval != val)
						throw new Exception();

					return new Size(sval, sval);
				} catch (Exception e) {
					throw new ParseException("value for argument --supersample is neither a size nor a square number.");
				}
			}
		};

		areaArgument = new ValueArgument<Rectangle>(
				"area",
				"a",
				false,
				"Area in the complex plane that is mapped to the picture in the form '\"Re(z) Im(z) Re(w-z) Im(w-z)\"' for the bottom left point z in C and the top right point w in C (in other words, 'x0 y0 width height'), 'file' to load from area.txt (created by marking feature in the GUI) or 'file:n' (where n is a positive integer) to use the n-th area from areas.txt (zero-based). Defaults to '\"-2.1 -1.5 3 3\"'.") {
			@Override
			protected Rectangle convert(String value) throws ParseException {
				if (value.equals("file"))
					return null;

				if (value.startsWith("file:")) {
					int n = Integer.parseInt(value.substring("file:".length()));
					ArrayList<Rectangle> areas;

					try {
						areas = loadAreas();
					} catch (Exception e) {
						throw new ParseException("Error loading area file.");
					}

					if (areas.size() <= n)
						throw new ParseException("The area file does not contain that many areas.");

					return areas.get(n);
				}

				try {
					String[] splits = value.split(" ");

					if (splits.length != 4)
						throw new Exception();

					return new Rectangle(Double.parseDouble(splits[0]), Double.parseDouble(splits[1]), Double.parseDouble(splits[2]),
							Double.parseDouble(splits[3]));
				} catch (Exception e) {
					throw new ParseException("value for argument --area is not a valid area.");
				}
			}
		};

		saveCommandlineArgument = new SwitchArgument("savecommand", "", false,
				"When rendering to disk, save the command to render this exact image to [outputfile].txt");
	}

}
