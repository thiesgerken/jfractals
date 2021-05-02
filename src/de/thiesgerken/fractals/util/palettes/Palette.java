package de.thiesgerken.fractals.util.palettes;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;

public class Palette {

	private int[] colors;
	private String name;

	public Palette(String name) throws IOException {
		this();

		if (name.equals("grey"))
			return;

		BufferedImage img;

		try {
			img = ImageIO.read(getClass().getResourceAsStream(name + ".png"));
		} catch (Exception ex) {
			try {
				img = ImageIO.read(new File(name));
			} catch (Exception exx) {
				throw new IOException("'" + name + "' is neither a built-in palette nor a valid (and readable) image file.", exx);
			}
		}

		colors = new int[img.getWidth()];

		for (int i = 0; i < colors.length; i++)
			colors[i] = img.getRGB(i, 0);

		this.name = name;
	}

	public Palette() {
		colors = new int[512];

		for (int i = 0; i < 256; i++) {
			colors[i] = i | (i << 8) | (i << 16);
			colors[511 - i] = colors[i];
		}

		this.name = "grey";
	}

	public Palette(SortedMap<Double, Integer> colorLocations, int length, String name) {
		this();

		if (colorLocations == null || length <= 0 || colorLocations.size() < 2)
			return;

		Double[] keys = colorLocations.keySet().toArray(new Double[0]);

		double start = keys[0];
		double end = keys[keys.length - 1];
		double width = end - start;

		colors = new int[length];

		for (int i = 0; i < keys.length - 1; i++) {
			double gradientStart = (keys[i] - start) / width * (length);
			double gradientEnd = (keys[i + 1] - start) / width * (length);

			Color c1 = new Color(colorLocations.get(keys[i]));
			Color c2 = new Color(colorLocations.get(keys[i + 1]));

			for (int j = (int) gradientStart; j < (int) gradientEnd; j++) {
				double factor = (j - gradientStart) / (gradientEnd - gradientStart);

				if (factor > 1)
					factor = 1;

				if (factor < 0)
					factor = 0;

				Color c = new Color((int) (c1.getRed() + (c2.getRed() - c1.getRed()) * factor),
						(int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * factor), (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * factor));

				colors[j] = c.getRGB();
			}
		}
	}

	public Palette(int[] colors, String name) {
		this.colors = colors;
		this.name = name;
	}

	public Palette(BufferedImage img, String name) {
		colors = new int[img.getWidth()];

		for (int i = 0; i < colors.length; i++)
			colors[i] = img.getRGB(i, 0);

		this.name = name;
	}

	public int getLength() {
		return colors.length;
	}

	public int[] getColors() {
		return colors;
	}

	public String getName() {
		return name;
	}

	public BufferedImage save(int height) {
		BufferedImage img = new BufferedImage(colors.length, height, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < colors.length; x++)
			for (int y = 0; y < height; y++)
				img.setRGB(x, y, colors[x]);

		return img;
	}

	public ByteBuffer createBuffer(ByteOrder order) {
		ByteBuffer result = ByteBuffer.allocateDirect(colors.length * 4);
		result.order(order);

		for (int i = 0; i < colors.length; i++)
			result.putInt(4 * i, colors[i]);

		result.rewind();

		return result;
	}

	@Override
	public String toString() {
		return (name.isEmpty() ? "" : "'" + name + "' ") + "(" + getLength() + " color" + (getLength() != 1 ? "s)" : ")");
	}

	public static ArrayList<String> listPaletteNames() throws URISyntaxException, IOException {
		ArrayList<String> result = new ArrayList<String>();

		result.add("grey");

		for (int i = 1; i <= 4; i++)
			result.add("cyclic" + String.format("%02d", i));
		
		for (int i = 1; i <= 16; i++)
			result.add("dark" + String.format("%02d", i));
		
		for (int i = 1; i <= 18; i++)
			result.add("normal" + String.format("%02d", i));

		return result;
	}

	public static String[] listResources() throws URISyntaxException, UnsupportedEncodingException, IOException {
		Class<? extends Palette> clazz = (new Palette()).getClass();
		URL dirURL = clazz.getClassLoader().getResource(clazz.getName().replace(".", "/") + ".class");

		System.out.println(clazz.getClassLoader().getClass().getName());
		if (dirURL == null)
			throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);

		if (dirURL.getProtocol().equals("file"))
			return (new File(dirURL.toURI())).getParentFile().list();
		else {
			String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
			JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));

			// gives ALL entries in jar
			Enumeration<JarEntry> entries = jar.entries();

			// avoid duplicates in case it is a subdirectory
			Set<String> result = new HashSet<String>();

			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();

				// filter according to the path
				if (name.startsWith("")) {
					String entry = name.substring("".length());
					int checkSubdir = entry.indexOf("/");

					// if it is a subdirectory, we just return the directory
					// name
					if (checkSubdir >= 0)
						entry = entry.substring(0, checkSubdir);

					result.add(entry);
				}
			}

			jar.close();
			return result.toArray(new String[result.size()]);
		}
	}
}
