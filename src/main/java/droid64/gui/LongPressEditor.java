package droid64.gui;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

import droid64.d64.FileType;

/**
 * Holding the left mouse button on a file name opens an in-place editor in the file list (list font and colours),
 * holding it on the type (PRG, DEL ...) offers the other types.
 */
final class LongPressEditor extends MouseAdapter {

	private static final int DELAY_MS = 600;
	private static final int MAX_NAME = 16;
	private static final int NAME_COLUMN = 2;
	private static final int TYPE_COLUMN = 3;
	private static final FileType[] TYPES = { FileType.PRG, FileType.SEQ, FileType.USR, FileType.DEL };

	private final JTable table;
	private final IntPredicate editable;
	private final BiConsumer<Integer, String> renamer;
	private final BiConsumer<Integer, FileType> typeSetter;
	private final java.util.function.IntFunction<String> typeOf;
	private final java.util.function.IntFunction<String> nameOf;
	private final Timer timer;
	private Point pressPoint;
	private int pressRow = -1;
	private int pressColumn = -1;
	private JTextField editor;
	private boolean edited;
	private long started;

	LongPressEditor(JTable table, IntPredicate editable, java.util.function.IntFunction<String> typeOf,
			java.util.function.IntFunction<String> nameOf, BiConsumer<Integer, String> renamer, BiConsumer<Integer, FileType> typeSetter) {
		this.table = table;
		this.editable = editable;
		this.typeOf = typeOf;
		this.nameOf = nameOf;
		this.renamer = renamer;
		this.typeSetter = typeSetter;
		timer = new Timer(DELAY_MS, e -> fire());
		timer.setRepeats(false);
		table.addMouseListener(this);
		table.addMouseMotionListener(this);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		cancelTimer();
		if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1 || e.isPopupTrigger() || e.isShiftDown()
				|| e.isControlDown() || e.isMetaDown()) {
			return;
		}
		int row = table.rowAtPoint(e.getPoint());
		int column = table.columnAtPoint(e.getPoint());
		if (row < 0 || column < 0) {
			return;
		}
		int modelColumn = table.convertColumnIndexToModel(column);
		if ((modelColumn == NAME_COLUMN || modelColumn == TYPE_COLUMN) && editable.test(row)) {
			pressPoint = e.getPoint();
			pressRow = row;
			pressColumn = column;
			timer.restart();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		cancelTimer();
		if (editor != null) {
			// the release that ends the long press must not take the editor away or drop its selection
			var field = editor;
			SwingUtilities.invokeLater(() -> refocus(field));
		}
	}

	private void refocus(JTextField field) {
		if (editor == field) {
			field.requestFocusInWindow();
			if (!edited) {
				field.selectAll();
			}
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (pressPoint != null && pressPoint.distance(e.getPoint()) > 4) {
			cancelTimer();
		}
	}

	private void cancelTimer() {
		timer.stop();
		pressPoint = null;
	}

	private void fire() {
		if (pressPoint == null || pressRow < 0 || pressRow >= table.getRowCount()) {
			return;
		}
		int row = pressRow;
		int column = pressColumn;
		pressPoint = null;
		table.setRowSelectionInterval(row, row);
		if (table.convertColumnIndexToModel(column) == NAME_COLUMN) {
			startNameEdit(row, column);
		} else {
			showTypeMenu(row, column);
		}
	}

	private void startNameEdit(int row, int column) {
		closeEditor();
		var rect = table.getCellRect(row, column, true);
		var text = nameOf.apply(row);
		var fm = table.getFontMetrics(table.getFont());
		var prefix = " \"";
		int prefixWidth = fm.stringWidth(prefix);
		// the list shows the name as  "NAME"  - the field starts after the space and the quote, so the text stays where it was
		var field = new JTextField() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(java.awt.Graphics g) {
				super.paintComponent(g);
				g.setColor(getForeground());
				g.setFont(getFont());
				var m = g.getFontMetrics();
				g.drawString(prefix, 0, (getHeight() - m.getHeight()) / 2 + m.getAscent());
			}
		};
		var doc = new PlainDocument();
		doc.setDocumentFilter(new DocumentFilter() {
			@Override
			public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet a) throws BadLocationException {
				if (s == null) {
					return;
				}
				s = applyCase(s);
				int room = MAX_NAME - (fb.getDocument().getLength() - length);
				if (room > 0) {
					super.replace(fb, offset, length, s.length() > room ? s.substring(0, room) : s, a);
				} else if (length > 0 && s.isEmpty()) {
					super.replace(fb, offset, length, s, a);
				}
			}

			@Override
			public void insertString(FilterBypass fb, int offset, String s, AttributeSet a) throws BadLocationException {
				replace(fb, offset, 0, s, a);
			}
		});
		field.setDocument(doc);
		field.setText(text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text);
		field.setFont(table.getFont());
		// the list colours (not reversed), so it stays readable on a selected, reversed row
		Color fg = table.getForeground();
		Color bg = table.getBackground();
		field.setOpaque(true);
		field.setBackground(bg);
		field.setForeground(fg);
		field.setCaretColor(fg);
		field.setSelectionColor(fg);
		field.setSelectedTextColor(bg);
		field.setBorder(BorderFactory.createEmptyBorder(0, prefixWidth, 0, 0));
		field.setBounds(rect.x, rect.y, rect.width, rect.height);
		field.setFocusTraversalKeysEnabled(false);
		field.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent ev) {
				if (ev.getKeyCode() == KeyEvent.VK_ENTER) {
					ev.consume();
					commit(row, field);
				} else if (ev.getKeyCode() == KeyEvent.VK_ESCAPE) {
					ev.consume();
					closeEditor();
				}
			}
		});
		field.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent ev) {
				if (editor != field) {
					return;
				}
				var other = ev.getOppositeComponent();
				if (!ev.isTemporary() && (other == table || other == null) && table.isShowing() && System.currentTimeMillis() - started < 1500) {
					// the list took the focus back (mouse release, selection change): give it back to the editor
					SwingUtilities.invokeLater(() -> refocus(field));
				} else if (!ev.isTemporary()) {
					closeEditor();
				}
			}
		});
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent ev) {
				edited = true;
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent ev) {
				edited = true;
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent ev) {
				edited = true;
			}
		});
		var caret = new BlockCaret();
		caret.setBlinkRate(530);
		field.setCaret(caret);
		edited = false;
		started = System.currentTimeMillis();
		editor = field;
		table.add(field);
		field.requestFocusInWindow();
		field.selectAll();
		table.repaint(rect);
	}

	/**
	 * As on the C64: letters are capitals, lower case only while Shift is held (Caps Lock is ignored).
	 * Text that is not typed (paste) becomes capitals.
	 */
	private static String applyCase(String text) {
		var event = java.awt.EventQueue.getCurrentEvent();
		boolean shift = event instanceof KeyEvent && ((KeyEvent) event).isShiftDown();
		var sb = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			if (c < 128 && Character.isLetter(c)) {
				sb.append(shift ? Character.toLowerCase(c) : Character.toUpperCase(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private void commit(int row, JTextField field) {
		var newName = field.getText();
		var oldName = nameOf.apply(row);
		closeEditor();
		if (!newName.isEmpty() && !newName.equals(oldName)) {
			renamer.accept(row, newName);
		}
	}

	private void closeEditor() {
		if (editor != null) {
			var field = editor;
			editor = null;
			table.remove(field);
			table.repaint();
			table.requestFocusInWindow();
		}
	}

	private void showTypeMenu(int row, int column) {
		var current = typeOf.apply(row);
		Color fg = table.getForeground();
		Color bg = table.getBackground();
		var list = new javax.swing.JList<>(java.util.Arrays.stream(TYPES).map(FileType::name).toArray(String[]::new));
		list.setFont(table.getFont());
		// inverse colours: the list background as text, the list text colour as background; the selected line is the normal way round
		list.setBackground(fg);
		list.setForeground(bg);
		list.setSelectionBackground(bg);
		list.setSelectionForeground(fg);
		list.setFixedCellHeight(table.getRowHeight());
		list.setSelectedValue(current, false);
		list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		var menu = new JPopupMenu();
		menu.setBorder(BorderFactory.createLineBorder(bg));
		menu.setBackground(fg);
		menu.setOpaque(true);
		menu.add(list);
		list.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent ev) {
				int i = list.locationToIndex(ev.getPoint());
				if (i >= 0) {
					list.setSelectedIndex(i);
				}
			}
		});
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent ev) {
				int i = list.locationToIndex(ev.getPoint());
				menu.setVisible(false);
				if (i >= 0 && !TYPES[i].name().equals(current)) {
					typeSetter.accept(row, TYPES[i]);
				}
			}
		});
		var rect = table.getCellRect(row, column, true);
		menu.show(table, rect.x, rect.y + rect.height);
	}

	/** A block cursor like on the C64: a blinking filled cell in the text colour with the character reversed. */
	private static final class BlockCaret extends javax.swing.text.DefaultCaret {
		private static final long serialVersionUID = 1L;

		private int cellWidth(javax.swing.text.JTextComponent c) {
			return Math.max(4, c.getFontMetrics(c.getFont()).charWidth('M'));
		}

		@Override
		public void paint(java.awt.Graphics g) {
			var c = getComponent();
			if (c == null || !isVisible()) {
				return;
			}
			try {
				var r = c.modelToView2D(getDot());
				if (r == null) {
					return;
				}
				var fm = c.getFontMetrics(c.getFont());
				int x = (int) r.getX();
				int y = (int) r.getY();
				int w = cellWidth(c);
				int h = (int) r.getHeight();
				g.setColor(c.getCaretColor());
				g.fillRect(x, y, w, h);
				int dot = getDot();
				var text = c.getText();
				if (dot < text.length()) {
					g.setColor(c.getBackground());
					g.setFont(c.getFont());
					g.drawString(String.valueOf(text.charAt(dot)), x, y + fm.getAscent());
				}
			} catch (BadLocationException e) {
				// nothing to paint
			}
		}

		@Override
		protected synchronized void damage(java.awt.Rectangle r) {
			if (r != null) {
				var c = getComponent();
				x = r.x;
				y = r.y;
				width = (c != null ? cellWidth(c) : 8) + 2;
				height = r.height;
				repaint();
			}
		}
	}
}
