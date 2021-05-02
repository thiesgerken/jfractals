package de.thiesgerken.fractals.multibrot;

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
import de.thiesgerken.fractals.util.palettes.Palette;

public class MultibrotCLI extends FractalCLI {
	private Logger logger = Logger.getLogger(MultibrotCLI.class.getName());

	private EnumArgument formatArgument;
	private ValueArgument<String> outputArgument;
	private ValueArgument<Integer> partSizeArgument;
	private ValueArgument<Double> paletteCyclesArgument;
	private ValueArgument<Double> palettePhaseArgument;
	private ValueArgument<Palette> paletteArgument;
	private ValueArgument<Integer> maxIterationsArgument;
	private ValueArgument<Double> bailoutArgument;
	private ValueArgument<Double> exponentArgument;
	private SwitchArgument invertArgument;
	private ValueArgument<Size> superSamplingArgument;
	private ValueArgument<Rectangle> areaArgument;
	private SwitchArgument guiArgument;
	private SwitchArgument savePaletteArgument;
	private SwitchArgument saveCommandlineArgument;
	private ValueArgument<Double> histogramRatioArgument;

	private boolean singleAreaMode;
	private boolean singlePaletteMode;

	public static String generateArgumentString(Multibrot brot, boolean forceRender) {
		return generateArgumentString(brot, forceRender, "out.png");
	}

	public static String generateArgumentString(Multibrot brot, boolean forceRender, String outputFile) {
		StringBuilder sb = new StringBuilder();

		sb.append("jfractals multibrot");

		if (brot instanceof MultibrotGUI && !forceRender)
			sb.append(" -g");
		else
			sb.append(" -o \"" + outputFile + "\"");

		sb.append(" -s " + brot.getSize().toString());
		sb.append(" -i " + brot.getMaxIterations());
		sb.append(" -b " + brot.getBailout());
		sb.append(" -a \"" + brot.getArea().toString() + "\"");
		sb.append(" -e " + brot.getExponent());
		sb.append(" -p \"" + brot.getPalette().getName() + "\"");

		if (forceRender && brot instanceof MultibrotGUI)
			sb.append(" --pcycles 1");
		else
			sb.append(" --pcycles " + brot.getPaletteCycles());

		sb.append(" --pphase " + brot.getPalettePhase());
		sb.append(" --supersampling " + brot.getSuperSampling().toString());

		if (brot.use64bitFloats())
			sb.append(" --fp64");

		if (brot.getInvert())
			sb.append(" --invert");

		return sb.toString();
	}

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

	private static void saveCommandLine(String outputFile, MultibrotRenderer renderer) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(outputFile + ".command.txt"));
			writer.write(generateArgumentString(renderer, false, outputFile));
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	public void doStuff() {
		super.doStuff();

		if (guiArgument.wasParsed() && (formatArgument.wasParsed() || outputArgument.wasParsed())) {
			out.println("Error: you can not specify --gui and an output format or file.");
			return;
		}

		if (guiArgument.wasParsed())
			showMultibrotGUI();
		else
			startMultibrotRendering();
	}

	public Argument[] getArguments() {
		return new Argument[] { deviceArgument, fp64Argument, sizeArgument, outputArgument, formatArgument, partSizeArgument, paletteCyclesArgument,
				palettePhaseArgument, paletteArgument, maxIterationsArgument, bailoutArgument, exponentArgument, invertArgument, superSamplingArgument,
				areaArgument, guiArgument, histogramRatioArgument, savePaletteArgument, saveCommandlineArgument };
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
				false,
				"Output file name, e.g. 'mandelbrot.png'. Must end with a valid image format extension, e.g. 'bmp', 'jpg', 'jpeg', 'png' or 'gif', unless --format is specified.") {
			@Override
			protected String convert(String value) throws ParseException {
				if (value == null || value.isEmpty())
					throw new ParseException("Output file name must not be empty!");

				return value;
			}
		};

		formatArgument = new EnumArgument("format", "f", false, "Output file format. This value has a higher priority than the output file name extension.",
				new String[] { "png", "bmp", "gif", "jpg", "jpeg" });

		paletteCyclesArgument = new ValueArgument<Double>("pcycles", "", false,
				"Count of palette cycles that are mapped on 0-maxIterations. Defaults to '11' when using the GUI and '1' otherwise.") {
			@Override
			protected Double convert(String value) throws ParseException {
				try {
					return Double.parseDouble(value);
				} catch (Exception e) {
					throw new ParseException("value for argument --pcycles must be a float.");
				}
			}
		};

		palettePhaseArgument = new ValueArgument<Double>("pphase", "", false, "Palette shift in percent. Defaults to '0.0'.") {
			@Override
			protected Double convert(String value) throws ParseException {
				try {
					return Double.parseDouble(value);
				} catch (Exception e) {
					throw new ParseException("value for argument --pphase must be a float.");
				}
			}
		};

		paletteArgument = new ValueArgument<Palette>(
				"palette",
				"p",
				false,
				"Palette. Valid values are a integrated palette or the filename of an image. (Note that only the first row of pixels is used) Defaults to 'cyclic01'. Use 'all' to render one image per built-in palette (four images for each cyclic palette with different phases). A list of built-in palettes is available via the command 'listpalettes'.") {
			@Override
			protected Palette convert(String value) throws ParseException {
				if (value.equals("grey"))
					return new Palette();

				if (value.equals("all"))
					return null;

				try {
					return new Palette(value);
				} catch (Exception e) {
					throw new ParseException("value for argument --palette is neither a recognized palette name nor the name of an image file.");
				}
			}
		};

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

		bailoutArgument = new ValueArgument<Double>("bailout", "b", false, "Iteration bailout as a positive float. Defaults to '4'.") {
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

		exponentArgument = new ValueArgument<Double>("exponent", "e", false, "Value of k in the used formula z=z^k+c as a float. Defaults to '2'.") {
			@Override
			protected Double convert(String value) throws ParseException {
				try {
					return Double.parseDouble(value);
				} catch (Exception e) {
					throw new ParseException("value for argument --exponent must be a float.");
				}
			}
		};

		invertArgument = new SwitchArgument("invert", "", false, "Invert the picture (use the formula z=z^k+1/c instead of z=z^k+c).");

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

		savePaletteArgument = new SwitchArgument("savepalette", "", false,
				"When rendering to disk, save the used palette to ./palette.png, used mainly for debug reasons.");

		saveCommandlineArgument = new SwitchArgument("savecommand", "", false,
				"When rendering to disk, save the command to render this exact image to [outputfile].txt");

		histogramRatioArgument = new ValueArgument<Double>(
				"ratio",
				"",
				false,
				"File rendering uses a histogram to calculate a good palette scaling. This factor controls the mixing between this palette scaling and traditional methods. '1' indicates, that only the histogram method is being used, and '0' means linear scaling. Defaults to '1', must be a float in the interval [0,1].") {

			@Override
			protected Double convert(String value) throws ParseException {
				Double val = Double.parseDouble(value);

				if (val < 0 || val > 1)
					throw new ParseException("--ratio must be a float in [0,1]");

				return val;
			}

		};

		guiArgument = new SwitchArgument("gui", "g", false, "Do not render into an image file, instead show a gui.");
	}

	private void setMultibrotImageParameters(Multibrot brot) throws IOException {
		brot.setDevice(deviceArgument.getValue());
		brot.setUse64bitFloats(fp64Argument.wasParsed());
		brot.setInvert(invertArgument.wasParsed());

		if (sizeArgument.wasParsed())
			brot.setSize(sizeArgument.getValue());

		if (paletteArgument.wasParsed()) {
			brot.setPalette(paletteArgument.getValue());
			singlePaletteMode = (paletteArgument.getValue() != null);
		} else {
			brot.setPalette(new Palette("cyclic01"));
			singlePaletteMode = true;
		}

		if (paletteCyclesArgument.wasParsed())
			brot.setPaletteCycles(paletteCyclesArgument.getValue());

		if (palettePhaseArgument.wasParsed())
			brot.setPalettePhase(palettePhaseArgument.getValue());

		if (maxIterationsArgument.wasParsed())
			brot.setMaxIterations(maxIterationsArgument.getValue());

		if (bailoutArgument.wasParsed())
			brot.setBailout(bailoutArgument.getValue());

		if (exponentArgument.wasParsed())
			brot.setExponent(exponentArgument.getValue());

		if (superSamplingArgument.wasParsed())
			brot.setSuperSampling(superSamplingArgument.getValue());

		if (areaArgument.wasParsed()) {
			brot.setArea(areaArgument.getValue());
			singleAreaMode = (areaArgument.getValue() != null);
		} else
			singleAreaMode = true;
	}

	private void showMultibrotGUI() {
		MultibrotGUI gui = new MultibrotGUI();

		try {
			setMultibrotImageParameters(gui);
		} catch (IOException e) {
			out.println("Error: Could not set image parameters.");
			return;
		}

		if (gui.getPalette() == null) {
			out.println("Error: You have to specify a specific palette when using --gui.");
			return;
		}

		if (gui.getArea() == null) {
			out.println("Error: You have to specify a specific area when using --gui.");
			return;
		}

		if (saveCommandlineArgument.wasParsed())
			out.println("Warning: --savecommand is without effect when using --gui.");

		if (savePaletteArgument.wasParsed())
			out.println("Warning: --savepalette is without effect when using --gui.");

		out.println("Command line arguments seem to be okay, showing gui.");
		gui.show();
	}

	private void startMultibrotRendering() {
		MultibrotRendererQuality renderer = new MultibrotRendererQuality();

		try {
			setMultibrotImageParameters(renderer);
		} catch (IOException e) {
			out.println("Error: Could not set image parameters.");
			return;
		}

		if (!outputArgument.wasParsed()) {
			out.println("Error: No output file specified.");
			return;
		}

		if (partSizeArgument.wasParsed())
			renderer.setDesiredPartSize(partSizeArgument.getValue());

		if (histogramRatioArgument.wasParsed())
			renderer.setHistogramRatio(histogramRatioArgument.getValue());

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

		ArrayList<String> palettes = new ArrayList<String>();

		try {
			if (singlePaletteMode)
				palettes.add(renderer.getPalette().getName());
			else
				palettes.addAll(Palette.listPaletteNames());
		} catch (Exception e) {
			out.println("Error: Could not load palette names.");
			return;
		}

		ArrayList<Rectangle> areas = new ArrayList<Rectangle>();

		try {
			if (singleAreaMode)
				areas.add(renderer.getArea());
			else
				areas.addAll(loadAreas());
		} catch (Exception e) {
			out.println("Error: Could not load areas.");
			return;
		}

		double originalPhase = renderer.getPalettePhase();

		try {
			logger.log(Level.INFO, "Command line arguments seem to be okay, begin rendering.");

			for (int areaIndex = 0; areaIndex < areas.size(); areaIndex++) {
				Rectangle area = areas.get(areaIndex);

				if (!singleAreaMode) {
					logger.log(Level.INFO, "Using Area '" + area + "'");
					renderer.setArea(area);
				}

				logger.log(Level.INFO, "Calculating iteration counts");
				renderer.calculate();

				for (String palName : palettes) {
					int count;
					Palette pal;

					if (!singlePaletteMode) {
						pal = new Palette(palName);
						renderer.setPalette(pal);
						count = palName.startsWith("cyclic") ? 4 : 1;

						logger.log(Level.INFO, "Coloring with Palette '" + pal.getName() + "'");
					} else {
						pal = renderer.getPalette();
						count = 1;
					}

					for (int i = 1; i <= count; i++) {
						renderer.setPalettePhase(originalPhase + (double) (i - 1) / count);

						BufferedImage image = renderer.colorImage();
						String filename = outputArgument.getValue();

						if (!singleAreaMode) {
							int indexOfExtension = filename.lastIndexOf(".");

							if (indexOfExtension == -1)
								filename = filename + "_area" + areaIndex;
							else
								filename = filename.substring(0, indexOfExtension) + "_area" + areaIndex
										+ filename.substring(indexOfExtension, filename.length());

						}

						if (!singlePaletteMode) {
							int indexOfExtension = filename.lastIndexOf(".");

							if (indexOfExtension == -1)
								filename = filename + "_" + pal.getName() + (count == 1 ? "" : "_" + i);
							else
								filename = filename.substring(0, indexOfExtension) + "_" + pal.getName() + (count == 1 ? "" : "_" + i)
										+ filename.substring(indexOfExtension, filename.length());
						}

						logger.log(Level.INFO, "Saving image to '" + filename + "', format '" + outputFormat + "'.");

						if (!ImageIO.write(image, outputFormat, new File(filename)))
							throw new Exception("An error occured while saving the image.");

						if (saveCommandlineArgument.wasParsed()) {
							logger.log(Level.INFO, "Saving rendering command to '" + filename + ".command.txt'.");
							saveCommandLine(filename, renderer);
						}

						if (savePaletteArgument.wasParsed()) {
							logger.log(Level.INFO, "Saving used palette to '" + filename + ".palette.png'.");
							ImageIO.write(renderer.getRealPalette(), "png", new File(filename + ".palette.png"));
						}
					}
				}

				if (areaIndex != areas.size() - 1)
					renderer.freeBuffers();
			}
		} catch (Exception e) {
			out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
			return;
		} finally {
			renderer.release();
		}
	}
}
