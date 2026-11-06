package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;

/**
 * Hex editor for the contents of a file, in the way of the Star Commander file editor: hexadecimal and character columns
 * (ASCII or C64 PETSCII), editing in either column, overwrite and insert mode, find, go to, fill, insert and delete bytes,
 * copy and paste, undo and redo. PRG files also show the C64 memory address of each byte.
 */
public final class HexEditorDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int PER_ROW = 16;
	private static final int MAX_UNDO = 300;

	/** Writes the edited data back. */
	public interface Saver {
		/** @return null if saved, otherwise an error message */
		String save(byte[] data);
	}

	private final transient Saver saver;
	private final boolean prg;
	private final String fileName;
	private transient byte[] data;
	private transient byte[] saved;
	private final transient Deque<byte[]> undo = new ArrayDeque<>();
	private final transient Deque<byte[]> redo = new ArrayDeque<>();

	private int cursor;
	private boolean lowNibble;
	private boolean textPane;
	private int anchor = -1;
	private boolean insertMode;
	private C64.Machine machine = C64.Machine.C64;
	private int textMode;	// 0 ASCII, 1 C64 upper case, 2 C64 lower case
	private String lastFind = "";
	private int lastFindKind;

	private final Canvas canvas = new Canvas();
	private final JScrollPane scroll;
	private final JLabel status = new JLabel(" ");
	private final JButton saveButton = new JButton("Save");
	private final JButton undoButton = new JButton("Undo");
	private final JButton redoButton = new JButton("Redo");

	/**
	 * Show the editor.
	 * @param mainPanel main window
	 * @param name file name shown in the title
	 * @param content the bytes of the file
	 * @param isPrg true if the first two bytes are a load address
	 * @param saver writes the data back, null makes the editor read only
	 */
	public static void show(MainPanel mainPanel, String name, byte[] content, boolean isPrg, C64.Machine machine, Saver saver) {
		var d = new HexEditorDialog(mainPanel, name, content, isPrg, machine, saver);
		d.setVisible(true);
	}

	private HexEditorDialog(MainPanel mainPanel, String name, byte[] content, boolean isPrg, C64.Machine machine, Saver saver) {
		super(mainPanel.getParent(), "Hex edit", ModalityType.MODELESS);
		this.saver = saver;
		this.machine = machine != null ? machine : C64.Machine.C64;
		this.prg = isPrg && content.length >= 2;
		this.fileName = name;
		this.data = content.clone();
		this.saved = content.clone();
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				close();
			}
		});

		var tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		saveButton.addActionListener(e -> save());
		saveButton.setEnabled(saver != null);
		undoButton.addActionListener(e -> undo());
		redoButton.addActionListener(e -> redo());
		var find = new JButton("Find…");
		find.addActionListener(e -> find());
		var next = new JButton("Find next");
		next.addActionListener(e -> findNext());
		var go = new JButton("Go to…");
		go.addActionListener(e -> goTo());
		var fill = new JButton("Fill…");
		fill.addActionListener(e -> fill());
		var ins = new JButton("Insert…");
		ins.addActionListener(e -> insertBytes());
		var del = new JButton("Delete");
		del.addActionListener(e -> deleteBytes());
		var insBox = new JCheckBox("Insert mode");
		insBox.addActionListener(e -> {
			insertMode = insBox.isSelected();
			canvas.requestFocusInWindow();
			updateStatus();
		});
		var modeBox = new JComboBox<>(new String[] { "ASCII", "C64 upper case", "C64 lower case" });
		modeBox.setSelectedIndex(1);
		textMode = 1;
		modeBox.addActionListener(e -> {
			textMode = modeBox.getSelectedIndex();
			canvas.repaint();
			canvas.requestFocusInWindow();
		});
		for (var b : new JButton[] { saveButton, undoButton, redoButton, find, next, go, fill, ins, del }) {
			b.setFocusable(false);
			tools.add(b);
		}
		insBox.setFocusable(false);
		tools.add(insBox);
		tools.add(new JLabel("Characters"));
		modeBox.setFocusable(false);
		tools.add(modeBox);
		var machineBox = new JComboBox<>(C64.Machine.values());
		machineBox.setSelectedItem(this.machine);
		machineBox.addActionListener(e -> {
			this.machine = (C64.Machine) machineBox.getSelectedItem();
			canvas.repaint();
			canvas.requestFocusInWindow();
		});
		machineBox.setFocusable(false);
		tools.add(new JLabel("Computer"));
		tools.add(machineBox);

		scroll = new JScrollPane(canvas);
		scroll.getVerticalScrollBar().setUnitIncrement(canvas.lineHeight());
		status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(tools, BorderLayout.NORTH);
		getContentPane().add(scroll, BorderLayout.CENTER);
		getContentPane().add(status, BorderLayout.SOUTH);
		installKeys();
		setSize(Math.max(canvas.getPreferredSize().width + 40, 1120), 620);
		setLocationRelativeTo(mainPanel.getParent());
		updateStatus();
		canvas.requestFocusInWindow();
	}

	// ------------------------------------------------------------------ editing

	private boolean modified() {
		return !Arrays.equals(data, saved);
	}

	private void pushUndo() {
		undo.push(data.clone());
		while (undo.size() > MAX_UNDO) {
			undo.removeLast();
		}
		redo.clear();
	}

	private void undo() {
		if (!undo.isEmpty()) {
			redo.push(data.clone());
			data = undo.pop();
			cursor = Math.min(cursor, Math.max(0, data.length - 1));
			anchor = -1;
			changed();
		}
	}

	private void redo() {
		if (!redo.isEmpty()) {
			undo.push(data.clone());
			data = redo.pop();
			cursor = Math.min(cursor, Math.max(0, data.length - 1));
			anchor = -1;
			changed();
		}
	}

	private void changed() {
		canvas.revalidate();
		canvas.repaint();
		updateStatus();
		reveal();
	}

	private void setByte(int index, int value) {
		pushUndo();
		if (index >= data.length) {
			data = Arrays.copyOf(data, index + 1);
		}
		data[index] = (byte) value;
	}

	private void insertAt(int index, byte[] bytes) {
		var n = new byte[data.length + bytes.length];
		System.arraycopy(data, 0, n, 0, index);
		System.arraycopy(bytes, 0, n, index, bytes.length);
		System.arraycopy(data, index, n, index + bytes.length, data.length - index);
		data = n;
	}

	private void typeNibble(int v) {
		if (cursor > data.length || (!insertMode && cursor >= data.length)) {
			return;
		}
		if (!lowNibble) {
			if (insertMode) {
				pushUndo();
				insertAt(cursor, new byte[] { (byte) (v << 4) });
			} else {
				setByte(cursor, (v << 4) | (data[cursor] & 0x0f));
			}
			lowNibble = true;
		} else {
			setByte(cursor, (data[cursor] & 0xf0) | v);
			lowNibble = false;
			cursor++;
		}
		anchor = -1;
		changed();
	}

	private void typeChar(char c) {
		int b;
		if (textMode == 0) {
			if (c < 0x20 || c > 0xff) {
				return;
			}
			b = c;
		} else {
			b = C64.asciiToPetscii(c);
			if (b < 0) {
				return;
			}
		}
		if (insertMode) {
			pushUndo();
			insertAt(cursor, new byte[] { (byte) b });
		} else if (cursor < data.length) {
			setByte(cursor, b);
		} else {
			return;
		}
		cursor++;
		anchor = -1;
		lowNibble = false;
		changed();
	}

	private int selStart() {
		return anchor < 0 ? cursor : Math.min(anchor, cursor);
	}

	private int selEnd() {
		return anchor < 0 ? cursor : Math.max(anchor, cursor);
	}

	private boolean hasSelection() {
		return anchor >= 0 && anchor != cursor;
	}

	private void deleteBytes() {
		if (data.length == 0) {
			return;
		}
		int from = selStart();
		int to = hasSelection() ? selEnd() : from;
		to = Math.min(to, data.length - 1);
		if (from > to || from >= data.length) {
			return;
		}
		pushUndo();
		var n = new byte[data.length - (to - from + 1)];
		System.arraycopy(data, 0, n, 0, from);
		System.arraycopy(data, to + 1, n, from, data.length - to - 1);
		data = n;
		cursor = Math.min(from, data.length);
		anchor = -1;
		lowNibble = false;
		changed();
	}

	private void insertBytes() {
		var count = new JTextField("1", 6);
		var value = new JTextField("00", 4);
		if (!ask("Insert bytes at the cursor", new Object[] { "Count (decimal)", count, "Value (hex)", value })) {
			return;
		}
		int n = parseInt(count.getText(), 10);
		int v = parseInt(value.getText(), 16);
		if (n < 1 || n > 65536 || v < 0 || v > 255) {
			return;
		}
		pushUndo();
		var bytes = new byte[n];
		Arrays.fill(bytes, (byte) v);
		insertAt(Math.min(cursor, data.length), bytes);
		anchor = -1;
		changed();
	}

	private void fill() {
		int from = selStart();
		int len = hasSelection() ? selEnd() - selStart() + 1 : 1;
		var count = new JTextField(String.valueOf(len), 6);
		var value = new JTextField("00", 4);
		if (!ask("Fill from the cursor or the selection", new Object[] { "Count (decimal)", count, "Value (hex)", value })) {
			return;
		}
		int n = parseInt(count.getText(), 10);
		int v = parseInt(value.getText(), 16);
		if (n < 1 || v < 0 || v > 255 || from >= data.length) {
			return;
		}
		pushUndo();
		for (int i = from; i < Math.min(data.length, from + n); i++) {
			data[i] = (byte) v;
		}
		changed();
	}

	private void copy() {
		if (data.length == 0) {
			return;
		}
		int from = selStart();
		int to = Math.min(hasSelection() ? selEnd() : from, data.length - 1);
		var sb = new StringBuilder();
		for (int i = from; i <= to; i++) {
			sb.append(String.format("%02X", data[i] & 0xff));
			if (i < to) {
				sb.append(' ');
			}
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
	}

	private void paste() {
		try {
			var text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			var bytes = parseHex(text);
			if (bytes == null || bytes.length == 0) {
				return;
			}
			pushUndo();
			if (insertMode) {
				insertAt(Math.min(cursor, data.length), bytes);
			} else {
				if (cursor + bytes.length > data.length) {
					data = Arrays.copyOf(data, cursor + bytes.length);
				}
				System.arraycopy(bytes, 0, data, cursor, bytes.length);
			}
			cursor += bytes.length;
			anchor = -1;
			changed();
		} catch (Exception e) {	// NOSONAR clipboard without text
			Toolkit.getDefaultToolkit().beep();
		}
	}

	// ------------------------------------------------------------------ find, go to

	private static byte[] parseHex(String text) {
		var clean = text.replaceAll("(?i)0x|\\$|[\\s,]", "");
		if (clean.isEmpty() || clean.length() % 2 != 0 || !clean.matches("[0-9a-fA-F]+")) {
			return null;
		}
		var out = new byte[clean.length() / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
		}
		return out;
	}

	private static int parseInt(String s, int radix) {
		try {
			return Integer.parseInt(s.trim().replaceFirst("^(?i)\\$|0x", ""), radix);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private boolean ask(String title, Object[] content) {
		return JOptionPane.showConfirmDialog(this, content, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}

	private void find() {
		var kind = new JComboBox<>(new String[] { "Hex bytes", "Text (ASCII)", "Text (PETSCII)" });
		kind.setSelectedIndex(lastFindKind);
		var field = new JTextField(lastFind, 24);
		if (!ask("Find", new Object[] { kind, field })) {
			return;
		}
		lastFind = field.getText();
		lastFindKind = kind.getSelectedIndex();
		findFrom(cursor + 1);
	}

	private void findNext() {
		if (lastFind.isEmpty()) {
			find();
		} else {
			findFrom(cursor + 1);
		}
	}

	private void findFrom(int start) {
		byte[] needle;
		if (lastFindKind == 0) {
			needle = parseHex(lastFind);
		} else {
			needle = new byte[lastFind.length()];
			for (int i = 0; i < needle.length; i++) {
				int b = lastFindKind == 1 ? lastFind.charAt(i) : C64.asciiToPetscii(lastFind.charAt(i));
				needle[i] = (byte) b;
			}
		}
		if (needle == null || needle.length == 0) {
			JOptionPane.showMessageDialog(this, "Enter hexadecimal bytes such as A9 00 8D, or some text.", "Find", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		for (int pass = 0; pass < 2; pass++) {
			int from = pass == 0 ? Math.max(0, start) : 0;
			int to = pass == 0 ? data.length - needle.length : Math.min(data.length - needle.length, start - 1);
			for (int i = from; i <= to; i++) {
				boolean ok = true;
				for (int j = 0; j < needle.length && ok; j++) {
					ok = data[i + j] == needle[j];
				}
				if (ok) {
					cursor = i + needle.length - 1;
					anchor = i;
					lowNibble = false;
					changed();
					return;
				}
			}
		}
		JOptionPane.showMessageDialog(this, "Not found.", "Find", JOptionPane.INFORMATION_MESSAGE);
	}

	private void goTo() {
		var kind = new JComboBox<>(prg ? new String[] { "Memory address (hex)", "File offset (hex)", "File offset (decimal)" }
				: new String[] { "File offset (hex)", "File offset (decimal)" });
		var field = new JTextField(8);
		if (!ask("Go to", new Object[] { kind, field })) {
			return;
		}
		int sel = kind.getSelectedIndex() + (prg ? 0 : 1);
		int v = parseInt(field.getText(), sel == 2 ? 10 : 16);
		if (v < 0) {
			return;
		}
		int target = sel == 0 ? v - loadAddress() + 2 : v;
		if (target < 0 || target >= data.length) {
			JOptionPane.showMessageDialog(this, "Outside of the file.", "Go to", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		cursor = target;
		anchor = -1;
		lowNibble = false;
		changed();
	}

	private int loadAddress() {
		return prg && data.length >= 2 ? (data[0] & 0xff) | ((data[1] & 0xff) << 8) : 0;
	}

	// ------------------------------------------------------------------ save and close

	private void save() {
		if (saver == null) {
			return;
		}
		var err = saver.save(data.clone());
		if (err == null) {
			saved = data.clone();
			updateStatus();
			canvas.repaint();
		} else {
			JOptionPane.showMessageDialog(this, err, "Save failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void close() {
		if (modified()) {
			var options = saver != null ? new String[] { "Save", "Discard", "Cancel" } : new String[] { "Discard", "Cancel" };
			int r = JOptionPane.showOptionDialog(this, "The file has been changed.", "Hex edit", JOptionPane.DEFAULT_OPTION,
					JOptionPane.WARNING_MESSAGE, null, options, options[0]);
			if (r < 0 || "Cancel".equals(options[r])) {
				return;
			}
			if ("Save".equals(options[r])) {
				save();
				if (modified()) {
					return;
				}
			}
		}
		dispose();
	}

	// ------------------------------------------------------------------ status, keys

	private void updateStatus() {
		var sb = new StringBuilder();
		sb.append(String.format("Offset $%04X (%d)", cursor, cursor));
		if (prg && cursor >= 2 && cursor < data.length) {
			sb.append(String.format("  Address $%04X", (loadAddress() + cursor - 2) & 0xffff));
		}
		if (cursor < data.length) {
			int v = data[cursor] & 0xff;
			sb.append(String.format("  Value $%02X %d %s", v, v, String.format("%8s", Integer.toBinaryString(v)).replace(' ', '0')));
		}
		sb.append(String.format("   Size %d bytes", data.length));
		if (prg) {
			sb.append(String.format("  Load $%04X", loadAddress()));
		}
		sb.append(insertMode ? "   INS" : "   OVR");
		if (saver == null) {
			sb.append("   read only");
		}
		status.setText(sb.toString());
		setTitle("Hex edit - " + fileName + (modified() ? " *" : ""));
		undoButton.setEnabled(!undo.isEmpty());
		redoButton.setEnabled(!redo.isEmpty());
		saveButton.setEnabled(saver != null && modified());
	}

	private void reveal() {
		var r = canvas.cellRect(cursor, textPane);
		canvas.scrollRectToVisible(new Rectangle(r.x - 20, r.y - canvas.lineHeight(), r.width + 40, r.height + 2 * canvas.lineHeight()));
	}

	private void installKeys() {
		int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_S, menu), this::save);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), this::undo);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | KeyEvent.SHIFT_DOWN_MASK), this::redo);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), this::redo);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, menu), this::find);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_G, menu), this::goTo);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_C, menu), this::copy);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_V, menu), this::paste);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_A, menu), () -> {
			anchor = 0;
			cursor = Math.max(0, data.length - 1);
			changed();
		});
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), this::findNext);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), () -> {
			insertMode = !insertMode;
			updateStatus();
		});
	}

	private void bind(KeyStroke ks, Runnable r) {
		var key = "act" + ks;
		canvas.getInputMap(JComponent.WHEN_FOCUSED).put(ks, key);
		canvas.getActionMap().put(key, new javax.swing.AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				r.run();
			}
		});
	}

	private void moveCursor(int newCursor, boolean extend) {
		if (extend && anchor < 0) {
			anchor = cursor;
		} else if (!extend) {
			anchor = -1;
		}
		cursor = Math.max(0, Math.min(newCursor, insertMode ? data.length : Math.max(0, data.length - 1)));
		lowNibble = false;
		updateStatus();
		canvas.repaint();
		reveal();
	}

	// ------------------------------------------------------------------ the editor surface

	private final class Canvas extends JComponent implements Scrollable {

		private static final long serialVersionUID = 1L;
		private final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 14);
		private final FontMetrics fm = getFontMetrics(font);
		private final int cw = 10;
		private final int lh = 18;
		private static final int PAD = 8;

		Canvas() {
			setFocusable(true);
			setFont(font);
			setBackground(Color.WHITE);
			setOpaque(true);
			setFocusTraversalKeysEnabled(false);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					requestFocusInWindow();
					int i = indexAt(e.getX(), e.getY());
					if (i >= 0) {
						moveCursor(i, e.isShiftDown());
						if (!e.isShiftDown()) {
							anchor = i;
						}
					}
				}
			});
			addMouseMotionListener(new MouseAdapter() {
				@Override
				public void mouseDragged(MouseEvent e) {
					int i = indexAt(e.getX(), e.getY());
					if (i >= 0) {
						if (anchor < 0) {
							anchor = cursor;
						}
						cursor = Math.min(i, Math.max(0, data.length - 1));
						updateStatus();
						repaint();
					}
				}
			});
			addKeyListener(new java.awt.event.KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					onKey(e);
				}

				@Override
				public void keyTyped(KeyEvent e) {
					onTyped(e);
				}
			});
		}

		int lineHeight() {
			return lh;
		}

		private int addrWidth() {
			return data.length > 0x10000 ? 6 : 4;
		}

		private int offX() {
			return PAD;
		}

		private int memX() {
			return offX() + (addrWidth() + 2) * cw;
		}

		private int hexX() {
			return memX() + (prg ? 7 * cw : 0);
		}

		private int hexCol(int col) {
			return hexX() + col * 3 * cw + (col >= 8 ? cw : 0);
		}

		private int textX() {
			return hexCol(PER_ROW) + 2 * cw;
		}

		private int rows() {
			return Math.max(1, (data.length + PER_ROW) / PER_ROW);
		}

		Rectangle cellRect(int index, boolean text) {
			int row = index / PER_ROW;
			int col = index % PER_ROW;
			return text ? new Rectangle(textX() + col * cw, PAD + row * lh, cw, lh) : new Rectangle(hexCol(col), PAD + row * lh, 2 * cw, lh);
		}

		private int indexAt(int x, int y) {
			int row = (y - PAD) / lh;
			if (row < 0) {
				return -1;
			}
			int col = -1;
			if (x >= textX()) {
				col = (x - textX()) / cw;
				textPane = true;
			} else if (x >= hexX()) {
				for (int c = PER_ROW - 1; c >= 0; c--) {
					if (x >= hexCol(c)) {
						col = c;
						break;
					}
				}
				textPane = false;
			}
			if (col < 0 || col >= PER_ROW) {
				return -1;
			}
			return Math.min(row * PER_ROW + col, Math.max(data.length - (insertMode ? 0 : 1), 0));
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(textX() + PER_ROW * cw + PAD * 2, PAD * 2 + rows() * lh);
		}

		@Override
		protected void paintComponent(Graphics g0) {
			var g = (Graphics2D) g0.create();
			g.setColor(machine.background);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(font);
			var clip = g.getClipBounds();
			int firstRow = Math.max(0, (clip.y - PAD) / lh);
			int lastRow = Math.min(rows() - 1, (clip.y + clip.height - PAD) / lh);
			var fg = machine.foreground;
			var addrColor = machine.mix(0.6);
			var selColor = machine.mix(0.5);
			var changedColor = machine.mix(0.2);
			int selFrom = hasSelection() ? selStart() : -1;
			int selTo = hasSelection() ? selEnd() : -1;
			for (int row = firstRow; row <= lastRow; row++) {
				int y = PAD + row * lh;
				int base = row * PER_ROW;
				glyphs(g, String.format("%0" + addrWidth() + "X", base), offX(), y, addrColor, machine.background);
				if (prg && base + PER_ROW > 2) {
					int addr = loadAddress() + Math.max(base, 2) - 2;
					glyphs(g, base >= 2 ? String.format("$%04X", addr & 0xffff) : "", memX(), y, addrColor, machine.background);
				}
				for (int col = 0; col < PER_ROW; col++) {
					int i = base + col;
					boolean exists = i < data.length;
					var hr = new Rectangle(hexCol(col) - cw / 2, y, 3 * cw, lh);
					var tr = new Rectangle(textX() + col * cw, y, cw, lh);
					boolean sel = exists && i >= selFrom && i <= selTo && selFrom >= 0;
					boolean diff = exists && (i >= saved.length || saved[i] != data[i]);
					if (sel || diff) {
						g.setColor(sel ? selColor : changedColor);
						g.fillRect(hr.x, hr.y, hr.width - cw / 2, hr.height);
						g.fillRect(tr.x, tr.y, tr.width, tr.height);
					}
					if (!exists) {
						continue;
					}
					int v = data[i] & 0xff;
					var cellBg = sel ? selColor : diff ? changedColor : machine.background;
					glyphs(g, String.format("%02X", v), hexCol(col), y, prg && i < 2 ? machine.mix(0.6) : fg, cellBg);
					drawChar(g, v, tr, cellBg, fg);
				}
			}
			// cursor
			if (cursor <= data.length) {
				var hr = cellRect(cursor, false);
				var tr = cellRect(cursor, true);
				g.setColor(machine.foreground);
				var active = textPane ? tr : hr;
				var other = textPane ? hr : tr;
				g.drawRect(active.x - 1, active.y, textPane ? active.width : active.width, active.height - 1);
				if (!textPane && lowNibble) {
					g.fillRect(hr.x + cw, hr.y + lh - 3, cw, 2);
				} else if (!textPane) {
					g.fillRect(hr.x, hr.y + lh - 3, cw, 2);
				}
				g.setColor(machine.mix(0.6));
				g.drawRect(other.x, other.y + lh - 3, other.width, 1);
			}
			g.dispose();
		}

		/** Draw text with the character ROM of the computer. */
		private void glyphs(Graphics2D g, String text, int x, int y, Color color, Color bg) {
			for (int i = 0; i < text.length(); i++) {
				int pet = C64.asciiToPetscii(text.charAt(i));
				if (text.charAt(i) == '$') {
					pet = 0x24;
				}
				if (pet < 0) {
					continue;
				}
				C64.drawPetscii(g, pet, x + i * cw, y + 1, cw, lh - 2, color, bg, machine, false);
			}
		}

		private void drawChar(Graphics2D g, int v, Rectangle r, Color bg, Color fg) {
			if (textMode == 0) {
				g.setColor(fg);
				var s = v >= 0x20 && v < 0x7f ? String.valueOf((char) v) : "·";
				g.drawString(s, r.x, r.y + fm.getAscent() + 1);
			} else {
				// C64 character ROM glyphs, dark on light like the rest of the editor
				var oldHint = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
				C64.drawPetscii(g, v, r.x, r.y + 1, r.width, r.height - 2, fg, bg, machine, textMode == 2);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldHint);
			}
		}

		private void onKey(KeyEvent e) {
			boolean shift = e.isShiftDown();
			int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
			if ((e.getModifiersEx() & menu) != 0) {
				return;
			}
			switch (e.getKeyCode()) {
			case KeyEvent.VK_LEFT:
				moveCursor(cursor - 1, shift);
				break;
			case KeyEvent.VK_RIGHT:
				moveCursor(cursor + 1, shift);
				break;
			case KeyEvent.VK_UP:
				moveCursor(cursor - PER_ROW, shift);
				break;
			case KeyEvent.VK_DOWN:
				moveCursor(cursor + PER_ROW, shift);
				break;
			case KeyEvent.VK_PAGE_UP:
				moveCursor(cursor - PER_ROW * Math.max(1, scroll.getViewport().getHeight() / lh - 1), shift);
				break;
			case KeyEvent.VK_PAGE_DOWN:
				moveCursor(cursor + PER_ROW * Math.max(1, scroll.getViewport().getHeight() / lh - 1), shift);
				break;
			case KeyEvent.VK_HOME:
				moveCursor(e.isControlDown() ? 0 : cursor - cursor % PER_ROW, shift);
				break;
			case KeyEvent.VK_END:
				moveCursor(e.isControlDown() ? data.length : cursor - cursor % PER_ROW + PER_ROW - 1, shift);
				break;
			case KeyEvent.VK_TAB:
				textPane = !textPane;
				lowNibble = false;
				repaint();
				break;
			case KeyEvent.VK_BACK_SPACE:
				if (insertMode && cursor > 0) {
					anchor = -1;
					cursor--;
					deleteBytes();
				} else {
					moveCursor(cursor - 1, false);
				}
				break;
			case KeyEvent.VK_DELETE:
				if (insertMode || hasSelection()) {
					deleteBytes();
				}
				break;
			default:
				break;
			}
		}

		private void onTyped(KeyEvent e) {
			int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
			if ((e.getModifiersEx() & (menu | KeyEvent.ALT_DOWN_MASK)) != 0) {
				return;
			}
			char c = e.getKeyChar();
			if (textPane) {
				typeChar(c);
			} else {
				int v = Character.digit(c, 16);
				if (v >= 0) {
					typeNibble(v);
				}
			}
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return new Dimension(getPreferredSize().width, 480);
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return lh;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return Math.max(lh, visibleRect.height - lh);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return false;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}
}
