package de.thiesgerken.fractals.multibrot;

import static com.jogamp.opencl.CLCommandQueue.Mode.PROFILING_MODE;
import static com.jogamp.opencl.CLMemory.Mem.READ_ONLY;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram.CompilerOptions;

import de.thiesgerken.fractals.Fractal;
import de.thiesgerken.fractals.util.Rectangle;
import de.thiesgerken.fractals.util.Size;
import de.thiesgerken.fractals.util.palettes.Palette;

public abstract class Multibrot extends Fractal {
	protected final static Logger logger = Logger.getLogger(Multibrot.class.getName());

	/* runtime variables */
	protected CLBuffer<ByteBuffer> paletteBuffer;
	protected CLKernel kernel;

	/* User-defined parameters */
	protected Size superSampling;
	protected Rectangle area;
	protected Palette palette;
	protected double palettePhase;
	protected double paletteCycles;
	protected int maxIterations;
	protected double bailout;
	protected double exponent;
	protected boolean invert;

	public Multibrot() {
		super();

		this.area = getDefaultArea();
		this.maxIterations = 120;
		this.bailout = 4.0d;
		this.exponent = 2.0d;
		this.invert = false;
		this.palette = new Palette();
		this.palettePhase = 0;
		this.paletteCycles = 4;
		this.superSampling = new Size(1, 1);
	}

	public static Rectangle getDefaultArea() {
		return new Rectangle(-2.1d, -1.5d, 3.0d, 3.0d);
	}

	protected abstract CLContext createContext();

	protected void initCL() throws Exception {
		if (device == null)
			throw new Exception("device must not be null");

		if (isInitialized)
			return;

		logger.log(Level.INFO, "Initializing CL for device " + device.getName());

		context = createContext();
		cl = context.getCL();
		queue = device.createCommandQueue(PROFILING_MODE);
		program = context.createProgram(read(getClass().getResourceAsStream("Multibrot.cl")) + "\n" + read(getSource()));
		probe = new CLEventList(1);

		isInitialized = true;
		isCompiled = false;
		isConfigured = false;
	}

	protected abstract InputStream getSource();

	protected abstract void setCustomKernelArguments();

	protected void setKernelArguments() {
		if (isConfigured)
			return;

		logger.log(Level.INFO, "Setting kernel arguments");

		if (paletteBuffer != null && !paletteBuffer.isReleased())
			paletteBuffer.release();

		if (palette != null) {
			paletteBuffer = context.createBuffer(palette.createBuffer(device.getByteOrder()), READ_ONLY);
			queue.putWriteBuffer(paletteBuffer, true);
		}

		kernel.setForce32BitArgs(!fp64);

		setArg(kernel.getID(), 0, new int[] { size.getWidth(), size.getHeight() });
		setArg(kernel.getID(), 1, new double[] { area.getX(), area.getY(), area.getWidth(), area.getHeight() });
		kernel.setArg(2, maxIterations);
		kernel.setArg(3, bailout * bailout);
		kernel.setArg(4, exponent);
		kernel.setArg(5, invert ? 1 : 0);
		setArg(kernel.getID(), 6, new int[] { superSampling.getWidth(), superSampling.getHeight() });

		setCustomKernelArguments();

		isConfigured = true;
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

		kernel = program.createCLKernel("multibrot");
		buildCustomKernel();

		isCompiled = true;
		isConfigured = false;
	}

	public void release() {
		logger.log(Level.INFO, "Releasing resources");

		if (paletteBuffer != null && !paletteBuffer.isReleased())
			paletteBuffer.release();

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

	protected abstract void buildCustomKernel();

	protected void printParameters() {
		StringBuilder sb = new StringBuilder();

		sb.append("Image Parameters: ");

		sb.append("maxIterations = " + maxIterations);
		sb.append(", size = " + size);
		sb.append(", superSampling = " + superSampling);
		sb.append(", area = " + area);
		sb.append(", palette = " + palette);
		sb.append(", palettePhase = " + palettePhase);
		sb.append(", paletteCycles = " + paletteCycles);
		sb.append(", bailout = " + bailout);
		sb.append(", exponent = " + exponent);
		sb.append(", invert = " + invert);
		sb.append(", fp64 = " + fp64);

		logger.log(Level.INFO, sb.toString());
	}

	public boolean getInvert() {
		return invert;
	}

	public void setInvert(boolean invert) {
		if (invert != this.invert)
			isConfigured = false;

		this.invert = invert;
	}

	public double getExponent() {
		return exponent;
	}

	public void setExponent(double exponent) {
		if (exponent != this.exponent)
			isConfigured = false;

		this.exponent = exponent;
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

	public Rectangle getArea() {
		return area;
	}

	public void setArea(Rectangle viewPort) {
		if (viewPort != this.area)
			isConfigured = false;

		this.area = viewPort;
	}

	public Palette getPalette() {
		return palette;
	}

	public void setPalette(Palette palette) {
		if (palette != this.palette)
			isConfigured = false;

		this.palette = palette;
	}

	public double getPalettePhase() {
		return palettePhase;
	}

	public void setPalettePhase(double palettePhase) {
		if (palettePhase != this.palettePhase)
			isConfigured = false;

		this.palettePhase = palettePhase;
	}

	public double getPaletteCycles() {
		return paletteCycles;
	}

	public void setPaletteCycles(double paletteCycles) {
		if (paletteCycles != this.paletteCycles)
			isConfigured = false;

		this.paletteCycles = paletteCycles;
	}

	public Size getSuperSampling() {
		return superSampling;
	}

	public void setSuperSampling(Size superSampling) {
		if (superSampling != this.superSampling)
			isConfigured = false;

		this.superSampling = superSampling;
	}

}
