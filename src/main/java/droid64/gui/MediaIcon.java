package droid64.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/** Small pictograms: a floppy disk and a cassette tape. */
final class MediaIcon implements Icon {

	static final MediaIcon DISK = new MediaIcon(false);
	static final MediaIcon TAPE = new MediaIcon(true);

	private static final int W = 18;
	private static final int H = 14;
	private final boolean tape;

	private MediaIcon(boolean tape) {
		this.tape = tape;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(x, y);
		if (tape) {
			g2.setColor(new Color(70, 70, 80));
			g2.fillRoundRect(0, 1, W - 1, H - 2, 3, 3);
			g2.setColor(new Color(225, 225, 215));
			g2.fillRoundRect(2, 3, W - 5, 5, 2, 2);
			g2.setColor(new Color(70, 70, 80));
			g2.fillOval(4, 4, 3, 3);
			g2.fillOval(W - 8, 4, 3, 3);
			g2.setColor(new Color(190, 190, 180));
			g2.fillRect(5, H - 5, W - 10, 3);
		} else {
			g2.setColor(new Color(40, 60, 110));
			g2.fillRoundRect(1, 0, H, H, 3, 3);
			g2.setColor(new Color(205, 205, 205));
			g2.fillRect(4, 0, 8, 5);
			g2.setColor(new Color(40, 60, 110));
			g2.fillRect(9, 1, 2, 3);
			g2.setColor(new Color(235, 235, 225));
			g2.fillRect(3, 8, 10, 5);
		}
		g2.dispose();
	}

	@Override
	public int getIconWidth() {
		return W;
	}

	@Override
	public int getIconHeight() {
		return H;
	}
}
