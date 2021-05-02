package de.thiesgerken.fractals.multibrot;

import static com.jogamp.opencl.CLEvent.ProfilingCommand.END;
import static com.jogamp.opencl.CLEvent.ProfilingCommand.START;
import static com.jogamp.opencl.CLMemory.Mem.WRITE_ONLY;
import static javax.media.opengl.GL.GL_BGRA;
import static javax.media.opengl.GL.GL_COLOR_BUFFER_BIT;
import static javax.media.opengl.GL.GL_DEPTH_TEST;
import static javax.media.opengl.GL.GL_UNSIGNED_BYTE;
import static javax.media.opengl.GL2ES2.GL_STREAM_DRAW;
import static javax.media.opengl.GL2GL3.GL_PIXEL_UNPACK_BUFFER;
import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW;
import static javax.media.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION;

import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;

import com.jogamp.newt.event.InputEvent;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.gl.CLGLBuffer;
import com.jogamp.opencl.gl.CLGLContext;

import de.thiesgerken.fractals.util.Formatter;
import de.thiesgerken.fractals.util.Size;

public class MultibrotGUI extends Multibrot implements GLEventListener {

	private GLCanvas canvas;
	private CLGLContext sharedContext;
	private CLGLBuffer<?> imageBuffer;
	private boolean isBufferInitialized;

	public MultibrotGUI() {
		super();

		GLProfile.initSingleton();
	}

	public void show() {
		printParameters();
		printHelp();
		logger.log(Level.INFO, "Initializing window");

		canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL2)));
		canvas.addGLEventListener(this);
		initSceneInteraction();

		Frame frame = new Frame("Multibrot");
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				MultibrotGUI.this.release(e.getWindow());
			}

			@Override
			public void windowStateChanged(WindowEvent e) {
				System.out.println(e + " ---- ");
			}
		});
		canvas.setPreferredSize(size.toDimension());
		frame.add(canvas);
		frame.pack();

		frame.setVisible(true);
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		if (sharedContext == null) {
			logger.log(Level.INFO, "Initializing GL");

			drawable.setGL(drawable.getGL().getGL2());
			drawable.getGL().glFinish();

			sharedContext = CLGLContext.create(drawable.getContext(), device);

			GL2 gl = drawable.getGL().getGL2();

			gl.setSwapInterval(0);
			gl.glDisable(GL_DEPTH_TEST);
			gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

			initView(gl, drawable.getWidth(), drawable.getHeight());

			try {
				initCL();
				buildKernel();
				setKernelArguments();
			} catch (Exception e) {
				e.printStackTrace();
			}

			initBuffer(gl);
			drawable.getGL().glFinish();
		}
	}

	private void initView(GL2 gl, int width, int height) {
		logger.log(Level.INFO, "Initializing View");

		gl.glViewport(0, 0, width, height);

		gl.glMatrixMode(GL_MODELVIEW);
		gl.glLoadIdentity();

		gl.glMatrixMode(GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0.0, width, 0.0, height, 0.0, 1.0);
	}

	private void initBuffer(GL gl) {
		if (isBufferInitialized)
			return;

		if (imageBuffer != null) {
			logger.log(Level.INFO, "Releasing Buffer");

			imageBuffer.release();
			gl.glDeleteBuffers(1, new int[] { imageBuffer.GLID }, 0);
		}

		logger.log(Level.INFO,
				"Allocating " + size.getWidth() + "x" + size.getHeight() + " Buffer, size is " + Formatter.formatSize(size.getWidth() * size.getHeight() * 4));

		int[] id = new int[1];
		gl.glGenBuffers(1, id, 0);
		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id[0]);
		gl.glBufferData(GL_PIXEL_UNPACK_BUFFER, size.getWidth() * size.getHeight() * 4, null, GL_STREAM_DRAW);
		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

		imageBuffer = sharedContext.createFromGLBuffer(id[0], size.getWidth() * size.getHeight() * 4, WRITE_ONLY);
		kernel.setArg(10, imageBuffer);

		isBufferInitialized = true;
	}

	// rendering cycle
	@Override
	public void display(GLAutoDrawable drawable) {
		GL gl = drawable.getGL();

		// make sure GL does not use our objects before we start computing
		gl.glFinish();

		try {
			initCL();
			buildKernel();
			setKernelArguments();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!isBufferInitialized)
			initBuffer(gl);

		compute();

		render(gl.getGL2());
	}

	// OpenCL
	private void compute() {

		probe.release();

		Size globalWorkSize = calculateGlobal2DWorkSize(size);
		Size localWorkSize = calculateLocal2DWorkSize(size);

		// acquire GL objects, and enqueue the kernel
		queue.putAcquireGLObject(imageBuffer);
		queue.put2DRangeKernel(kernel, 0, 0, globalWorkSize.getWidth(), globalWorkSize.getHeight(), localWorkSize.getWidth(), localWorkSize.getHeight(), probe);
		queue.putReleaseGLObject(imageBuffer);

		// block until done (important: finish before doing further gl work)
		queue.finish();

		CLEvent event = probe.getEvent(0);
		long start = event.getProfilingInfo(START);
		long end = event.getProfilingInfo(END);

		logger.log(Level.INFO, "Calculation took " + Formatter.formatTime(end - start));
	}

	// OpenGL
	private void render(GL2 gl) {
		gl.glClear(GL_COLOR_BUFFER_BIT);

		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, imageBuffer.GLID);
		gl.glPixelZoom(1, -1);
		gl.glRasterPos2i(0, size.getHeight() - 1);
		gl.glDrawPixels(size.getWidth(), size.getHeight(), GL_BGRA, GL_UNSIGNED_BYTE, 0);

		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		if (size.getWidth() == width && size.getHeight() == height)
			return;

		if (size.getHeight() != height) {
			double newHeight = area.getWidth() * size.getHeight() / size.getWidth();

			area.setY(area.getY() - (newHeight - area.getHeight()) / 2);
			area.setHeight(newHeight);
		}

		if (size.getWidth() != width) {
			double newWidth = area.getHeight() * size.getWidth() / size.getHeight();

			area.setX(area.getX() - (newWidth - area.getWidth()) / 2);
			area.setWidth(newWidth);
		}
		size = new Size(width, height);

		isBufferInitialized = false;
		isConfigured = false;

		initView(drawable.getGL().getGL2(), width, height);
	}

	private void initSceneInteraction() {
		MouseAdapter mouseAdapter = new MouseAdapter() {

			private Point lastPosition = new Point();

			@Override
			public void mouseDragged(MouseEvent e) {
				double deltaPosX = e.getX() - lastPosition.getX();
				double deltaPosY = e.getY() - lastPosition.getY();

				double deltaX = deltaPosX / size.getWidth() * area.getWidth();
				double deltaY = deltaPosY / size.getHeight() * area.getHeight();

				area.setX(area.getX() - deltaX);
				area.setY(area.getY() - deltaY);

				isConfigured = false;
				lastPosition = e.getPoint();

				logger.log(Level.INFO, "Set area to " + area.getX() + ", " + area.getY() + ", " + area.getWidth() + ", " + area.getHeight());
				canvas.display();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				lastPosition = e.getPoint();
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				float rotation = 1 + e.getWheelRotation() / 2.0f;

				double x = area.getX() + (double) e.getX() / size.getWidth() * area.getWidth();
				double y = area.getY() + (double) e.getY() / size.getHeight() * area.getHeight();

				area.setWidth(area.getWidth() * rotation);
				area.setHeight(area.getHeight() * rotation);

				area.setX(x - 0.5 * area.getWidth());
				area.setY(y - 0.5 * area.getHeight());

				isConfigured = false;

				logger.log(Level.INFO, "Set area to " + area.getX() + ", " + area.getY() + ", " + area.getWidth() + ", " + area.getHeight());
				canvas.display();
			}
		};

		KeyAdapter keyAdapter = new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_PLUS && e.getModifiers() == 0) {
					maxIterations += 10;
					isConfigured = false;

					canvas.display();
					logger.log(Level.INFO, "Set maxIterations to " + maxIterations);
				} else if (e.getKeyCode() == KeyEvent.VK_MINUS && e.getModifiers() == 0) {
					if (maxIterations <= 10)
						maxIterations = 1;
					else
						maxIterations -= 10;

					isConfigured = false;

					canvas.display();
					logger.log(Level.INFO, "Set maxIterations to " + maxIterations);
				} else if (e.getKeyCode() == KeyEvent.VK_P && e.getModifiers() == 0) {
					fp64 = !fp64;

					isConfigured = false;
					isCompiled = false;
					isBufferInitialized = false;

					logger.log(Level.INFO, "Using " + (fp64 ? "64" : "32") + "-bit floats");
					canvas.display();
				} else if (e.getKeyCode() == KeyEvent.VK_H && e.getModifiers() == 0) {
					printHelp();
				} else if (e.getKeyCode() == KeyEvent.VK_R && e.getModifiers() == 0) {
					area = getDefaultArea();
					isConfigured = false;

					logger.log(Level.INFO, "Resetting Area");
					canvas.display();
				} else if (e.getKeyCode() == KeyEvent.VK_C && e.getModifiers() == InputEvent.CTRL_MASK) {
					String cmd = MultibrotCLI.generateArgumentString(MultibrotGUI.this, false);

					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cmd), null);
					logger.log(Level.INFO, cmd);
				} else if (e.getKeyCode() == KeyEvent.VK_C && e.getModifiers() == (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) {
					String cmd = MultibrotCLI.generateArgumentString(MultibrotGUI.this, true);

					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cmd), null);
					logger.log(Level.INFO, cmd);
				} else if (e.getKeyCode() == KeyEvent.VK_SPACE && e.getModifiers() == 0) {
					try {
						BufferedWriter writer = null;
						try {
							writer = new BufferedWriter(new FileWriter("areas.txt", true));

							writer.write("#### " + (new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")).format(new Date(System.currentTimeMillis())) + " ####\n");
							writer.write(Double.toString(area.getX()) + "\n");
							writer.write(Double.toString(area.getY()) + "\n");
							writer.write(Double.toString(area.getWidth()) + "\n");
							writer.write(Double.toString(area.getHeight()) + "\n");
						} finally {
							if (writer != null)
								writer.close();
						}
						logger.log(Level.INFO, "Marked area " + area.getX() + ", " + area.getY() + ", " + area.getWidth() + ", " + area.getHeight()
								+ " as interesting.");
					} catch (Exception ex) {
						logger.log(Level.SEVERE, "Error saving area. " + ex);
					}
				}
			}
		};

		canvas.addMouseMotionListener(mouseAdapter);
		canvas.addMouseWheelListener(mouseAdapter);
		canvas.addKeyListener(keyAdapter);
	}

	public void printHelp() {
		logger.log(
				Level.INFO,
				"Usage: Press a mouse button and move the mouse to shift the area and scroll in/out to zoom in/out. Press +/- to increase/decrease maxIterations by 10, 'd' to switch precision and 'r' to reset the area. Control+C copies the command line arguments to reproduce this exact picture to your clipboard and Control+Shift+C copies the command line arguments to render this picture (with --pcycles set to 1) to your clipboard. Pressing 'h' prints this help.");
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
	}

	@Override
	protected CLContext createContext() {
		return sharedContext;
	}

	private void release(Window win) {
		if (sharedContext != null) {
			// release all resources
			logger.log(Level.INFO, "Releasing resources");
			sharedContext.release();
		}
		win.dispose();
	}

	@Override
	protected InputStream getSource() {
		return getClass().getResourceAsStream("MultibrotFast.cl");
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
