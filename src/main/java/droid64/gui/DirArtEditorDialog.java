package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import droid64.d64.CbmException;
import droid64.d64.DiskImage;

/**
 * Draw directory art with PETSCII characters and write it to a disk image as directory entries.
 * Each line is one directory entry with a 16 character name; the first line is the disk header.
 */
public class DirArtEditorDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int CELL = 16;
	/** Free space between the disk name line and the directory entries. */
	private static final int GAP = 14;
	private static final int ART_COL = 5;
	private static final int LINE_COLS = 30;

	private final java.awt.Frame owner;
	private final transient DiskPanel panel;
	private final transient DiskImage image;
	private final List<DirArtWriter.Row> rows = new ArrayList<>();
	private final byte[] header = new byte[DirArtWriter.NAME_LENGTH];
	private final JTextField idField = new JTextField("00", 2);
	private final JSpinner rowCount;
	private final JComboBox<DirArtWriter.Mode> modeBox = new JComboBox<>(DirArtWriter.Mode.values());
	private final JCheckBox writeHeader = new JCheckBox("Also write disk name and ID", true);
	private final JLabel info = new JLabel(" ");
	private final JComboBox<C64.Machine> machineBox = new JComboBox<>(C64.Machine.values());
	private final JComboBox<String> charsetBox = new JComboBox<>(new String[] { "Upper case / graphics", "Lower / upper case" });
	private C64.Machine drawMachine = C64.Machine.C64;
	private boolean drawLower = false;
	private final Canvas canvas = new Canvas();
	private final Palette palette = new Palette();

	private int brush = 0xa0 - 0x80 + 0x60;
	/** Rows (1 = first directory entry) selected with shift / ctrl click, for batch clearing. */
	private final java.util.TreeSet<Integer> selected = new java.util.TreeSet<>();
	private int curRow = 0;
	private int curCol = 0;
	private boolean updating = false;

	public DirArtEditorDialog(java.awt.Frame owner, DiskPanel panel, DiskImage image) {
		super(owner, "DIR art editor", false);
		this.owner = owner;
		this.panel = panel;
		this.image = image;
		java.util.Arrays.fill(header, (byte) 0x20);
		brush = 0x66;

		int max = image != null && DirArtWriter.isSupported(image) ? DirArtWriter.maxEntries(image) : 144;
		rowCount = new JSpinner(new SpinnerNumberModel(18, 1, max, 1));
		for (int i = 0; i < 18; i++) {
			rows.add(new DirArtWriter.Row());
		}

		var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Computer"));
		top.add(machineBox);
		top.add(charsetBox);
		top.add(new JLabel("Entries"));
		top.add(rowCount);
		top.add(new JLabel("Disk ID"));
		top.add(idField);
		var importBtn = new JButton("Read from disk");
		var clearBtn = new JButton("Clear all");
		var fillBtn = new JButton("Fill line");
		var clearLineBtn = new JButton("Clear line(s)");
		top.add(importBtn);
		top.add(clearLineBtn);
		top.add(fillBtn);
		top.add(clearBtn);

		var bottom = new JPanel(new BorderLayout());
		var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(new JLabel("Write to disk:"));
		controls.add(modeBox);
		controls.add(writeHeader);
		var writeBtn = new JButton("Write to disk");
		var close = new JButton("Close");
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(writeBtn);
		buttons.add(close);
		bottom.add(controls, BorderLayout.WEST);
		bottom.add(buttons, BorderLayout.EAST);
		var south = new JPanel(new BorderLayout());
		south.add(info, BorderLayout.NORTH);
		south.add(bottom, BorderLayout.SOUTH);

		setLayout(new BorderLayout());
		add(top, BorderLayout.NORTH);
		add(new JScrollPane(canvas), BorderLayout.CENTER);
		add(palette, BorderLayout.EAST);
		add(south, BorderLayout.SOUTH);

		rowCount.addChangeListener(e -> {
			if (!updating) {
				resizeRows((Integer) rowCount.getValue());
			}
		});
		charsetBox.addActionListener(e -> {
			canvas.repaint();
			palette.repaint();
			canvas.requestFocusInWindow();
		});
		machineBox.addActionListener(e -> {
			canvas.repaint();
			palette.repaint();
			canvas.requestFocusInWindow();
		});
		importBtn.addActionListener(e -> readFromDisk());
		clearBtn.addActionListener(e -> {
			rows.clear();
			selected.clear();
			int n = (Integer) rowCount.getValue();
			for (int i = 0; i < n; i++) {
				rows.add(new DirArtWriter.Row());
			}
			java.util.Arrays.fill(header, (byte) 0x20);
			canvas.repaint();
		});
		clearLineBtn.setToolTipText("Clears the selected lines (shift click: range, ctrl / cmd click: add one), or the current line if none is selected");
		clearLineBtn.addActionListener(e -> {
			var blank = new byte[DirArtWriter.NAME_LENGTH];
			java.util.Arrays.fill(blank, (byte) 0x20);
			if (selected.isEmpty()) {
				setRowName(curRow, blank);
			} else {
				selected.forEach(r -> setRowName(r, blank));
				selected.clear();
			}
			canvas.requestFocusInWindow();
			canvas.repaint();
		});
		fillBtn.addActionListener(e -> {
			var name = curRow == 0 ? header : rows.get(curRow - 1).getName();
			java.util.Arrays.fill(name, (byte) brush);
			setRowName(curRow, name);
			canvas.repaint();
		});
		writeBtn.addActionListener(e -> writeToDisk());
		close.addActionListener(e -> dispose());

		if (image != null && DirArtWriter.isSupported(image)) {
			modeBox.setSelectedItem(DirArtWriter.Mode.APPEND);
			if (panel != null) {
				loadSelectedDel(panel.getSelectedDelEntries());
			}
		}
		updateInfo();
		pack();
		setSize(Math.max(getWidth(), 960), 720);
		setLocationRelativeTo(owner);
		SwingUtilities.invokeLater(canvas::requestFocusInWindow);
	}

	private void resizeRows(int n) {
		while (rows.size() < n) {
			rows.add(new DirArtWriter.Row());
		}
		while (rows.size() > n) {
			rows.remove(rows.size() - 1);
		}
		curRow = Math.min(curRow, n);
		selected.removeIf(r -> r > n);
		canvas.revalidate();
		canvas.repaint();
		updateInfo();
	}

	private void setRowName(int row, byte[] name) {
		if (row == 0) {
			System.arraycopy(name, 0, header, 0, DirArtWriter.NAME_LENGTH);
		} else {
			rows.get(row - 1).setName(name);
		}
	}

	private int getCell(int row, int col) {
		return row == 0 ? header[col] & 0xff : rows.get(row - 1).raw[3 + col] & 0xff;
	}

	private void setCell(int row, int col, int value) {
		if (row == 0) {
			header[col] = (byte) value;
		} else {
			rows.get(row - 1).raw[3 + col] = (byte) value;
		}
	}

	private void updateInfo() {
		var sb = new StringBuilder();
		sb.append(rows.size()).append(" entries");
		if (image != null && DirArtWriter.isSupported(image)) {
			sb.append(" - disk allows up to ").append(DirArtWriter.maxEntries(image));
		} else {
			sb.append(" - no D64 / D71 / D81 image is loaded in the active list, so writing is not possible");
		}
		sb.append(String.format(" - brush: $%02X (the space character erases)", brush));
		sb.append(" - left click draws, right click picks, shift / ctrl click selects lines, type to enter text");
		info.setText(sb.toString());
	}

	/** Show the DEL entries that are selected in the file list, to edit them in place. */
	private void loadSelectedDel(List<Integer> indexes) {
		if (indexes.isEmpty()) {
			return;
		}
		try {
			var all = DirArtWriter.read(image);
			var list = new ArrayList<DirArtWriter.Row>();
			for (int i : indexes) {
				if (i >= 0 && i < all.size() && all.get(i).getTypeCode() == DirArtWriter.TYPE_DEL) {
					list.add(all.get(i));
				}
			}
			if (list.isEmpty()) {
				return;
			}
			rows.clear();
			rows.addAll(list);
			updating = true;
			rowCount.setValue(rows.size());
			updating = false;
			modeBox.setSelectedItem(DirArtWriter.Mode.UPDATE);
			writeHeader.setSelected(false);
			var id = DirArtWriter.readDiskId(image);
			idField.setText(new String(new char[] { (char) (id[0] & 0x7f), (char) (id[1] & 0x7f) }));
			var name = DirArtWriter.readDiskName(image);
			for (int i = 0; i < name.length; i++) {
				header[i] = (name[i] & 0xff) == 0xa0 ? (byte) 0x20 : name[i];
			}
		} catch (CbmException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "DIR art", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void readFromDisk() {
		if (image == null || !DirArtWriter.isSupported(image)) {
			JOptionPane.showMessageDialog(this, "Load a D64, D71 or D81 image in the active list first.", "DIR art", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		try {
			var list = DirArtWriter.read(image);
			rows.clear();
			selected.clear();
			list.forEach(rows::add);
			if (rows.isEmpty()) {
				rows.add(new DirArtWriter.Row());
			}
			var name = DirArtWriter.readDiskName(image);
			for (int i = 0; i < name.length; i++) {
				header[i] = (name[i] & 0xff) == 0xa0 ? (byte) 0x20 : name[i];
			}
			var id = DirArtWriter.readDiskId(image);
			idField.setText(new String(new char[] { (char) (id[0] & 0x7f), (char) (id[1] & 0x7f) }));
			updating = true;
			rowCount.setValue(rows.size());
			updating = false;
			curRow = 0;
			curCol = 0;
			modeBox.setSelectedItem(DirArtWriter.Mode.REPLACE);
			canvas.revalidate();
			canvas.repaint();
			updateInfo();
		} catch (CbmException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "DIR art", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void writeToDisk() {
		if (image == null || !DirArtWriter.isSupported(image)) {
			JOptionPane.showMessageDialog(this, "Load a D64, D71 or D81 image in the active list first.", "DIR art", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		var mode = (DirArtWriter.Mode) modeBox.getSelectedItem();
		var toWrite = DirArtWriter.rowsToWrite(rows);
		if (toWrite.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Nothing was changed, so there is nothing to write.", "DIR art", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		var warning = mode == DirArtWriter.Mode.UPDATE
				? toWrite.size() + " entries will be updated in the directory of this disk image (the other entries are kept)."
				: mode == DirArtWriter.Mode.REPLACE
				? "The whole directory of this disk image will be replaced by these " + toWrite.size() + " entries.\n"
						+ "Files that are not in the list will no longer be visible on the disk."
				: toWrite.size() + " entries (up to the last changed line) will be written to the directory of this disk image.";
		if (JOptionPane.showConfirmDialog(this, warning + "\n\nContinue?", "DIR art", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			byte[] diskName = null;
			byte[] diskId = null;
			if (writeHeader.isSelected()) {
				diskName = header.clone();
				var text = idField.getText();
				diskId = new byte[] { (byte) (text.length() > 0 ? C64.asciiToPetscii(text.charAt(0)) : 0x30),
						(byte) (text.length() > 1 ? C64.asciiToPetscii(text.charAt(1)) : 0x30) };
			}
			DirArtWriter.write(image, toWrite, mode, diskName, diskId);
			if (panel != null) {
				panel.imageChanged();
			} else {
				image.save();
			}
			JOptionPane.showMessageDialog(this, "The directory art was written.", "DIR art", JOptionPane.INFORMATION_MESSAGE);
		} catch (CbmException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "DIR art", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Screen code for an ASCII char, for the text around the art. */
	private static int ascii(char c) {
		if (c >= 'A' && c <= 'Z') {
			return c - 'A' + 1;
		} else if (c >= 'a' && c <= 'z') {
			return c - 'a' + 1;
		}
		return c;
	}

	private void drawText(Graphics2D g, String text, int col, int row, Color fg, Color bg) {
		for (int i = 0; i < text.length(); i++) {
			C64.drawScreenCode(g, ascii(text.charAt(i)), (col + i) * CELL, row * CELL, CELL, CELL, fg, bg, drawMachine, drawLower);
		}
	}

	private class Canvas extends JComponent {
		private static final long serialVersionUID = 1L;

		Canvas() {
			setFocusable(true);
			setFocusTraversalKeysEnabled(false);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					requestFocusInWindow();
					handleMouse(e);
				}
			});
			addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				@Override
				public void mouseDragged(MouseEvent e) {
					handleMouse(e);
				}
			});
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					handleKey(e);
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(LINE_COLS * CELL + 20, (rows.size() + 1) * CELL + GAP + 20);
		}

		private void handleMouse(MouseEvent e) {
			int y = e.getY() - 10;
			if (y >= CELL && y < CELL + GAP) {
				return;
			}
			int row = y < CELL ? 0 : (y - GAP) / CELL;
			int col = (e.getX() - 10) / CELL;
			if (e.getY() < 10 || e.getX() < 10 || row < 0 || row > rows.size()) {
				return;
			}
			boolean extend = e.getID() == MouseEvent.MOUSE_PRESSED && row > 0 && !SwingUtilities.isRightMouseButton(e)
					&& (e.isShiftDown() || e.isControlDown() || e.isMetaDown());
			if (extend) {
				if (e.isShiftDown()) {
					int from = Math.max(1, curRow);
					for (int r = Math.min(from, row); r <= Math.max(from, row); r++) {
						selected.add(r);
					}
				} else if (!selected.remove(row)) {
					selected.add(row);
				}
				curRow = row;
				if (col >= ART_COL && col < ART_COL + DirArtWriter.NAME_LENGTH) {
					curCol = col - ART_COL;
				}
			} else if (e.isShiftDown() || e.isControlDown() || e.isMetaDown()) {
				return;
			} else if (col >= ART_COL && col < ART_COL + DirArtWriter.NAME_LENGTH) {
				if (e.getID() == MouseEvent.MOUSE_PRESSED) {
					selected.clear();
				}
				curRow = row;
				curCol = col - ART_COL;
				if (SwingUtilities.isRightMouseButton(e)) {
					brush = getCell(row, curCol);
					palette.repaint();
					updateInfo();
				} else {
					setCell(row, curCol, brush);
				}
			} else if (row > 0 && col >= ART_COL + DirArtWriter.NAME_LENGTH + 2 && e.getID() == MouseEvent.MOUSE_PRESSED) {
				var r = rows.get(row - 1);
				r.setTypeCode((r.getTypeCode() + 1) % DirArtWriter.TYPE_NAMES.length);
			} else if (row > 0 && col < ART_COL - 1 && e.getID() == MouseEvent.MOUSE_PRESSED) {
				var r = rows.get(row - 1);
				var answer = JOptionPane.showInputDialog(DirArtEditorDialog.this, "Size in blocks", Integer.toString(r.getBlocks()));
				if (answer != null) {
					try {
						r.setBlocks(Math.max(0, Math.min(65535, Integer.parseInt(answer.trim()))));
					} catch (NumberFormatException ex) {
						// ignore
					}
				}
			}
			repaint();
		}

		private void handleKey(KeyEvent e) {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_LEFT:
				curCol = Math.max(0, curCol - 1);
				break;
			case KeyEvent.VK_RIGHT:
				curCol = Math.min(DirArtWriter.NAME_LENGTH - 1, curCol + 1);
				break;
			case KeyEvent.VK_UP:
				curRow = Math.max(0, curRow - 1);
				break;
			case KeyEvent.VK_DOWN:
				curRow = Math.min(rows.size(), curRow + 1);
				break;
			case KeyEvent.VK_BACK_SPACE:
				curCol = Math.max(0, curCol - 1);
				setCell(curRow, curCol, 0x20);
				break;
			case KeyEvent.VK_ESCAPE:
				selected.clear();
				break;
			case KeyEvent.VK_DELETE:
				setCell(curRow, curCol, 0x20);
				break;
			case KeyEvent.VK_ENTER:
				curCol = 0;
				curRow = Math.min(rows.size(), curRow + 1);
				break;
			default:
				char c = e.getKeyChar();
				if (c == KeyEvent.CHAR_UNDEFINED || e.isMetaDown() || e.isControlDown()) {
					return;
				}
				int p = C64.asciiToPetscii(c);
				if (p >= 0) {
					setCell(curRow, curCol, p);
					if (curCol < DirArtWriter.NAME_LENGTH - 1) {
						curCol++;
					}
				}
				break;
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0;
			var machine = (C64.Machine) machineBox.getSelectedItem();
			drawMachine = machine;
			drawLower = charsetBox.getSelectedIndex() == 1;
			var bg = machine.background;
			var fg = machine.foreground;
			var headBg = machine.mix(0.18);
			g.setColor(bg);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.translate(10, 10);
			// header line (disk name): own colour so it is not mistaken for a directory entry
			g.setColor(headBg);
			g.fillRect(-10, 0, getWidth(), CELL);
			drawText(g, "DISK", 0, 0, fg, headBg);
			drawText(g, "\"", ART_COL - 1, 0, fg, headBg);
			for (int c = 0; c < DirArtWriter.NAME_LENGTH; c++) {
				C64.drawPetscii(g, header[c] & 0xff, (ART_COL + c) * CELL, 0, CELL, CELL, bg, fg, drawMachine, drawLower);
			}
			drawText(g, "\" " + idField.getText().toUpperCase() + " 2A", ART_COL + DirArtWriter.NAME_LENGTH, 0, fg, headBg);
			g.translate(0, GAP);
			for (int r = 0; r < rows.size(); r++) {
				var row = rows.get(r);
				int line = r + 1;
				var blocks = Integer.toString(row.getBlocks());
				drawText(g, blocks, 0, line, fg, bg);
				drawText(g, "\"", ART_COL - 1, line, fg, bg);
				for (int c = 0; c < DirArtWriter.NAME_LENGTH; c++) {
					C64.drawPetscii(g, row.raw[3 + c] & 0xff, (ART_COL + c) * CELL, line * CELL, CELL, CELL, fg, bg, drawMachine, drawLower);
				}
				drawText(g, "\" " + DirArtWriter.TYPE_NAMES[Math.min(row.getTypeCode(), DirArtWriter.TYPE_NAMES.length - 1)],
						ART_COL + DirArtWriter.NAME_LENGTH, line, fg, bg);
			}
			g.setColor(new Color(0xff, 0x40, 0x40, 110));
			for (int sel : selected) {
				if (sel <= rows.size()) {
					g.fillRect(-10, sel * CELL, getWidth(), CELL);
				}
			}
			g.setColor(machine.mix(0.25));
			for (int line = 1; line <= rows.size(); line++) {
				for (int c = 0; c < DirArtWriter.NAME_LENGTH; c++) {
					g.drawRect((ART_COL + c) * CELL, line * CELL, CELL - 1, CELL - 1);
				}
			}
			g.translate(0, -GAP);
			for (int c = 0; c < DirArtWriter.NAME_LENGTH; c++) {
				g.drawRect((ART_COL + c) * CELL, 0, CELL - 1, CELL - 1);
			}
			g.setColor(fg);
			g.drawLine(-10, CELL + GAP / 2, getWidth(), CELL + GAP / 2);
			g.setColor(Color.YELLOW);
			g.drawRect((ART_COL + curCol) * CELL, curRow * CELL + (curRow > 0 ? GAP : 0), CELL - 1, CELL - 1);
			g.translate(-10, -10);
		}
	}

	private class Palette extends JComponent {
		private static final long serialVersionUID = 1L;
		private static final int PCELL = 20;

		Palette() {
			setPreferredSize(new Dimension(16 * PCELL + 20, 16 * PCELL + 40));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int col = (e.getX() - 10) / PCELL;
					int row = (e.getY() - 10) / PCELL;
					if (e.getX() >= 10 && e.getY() >= 10 && col >= 0 && col < 16 && row >= 0 && row < 16) {
						brush = row * 16 + col;
						repaint();
						updateInfo();
						canvas.requestFocusInWindow();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0;
			var machine = (C64.Machine) machineBox.getSelectedItem();
			drawMachine = machine;
			drawLower = charsetBox.getSelectedIndex() == 1;
			var bg = machine.background;
			var fg = machine.foreground;
			g.setColor(Color.DARK_GRAY);
			g.fillRect(0, 0, getWidth(), getHeight());
			for (int i = 0; i < 256; i++) {
				int x = 10 + (i % 16) * PCELL;
				int y = 10 + (i / 16) * PCELL;
				C64.drawPetscii(g, i, x, y, PCELL, PCELL, fg, bg, drawMachine, drawLower);
				if (i == brush) {
					g.setColor(Color.YELLOW);
					g.drawRect(x, y, PCELL - 1, PCELL - 1);
				}
			}
			g.setColor(Color.WHITE);
			g.drawString(String.format("Brush $%02X", brush), 10, 16 * PCELL + 30);
		}
	}

	public static void show(java.awt.Frame owner, DiskPanel panel, DiskImage image) {
		new DirArtEditorDialog(owner, panel, image).setVisible(true);
	}
}
