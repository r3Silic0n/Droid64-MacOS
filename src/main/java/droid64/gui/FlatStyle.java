package droid64.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

/**
 * Square, flat, colored buttons that look the same under every look-and-feel
 * (including macOS Aqua, which ignores button background colors).
 * Adjacent buttons in a GridLayout touch each other.
 */
public final class FlatStyle {

	private FlatStyle() {
	}

	static void paintBackground(Graphics g, AbstractButton b) {
		var m = b.getModel();
		Color c = b.getBackground();
		if (m.isSelected() || m.isPressed() && m.isArmed()) {
			c = darker(c, 0.82f);
		} else if (m.isRollover() && b.isEnabled()) {
			c = lighter(c);
		}
		var g2 = (Graphics2D) g.create();
		g2.setColor(c);
		g2.fillRect(0, 0, b.getWidth(), b.getHeight());
		g2.setColor(darker(b.getBackground(), 0.55f));
		g2.drawRect(0, 0, b.getWidth() - 1, b.getHeight() - 1);
		g2.dispose();
	}

	private static Color darker(Color c, float f) {
		return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
	}

	private static Color lighter(Color c) {
		return new Color(Math.min(255, c.getRed() + 18), Math.min(255, c.getGreen() + 18), Math.min(255, c.getBlue() + 18));
	}

	public static class Button extends JButton {
		private static final long serialVersionUID = 1L;
		private boolean keepBorder = false;

		public Button(String text) {
			super(text);
		}

		/** Button which also paints its own (colored) border, e.g. a color swatch. */
		public static Button withBorder(String text) {
			var b = new Button(text);
			b.keepBorder = true;
			return b;
		}

		@Override
		protected void paintBorder(Graphics g) {
			if (keepBorder && getBorder() != null) {
				getBorder().paintBorder(this, g, 0, 0, getWidth(), getHeight());
			}
		}

		@Override
		public void updateUI() {
			setUI(new BasicButtonUI());
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setRolloverEnabled(true);
		}

		@Override
		protected void paintComponent(Graphics g) {
			paintBackground(g, this);
			super.paintComponent(g);
		}
	}

	public static class ToggleButton extends JToggleButton {
		private static final long serialVersionUID = 1L;

		public ToggleButton(String text) {
			super(text);
		}

		@Override
		public void updateUI() {
			setUI(new BasicToggleButtonUI());
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setRolloverEnabled(true);
		}

		@Override
		protected void paintComponent(Graphics g) {
			paintBackground(g, this);
			super.paintComponent(g);
		}
	}

}
