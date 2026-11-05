package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Show a SEQ / PRG file as it would look on a C64 screen: PETSCII text with colour codes,
 * reverse, cursor movement and clear screen, or a raw screen RAM dump (screen codes).
 */
public class PetsciiScreenDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int COLS = 40;
	private static final int MAX_ROWS = 1000;

	private static final int[] COLOR_CODES = { 0x90, 0x05, 0x1c, 0x9f, 0x9c, 0x1e, 0x1f, 0x9e, 0x81, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b };

	private final byte[] raw;
	private final JComboBox<String> modeBox = new JComboBox<>(new String[] { "PETSCII text (SEQ / printed output)", "Screen RAM (screen codes)" });
	private final JCheckBox skipAddress = new JCheckBox("Skip 2 byte load address", false);
	private final JCheckBox lowerCase = new JCheckBox("Lower case character set", false);
	private final JSpinner offset = new JSpinner(new SpinnerNumberModel(0, 0, 65535, 1));
	private final JComboBox<Integer> zoomBox = new JComboBox<>(new Integer[] { 1, 2, 3 });
	private final JLabel info = new JLabel(" ");
	private final Screen screen = new Screen();

	private final int[][] chars = new int[MAX_ROWS][COLS];
	private final int[][] colors = new int[MAX_ROWS][COLS];
	private int usedRows = 25;

	public PetsciiScreenDialog(java.awt.Frame owner, String name, byte[] data) {
		super(owner, "PETSCII screen - " + name, false);
		this.raw = data == null ? new byte[0] : data;
		var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Show as"));
		top.add(modeBox);
		top.add(skipAddress);
		top.add(lowerCase);
		top.add(new JLabel("Start offset"));
		top.add(offset);
		top.add(new JLabel("Zoom"));
		top.add(zoomBox);
		zoomBox.setSelectedItem(2);
		if (raw.length >= 1000 && raw.length <= 1030 && !looksLikeText()) {
			modeBox.setSelectedIndex(1);
		}
		var bottom = new JPanel(new BorderLayout());
		var close = new JButton("Close");
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(close);
		bottom.add(info, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.EAST);
		setLayout(new BorderLayout());
		add(top, BorderLayout.NORTH);
		add(new JScrollPane(screen), BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
		java.awt.event.ActionListener redo = e -> interpret();
		modeBox.addActionListener(redo);
		skipAddress.addActionListener(redo);
		lowerCase.addActionListener(e -> screen.repaint());
		zoomBox.addActionListener(redo);
		offset.addChangeListener(e -> interpret());
		close.addActionListener(e -> dispose());
		interpret();
		setSize(Math.max(40 * 8 * 2 + 80, 940), 25 * 8 * 2 + 150);
		setLocationRelativeTo(owner);
	}

	private boolean looksLikeText() {
		int controls = 0;
		for (var b : raw) {
			int v = b & 0xff;
			if (v == 0x0d || v == 0x93 || v == 0x11 || v == 0x13) {
				controls++;
			}
		}
		return controls > 3;
	}

	private void interpret() {
		for (var row : chars) {
			java.util.Arrays.fill(row, 0x20);
		}
		for (var row : colors) {
			java.util.Arrays.fill(row, C64.LIGHT_BLUE);
		}
		int start = (skipAddress.isSelected() ? 2 : 0) + (Integer) offset.getValue();
		usedRows = 25;
		if (modeBox.getSelectedIndex() == 1) {
			for (int i = 0; i < 1000 && start + i < raw.length; i++) {
				chars[i / COLS][i % COLS] = raw[start + i] & 0xff;
			}
		} else {
			runStream(start);
		}
		int zoom = (Integer) zoomBox.getSelectedItem();
		screen.setPreferredSize(new Dimension(COLS * 8 * zoom + 40, usedRows * 8 * zoom + 40));
		screen.revalidate();
		screen.repaint();
		info.setText(raw.length + " bytes, " + usedRows + " rows");
	}

	private void runStream(int start) {
		int row = 0;
		int col = 0;
		int color = C64.LIGHT_BLUE;
		boolean reverse = false;
		int maxRow = 24;
		boolean lowerSet = lowerCase.isSelected();
		boolean charsetSwitched = false;
		for (int i = start; i < raw.length; i++) {
			int b = raw[i] & 0xff;
			int colorIndex = indexOfColor(b);
			if (b == 0x0e || b == 0x8e) {
				lowerSet = b == 0x0e;
				charsetSwitched = true;
			} else if (colorIndex >= 0) {
				color = colorIndex;
			} else if (b == 0x93) {
				for (var r : chars) {
					java.util.Arrays.fill(r, 0x20);
				}
				row = 0;
				col = 0;
				maxRow = 24;
			} else if (b == 0x13) {
				row = 0;
				col = 0;
			} else if (b == 0x0d || b == 0x8d) {
				col = 0;
				row++;
				reverse = false;
			} else if (b == 0x11) {
				row++;
			} else if (b == 0x91) {
				row = Math.max(0, row - 1);
			} else if (b == 0x1d) {
				col++;
				if (col >= COLS) {
					col = 0;
					row++;
				}
			} else if (b == 0x9d) {
				if (col > 0) {
					col--;
				} else if (row > 0) {
					row--;
					col = COLS - 1;
				}
			} else if (b == 0x12) {
				reverse = true;
			} else if (b == 0x92) {
				reverse = false;
			} else if (b == 0x14) {
				if (col > 0) {
					col--;
					chars[row][col] = 0x20;
				}
			} else if ((b >= 0x20 && b < 0x80) || b >= 0xa0) {
				if (row >= MAX_ROWS) {
					break;
				}
				int sc = screenCodeFor(b);
				chars[row][col] = reverse ? (sc | 0x80) : sc;
				colors[row][col] = color;
				maxRow = Math.max(maxRow, row);
				col++;
				if (col >= COLS) {
					col = 0;
					row++;
				}
			}
			if (row >= MAX_ROWS) {
				row = MAX_ROWS - 1;
			}
			maxRow = Math.max(maxRow, Math.min(row, MAX_ROWS - 1));
		}
		usedRows = Math.max(25, Math.min(MAX_ROWS, maxRow + 1));
		if (charsetSwitched) {
			// the last character set switch in the file decides how the screen looks
			lowerCase.setSelected(lowerSet);
		}
	}

	/** Screen code for a printable PETSCII code. */
	private static int screenCodeFor(int b) {
		if (b < 0x40) {
			return b;
		} else if (b < 0x60) {
			return b - 0x40;
		} else if (b < 0x80) {
			return b - 0x20;
		} else if (b < 0xc0) {
			return b - 0x40;
		} else if (b < 0xff) {
			return b - 0x80;
		}
		return 0x5e;
	}

	private static int indexOfColor(int b) {
		for (int i = 0; i < COLOR_CODES.length; i++) {
			if (COLOR_CODES[i] == b) {
				return i;
			}
		}
		return -1;
	}

	private class Screen extends JComponent {
		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0;
			int zoom = (Integer) zoomBox.getSelectedItem();
			int cell = 8 * zoom;
			var border = C64.PALETTE[14];
			var bg = C64.PALETTE[C64.BLUE];
			g.setColor(border);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(bg);
			g.fillRect(20, 20, COLS * cell, usedRows * cell);
			for (int r = 0; r < usedRows; r++) {
				for (int c = 0; c < COLS; c++) {
					int sc = chars[r][c];
					if (sc != 0x20) {
						C64.drawScreenCode(g, sc, 20 + c * cell, 20 + r * cell, cell, cell, C64.PALETTE[colors[r][c]], bg, C64.Machine.C64, lowerCase.isSelected());
					}
				}
			}
		}
	}

	public static void show(java.awt.Frame owner, String name, byte[] data) {
		show(owner, name, data, false);
	}

	/** @param hasLoadAddress true for PRG files, whose first two bytes are the load address */
	public static void show(java.awt.Frame owner, String name, byte[] data, boolean hasLoadAddress) {
		var dlg = new PetsciiScreenDialog(owner, name, data);
		if (hasLoadAddress) {
			dlg.skipAddress.setSelected(true);
			dlg.interpret();
		}
		dlg.setVisible(true);
	}

	Color unused() {
		return Color.BLACK;
	}
}
