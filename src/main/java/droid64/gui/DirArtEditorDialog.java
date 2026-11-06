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
import droid64.d64.DiskImageType;

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
	/** First screen column of the disk ID characters in the header line (after the closing quote and a space). */
	private static final int ID_COL = ART_COL + DirArtWriter.NAME_LENGTH + 2;

	private final java.awt.Frame owner;
	private final transient DiskPanel panel;
	private final transient DiskImage image;
	private final List<DirArtWriter.Row> rows = new ArrayList<>();
	private final byte[] header = new byte[DirArtWriter.NAME_LENGTH];
	/** The 5 characters of the disk ID field in the header line: ID, space, DOS type. */
	private final char[] diskIdChars = "00 2A".toCharArray();
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
	/** The art (disk name, ID and lines) as it was when the editor was opened or last kept. */
	private String savedContent = "";

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
		// start with the real disk name and ID, so that changing only one of them keeps the other
		if (image != null && DirArtWriter.isSupported(image)) {
			try {
				var name = DirArtWriter.readDiskName(image);
				for (int i = 0; i < name.length && i < header.length; i++) {
					header[i] = (name[i] & 0xff) == 0xa0 ? (byte) 0x20 : name[i];
				}
				setDiskId(idText(DirArtWriter.readDiskId(image)));
			} catch (CbmException e) {
				// keep the empty header
			}
		}
		loadSavedArt();
		savedContent = artContent();
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
		close.addActionListener(e -> requestClose());
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				requestClose();
			}
		});

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
			writeHeader.setSelected(true);
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
			sb.append(" - no D64 / D71 / D81 image is loaded in the active list, Write to disk saves the DIR art as a new disk image");
		}
		sb.append(String.format(" - brush: $%02X (the space character erases)", brush));
		sb.append(" - left click draws, right click picks, shift / ctrl click selects lines, type to enter text");
		info.setText(sb.toString());
	}

	private void setDiskId(String text) {
		for (int i = 0; i < diskIdChars.length; i++) {
			diskIdChars[i] = i < text.length() ? text.charAt(i) : ' ';
		}
	}

	/** The 5 bytes of the disk ID field (ID, shifted space, DOS type) as text. */
	static String idText(byte[] id) {
		var sb = new StringBuilder();
		for (int i = 0; i < DirArtWriter.ID_LENGTH && i < id.length; i++) {
			sb.append((id[i] & 0xff) == 0xa0 ? ' ' : (char) (id[i] & 0x7f));
		}
		return sb.toString();
	}

	/** The text of the disk ID field as the 5 bytes of the header; a space is a shifted space ($A0), like Rename Disk does. */
	static byte[] idBytes(String text) {
		var b = new byte[DirArtWriter.ID_LENGTH];
		for (int i = 0; i < b.length; i++) {
			char c = i < text.length() ? text.charAt(i) : ' ';
			b[i] = c == ' ' ? (byte) 0xa0 : (byte) c;
		}
		return b;
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
			setDiskId(idText(id));
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
			setDiskId(idText(id));
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

	// ------------------------------------------------------------------ the art is kept for the next time

	private static java.io.File artFile() {
		return new java.io.File(System.getProperty("user.home"), ".droiD64-dirart.txt");
	}

	private static String hex(byte[] b) {
		var sb = new StringBuilder();
		for (byte v : b) {
			sb.append(String.format("%02x", v & 0xff));
		}
		return sb.toString();
	}

	private static byte[] unhex(String s, int length) {
		if (s == null || s.length() != length * 2) {
			return null;
		}
		var b = new byte[length];
		for (int i = 0; i < length; i++) {
			b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
		}
		return b;
	}

	/** Disk name, ID and all lines as text: used to find out if anything was changed. */
	private String artContent() {
		var sb = new StringBuilder();
		sb.append("header=").append(hex(header)).append('\n');
		sb.append("id=").append(hex(new String(diskIdChars).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))).append('\n');
		for (var r : rows) {
			sb.append("row=").append(hex(r.raw)).append('\n');
		}
		return sb.toString();
	}

	private void saveArt() {
		try {
			var text = artContent() + "machine=" + machineBox.getSelectedIndex() + "\ncharset=" + charsetBox.getSelectedIndex() + "\n";
			java.nio.file.Files.write(artFile().toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			savedContent = artContent();
		} catch (java.io.IOException e) {
			JOptionPane.showMessageDialog(this, "The DIR art could not be kept: " + e.getMessage(), "DIR art", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Restore the art kept the last time. The disk name and ID of a loaded disk image are not replaced. */
	private void loadSavedArt() {
		var file = artFile();
		if (!file.isFile()) {
			return;
		}
		try {
			byte[] savedHeader = null;
			byte[] savedId = null;
			var savedRows = new ArrayList<byte[]>();
			int machine = -1;
			int charset = -1;
			for (var line : java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
				int eq = line.indexOf('=');
				if (eq < 0) {
					continue;
				}
				var key = line.substring(0, eq);
				var value = line.substring(eq + 1).trim();
				switch (key) {
				case "header":
					savedHeader = unhex(value, DirArtWriter.NAME_LENGTH);
					break;
				case "id":
					savedId = unhex(value, diskIdChars.length);
					break;
				case "row": {
					var r = unhex(value, 30);
					if (r != null) {
						savedRows.add(r);
					}
					break;
				}
				case "machine":
					machine = Integer.parseInt(value);
					break;
				case "charset":
					charset = Integer.parseInt(value);
					break;
				default:
					break;
				}
			}
			if (savedRows.isEmpty()) {
				return;
			}
			boolean diskLoaded = image != null && DirArtWriter.isSupported(image);
			int max = diskLoaded ? DirArtWriter.maxEntries(image) : 144;
			rows.clear();
			for (var raw : savedRows) {
				if (rows.size() < max) {
					var row = new DirArtWriter.Row();
					System.arraycopy(raw, 0, row.raw, 0, 30);
					rows.add(row);
				}
			}
			if (!diskLoaded) {
				if (savedHeader != null) {
					System.arraycopy(savedHeader, 0, header, 0, header.length);
				}
				if (savedId != null) {
					for (int i = 0; i < diskIdChars.length; i++) {
						diskIdChars[i] = (char) (savedId[i] & 0xff);
					}
				}
			}
			updating = true;
			rowCount.setValue(rows.size());
			updating = false;
			if (machine >= 0 && machine < machineBox.getItemCount()) {
				machineBox.setSelectedIndex(machine);
			}
			if (charset >= 0 && charset < charsetBox.getItemCount()) {
				charsetBox.setSelectedIndex(charset);
			}
		} catch (java.io.IOException | RuntimeException e) {
			// a damaged file is ignored: the editor starts empty
		}
	}

	/** Closing: ask only if the art was changed since it was opened or kept. */
	private void requestClose() {
		if (!artContent().equals(savedContent)) {
			Object[] options = { "Keep it", "Discard", "Cancel" };
			int answer = JOptionPane.showOptionDialog(this, "The DIR art was changed.\nKeep it for the next time the editor is opened?",
					"DIR art", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (answer == 0) {
				saveArt();
			} else if (answer != 1) {
				return;
			}
		}
		dispose();
	}

	/** The disk name in the header line as text, for a file name and a new disk. */
	private String headerText() {
		var sb = new StringBuilder();
		for (byte b : header) {
			int v = b & 0x7f;
			sb.append(v >= 0x20 && v < 0x5b ? (char) v : ' ');
		}
		return sb.toString().trim();
	}

	/** No disk image is loaded: offer to save the DIR art as a new disk image, so the work is not lost. */
	private void saveAsNewDisk() {
		var toWrite = DirArtWriter.rowsToWrite(rows);
		if (toWrite.isEmpty() && headerText().isEmpty()) {
			JOptionPane.showMessageDialog(this, "There is nothing to save yet.", "DIR art", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		var types = new DiskImageType[] { DiskImageType.D64, DiskImageType.D71, DiskImageType.D81 };
		var typeBox = new JComboBox<>(new String[] { "D64 (1541)", "D71 (1571)", "D81 (1581)" });
		var panelBox = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 0, 6));
		panelBox.add(new JLabel("No disk image is loaded. Save the DIR art as a new disk image?"));
		var typeRow = new javax.swing.JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		typeRow.add(new JLabel("Disk type"));
		typeRow.add(typeBox);
		panelBox.add(typeRow);
		if (JOptionPane.showConfirmDialog(this, panelBox, "DIR art - new disk image", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		var type = types[Math.max(0, typeBox.getSelectedIndex())];
		var base = DiskPanel.fileNameFromDiskName(headerText());
		var defaultName = Setting.checkFileNameExtension(type, false, base.isEmpty() ? "DirArt" : base);
		var last = Setting.LAST_SAVE_DIR.getString();
		var startDir = last != null && !last.isEmpty() && new java.io.File(last).isDirectory()
				? new java.io.File(last) : new java.io.File(System.getProperty("user.home"));
		var file = FileDialogHelper.openImageFileDialog(startDir, defaultName, true);
		if (file == null) {
			return;
		}
		if (file.getAbsoluteFile().getParentFile() != null) {
			Setting.LAST_SAVE_DIR.set(file.getAbsoluteFile().getParentFile().getPath());
		}
		var saveName = Setting.checkFileNameExtension(type, false, file.getPath());
		if (saveName == null) {
			return;
		}
		var error = writeNewImage(new java.io.File(saveName), type, toWrite);
		JOptionPane.showMessageDialog(this, error == null ? "The new disk image was saved:\n" + saveName : error, "DIR art",
				error == null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	/** Create a new disk image file with the disk name, ID and directory entries of the editor. @return null, or an error text */
	String writeNewImage(java.io.File file, DiskImageType type, List<DirArtWriter.Row> toWrite) {
		try {
			var img = type.getInstance(new ConsoleStream(new javax.swing.JTextArea()));
			var idText = new String(diskIdChars).replace(' ', '\u00a0');
			if (!img.saveNewImage(file, headerText(), idText)) {
				return "The disk image could not be created.";
			}
			img.setFile(file);
			var name = header.clone();
			for (int i = name.length - 1; i >= 0 && name[i] == 0x20; i--) {
				name[i] = (byte) 0xa0;
			}
			DirArtWriter.write(img, toWrite, DirArtWriter.Mode.REPLACE, name, idBytes(new String(diskIdChars)));
			img.save();
			return null;
		} catch (CbmException | RuntimeException e) {
			return "The disk image could not be saved: " + e.getMessage();
		}
	}

	private void writeToDisk() {
		if (image == null || !DirArtWriter.isSupported(image)) {
			saveAsNewDisk();
			return;
		}
		var mode = (DirArtWriter.Mode) modeBox.getSelectedItem();
		var toWrite = DirArtWriter.rowsToWrite(rows);
		boolean headerOnly = toWrite.isEmpty() && writeHeader.isSelected();
		if (toWrite.isEmpty() && !headerOnly) {
			JOptionPane.showMessageDialog(this, "Nothing was changed, so there is nothing to write.\n"
					+ "To write only the disk name and ID, tick \"Also write disk name and ID\".", "DIR art", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (headerOnly) {
			// only the disk name and ID: the directory entries are kept as they are
			mode = DirArtWriter.Mode.UPDATE;
		}
		var warning = headerOnly ? "The disk name and ID of this disk image will be changed (the directory entries are kept)." : mode == DirArtWriter.Mode.UPDATE
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
				// trailing spaces are stored as shifted spaces ($A0), like the DOS does
				for (int i = diskName.length - 1; i >= 0 && diskName[i] == 0x20; i--) {
					diskName[i] = (byte) 0xa0;
				}
				diskId = idBytes(new String(diskIdChars));
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
	private int ascii(char c) {
		if (c >= 'A' && c <= 'Z') {
			// the lower case / upper case character set has the capital letters at 0x41
			return drawLower ? c - 'A' + 0x41 : c - 'A' + 1;
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
			} else if (row == 0 && col >= ID_COL && col < ID_COL + diskIdChars.length && e.getID() == MouseEvent.MOUSE_PRESSED) {
				selected.clear();
				curRow = 0;
				curCol = DirArtWriter.NAME_LENGTH + (col - ID_COL);
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

		/** Last editable column in the current row: the header line also has the disk ID. */
		private int lastCol() {
			return curRow == 0 ? DirArtWriter.NAME_LENGTH + diskIdChars.length - 1 : DirArtWriter.NAME_LENGTH - 1;
		}

		private void tab(boolean back) {
			int n = rows.size();
			if (!back) {
				if (curRow == 0 && curCol < DirArtWriter.NAME_LENGTH) {
					curCol = DirArtWriter.NAME_LENGTH;
				} else if (curRow < n) {
					curRow++;
					curCol = 0;
				} else {
					curRow = 0;
					curCol = 0;
				}
			} else if (curRow == 0 && curCol >= DirArtWriter.NAME_LENGTH) {
				curCol = 0;
			} else if (curRow == 0) {
				curRow = n;
				curCol = 0;
			} else if (curRow == 1) {
				curRow = 0;
				curCol = DirArtWriter.NAME_LENGTH;
			} else {
				curRow--;
				curCol = 0;
			}
		}

		private void setCurrent(int value, char ch) {
			if (curRow == 0 && curCol >= DirArtWriter.NAME_LENGTH) {
				diskIdChars[curCol - DirArtWriter.NAME_LENGTH] = ch;
				writeHeader.setSelected(true);
			} else {
				setCell(curRow, curCol, value);
			}
		}

		private void handleKey(KeyEvent e) {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_TAB:
				tab(e.isShiftDown());
				break;
			case KeyEvent.VK_LEFT:
				curCol = Math.max(0, curCol - 1);
				break;
			case KeyEvent.VK_RIGHT:
				curCol = Math.min(lastCol(), curCol + 1);
				break;
			case KeyEvent.VK_UP:
				curRow = Math.max(0, curRow - 1);
				break;
			case KeyEvent.VK_DOWN:
				curRow = Math.min(rows.size(), curRow + 1);
				curCol = Math.min(curCol, lastCol());
				break;
			case KeyEvent.VK_BACK_SPACE:
				curCol = Math.max(0, curCol - 1);
				setCurrent(0x20, ' ');
				break;
			case KeyEvent.VK_ESCAPE:
				selected.clear();
				break;
			case KeyEvent.VK_DELETE:
				setCurrent(0x20, ' ');
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
				if (curRow == 0 && curCol >= DirArtWriter.NAME_LENGTH) {
					// disk ID: like on a C64 a letter without Shift is a capital letter
					if (c >= 0x20 && c < 0x7f) {
						setCurrent(0, Character.isLetter(c) ? (e.isShiftDown() ? Character.toLowerCase(c) : Character.toUpperCase(c)) : c);
						curCol = Math.min(lastCol(), curCol + 1);
					}
					break;
				}
				int p = C64.asciiToPetscii(c);
				if (Character.isLetter(c) && p >= 0 && charsetBox.getSelectedIndex() == 1 && !e.isShiftDown()) {
					// lower case / upper case character set: a letter without Shift is the capital letter
					p += 0x80;
				}
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
			var bg = machine.background();
			var fg = machine.foreground();
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
			drawText(g, "\" " + new String(diskIdChars), ART_COL + DirArtWriter.NAME_LENGTH, 0, fg, headBg);
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
			int curX = curCol < DirArtWriter.NAME_LENGTH ? ART_COL + curCol : ID_COL + (curCol - DirArtWriter.NAME_LENGTH);
			g.drawRect(curX * CELL, curRow * CELL + (curRow > 0 ? GAP : 0), CELL - 1, CELL - 1);
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
			var bg = machine.background();
			var fg = machine.foreground();
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
