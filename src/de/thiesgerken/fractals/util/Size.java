package de.thiesgerken.fractals.util;

import java.awt.Dimension;

public class Size {
	private int width;
	private int height;

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public Size(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public String toString() {
		return Formatter.formatInt(width) + "x" + Formatter.formatInt(height);
	}

	public Dimension toDimension() {
		return new Dimension(width, height);
	}

}
