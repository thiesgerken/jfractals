package de.thiesgerken.fractals.multibrot;

import static com.jogamp.opencl.CLEvent.ProfilingCommand.END;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.START;
import static com.jogamp.opencl.CLMemory.Mem.READ_ONLY;
import static com.jogamp.opencl.CLMemory.Mem.WRITE_ONLY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.logging.Level;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.CLKernel;

import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Size;

public class MultibrotRendererQuality extends MultibrotRenderer {

	protected CLKernel colorKernel;

	protected boolean hasData;
	protected CLBuffer<IntBuffer> cdfBuffer;
	protected CLBuffer<?> countBuffer;
	protected int minN, maxN, parts, normalPartHeight;
	protected long pxCount;
	protected float[] floatCounts;
	protected double[] doubleCounts;

	protected double histogramRatio;

	public MultibrotRendererQuality() {
		super();

		desiredPartSize = 0;
		histogramRatio = 1;
		paletteCycles = 1;
		palettePhase = 0;
		hasData = false;
	}

	@Override
	protected CLContext createContext() {
		return CLContext.create(device);
	}

	@Override
	protected InputStream getSource() {
		return getClass().getResourceAsStream("MultibrotQuality.cl");
	}

	@Override
	protected void setCustomKernelArguments() {
		colorKernel.setForce32BitArgs(!fp64);

		if (palette != null) {
			setArg(colorKernel.getID(), 1, new double[] { paletteCycles, palettePhase });
			colorKernel.setArg(2, palette.getLength());
			colorKernel.setArg(3, paletteBuffer);
		}
	}

	@Override
	protected void buildCustomKernel() {
		colorKernel = program.createCLKernel("color");
	}

	public void calculate() throws Exception {
		printParameters();

		initCL();
		buildKernel();
		setKernelArguments();

		// calculate part sizes
		int maxPartSize;

		if (desiredPartSize <= 0)
			maxPartSize = size.getHeight() * size.getWidth();
		else if (desiredPartSize % size.getWidth() == 0)
			maxPartSize = desiredPartSize;
		else
			maxPartSize = desiredPartSize + size.getWidth() - (desiredPartSize % size.getWidth());

		parts = (int) Math.ceil((double) size.getHeight() * size.getWidth() / maxPartSize);
		normalPartHeight = maxPartSize / size.getWidth();

		if (fp64) {
			countBuffer = context.createDoubleBuffer(normalPartHeight * size.getWidth());
			doubleCounts = new double[size.getHeight() * size.getWidth()];
		} else {
			countBuffer = context.createFloatBuffer(normalPartHeight * size.getWidth());
			floatCounts = new float[size.getHeight() * size.getWidth()];
		}

		kernel.setArg(7, countBuffer);

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
			logger.log(Level.INFO, "Calculating took " + Formatter.formatTime(end - start) + " and ca. " + Formatter.formatSize(countBuffer.getCLSize())
					+ " of device memory.");

			probe.release();
			queue.putReadBuffer(countBuffer, true);

			if (fp64) {
				DoubleBuffer countBackend = (DoubleBuffer) countBuffer.getBuffer().rewind();
				countBackend.get(doubleCounts, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight).rewind();
			} else {
				FloatBuffer countBackend = (FloatBuffer) countBuffer.getBuffer().rewind();
				countBackend.get(floatCounts, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight).rewind();
			}
		}

		logger.log(Level.INFO, "Generating histogram and cumulated density function");

		minN = maxIterations;
		maxN = 0;

		int[] histogram = new int[maxIterations];
		pxCount = 0;

		for (int i = 0; i < size.getWidth() * size.getHeight(); i++) {
			int val;

			if (fp64)
				val = (int) doubleCounts[i];
			else
				val = (int) floatCounts[i];

			if (val != -1) {
				histogram[val]++;
				pxCount++;

				if (val < minN)
					minN = val;

				if (val > maxN)
					maxN = val;
			}
		}

		if (pxCount == 0)
			minN = 0;

		int[] cdf = new int[maxN - minN + 1];
		cdf[0] = 0;

		for (int i = 1; i <= maxN - minN; i++)
			cdf[i] = cdf[i - 1] + histogram[i + minN - 1];

		cdfBuffer = context.createBuffer(Buffers.newDirectIntBuffer(cdf), READ_ONLY);

		queue.putWriteBuffer(cdfBuffer, true);
		queue.flush();
		queue.finish();

		hasData = true;
	}

	@Override
	public BufferedImage createImage() throws Exception {
		long overallTime = System.nanoTime();

		try {
			calculate();
			return colorImage();
		} finally {
			freeBuffers();
			logger.log(Level.INFO, "Rendering took a total of " + Formatter.formatTime(System.nanoTime() - overallTime) + ".");
		}
	}

	public BufferedImage colorImage() throws Exception {
		if (!hasData)
			throw new Exception("There is no data to render, you have to calculate something first!");

		if (palette == null)
			throw new Exception("No Palette specified");

		BufferedImage image = new BufferedImage(size.getWidth(), size.getHeight(), TYPE_INT_RGB);

		if (pxCount == 0) {
			logger.log(Level.INFO, "Pixel count is zero, skipping coloring.");
			return image;
		}

		int[] imageData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		CLBuffer<IntBuffer> imageBuffer = context.createIntBuffer(normalPartHeight * size.getWidth(), WRITE_ONLY);

		setKernelArguments();

		colorKernel.setArg(4, minN);
		colorKernel.setArg(5, maxN);
		colorKernel.setForce32BitArgs(false);
		colorKernel.setArg(6, pxCount);
		colorKernel.setForce32BitArgs(!fp64);
		colorKernel.setArg(7, histogramRatio);
		colorKernel.setArg(8, countBuffer);
		colorKernel.setArg(9, cdfBuffer);
		colorKernel.setArg(10, imageBuffer);

		for (int y = 0; y < parts; y++) {
			int partHeight = (y == parts - 1 ? size.getHeight() - y * normalPartHeight : normalPartHeight);

			logger.log(Level.INFO, "-- Coloring of part " + (y + 1) + " of " + parts + " (" + size.getWidth() + "x" + partHeight + " px) --");

			if (parts != 1) {
				if (fp64) {
					DoubleBuffer countBackend = (DoubleBuffer) countBuffer.getBuffer().rewind();
					countBackend.put(doubleCounts, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight).rewind();
				} else {
					FloatBuffer countBackend = (FloatBuffer) countBuffer.getBuffer().rewind();
					countBackend.put(floatCounts, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight).rewind();
				}

				queue.putWriteBuffer(countBuffer, true);
			}

			// calculate optimal sizes for the local and global work groups
			Size localWorkSize = calculateLocal2DWorkSize(new Size(size.getWidth(), partHeight));
			Size globalWorkSize = calculateGlobal2DWorkSize(new Size(size.getWidth(), partHeight));

			// overwrite the settings for width and area
			setArg(colorKernel.getID(), 0, new int[] { size.getWidth(), partHeight });

			probe.release();

			queue.put2DRangeKernel(colorKernel, 0, 0, globalWorkSize.getWidth(), globalWorkSize.getHeight(), localWorkSize.getWidth(),
					localWorkSize.getHeight(), probe);
			queue.finish();

			CLEvent event = probe.getEvent(0);
			logger.log(Level.INFO, "Coloring took " + Formatter.formatTime(event.getProfilingInfo(END) - event.getProfilingInfo(START)) + " and ca. "
					+ Formatter.formatSize(countBuffer.getCLSize() + imageBuffer.getCLSize() + cdfBuffer.getCLSize()) + " of device memory.");

			logger.log(Level.INFO, "Copying results to image");

			queue.putReadBuffer(imageBuffer, true);
			imageBuffer.getBuffer().get(imageData, size.getWidth() * normalPartHeight * y, size.getWidth() * partHeight);
			imageBuffer.getBuffer().rewind();
		}

		imageBuffer.release();
		return image;
	}

	public void freeBuffers() {
		logger.log(Level.INFO, "Releasing computation data and memory.");

		if (cdfBuffer != null && !cdfBuffer.isReleased())
			cdfBuffer.release();

		if (countBuffer != null && !countBuffer.isReleased())
			countBuffer.release();

		doubleCounts = null;
		floatCounts = null;
		hasData = false;
	}

	@Override
	public void release() {
		freeBuffers();

		if (colorKernel != null && !colorKernel.isReleased())
			colorKernel.release();

		super.release();
	}

	public BufferedImage getRealPalette() {
		if (!hasData)
			return null;

		final int factor = 10;

		BufferedImage img = new BufferedImage(factor * (maxN - minN) + 1, 20, TYPE_INT_RGB);

		for (int x = 0; x <= factor * (maxN - minN); x++)
			for (int y = 0; y < 20; y++) {
				int fraction = cdfBuffer.getBuffer().get(x / factor);
				double m = (double) x / factor;
				int n = x / factor;

				int diff;
				if (x != factor * (maxN - minN))
					diff = cdfBuffer.getBuffer().get(x / factor + 1) - fraction;
				else
					diff = 0;

				double percHist = (fraction + (m - n) * diff) / pxCount;
				double percLin = m / (maxN - minN);
				double perc = histogramRatio * percHist + (1 - histogramRatio) * percLin;

				int pindex = (int) ((perc * paletteCycles + palettePhase) * palette.getLength());

				img.setRGB(x, y, palette.getColors()[pindex % palette.getLength()]);
			}

		return img;
	}

	@Override
	public int getDesiredPartSize() {
		return desiredPartSize;
	}

	@Override
	public void setDesiredPartSize(int desiredPartSize) {
		this.desiredPartSize = desiredPartSize;
	}

	public double getHistogramRatio() {
		return histogramRatio;
	}

	public void setHistogramRatio(double histogramRatio) {
		this.histogramRatio = histogramRatio;
	}

}
