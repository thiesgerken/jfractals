package de.thiesgerken.fractals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;

import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.llb.CL;
import com.jogamp.opencl.util.CLProgramConfiguration;

import de.thiesgerken.fractals.util.Size;

public abstract class Fractal {

	/* runtime variables */
	protected boolean isInitialized;
	protected boolean isCompiled;
	protected boolean isConfigured;
	protected CL cl;
	protected CLContext context;
	protected CLCommandQueue queue;
	protected CLProgram program;
	protected CLProgramConfiguration configure;
	protected CLEventList probe;

	/* User-defined parameters */
	protected CLDevice device;
	protected boolean fp64;
	protected Size size;

	protected abstract void initCL() throws Exception;

	protected abstract void setKernelArguments();

	protected abstract void buildKernel() throws Exception;

	public abstract void release();

	protected Size calculateGlobal2DWorkSize(Size partSize) {
		int loc = fp64 || device.getMaxWorkGroupSize() < 1024 ? 16 : 32;

		if (partSize.getWidth() * partSize.getHeight() <= loc * loc)
			return new Size(partSize.getWidth(), partSize.getHeight());
		else
			return new Size(partSize.getWidth()
					+ (partSize.getWidth() % loc == 0 ? 0 : loc
							- partSize.getWidth() % loc), partSize.getHeight()
					+ (partSize.getHeight() % loc == 0 ? 0 : loc
							- partSize.getHeight() % loc));
	}

	protected Size calculateLocal2DWorkSize(Size partSize) {
		int loc = fp64 || device.getMaxWorkGroupSize() < 1024 ? 16 : 32;

		if (partSize.getWidth() * partSize.getHeight() <= loc * loc)
			return new Size(partSize.getWidth(), partSize.getHeight());
		else
			return new Size(loc, loc);
	}

	protected int calculateLocal1DWorkSize(int partSize) {
		int loc = fp64 || device.getMaxWorkGroupSize() < 1024 ? 16 : 32;

		if (partSize <= loc)
			return partSize;
		else
			return loc;
	}

	protected int calculateGlobal1DWorkSize(int partSize) {
		int loc = fp64 || device.getMaxWorkGroupSize() < 1024 ? 16 : 32;

		if (partSize <= loc)
			return partSize;
		else
			return partSize + (partSize % loc == 0 ? 0 : loc - partSize % loc);
	}

	protected void setArg(CLKernel kernel, int index, double value) {
		if (fp64)
			kernel.setArg(index, value);
		else
			kernel.setArg(index, (float) value);
	}

	protected void setArg(long kernelId, int index, int[] values) {
		ByteBuffer buf = ByteBuffer.allocateDirect(values.length * 4);
		buf.order(device.getByteOrder());

		for (int i = 0; i < values.length; i++)
			buf.putInt(i * 4, values[i]);

		cl.clSetKernelArg(kernelId, index, buf.capacity(), buf);
	}

	protected void setArg(long kernelId, int index, long[] values) {
		ByteBuffer buf = ByteBuffer.allocateDirect(values.length * 8);
		buf.order(device.getByteOrder());

		for (int i = 0; i < values.length; i++)
			buf.putLong(i * 8, values[i]);

		cl.clSetKernelArg(kernelId, index, buf.capacity(), buf);
	}

	protected void setArg(long kernelId, int index, double[] values) {
		int sizeOfFp = fp64 ? 8 : 4;

		ByteBuffer buf = ByteBuffer.allocateDirect(values.length * sizeOfFp);
		buf.order(device.getByteOrder());

		for (int i = 0; i < values.length; i++)
			if (fp64)
				buf.putDouble(i * sizeOfFp, values[i]);
			else
				buf.putFloat(i * sizeOfFp, (float) values[i]);

		cl.clSetKernelArg(kernelId, index, buf.capacity(), buf);
	}

	public CLDevice getDevice() {
		return device;
	}

	public void setDevice(CLDevice device) {
		this.device = device;

		isInitialized = false;
		isCompiled = false;
	}

	public boolean use64bitFloats() {
		return fp64;
	}

	public void setUse64bitFloats(boolean fp64) {
		if (fp64 != this.fp64)
			isCompiled = false;

		this.fp64 = fp64;
	}

	public Size getSize() {
		return size;
	}

	public void setSize(Size size) {
		if (size != this.size)
			isConfigured = false;

		this.size = size;
	}

	protected static String read(InputStream stream) throws Exception {
		char[] buffer = new char[2048];
		StringBuilder out = new StringBuilder();
		Reader in = new InputStreamReader(stream, "UTF-8");
		try {
			for (;;) {
				int rsz = in.read(buffer, 0, buffer.length);
				if (rsz < 0)
					break;
				out.append(buffer, 0, rsz);
			}
		} finally {
			in.close();
		}

		return out.toString();
	}

	public Fractal() {
		this.device = CLPlatform.listCLPlatforms()[0].listCLDevices()[0];
		this.fp64 = false;
		this.size = new Size(512, 512);
	}

}
