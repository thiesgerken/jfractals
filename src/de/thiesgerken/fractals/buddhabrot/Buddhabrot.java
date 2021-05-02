package de.thiesgerken.fractals.buddhabrot;

import static com.jogamp.opencl.CLCommandQueue.Mode.PROFILING_MODE;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.END;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.START;
import static com.jogamp.opencl.CLMemory.Mem.*;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Random;
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

public class Buddhabrot extends Fractal {
	protected final static Logger logger = Logger.getLogger(Buddhabrot.class.getName());

	/* runtime variables */
	protected CLBuffer<LongBuffer> countBuffer;
	protected CLBuffer<LongBuffer> boundsBuffer;
	protected CLKernel computeKernel;
	protected CLKernel boundsKernel;
	protected CLKernel paintKernel;
	protected long min;
	protected long max;

	/* parameters */
	protected Rectangle area;
	private int desiredPassSize;
	private int passCount;
	protected int maxIterations;
	protected int minIterations;
	protected double bailout;
	protected int desiredPaintPartSize;
	protected int overExposure;

	public Buddhabrot() {
		super();

		area = getDefaultArea();
		maxIterations = 512;
		minIterations = 0;
		bailout = 4.0d;
		desiredPassSize = 512 * 512;
		desiredPaintPartSize = 0;
		passCount = 1;
		overExposure = 1;
	}

	protected void initCL() throws Exception {
		if (device == null)
			throw new Exception("device must not be null");

		if (isInitialized)
			return;

		logger.log(Level.INFO, "Initializing CL for device " + device.getName());

		context = CLContext.create(device);
		cl = context.getCL();
		queue = device.createCommandQueue(PROFILING_MODE);
		program = context.createProgram(read(getClass().getResourceAsStream("MWC64X.cl")) + "\n" + read(getClass().getResourceAsStream("Buddhabrot.cl")));

		probe = new CLEventList(1);

		isInitialized = true;
		isCompiled = false;
		isConfigured = false;
	}

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

		computeKernel = program.createCLKernel("compute");
		boundsKernel = program.createCLKernel("getBounds");
		paintKernel = program.createCLKernel("paint");

		isCompiled = true;
		isConfigured = false;
	}

	protected void setKernelArguments() {
		if (isConfigured)
			return;

		logger.log(Level.INFO, "Setting kernel arguments");

		if (countBuffer != null && !countBuffer.isReleased())
			countBuffer.release();

		countBuffer = context.createLongBuffer(size.getWidth() * size.getHeight(), READ_WRITE);

		if (boundsBuffer != null && !boundsBuffer.isReleased())
			boundsBuffer.release();

		boundsBuffer = context.createLongBuffer(size.getHeight() * 2, WRITE_ONLY);

		logger.log(Level.INFO, "allocated about " + Formatter.formatSize(countBuffer.getCLSize()) + " of device memory");

		setArg(computeKernel.getID(), 1, new int[] { size.getWidth(), size.getHeight() });
		setArg(computeKernel.getID(), 2, new double[] { area.getX(), area.getY(), area.getWidth(), area.getHeight() });
		computeKernel.setArg(3, minIterations);
		computeKernel.setArg(4, maxIterations);
		setArg(computeKernel, 5, bailout * bailout);
		computeKernel.setArg(6, countBuffer);

		setArg(boundsKernel.getID(), 0, new int[] { size.getWidth(), size.getHeight() });
		boundsKernel.setArg(1, boundsBuffer);
		boundsKernel.setArg(2, countBuffer);

		paintKernel.setArg(4, countBuffer);
		paintKernel.setArg(5, overExposure);

		isConfigured = true;
	}

	public void calculate() throws Exception {
		initCL();
		buildKernel();
		setKernelArguments();

		logger.log(Level.INFO, "Image Parameters: " + printParameters());

		int globalWorkSize = calculateGlobal1DWorkSize(getDesiredPassSize());
		int localWorkSize = calculateLocal1DWorkSize(getDesiredPassSize());

		logger.log(
				Level.INFO,
				"Calculating using global worksize = " + Formatter.formatIntBase2(globalWorkSize) + ", local worksize = "
						+ Formatter.formatIntBase2(localWorkSize));

		queue.putWriteBuffer(countBuffer, true);

		Random rnd = new Random(System.nanoTime());

		for (int i = 0; i < getPassCount(); i++) {
			setArg(computeKernel.ID, 0, new int[] { rnd.nextInt(), rnd.nextInt() });

			probe.release();
			queue.put1DRangeKernel(computeKernel, 0, globalWorkSize, localWorkSize, probe);
			queue.finish();

			CLEvent event = probe.getEvent(0);
			logger.log(Level.INFO, "Pass " + (i + 1) + " took " + Formatter.formatTime(event.getProfilingInfo(END) - event.getProfilingInfo(START)));

			Thread.sleep(5);
		}

		logger.log(Level.INFO, "Reading back results");

		queue.putReadBuffer(countBuffer, true);
	}

	private void findBounds() throws Exception {
		initCL();
		buildKernel();
		setKernelArguments();

		int globalWorkSize = calculateGlobal1DWorkSize(size.getHeight());
		int localWorkSize = calculateLocal1DWorkSize(size.getHeight());

		logger.log(
				Level.INFO,
				"Finding row bounds using global worksize = " + Formatter.formatIntBase2(globalWorkSize) + ", local worksize = "
						+ Formatter.formatIntBase2(localWorkSize));

		probe.release();
		queue.put1DRangeKernel(boundsKernel, 0, globalWorkSize, localWorkSize, probe);
		queue.finish();

		CLEvent event = probe.getEvent(0);
		logger.log(Level.INFO, "Computing min and max of each row took " + Formatter.formatTime(event.getProfilingInfo(END) - event.getProfilingInfo(START)));

		queue.putReadBuffer(boundsBuffer, true);

		min = Long.MAX_VALUE;
		max = 0;

		for (int i = 0; i < size.getHeight(); i++) {
			long rowMin = boundsBuffer.getBuffer().get(i * 2);
			long rowMax = boundsBuffer.getBuffer().get(i * 2 + 1);

			if (rowMin < min)
				min = rowMin;

			if (rowMax > max)
				max = rowMax;
		}

		logger.log(Level.INFO, "Global min = " + min + ", Global max = " + max);

		long[] histogram = new long[(int) (max - min + 1)];

		for (int i = 0; i < size.getHeight() * size.getWidth(); i++)
			histogram[(int) (countBuffer.getBuffer().get(i) - min)]++;

		for (int i = 0; i <= max - min; i++) {
			if (histogram[i] != 0)
				System.out.println(i + ": " + histogram[i]);
		}

	}

	public void load(String filename, boolean force) throws Exception {
		initCL();
		buildKernel();
		setKernelArguments();

		logger.log(Level.INFO, "Loading data from " + filename);

		DataInputStream dis = null;

		try {
			dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));

			int dataWidth = dis.readInt();
			int dataHeight = dis.readInt();

			if (dataWidth != size.getWidth() || dataHeight != size.getHeight())
				throw new Exception("File is with size " + dataWidth + "x" + dataHeight + ", not " + size.getWidth() + "x" + size.getHeight());

			double dataAreaX = dis.readDouble();
			double dataAreaY = dis.readDouble();
			double dataAreaWidth = dis.readDouble();
			double dataAreaHeight = dis.readDouble();
			int dataMinIter = dis.readInt();
			int dataMaxIter = dis.readInt();
			double dataBailout = dis.readDouble();

			if (dataAreaX != area.getX() || dataAreaY != area.getY() || dataAreaWidth != area.getWidth() || dataAreaHeight != area.getHeight()
					|| dataMinIter != minIterations || dataMaxIter != maxIterations || dataBailout != bailout) {
				String message = "File uses the parameters " + "area = " + (new Rectangle(dataAreaX, dataAreaY, dataAreaWidth, dataAreaHeight)).toString()
						+ ", minIter = " + dataMinIter + ", maxIter = " + dataMaxIter + ", bailout = " + dataBailout;
				message += ", which is not compatible to " + printParameters();

				if (force)
					logger.log(Level.WARNING, "Warning! " + message + " But I am using it anyway.");
				else
					throw new Exception(message);
			}

			for (int i = 0; i < size.getHeight() * size.getWidth(); i++) {
				countBuffer.getBuffer().put(i, dis.readLong());
			}
		} finally {
			if (dis != null)
				dis.close();
		}
	}

	protected String printParameters() {
		StringBuilder sb = new StringBuilder();

		sb.append("minIterations = " + minIterations);
		sb.append(", maxIterations = " + maxIterations);
		sb.append(", size = " + size);
		sb.append(", area = " + area);
		sb.append(", bailout = " + bailout);
		sb.append(", fp64 = " + fp64);

		return sb.toString();
	}

	public void save(String filename) throws Exception {
		initCL();
		buildKernel();
		setKernelArguments();

		logger.log(Level.INFO, "Saving data to " + filename);

		DataOutputStream dos = null;

		try {
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));

			dos.writeInt(size.getWidth());
			dos.writeInt(size.getHeight());
			dos.writeDouble(area.getX());
			dos.writeDouble(area.getY());
			dos.writeDouble(area.getWidth());
			dos.writeDouble(area.getHeight());
			dos.writeInt(minIterations);
			dos.writeInt(maxIterations);
			dos.writeDouble(bailout);

			for (int i = 0; i < size.getHeight() * size.getWidth(); i++)
				dos.writeLong(countBuffer.getBuffer().get(i));

		} finally {
			if (dos != null)
				dos.close();
		}
	}

	public BufferedImage paint() throws Exception {
		initCL();
		buildKernel();
		setKernelArguments();

		findBounds();

		logger.log(Level.INFO, "Painting using overexposure = " + overExposure);

		// calculate part sizes
		int maxPartSize;

		if (desiredPaintPartSize <= 0)
			maxPartSize = size.getHeight() * size.getWidth();
		else if (desiredPaintPartSize % size.getWidth() == 0)
			maxPartSize = desiredPaintPartSize;
		else
			maxPartSize = desiredPaintPartSize + size.getWidth() - (desiredPaintPartSize % size.getWidth());

		int parts = (int) Math.ceil((double) size.getHeight() * size.getWidth() / maxPartSize);
		int normalPartHeight = maxPartSize / size.getWidth();

		BufferedImage image = new BufferedImage(size.getWidth(), size.getHeight(), TYPE_INT_RGB);

		if (min == max) {
			logger.log(Level.INFO, "min = max, returning black image.");
			return image;
		}

		int[] imageData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		CLBuffer<IntBuffer> imageBuffer = context.createIntBuffer(normalPartHeight * size.getWidth(), WRITE_ONLY);
		logger.log(Level.INFO, "Image parts need " + Formatter.formatSize(countBuffer.getCLSize()) + " of device memory");

		setArg(paintKernel.getID(), 1, new long[] { min, max });
		paintKernel.setArg(3, imageBuffer);

		for (int y = 0; y < parts; y++) {
			int partHeight = (y == parts - 1 ? size.getHeight() - y * normalPartHeight : normalPartHeight);

			// calculate optimal sizes for the local and global work groups
			Size localWorkSize = calculateLocal2DWorkSize(new Size(size.getWidth(), partHeight));
			Size globalWorkSize = calculateGlobal2DWorkSize(new Size(size.getWidth(), partHeight));

			// overwrite the settings for width and area
			setArg(paintKernel.getID(), 0, new int[] { size.getWidth(), partHeight });
			paintKernel.setArg(2, normalPartHeight * y);

			probe.release();

			queue.put2DRangeKernel(paintKernel, 0, 0, globalWorkSize.getWidth(), globalWorkSize.getHeight(), localWorkSize.getWidth(),
					localWorkSize.getHeight(), probe);
			queue.finish();

			CLEvent event = probe.getEvent(0);
			logger.log(
					Level.INFO,
					"Painting part " + (y + 1) + " of " + parts + " (" + size.getWidth() + "x" + partHeight + " px) took "
							+ Formatter.formatTime(event.getProfilingInfo(END) - event.getProfilingInfo(START)));

			logger.log(Level.INFO, "Copying results to image");

			queue.putReadBuffer(imageBuffer, true);
			imageBuffer.getBuffer().get(imageData, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight);
			imageBuffer.getBuffer().rewind();
		}

		imageBuffer.release();
		return image;
	}

	@Override
	public void release() {
		logger.log(Level.INFO, "Releasing resources");

		if (countBuffer != null && !countBuffer.isReleased())
			countBuffer.release();

		if (boundsBuffer != null && !boundsBuffer.isReleased())
			boundsBuffer.release();

		if (computeKernel != null && !computeKernel.isReleased())
			computeKernel.release();

		if (boundsKernel != null && !boundsKernel.isReleased())
			boundsKernel.release();

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

	public double getBailout() {
		return bailout;
	}

	public void setBailout(double bailout) {
		if (bailout != this.bailout)
			isConfigured = false;

		this.bailout = bailout;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		if (maxIterations != this.maxIterations)
			isConfigured = false;

		this.maxIterations = maxIterations;
	}

	public int getMinIterations() {
		return minIterations;
	}

	public void setMinIterations(int minIterations) {
		if (minIterations != this.minIterations)
			isConfigured = false;

		this.minIterations = minIterations;
	}

	public static Rectangle getDefaultArea() {
		return new Rectangle(-2.1d, -1.5d, 3.0d, 3.0d);
	}

	public Rectangle getArea() {
		return area;
	}

	public void setArea(Rectangle viewPort) {
		if (viewPort != this.area)
			isConfigured = false;

		this.area = viewPort;
	}

	protected int getDesiredPassSize() {
		return desiredPassSize;
	}

	protected void setDesiredPassSize(int desiredPassSize) {
		this.desiredPassSize = desiredPassSize;
	}

	public int getPassCount() {
		return passCount;
	}

	public void setPassCount(int passCount) {
		this.passCount = passCount;
	}

	public int getOverExposure() {
		return overExposure;
	}

	public void setOverExposure(int overExposure) {
		if (overExposure != this.overExposure)
			isConfigured = false;

		this.overExposure = overExposure;
	}
}
