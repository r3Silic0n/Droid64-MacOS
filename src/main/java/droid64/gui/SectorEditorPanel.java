package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import droid64.d64.CbmException;
import droid64.d64.DiskImage;

/** Edit the bytes of any sector of a disk image, as hex and as PETSCII. Used inside the BAM window. */
public class SectorEditorPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int HEX_W = 26;
	private static final int ROW_H = 20;
	private static final int LABEL_W = 34;
	private static final int PET_CELL = 20;
	private static final int GAP = 16;

	private final transient DiskImage image;
	private final transient DiskPanel panel;
	private final JSpinner trackSpinner;
	private final JSpinner sectorSpinner;
	private final JLabel statusLabel = new JLabel(" ");
	private final JRadioButton hexMode = new JRadioButton("Edit hex", true);
	private final JRadioButton petMode = new JRadioButton("Edit PETSCII");
	private final Canvas canvas = new Canvas();

	private byte[] data = new byte[DiskImage.BLOCK_SIZE];
	private byte[] original = new byte[DiskImage.BLOCK_SIZE];
	private int track;
	private int sector;
	private int cursor = 0;
	private boolean highNibble = true;
	private boolean dirty = false;
	private boolean loading = false;

	/** Called after another sector has been loaded, with track and sector. */
	private transient java.util.function.BiConsumer<Integer, Integer> sectorListener;

	public SectorEditorPanel(DiskPanel panel, DiskImage image, int track, int sector) {
		super(new BorderLayout());
		this.panel = panel;
		this.image = image;
		this.track = track;
		this.sector = sector;

		trackSpinner = new JSpinner(new SpinnerNumberModel(track, 1, image.getTrackCount(), 1));
		sectorSpinner = new JSpinner(new SpinnerNumberModel(sector, 0, Math.max(0, image.getMaxSectors(track) - 1), 1));

		var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Track"));
		top.add(trackSpinner);
		top.add(new JLabel("Sector"));
		top.add(sectorSpinner);
		var prev = new JButton("<");
		var next = new JButton(">");
		var follow = new JButton("Follow link");
		top.add(prev);
		top.add(next);
		top.add(follow);
		var group = new ButtonGroup();
		group.add(hexMode);
		group.add(petMode);
		top.add(hexMode);
		top.add(petMode);

		var bottom = new JPanel(new BorderLayout());
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		var write = new JButton("Write sector");
		var revert = new JButton("Revert");
		buttons.add(write);
		buttons.add(revert);
		bottom.add(statusLabel, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.EAST);

		add(top, BorderLayout.NORTH);
		add(canvas, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);

		trackSpinner.addChangeListener(e -> {
			if (!loading) {
				gotoSector((Integer) trackSpinner.getValue(), 0);
			}
		});
		sectorSpinner.addChangeListener(e -> {
			if (!loading) {
				gotoSector(this.track, (Integer) sectorSpinner.getValue());
			}
		});
		prev.addActionListener(e -> step(-1));
		next.addActionListener(e -> step(1));
		follow.addActionListener(e -> {
			int t = data[0] & 0xff;
			int s = data[1] & 0xff;
			if (t != 0) {
				gotoSector(t, s);
			}
		});
		write.addActionListener(e -> writeSector());
		revert.addActionListener(e -> {
			data = original.clone();
			dirty = false;
			updateStatus();
			canvas.repaint();
		});
		loadSector(track, sector);
	}

	private void step(int delta) {
		int t = track;
		int s = sector + delta;
		if (s < 0) {
			t--;
			if (t < 1) {
				return;
			}
			s = image.getMaxSectors(t) - 1;
		} else if (s >= image.getMaxSectors(t)) {
			t++;
			if (t > image.getTrackCount()) {
				return;
			}
			s = 0;
		}
		gotoSector(t, s);
	}

	void setSectorListener(java.util.function.BiConsumer<Integer, Integer> listener) {
		this.sectorListener = listener;
	}

	/** Ask what to do with unsaved changes. Returns false if the user cancelled. */
	boolean confirmLeave() {
		if (!dirty) {
			return true;
		}
		int answer = JOptionPane.showConfirmDialog(this, "Write the changes to the disk image?", "Sector editor",
				JOptionPane.YES_NO_CANCEL_OPTION);
		if (answer == JOptionPane.YES_OPTION) {
			return writeSector();
		}
		return answer == JOptionPane.NO_OPTION;
	}

	/** Show another sector (asks about unsaved changes first). */
	void gotoSector(int t, int s) {
		if (!confirmLeave()) {
			loading = true;
			trackSpinner.setValue(track);
			sectorSpinner.setValue(sector);
			loading = false;
			return;
		}
		loadSector(t, s);
	}

	private void loadSector(int t, int s) {
		try {
			var block = image.getBlock(t, s);
			loading = true;
			track = t;
			sector = s;
			data = block;
			original = block.clone();
			dirty = false;
			cursor = 0;
			highNibble = true;
			trackSpinner.setValue(t);
			((SpinnerNumberModel) sectorSpinner.getModel()).setMaximum(Math.max(0, image.getMaxSectors(t) - 1));
			sectorSpinner.setValue(s);
			loading = false;
			updateStatus();
			canvas.repaint();
			if (sectorListener != null) {
				sectorListener.accept(t, s);
			}
		} catch (CbmException e) {
			loading = false;
			JOptionPane.showMessageDialog(this, e.getMessage(), "Sector editor", JOptionPane.ERROR_MESSAGE);
			trackSpinner.setValue(track);
		}
	}

	private boolean writeSector() {
		try {
			image.writeBlock(track, sector, data);
			if (panel != null) {
				panel.imageChanged();
			} else {
				image.save();
			}
			original = data.clone();
			dirty = false;
			updateStatus();
			canvas.repaint();
			return true;
		} catch (CbmException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Sector editor", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void updateStatus() {
		var sb = new StringBuilder();
		sb.append("Track ").append(track).append(", sector ").append(sector);
		sb.append(" - ").append(image.isSectorFree(track, sector) ? "free in BAM" : "used in BAM");
		int nt = data[0] & 0xff;
		if (nt != 0) {
			sb.append(" - next: ").append(nt).append('/').append(data[1] & 0xff);
		} else {
			sb.append(" - last sector, ").append(Math.max(0, (data[1] & 0xff) - 1)).append(" data bytes");
		}
		sb.append(" - byte ").append(String.format("$%02X = %d", cursor, cursor));
		if (dirty) {
			sb.append(" - CHANGED");
		}
		statusLabel.setText(sb.toString());
	}

	private void setByte(int index, int value) {
		if ((data[index] & 0xff) != value) {
			data[index] = (byte) value;
			dirty = true;
		}
	}

	private class Canvas extends JComponent {
		private static final long serialVersionUID = 1L;
		private final Font hexFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);

		Canvas() {
			setFocusable(true);
			setPreferredSize(new Dimension(LABEL_W + 16 * HEX_W + GAP + 16 * PET_CELL + 10, 16 * ROW_H + 24));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					requestFocusInWindow();
					int col;
					int row = (e.getY() - 22) / ROW_H;
					int x = e.getX() - LABEL_W;
					if (x >= 0 && x < 16 * HEX_W) {
						col = x / HEX_W;
						hexMode.setSelected(true);
					} else if (x >= 16 * HEX_W + GAP && x < 16 * HEX_W + GAP + 16 * PET_CELL) {
						col = (x - 16 * HEX_W - GAP) / PET_CELL;
						petMode.setSelected(true);
					} else {
						return;
					}
					if (row >= 0 && row < 16) {
						cursor = row * 16 + col;
						highNibble = true;
						updateStatus();
						repaint();
					}
				}
			});
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					handleKey(e);
				}
			});
			setFocusTraversalKeysEnabled(false);
		}

		private void handleKey(KeyEvent e) {
			int code = e.getKeyCode();
			switch (code) {
			case KeyEvent.VK_LEFT:
				moveCursor(-1);
				break;
			case KeyEvent.VK_RIGHT:
				moveCursor(1);
				break;
			case KeyEvent.VK_UP:
				moveCursor(-16);
				break;
			case KeyEvent.VK_DOWN:
				moveCursor(16);
				break;
			case KeyEvent.VK_TAB:
				if (hexMode.isSelected()) {
					petMode.setSelected(true);
				} else {
					hexMode.setSelected(true);
				}
				break;
			default:
				char c = e.getKeyChar();
				if (c == KeyEvent.CHAR_UNDEFINED || e.isMetaDown() || e.isControlDown()) {
					return;
				}
				if (hexMode.isSelected()) {
					int v = Character.digit(c, 16);
					if (v >= 0) {
						int old = data[cursor] & 0xff;
						if (highNibble) {
							setByte(cursor, (v << 4) | (old & 0x0f));
							highNibble = false;
						} else {
							setByte(cursor, (old & 0xf0) | v);
							highNibble = true;
							moveCursor(1);
						}
					}
				} else {
					int p = C64.asciiToPetscii(c);
					if (p >= 0) {
						setByte(cursor, p);
						moveCursor(1);
					}
				}
				break;
			}
			updateStatus();
			repaint();
		}

		private void moveCursor(int delta) {
			int n = cursor + delta;
			if (n >= 0 && n < 256) {
				cursor = n;
			}
			highNibble = true;
		}

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0;
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setFont(hexFont);
			var fm = g.getFontMetrics();
			g.setColor(Color.GRAY);
			for (int c = 0; c < 16; c++) {
				g.drawString(String.format("%X", c), LABEL_W + c * HEX_W + HEX_W / 2 - 4, 16);
			}
			var petBg = C64.PALETTE[C64.BLUE];
			var petFg = C64.PALETTE[C64.LIGHT_BLUE];
			for (int i = 0; i < 256; i++) {
				int row = i / 16;
				int col = i % 16;
				int y = 22 + row * ROW_H;
				if (col == 0) {
					g.setColor(Color.GRAY);
					g.drawString(String.format("%02X", i), 4, y + 15);
				}
				int hx = LABEL_W + col * HEX_W;
				int px = LABEL_W + 16 * HEX_W + GAP + col * PET_CELL;
				boolean changed = data[i] != original[i];
				if (i == cursor) {
					g.setColor(hexMode.isSelected() ? new Color(0xbbd5ff) : new Color(0xe3ecf9));
					g.fillRect(hx, y, HEX_W, ROW_H);
				}
				g.setColor(changed ? Color.RED : Color.BLACK);
				var s = String.format("%02X", data[i] & 0xff);
				g.drawString(s, hx + (HEX_W - fm.stringWidth(s)) / 2, y + 15);
				C64.drawPetscii(g, data[i] & 0xff, px, y, PET_CELL, ROW_H - 2, changed ? Color.YELLOW : petFg, petBg);
				if (i == cursor) {
					g.setColor(petMode.isSelected() ? Color.RED : Color.GRAY);
					g.drawRect(px, y, PET_CELL - 1, ROW_H - 3);
					if (hexMode.isSelected()) {
						g.setColor(Color.BLUE);
						g.drawRect(hx, y, HEX_W - 1, ROW_H - 1);
					}
				}
			}
		}
	}

	int getTrack() {
		return track;
	}

	int getSector() {
		return sector;
	}

	/** Give the hex editor the keyboard focus. */
	void focusEditor() {
		canvas.requestFocusInWindow();
	}

	/** For tests. */
	byte[] getData() {
		return Arrays.copyOf(data, data.length);
	}
}
