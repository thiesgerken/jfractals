package de.thiesgerken.fractals.newton;

import static com.jogamp.opencl.CLCommandQueue.Mode.PROFILING_MODE;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.END;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.START;
import static com.jogamp.opencl.CLMemory.Mem.WRITE_ONLY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.System.out;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram.CompilerOptions;

import de.thiesgerken.fractals.Fractal;
import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Rectangle;
import de.thiesgerken.fractals.util.Size;

public class Newton extends Fractal {
	protected final static Logger logger = Logger.getLogger(Newton.class.getName());
	protected CLKernel kernel;

	/* User-defined parameters */
	protected Size superSampling;
	protected Rectangle area;
	protected int maxIterations;
	protected double epsilon;
	protected int desiredPartSize;
	private String function;
	private String derivative;

	public String getFunction() {
		return function;
	}

	public void setFunction(String function) {
		if (function != this.function) {
			isConfigured = false;
			isCompiled = false;
		}

		this.function = function;
	}

	public String getDerivative() {
		return derivative;
	}

	public void setDerivative(String derivative) {
		if (derivative != this.derivative) {
			isCompiled = false;
			isConfigured = false;
		}

		this.derivative = derivative;
	}

	public void setEpsilon(double epsilon) {
		if (epsilon != this.epsilon)
			isConfigured = false;

		this.epsilon = epsilon;
	}

	public double getEpsilon() {
		return epsilon;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		if (maxIterations != this.maxIterations)
			isConfigured = false;

		this.maxIterations = maxIterations;
	}

	public Rectangle getArea() {
		return area;
	}

	public void setArea(Rectangle viewPort) {
		if (viewPort != this.area)
			isConfigured = false;

		this.area = viewPort;
	}

	public Size getSuperSampling() {
		return superSampling;
	}

	public void setSuperSampling(Size superSampling) {
		if (superSampling != this.superSampling)
			isConfigured = false;

		this.superSampling = superSampling;
	}

	public int getDesiredPartSize() {
		return desiredPartSize;
	}

	public void setDesiredPartSize(int desiredPartSize) {
		this.desiredPartSize = desiredPartSize;
	}

	public Newton() {
		super();

		this.area = getDefaultArea();
		this.maxIterations = 120;
		this.epsilon = 1E-6;
		this.superSampling = new Size(1, 1);
		this.function = "cpow(z,3)-(fp2)(1,0)";
		this.derivative = "3*cpow(z,2)";
	}

	public static Rectangle getDefaultArea() {
		return new Rectangle(-2d, -2d, 4d, 4d);
	}

	@Override
	protected void initCL() throws Exception {
		if (device == null)
			throw new Exception("device must not be null");

		if (isInitialized)
			return;

		logger.log(Level.INFO, "Initializing CL for device " + device.getName());

		context = CLContext.create(device);
		cl = context.getCL();
		queue = device.createCommandQueue(PROFILING_MODE);
		program = context.createProgram(read(getClass().getResourceAsStream("Newton.cl")).replace("%% F %%", function).replace("%% DF %%", derivative));

		probe = new CLEventList(1);

		isInitialized = true;
		isCompiled = false;
		isConfigured = false;
	}

	@Override
	protected void setKernelArguments() {
		if (isConfigured)
			return;

		logger.log(Level.INFO, "Setting kernel arguments");

		kernel.setForce32BitArgs(!fp64);

		setArg(kernel.getID(), 0, new int[] { size.getWidth(), size.getHeight() });
		setArg(kernel.getID(), 1, new double[] { area.getX(), area.getY(), area.getWidth(), area.getHeight() });
		kernel.setArg(2, maxIterations);
		kernel.setArg(3, epsilon * epsilon);
		setArg(kernel.getID(), 4, new int[] { superSampling.getWidth(), superSampling.getHeight() });

		isConfigured = true;
	}

	@Override
	protected void buildKernel() throws Exception {
		if (isCompiled)
			return;

		if (fp64 && !device.isDoubleFPAvailable())
			throw new Exception("the selected device does not have 64bit floating point support");

		logger.log(Level.INFO, "Compiling kernel for " + (fp64 ? "64" : "32") + "-bit floats");

		configure = program.prepare();

		if (fp64) {
			configure.withDefine("FP64");

			if (device.isExtensionAvailable("cl_amd_fp64"))
				configure.withDefine("AMDFP64");
		}

		configure.forDevice(device);
		configure.withOption(CompilerOptions.FAST_RELAXED_MATH).build();

		kernel = program.createCLKernel("newton");

		isCompiled = true;
		isConfigured = false;
	}

	@Override
	public void release() {
		logger.log(Level.INFO, "Releasing resources");

		if (kernel != null && !kernel.isReleased())
			kernel.release();

		if (queue != null && !queue.isReleased())
			queue.release();

		if (program != null && !program.isReleased())
			program.release();

		if (context != null && !context.isReleased())
			context.release();

		isCompiled = false;
		isConfigured = false;
		isInitialized = false;
	}

	public BufferedImage createImage() throws Exception {
		printParameters();

		long overallTime = System.nanoTime();

		initCL();
		buildKernel();
		setKernelArguments();

		BufferedImage image = new BufferedImage(size.getWidth(), size.getHeight(), TYPE_INT_RGB);
		int[] imageData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		int maxPartSize;

		if (desiredPartSize <= 0)
			maxPartSize = size.getHeight() * size.getWidth();
		else if (desiredPartSize % size.getWidth() == 0)
			maxPartSize = desiredPartSize;
		else
			maxPartSize = desiredPartSize + size.getWidth() - (desiredPartSize % size.getWidth());

		int parts = (int) Math.ceil((double) size.getHeight() * size.getWidth() / maxPartSize);
		int normalPartHeight = maxPartSize / size.getWidth();

		CLBuffer<IntBuffer> imageBuffer = context.createIntBuffer(Math.min(size.getHeight() * size.getWidth(), maxPartSize), WRITE_ONLY);
		kernel.setArg(5, imageBuffer);

		for (int y = 0; y < parts; y++) {
			int partHeight = (y == parts - 1 ? size.getHeight() - y * normalPartHeight : normalPartHeight);

			logger.log(Level.INFO, "-- Calculation of part " + (y + 1) + " of " + parts + " (" + size.getWidth() + "x" + partHeight + " px) --");

			// calculate optimal sizes for the local and global work groups
			Size localWorkSize = calculateLocal2DWorkSize(new Size(size.getWidth(), partHeight));
			Size globalWorkSize = calculateGlobal2DWorkSize(new Size(size.getWidth(), partHeight));

			// overwrite the settings for width and area
			setArg(kernel.getID(), 0, new int[] { size.getWidth(), partHeight });
			setArg(kernel.getID(), 1, new double[] { area.getX(), area.getY() + y * area.getHeight() * normalPartHeight / size.getHeight(), area.getWidth(),
					area.getHeight() * partHeight / size.getHeight() });

			probe.release();

			queue.put2DRangeKernel(kernel, 0, 0, globalWorkSize.getWidth(), globalWorkSize.getHeight(), localWorkSize.getWidth(), localWorkSize.getHeight(),
					probe);
			queue.finish();

			CLEvent event = probe.getEvent(0);
			long start = event.getProfilingInfo(START);
			long end = event.getProfilingInfo(END);
			logger.log(Level.INFO, "Calculation took " + Formatter.formatTime(end - start) + " and " + Formatter.formatSize(imageBuffer.getCLSize())
					+ " of device memory.");

			logger.log(Level.INFO, "Copying results to image");

			queue.putReadBuffer(imageBuffer, true);
			imageBuffer.getBuffer().get(imageData, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight);
			imageBuffer.getBuffer().rewind();
		}

		if (parts > 1)
			out.println();

		logger.log(Level.INFO, "Rendering took a total of " + Formatter.formatTime(System.nanoTime() - overallTime) + ".");

		imageBuffer.release();
		return image;
	}

	protected void printParameters() {
		StringBuilder sb = new StringBuilder();

		sb.append("Image Parameters: ");

		sb.append("maxIterations = " + maxIterations);
		sb.append(", size = " + size);
		sb.append(", superSampling = " + superSampling);
		sb.append(", area = " + area);
		sb.append(", epsilon = " + epsilon);
		sb.append(", fp64 = " + fp64);

		logger.log(Level.INFO, sb.toString());
	}

}
