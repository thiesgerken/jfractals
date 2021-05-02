package de.thiesgerken.fractals.buddhabrot;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import de.thiesgerken.commandlineparser.Argument;
import de.thiesgerken.fractals.FractalCLI;

public class BuddhabrotCLI extends FractalCLI {
	// private Logger logger = Logger.getLogger(BuddhabrotCLI.class.getName());
	private Buddhabrot buddha;

	public void doStuff() {
		super.doStuff();

		buddha = new Buddhabrot();

		buddha.setDevice(deviceArgument.getValue());
		buddha.setUse64bitFloats(fp64Argument.wasParsed());

		if (sizeArgument.wasParsed())
			buddha.setSize(sizeArgument.getValue());

		buddha.setMinIterations(90000);
		buddha.setMaxIterations(100000);
		buddha.setPassCount(750);
		buddha.setDesiredPassSize(128 * 128);
		buddha.setOverExposure(120);

		try {
			load();

			while (true) {
				calculate();

				try {
					save();
				} catch (Exception e) {
					System.out.println(e);
				}

				paint("out.png");
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public Argument[] getArguments() {
		return new Argument[] { deviceArgument, fp64Argument, sizeArgument };
	}

	public void initializeArguments() {
		super.initializeArguments();
	}

	private void load() throws Exception {
		buddha.load("data.dat", true);
	}

	private void save() throws Exception {
		buddha.save("data.dat");
	}

	private void paint(String filename) throws Exception {
		BufferedImage image = buddha.paint();

		if (!ImageIO.write(image, "png", new File(filename)))
			throw new Exception("An error occured while saving the image.");
	}

	private void calculate() throws Exception {
		buddha.calculate();
	}
}
