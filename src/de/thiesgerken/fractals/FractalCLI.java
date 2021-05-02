package de.thiesgerken.fractals;

import static java.lang.System.out;

import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLPlatform;

import de.thiesgerken.commandlineparser.Argument;
import de.thiesgerken.commandlineparser.ParseException;
import de.thiesgerken.commandlineparser.SwitchArgument;
import de.thiesgerken.commandlineparser.ValueArgument;
import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Size;

public abstract class FractalCLI {
	protected ValueArgument<CLDevice> deviceArgument;
	protected ValueArgument<Size> sizeArgument;
	protected SwitchArgument fp64Argument;

	 public void doStuff() {
			if (!deviceArgument.wasParsed()) {
				try {
					deviceArgument.parse("gpu");
				} catch (ParseException e1) {
					try {
						deviceArgument.parse("0.0");
					} catch (ParseException e2) {
						out.println("Error: No cl device found.");
						return;
					}
				}
			}
	 }

	public Argument[] getArguments() {
		return new Argument[] { deviceArgument, sizeArgument, fp64Argument };
	}

	public void initializeArguments() {
		deviceArgument = new ValueArgument<CLDevice>(
				"device",
				"d",
				false,
				"device that is to be used for calculation. Valid values are a device id, e.g. '0.0', 'cpu' to use first cpu device or 'gpu' to use first gpu device. Defaults to gpu, and if none found, to 0.0.") {

			@Override
			protected CLDevice convert(String value) throws ParseException {
				if ("cpu".equals(value)) {
					for (CLPlatform platform : CLPlatform.listCLPlatforms())
						for (CLDevice device : platform.listCLDevices(CLDevice.Type.CPU))
							return device;

					throw new ParseException("Unable to find an OpenCL-capable cpu device.");
				} else if ("gpu".equals(value)) {
					for (CLPlatform platform : CLPlatform.listCLPlatforms())
						for (CLDevice device : platform.listCLDevices(CLDevice.Type.GPU))
							return device;
					throw new ParseException("Unable to find an OpenCL-capable gpu device.");
				} else {
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
						throw new ParseException("value for argument --device must be in the form 'x.y', where x and y are non-negative integers.");
					}

					if (plat >= CLPlatform.listCLPlatforms().length)
						throw new ParseException("The requested platform with id '" + plat + "' does not exist.");

					if (dev >= CLPlatform.listCLPlatforms()[plat].listCLDevices().length)
						throw new ParseException("The requested device with id '" + plat + "." + dev + "' does not exist.");

					return CLPlatform.listCLPlatforms()[plat].listCLDevices()[dev];
				}
			}
		};

		fp64Argument = new SwitchArgument("fp64", "", false, "Use high precision floats for calculation");

		sizeArgument = new ValueArgument<Size>("size", "s", false, "Image size in pixels in form 'wxh' (e.g. '500x300' or '5kx5k'). Defaults to '512x512'.") {
			@Override
			protected Size convert(String value) throws ParseException {
				try {
					return Formatter.parseSize(value);
				} catch (Exception e) {
					throw new ParseException("value for argument --size must be in the form 'wxh', where w and h are positive integers.");
				}
			}
		};
	}
}
