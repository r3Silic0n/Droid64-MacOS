package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Show the bytes of a file as C64 graphics: character set, sprites, hires or multicolor bitmap. */
public class GraphicsViewerDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** What the data is. */
	public enum Mode {
		CHARSET("Character set"), SPRITES("Sprites"), HIRES("Hires bitmap"), MULTI("Multicolor bitmap (Koala)");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final byte[] raw;
	private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
	private final JCheckBox skipAddress = new JCheckBox("Skip 2 byte load address", true);
	private final JCheckBox multicolor = new JCheckBox("Multicolor");
	private final JSpinner offset = new JSpinner(new SpinnerNumberModel(0, 0, 65535, 8));
	private final JComboBox<Integer> scaleBox = new JComboBox<>(new Integer[] { 1, 2, 3, 4, 5, 6 });
	private final JComboBox<String> bgBox = new JComboBox<>(C64.COLOR_NAMES);
	private final JComboBox<String> fgBox = new JComboBox<>(C64.COLOR_NAMES);
	private final JComboBox<String> mc1Box = new JComboBox<>(C64.COLOR_NAMES);
	private final JComboBox<String> mc2Box = new JComboBox<>(C64.COLOR_NAMES);
	private final JLabel imageLabel = new JLabel();
	private final JLabel info = new JLabel(" ");
	private BufferedImage current;
	private boolean loading = true;

	public GraphicsViewerDialog(java.awt.Frame owner, String name, byte[] data) {
		super(owner, "Graphics viewer - " + name, false);
		this.raw = data == null ? new byte[0] : data;

		bgBox.setSelectedIndex(0);
		fgBox.setSelectedIndex(1);
		mc1Box.setSelectedIndex(2);
		mc2Box.setSelectedIndex(3);
		scaleBox.setSelectedItem(3);
		detect();

		var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Show as"));
		top.add(modeBox);
		top.add(skipAddress);
		top.add(new JLabel("Start offset"));
		top.add(offset);
		top.add(multicolor);
		top.add(new JLabel("Zoom"));
		top.add(scaleBox);
		var colors = new JPanel(new FlowLayout(FlowLayout.LEFT));
		colors.add(new JLabel("Background"));
		colors.add(bgBox);
		colors.add(new JLabel("Foreground"));
		colors.add(fgBox);
		colors.add(new JLabel("Multicolor 1"));
		colors.add(mc1Box);
		colors.add(new JLabel("Multicolor 2"));
		colors.add(mc2Box);
		var north = new JPanel(new BorderLayout());
		north.add(top, BorderLayout.NORTH);
		north.add(colors, BorderLayout.SOUTH);

		var bottom = new JPanel(new BorderLayout());
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		var save = new JButton("Save as PNG...");
		var close = new JButton("Close");
		buttons.add(save);
		buttons.add(close);
		bottom.add(info, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.EAST);

		setLayout(new BorderLayout());
		add(north, BorderLayout.NORTH);
		var scroll = new JScrollPane(imageLabel);
		imageLabel.setHorizontalAlignment(JLabel.LEFT);
		imageLabel.setVerticalAlignment(JLabel.TOP);
		add(scroll, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);

		java.awt.event.ActionListener redraw = e -> {
			if (!loading) {
				render();
			}
		};
		modeBox.addActionListener(redraw);
		skipAddress.addActionListener(redraw);
		multicolor.addActionListener(redraw);
		scaleBox.addActionListener(redraw);
		bgBox.addActionListener(redraw);
		fgBox.addActionListener(redraw);
		mc1Box.addActionListener(redraw);
		mc2Box.addActionListener(redraw);
		offset.addChangeListener(e -> redraw.actionPerformed(null));
		close.addActionListener(e -> dispose());
		save.addActionListener(e -> savePng());

		loading = false;
		render();
		setSize(900, 700);
		setLocationRelativeTo(owner);
	}

	/** Pick the most likely type from the size of the file. */
	private void detect() {
		int len = raw.length;
		if (len >= 10000 && len <= 10010) {
			modeBox.setSelectedItem(Mode.MULTI);
		} else if (len >= 9000 && len <= 9020) {
			modeBox.setSelectedItem(Mode.HIRES);
		} else if (len == 2048 || len == 2050 || len == 4096 || len == 4098) {
			modeBox.setSelectedItem(Mode.CHARSET);
		} else if (len % 64 == 0 || (len - 2) % 64 == 0) {
			modeBox.setSelectedItem(Mode.SPRITES);
		} else {
			modeBox.setSelectedItem(Mode.CHARSET);
		}
		skipAddress.setSelected(len % 64 != 0 || len == 2048 || len == 4096 ? len % 8 == 2 : true);
		if (len == 2048 || len == 4096 || len % 64 == 0) {
			skipAddress.setSelected(false);
		}
	}

	private int base() {
		return (skipAddress.isSelected() ? 2 : 0) + (Integer) offset.getValue();
	}

	private int at(int index) {
		int p = base() + index;
		return p >= 0 && p < raw.length ? raw[p] & 0xff : 0;
	}

	private boolean has(int index) {
		int p = base() + index;
		return p >= 0 && p < raw.length;
	}

	private void render() {
		var mode = (Mode) modeBox.getSelectedItem();
		int bg = bgBox.getSelectedIndex();
		int fg = fgBox.getSelectedIndex();
		int mc1 = mc1Box.getSelectedIndex();
		int mc2 = mc2Box.getSelectedIndex();
		boolean mc = multicolor.isSelected();
		multicolor.setEnabled(mode == Mode.CHARSET || mode == Mode.SPRITES);
		if (mode == Mode.CHARSET) {
			current = renderCharset(bg, fg, mc1, mc2, mc);
		} else if (mode == Mode.SPRITES) {
			current = renderSprites(bg, fg, mc1, mc2, mc);
		} else if (mode == Mode.HIRES) {
			current = renderHires(bg, fg);
		} else {
			current = renderMulti(bg);
		}
		int scale = (Integer) scaleBox.getSelectedItem();
		var scaled = new BufferedImage(current.getWidth() * scale, current.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < scaled.getHeight(); y++) {
			for (int x = 0; x < scaled.getWidth(); x++) {
				scaled.setRGB(x, y, current.getRGB(x / scale, y / scale));
			}
		}
		imageLabel.setIcon(new ImageIcon(scaled));
		info.setText(mode + ": " + raw.length + " bytes, starting at byte " + base() + " - " + current.getWidth() + " x " + current.getHeight() + " pixels");
	}

	private BufferedImage renderCharset(int bg, int fg, int mc1, int mc2, boolean mc) {
		int available = Math.max(0, raw.length - base());
		int chars = Math.max(1, Math.min(512, available / 8));
		int cols = 32;
		int rows = (chars + cols - 1) / cols;
		var img = new BufferedImage(cols * 8, rows * 8, BufferedImage.TYPE_INT_RGB);
		for (int c = 0; c < chars; c++) {
			int ox = (c % cols) * 8;
			int oy = (c / cols) * 8;
			for (int line = 0; line < 8; line++) {
				int b = at(c * 8 + line);
				drawLine(img, ox, oy + line, b, 8, mc, bg, fg, mc1, mc2);
			}
		}
		return img;
	}

	/** Draw 8 pixels from a byte. */
	private static void drawLine(BufferedImage img, int x, int y, int b, int width, boolean mc, int bg, int fg, int mc1, int mc2) {
		if (mc) {
			for (int p = 0; p < width / 2; p++) {
				int bits = (b >> (6 - p * 2)) & 3;
				int col = bits == 0 ? bg : bits == 1 ? mc1 : bits == 2 ? mc2 : fg;
				img.setRGB(x + p * 2, y, C64.PALETTE[col].getRGB());
				img.setRGB(x + p * 2 + 1, y, C64.PALETTE[col].getRGB());
			}
		} else {
			for (int p = 0; p < width; p++) {
				int bit = (b >> (7 - p)) & 1;
				img.setRGB(x + p, y, C64.PALETTE[bit == 1 ? fg : bg].getRGB());
			}
		}
	}

	private BufferedImage renderSprites(int bg, int fg, int mc1, int mc2, boolean mc) {
		int available = Math.max(0, raw.length - base());
		int count = Math.max(1, Math.min(512, available / 64));
		int cols = 8;
		int rows = (count + cols - 1) / cols;
		var img = new BufferedImage(cols * 26, rows * 23, BufferedImage.TYPE_INT_RGB);
		var g = img.createGraphics();
		g.setColor(C64.PALETTE[bg]);
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.dispose();
		for (int s = 0; s < count; s++) {
			int ox = (s % cols) * 26 + 1;
			int oy = (s / cols) * 23 + 1;
			for (int line = 0; line < 21; line++) {
				for (int byteNo = 0; byteNo < 3; byteNo++) {
					int b = at(s * 64 + line * 3 + byteNo);
					if (mc) {
						for (int p = 0; p < 4; p++) {
							int bits = (b >> (6 - p * 2)) & 3;
							if (bits != 0) {
								int col = bits == 1 ? mc1 : bits == 2 ? fg : mc2;
								int px = ox + byteNo * 8 + p * 2;
								img.setRGB(px, oy + line, C64.PALETTE[col].getRGB());
								img.setRGB(px + 1, oy + line, C64.PALETTE[col].getRGB());
							}
						}
					} else {
						for (int p = 0; p < 8; p++) {
							if (((b >> (7 - p)) & 1) == 1) {
								img.setRGB(ox + byteNo * 8 + p, oy + line, C64.PALETTE[fg].getRGB());
							}
						}
					}
				}
			}
		}
		return img;
	}

	private BufferedImage renderHires(int bg, int fg) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		boolean haveScreen = has(8000 + 999);
		for (int cell = 0; cell < 1000; cell++) {
			int scr = haveScreen ? at(8000 + cell) : (fg << 4) | bg;
			int cfg = scr >> 4;
			int cbg = scr & 15;
			int ox = (cell % 40) * 8;
			int oy = (cell / 40) * 8;
			for (int line = 0; line < 8; line++) {
				drawLine(img, ox, oy + line, at(cell * 8 + line), 8, false, cbg, cfg, 0, 0);
			}
		}
		return img;
	}

	private BufferedImage renderMulti(int bgSel) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		boolean haveScreen = has(8000 + 999);
		boolean haveColor = has(9000 + 999);
		int bg = has(10000) ? at(10000) & 15 : bgSel;
		for (int cell = 0; cell < 1000; cell++) {
			int scr = haveScreen ? at(8000 + cell) : 0x23;
			int col = haveColor ? at(9000 + cell) & 15 : 1;
			int ox = (cell % 40) * 8;
			int oy = (cell / 40) * 8;
			for (int line = 0; line < 8; line++) {
				drawLine(img, ox, oy + line, at(cell * 8 + line), 8, true, bg, col, scr >> 4, scr & 15);
			}
		}
		return img;
	}

	private void savePng() {
		if (current == null) {
			return;
		}
		var dlg = new FileDialog(this, "Save as PNG", FileDialog.SAVE);
		dlg.setFile("picture.png");
		dlg.setVisible(true);
		if (dlg.getFile() != null) {
			try {
				var f = new File(dlg.getDirectory(), dlg.getFile());
				int scale = (Integer) scaleBox.getSelectedItem();
				var out = new BufferedImage(current.getWidth() * scale, current.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
				for (int y = 0; y < out.getHeight(); y++) {
					for (int x = 0; x < out.getWidth(); x++) {
						out.setRGB(x, y, current.getRGB(x / scale, y / scale));
					}
				}
				ImageIO.write(out, "png", f);
			} catch (java.io.IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Save as PNG", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public static void show(java.awt.Frame owner, String name, byte[] data) {
		new GraphicsViewerDialog(owner, name, data).setVisible(true);
	}

	/** For tests. */
	void setMode(Mode m) {
		modeBox.setSelectedItem(m);
	}

	Color unused() {
		return Color.BLACK;
	}
}
