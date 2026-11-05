package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import droid64.d64.DiskImage;

/** A coloured grid of all sectors of the disk. Green: free, red: used. Click a sector to edit it. */
public class GraphicBamDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int CELL = 16;
	private static final int LEFT = 26;
	private static final int TOP = 20;

	private static final Color FREE = new Color(0x4cc24c);
	private static final Color USED = new Color(0xd9472b);

	private final transient DiskImage image;
	private final transient DiskPanel panel;
	private final java.awt.Frame owner;
	private final JLabel info = new JLabel(" ");
	private final Grid grid;
	private final SectorEditorPanel editor;

	public GraphicBamDialog(java.awt.Frame owner, DiskPanel panel, DiskImage image, String title) {
		super(owner, "BAM / Sector editor - " + title, false);
		this.owner = owner;
		this.panel = panel;
		this.image = image;
		this.grid = new Grid();
		setLayout(new BorderLayout());
		var scroll = new JScrollPane(grid);
		int firstTrack = image instanceof droid64.d64.D81 ? 40 : Math.min(18, image.getTrackCount());
		this.editor = new SectorEditorPanel(panel, image, firstTrack, 0);
		editor.setSectorListener((t, s) -> {
			grid.selTrack = t;
			grid.selSector = s;
			grid.repaint();
			updateInfo(t, s);
		});
		grid.selTrack = firstTrack;
		grid.selSector = 0;
		var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, editor);
		split.setResizeWeight(0);
		add(split, BorderLayout.CENTER);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				closeIfAllowed();
			}
		});
		var bottom = new JPanel(new BorderLayout());
		bottom.add(info, BorderLayout.CENTER);
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		var refresh = new JButton("Refresh");
		var close = new JButton("Close");
		refresh.addActionListener(e -> {
			grid.repaint();
			updateInfo(-1, -1);
		});
		close.addActionListener(e -> closeIfAllowed());
		buttons.add(refresh);
		buttons.add(close);
		bottom.add(buttons, BorderLayout.EAST);
		add(bottom, BorderLayout.SOUTH);
		updateInfo(-1, -1);
		int w = Math.min(grid.getPreferredSize().width + 20, 600) + editor.getPreferredSize().width + 30;
		int h = Math.max(Math.min(grid.getPreferredSize().height + 110, 760), editor.getPreferredSize().height + 110);
		setSize(w, h);
		setLocationRelativeTo(owner);
	}

	private void closeIfAllowed() {
		if (editor.confirmLeave()) {
			dispose();
		}
	}

	private void updateInfo(int t, int s) {
		int free = 0;
		int used = 0;
		for (int tr = 1; tr <= image.getTrackCount(); tr++) {
			for (int se = 0; se < image.getMaxSectors(tr); se++) {
				if (image.isSectorFree(tr, se)) {
					free++;
				} else {
					used++;
				}
			}
		}
		var text = "Free: " + free + "   Used: " + used + "   (click a sector to edit it on the right)";
		if (t > 0) {
			text = "Track " + t + ", sector " + s + ": " + (image.isSectorFree(t, s) ? "free" : "used") + "     " + text;
		}
		info.setText(text);
	}

	private class Grid extends JComponent {
		private static final long serialVersionUID = 1L;
		private int hoverTrack = -1;
		private int hoverSector = -1;
		private int selTrack = -1;
		private int selSector = -1;

		Grid() {
			setPreferredSize(new Dimension(LEFT + image.getTrackCount() * CELL + 10,
					TOP + image.getMaxSectorCount() * CELL + 10));
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int t = (e.getX() - LEFT) / CELL + 1;
					int s = (e.getY() - TOP) / CELL;
					if (e.getX() < LEFT || e.getY() < TOP || t < 1 || t > image.getTrackCount() || s < 0
							|| s >= image.getMaxSectors(t)) {
						t = -1;
						s = -1;
					}
					if (t != hoverTrack || s != hoverSector) {
						hoverTrack = t;
						hoverSector = s;
						updateInfo(t, s);
						repaint();
					}
				}
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (hoverTrack > 0) {
						editor.gotoSector(hoverTrack, hoverSector);
						editor.focusEditor();
						repaint();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
			for (int t = 1; t <= image.getTrackCount(); t++) {
				int x = LEFT + (t - 1) * CELL;
				g.setColor(Color.GRAY);
				if (t == 1 || t % 5 == 0) {
					g.drawString(Integer.toString(t), x, TOP - 6);
				}
				for (int s = 0; s < image.getMaxSectors(t); s++) {
					int y = TOP + s * CELL;
					if (t == 1) {
						g.setColor(Color.GRAY);
						g.drawString(Integer.toString(s), 4, y + 12);
					}
					g.setColor(image.isSectorFree(t, s) ? FREE : USED);
					g.fillOval(x + 1, y + 1, CELL - 2, CELL - 2);
					if (t == hoverTrack && s == hoverSector) {
						g.setColor(Color.BLUE);
						g.drawOval(x, y, CELL - 1, CELL - 1);
					}
					if (t == selTrack && s == selSector) {
						g.setColor(Color.BLACK);
						g.drawRect(x, y, CELL - 1, CELL - 1);
					}
				}
			}
		}
	}

	public static void show(java.awt.Frame owner, DiskPanel panel, DiskImage image, String title) {
		new GraphicBamDialog(owner, panel, image, title).setVisible(true);
	}
}
