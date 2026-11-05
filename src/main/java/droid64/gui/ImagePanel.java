package droid64.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.swing.JPanel;

public class ImagePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private transient BufferedImage image;
	private String name;
	private int scale = 1;

	public ImagePanel() {
		image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		name = "";
		var dim = new Dimension(320, 200);
		setSize(dim);
		setMinimumSize(dim);
		setPreferredSize(dim);
	}

	public ImagePanel(CbmPicture picture) throws IOException {
		BufferedImage bufImage = picture.getImage();
		this.image = picture.getImage();
		this.name = picture.getName();
		var dim = new Dimension(bufImage.getWidth(), bufImage.getHeight());
		setSize(dim);
		setMinimumSize(dim);
		setPreferredSize(dim);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawImage(image, 0, 0, image.getWidth() * scale, image.getHeight() * scale, this);
	}

	public void setImage(CbmPicture picture) throws IOException {
		if (picture != null) {
			var img = picture.getImage();
			this.image = img;
			this.name = picture.getName();
			update();
		}
	}

	public void setImage(Image img, String name) {
		this.name = name;
		if (img instanceof BufferedImage) {
			image = (BufferedImage) img;
		} else {
			image = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			var bGr = image.createGraphics();
			bGr.drawImage(img, 0, 0, null);
			bGr.dispose();
		}
		update();
	}

	private void update() {
		var dim = new Dimension(image.getWidth() * scale, image.getHeight() * scale);
		setSize(dim);
		setMinimumSize(dim);
		setPreferredSize(dim);
		invalidate();
		repaint();
	}

	/** @param scale display zoom, 1 = real pixels */
	public void setScale(int scale) {
		this.scale = Math.max(1, scale);
		update();
		revalidate();
	}

	@Override
	public String getName() {
		return name;
	}

	public BufferedImage getImage() {
		return this.image;
	}
}
