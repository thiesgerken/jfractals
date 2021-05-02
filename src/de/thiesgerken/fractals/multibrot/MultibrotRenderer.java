package de.thiesgerken.fractals.multibrot;

import java.awt.image.BufferedImage;

public abstract class MultibrotRenderer extends Multibrot {
	protected int desiredPartSize;

	public abstract BufferedImage createImage() throws Exception;

	public int getDesiredPartSize() {
		return desiredPartSize;
	}

	public void setDesiredPartSize(int desiredPartSize) {
		this.desiredPartSize = desiredPartSize;
	}
}
