package droid64.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.Timer;

import droid64.d64.DiskImage;

/** Track x sector grid of a disk image, one small square per sector: the same look as the OpenCBM sector map. */
final class SectorGrid extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final Color C_USED = new Color(0x4A, 0x7F, 0xC8);
	private static final Color C_FREE = new Color(0x3A, 0x41, 0x4C);
	private static final Color C_WARN = new Color(0xFF, 0xB0, 0x2E);
	private static final Color C_BAD = new Color(0xE5, 0x48, 0x3F);
	private static final Color C_CUR = new Color(0x5B, 0x9B, 0xFF);
	private static final Color TEXT = new Color(0xC9, 0xCE, 0xD6);
	private static final Color AXIS = new Color(0x8C, 0x94, 0xA1);
	private static final int PAD_L = 28;
	private static final int PAD_T = 44;

	private final transient DiskImage image;
	private final int pitch;
	private final int cell;
	private transient byte[][] marks;
	private int selTrack = -1;
	private int selSector = -1;
	private int hoverTrack = -1;
	private int hoverSector = -1;
	private boolean blinkOn = true;
	private transient BiConsumer<Integer, Integer> clickListener;
	private transient BiConsumer<Integer, Integer> hoverListener;
	private final Timer blink = new Timer(450, e -> {
		blinkOn = !blinkOn;
		repaint();
	});

	SectorGrid(DiskImage image) {
		this.image = image;
		this.pitch = Math.max(8, Math.min(16, 640 / Math.max(1, image.getTrackCount())));
		this.cell = pitch - 2;
		setBackground(new Color(0x1B, 0x1F, 0x25));
		setOpaque(true);
		setPreferredSize(new Dimension(PAD_L + image.getTrackCount() * pitch + 12, PAD_T + image.getMaxSectorCount() * pitch + 10));
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int t = (e.getX() - PAD_L) / pitch + 1;
				int s = (e.getY() - PAD_T) / pitch;
				if (e.getX() < PAD_L || e.getY() < PAD_T || t < 1 || t > image.getTrackCount() || s < 0
						|| s >= image.getMaxSectors(t)) {
					t = -1;
					s = -1;
				}
				if (t != hoverTrack || s != hoverSector) {
					hoverTrack = t;
					hoverSector = s;
					if (hoverListener != null) {
						hoverListener.accept(t, s);
					}
					repaint();
				}
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (hoverTrack > 0 && clickListener != null) {
					clickListener.accept(hoverTrack, hoverSector);
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hoverTrack = -1;
				hoverSector = -1;
				if (hoverListener != null) {
					hoverListener.accept(-1, -1);
				}
				repaint();
			}
		});
	}

	void setClickListener(BiConsumer<Integer, Integer> l) {
		clickListener = l;
	}

	void setHoverListener(BiConsumer<Integer, Integer> l) {
		hoverListener = l;
	}

	/** Problem levels from an image check (ImageCheck.OK/WARN/ERROR per track and sector), or null for none. */
	void setMarks(byte[][] marks) {
		this.marks = marks;
		repaint();
	}

	void setSelected(int track, int sector) {
		selTrack = track;
		selSector = sector;
		repaint();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		blink.start();
	}

	@Override
	public void removeNotify() {
		blink.stop();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		var g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(getFont().deriveFont(10f));
		FontMetrics fm = g.getFontMetrics();
		int lx = 12;
		String[] names = { "used", "free", "warning", "error" };
		Color[] cols = { C_USED, C_FREE, C_WARN, C_BAD };
		for (int i = 0; i < names.length; i++) {
			g.setColor(cols[i]);
			g.fillRect(lx, 9, 10, 10);
			g.setColor(TEXT);
			g.drawString(names[i], lx + 15, 18);
			lx += 15 + fm.stringWidth(names[i]) + 18;
		}
		int tracks = image.getTrackCount();
		int step = pitch >= 12 ? 5 : 10;
		g.setColor(AXIS);
		for (int t = 1; t <= tracks; t++) {
			if (t == 1 || t % step == 0) {
				String s = String.valueOf(t);
				g.drawString(s, PAD_L + (t - 1) * pitch + (cell - fm.stringWidth(s)) / 2, PAD_T - 6);
			}
		}
		for (int s = 0; s < image.getMaxSectorCount(); s += 5) {
			String str = String.valueOf(s);
			g.drawString(str, PAD_L - 6 - fm.stringWidth(str), PAD_T + s * pitch + cell - 1);
		}
		for (int t = 1; t <= tracks; t++) {
			for (int s = 0; s < image.getMaxSectors(t); s++) {
				int x = PAD_L + (t - 1) * pitch;
				int y = PAD_T + s * pitch;
				int mark = marks != null && t < marks.length && s < marks[t].length ? marks[t][s] : 0;
				Color c = mark == ImageCheck.ERROR ? C_BAD : mark == ImageCheck.WARN ? C_WARN
						: image.isSectorFree(t, s) ? C_FREE : C_USED;
				if (t == selTrack && s == selSector) {
					g.setColor(blinkOn ? C_CUR : getBackground());
					g.fillRect(x, y, cell, cell);
					g.setColor(blinkOn ? Color.WHITE : C_CUR);
					g.drawRect(x - 1, y - 1, cell + 1, cell + 1);
				} else {
					g.setColor(c);
					g.fillRect(x, y, cell, cell);
					if (t == hoverTrack && s == hoverSector) {
						g.setColor(Color.WHITE);
						g.drawRect(x - 1, y - 1, cell + 1, cell + 1);
					}
				}
			}
		}
		g.dispose();
	}
}
