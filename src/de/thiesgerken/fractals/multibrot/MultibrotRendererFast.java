package de.thiesgerken.fractals.multibrot;

import static com.jogamp.opencl.CLEvent.ProfilingCommand.END;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.START;
import static com.jogamp.opencl.CLMemory.Mem.WRITE_ONLY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.System.out;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.logging.Level;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLEvent;

import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Size;

public class MultibrotRendererFast extends MultibrotRenderer {
	protected int desiredPartSize;

	public MultibrotRendererFast() {
		super();
		desiredPartSize = 0;
	}

	@Override
	protected CLContext createContext() {
		return CLContext.create(device);
	}

	@Override
	protected InputStream getSource() {
		return getClass().getResourceAsStream("MultibrotFast.cl");
	}

	@Override
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
		kernel.setArg(10, imageBuffer);

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

	@Override
	protected void setCustomKernelArguments() {
		setArg(kernel.getID(), 7, new double[] { paletteCycles, palettePhase });
		kernel.setArg(8, palette.getLength());
		kernel.setArg(9, paletteBuffer);
	}

	@Override
	protected void buildCustomKernel() {
	}
}
