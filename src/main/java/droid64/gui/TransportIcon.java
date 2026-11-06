package droid64.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Small flat play / pause / stop symbols for the music player, drawn in the colour of the button text. */
final class TransportIcon implements Icon {

	static final TransportIcon PLAY = new TransportIcon(0);
	static final TransportIcon PAUSE = new TransportIcon(1);
	static final TransportIcon STOP = new TransportIcon(2);
	static final TransportIcon PREVIOUS = new TransportIcon(3);
	static final TransportIcon NEXT = new TransportIcon(4);

	private static final int SIZE = 14;
	private final int kind;

	private TransportIcon(int kind) {
		this.kind = kind;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		var base = c.getForeground() != null ? c.getForeground() : Color.DARK_GRAY;
		// a bit softer than the text colour, so that the symbols do not shout
		g2.setColor(c.isEnabled() ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 215) : new Color(150, 150, 150));
		g2.translate(x, y);
		switch (kind) {
		case 0:
			var p = new Path2D.Float();
			p.moveTo(3, 1.5);
			p.lineTo(12.5, 7);
			p.lineTo(3, 12.5);
			p.closePath();
			g2.fill(p);
			break;
		case 1:
			g2.fill(new RoundRectangle2D.Float(2.5f, 1.5f, 3.2f, 11, 1.2f, 1.2f));
			g2.fill(new RoundRectangle2D.Float(8.3f, 1.5f, 3.2f, 11, 1.2f, 1.2f));
			break;
		case 3:
			g2.fill(new RoundRectangle2D.Float(2.5f, 2f, 2.4f, 10, 1f, 1f));
			var l = new Path2D.Float();
			l.moveTo(12, 2);
			l.lineTo(5.5f, 7);
			l.lineTo(12, 12);
			l.closePath();
			g2.fill(l);
			break;
		case 4:
			g2.fill(new RoundRectangle2D.Float(9.1f, 2f, 2.4f, 10, 1f, 1f));
			var r = new Path2D.Float();
			r.moveTo(2, 2);
			r.lineTo(8.5f, 7);
			r.lineTo(2, 12);
			r.closePath();
			g2.fill(r);
			break;
		default:
			g2.fill(new RoundRectangle2D.Float(2.5f, 2.5f, 9, 9, 1.8f, 1.8f));
			break;
		}
		g2.dispose();
	}

	@Override
	public int getIconWidth() {
		return SIZE;
	}

	@Override
	public int getIconHeight() {
		return SIZE;
	}
}
