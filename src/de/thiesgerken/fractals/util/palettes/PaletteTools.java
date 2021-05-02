package de.thiesgerken.fractals.util.palettes;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import de.thiesgerken.fractals.multibrot.Multibrot;
import de.thiesgerken.fractals.multibrot.MultibrotRendererQuality;
import de.thiesgerken.fractals.util.Rectangle;
import de.thiesgerken.fractals.util.Size;

public final class PaletteTools {

	public static void createPalettes() {
		Random rnd = new Random(System.nanoTime());

		for (int i = 0; i < 200; i++) {
			SortedMap<Double, Integer> map = new TreeMap<Double, Integer>();

			MultibrotRendererQuality renderer = new MultibrotRendererQuality();
			renderer.setMaxIterations(3000);
			renderer.setSuperSampling(new Size(1, 1));
			renderer.setSize(new Size(512, 512));

			int count = 6; // + rnd.nextInt(2);

			double lastH = -1;

			for (int j = 0; j < count; j++) {
				double h;

				if (lastH == -1)
					h = rnd.nextDouble() * 2 * Math.PI;
				else
					h = Math.abs((lastH + (rnd.nextDouble() * 0.5 + 0.5 - 0.5) * 2 * 0.75 * Math.PI) % (2 * Math.PI));

				double s = 1;
				double v = rnd.nextDouble() * 0.4 + (j == count - 1 ? 0.5 : 0.6);

				// if (rnd.nextInt(15) == 0 && j != count - 1)
				// v = 0;
				if (j <= 1)
					v = 0;

				map.put(1 - Math.pow(1.4, -j), hsvtorgb(h, s, v).getRGB());
				lastH = h;
			}

			Palette pal = new Palette(map, 2500, "");

			renderer.setPalette(pal);

			try {
				ImageIO.write(pal.save(50), "png", new File("E:\\Downloaded\\palettes\\" + i + ".png"));
			} catch (IOException e1) {
			}

			for (int k = 0; k < 5; k++) {
				if (k == 0)
					renderer.setArea(new Rectangle(-0.8067167602654082, -0.18176859345840057, 0.005939316004514694, 0.005939316004514694));
				else if (k == 1)
					renderer.setArea(new Rectangle(0.32850209983298756, -0.04886954509579175, 7.34781609895479E-5, 7.34781609895479E-5));
				else if (k == 2)
					renderer.setArea(new Rectangle(0.3614533327385504, -0.07203446147148164, 4.2997788086639985E-4, 4.2997788086639985E-4));
				else if (k == 3)
					renderer.setArea(new Rectangle(0.347955052461807, -0.06369931771926937, 3.0266957148647364E-6, 3.0266957148647364E-6));
				else if (k == 4)
					renderer.setArea(new Rectangle(-0.7729192989296281, -0.1053321989311371, 0.011878632009029388, 0.011878632009029388));

				renderer.setUse64bitFloats(k == 3);

				try {
					ImageIO.write(renderer.createImage(), "png", new File("E:\\Downloaded\\palettes\\" + i + "_" + k + ".png"));
				} catch (Exception e) {
				}
			}

			renderer.release();
		}
	}

	public static void testPalettes() {
		for (String f : (new File("E:\\Downloaded\\palettes\\")).list()) {
			MultibrotRendererQuality renderer = new MultibrotRendererQuality();
			renderer.setMaxIterations(3000);
			renderer.setSuperSampling(new Size(4, 4));
			renderer.setSize(new Size(1024, 1024));

			try {
				renderer.setPalette(new Palette("E:\\Downloaded\\palettes\\" + f));
			} catch (IOException e1) {
			}

			for (int k = 0; k <= 7; k++) {
				if (k == 0)
					renderer.setArea(new Rectangle(-0.6022716522216798, -0.6677799224853516, 0.0087890625, 0.0087890625));
				if (k == 1)
					renderer.setArea(new Rectangle(-0.8067167602654082, -0.18176859345840057, 0.005939316004514694, 0.005939316004514694));
				else if (k == 2)
					renderer.setArea(new Rectangle(0.36420760318859, -0.07472417712127012, 1.5907145139607438E-4, 1.5907145139607438E-4));
				else if (k == 3)
					renderer.setArea(new Rectangle(0.32850209983298756, -0.04886954509579175, 7.34781609895479E-5, 7.34781609895479E-5));
				else if (k == 4)
					renderer.setArea(new Rectangle(0.3614533327385504, -0.07203446147148164, 4.2997788086639985E-4, 4.2997788086639985E-4));
				else if (k == 5)
					renderer.setArea(new Rectangle(0.347955052461807, -0.06369931771926937, 3.0266957148647364E-6, 3.0266957148647364E-6)); // fp64
				else if (k == 6)
					renderer.setArea(new Rectangle(-0.7729192989296281, -0.1053321989311371, 0.011878632009029388, 0.011878632009029388));
				else if (k == 7)
					renderer.setArea(Multibrot.getDefaultArea());

				renderer.setUse64bitFloats(k == 5);

				try {
					ImageIO.write(renderer.createImage(), "png", new File("E:\\Downloaded\\test\\" + f.substring(0, f.length() - 4) + " " + k + ".png"));
				} catch (Exception e) {
				} finally {

				}
			}

			renderer.release();
		}
	}

	public static Color hsvtorgb(double h, double s, double v) {
		int hi = (int) (h * 3 / Math.PI);
		double f = h * 3 / Math.PI - hi;

		int p = (int) (v * (1 - s) * 255);
		int q = (int) (v * (1 - s * f) * 255);
		int t = (int) (v * (1 - s * (1 - f)) * 255);

		if (hi == 1)
			return new Color(q, (int) (v * 255), p);

		if (hi == 2)
			return new Color(p, (int) (v * 255), t);

		if (hi == 3)
			return new Color(p, q, (int) (v * 255));

		if (hi == 4)
			return new Color(t, p, (int) (v * 255));

		if (hi == 5)
			return new Color((int) (v * 255), p, q);

		return new Color((int) (v * 255), t, p);
	}

}
