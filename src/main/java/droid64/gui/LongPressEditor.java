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
	private final Timer timer;
	private Point pressPoint;
	private int pressRow = -1;
	private int pressColumn = -1;
	private JTextField editor;

	LongPressEditor(JTable table, IntPredicate editable, java.util.function.IntFunction<String> typeOf,
			BiConsumer<Integer, String> renamer, BiConsumer<Integer, FileType> typeSetter) {
		this.table = table;
		this.editable = editable;
		this.typeOf = typeOf;
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
		var text = String.valueOf(table.getValueAt(row, column));
		var field = new JTextField();
		var doc = new PlainDocument();
		doc.setDocumentFilter(new DocumentFilter() {
			@Override
			public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet a) throws BadLocationException {
				if (s == null) {
					return;
				}
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
		// like a BASIC cursor line: the list colours, reversed
		Color fg = table.getForeground();
		Color bg = table.getBackground();
		field.setBackground(fg);
		field.setForeground(bg);
		field.setCaretColor(bg);
		field.setSelectionColor(bg);
		field.setSelectedTextColor(fg);
		field.setBorder(BorderFactory.createEmptyBorder());
		field.setBounds(rect.x, rect.y, rect.width, rect.height);
		field.setFocusTraversalKeysEnabled(false);
		field.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent ev) {
				char c = ev.getKeyChar();
				if (Character.isLetter(c) && c < 128 && !ev.isControlDown() && !ev.isMetaDown()) {
					// as on the C64: capitals by default, lower case only with Shift (Caps Lock is ignored)
					ev.consume();
					field.replaceSelection(String.valueOf(ev.isShiftDown() ? Character.toLowerCase(c) : Character.toUpperCase(c)));
				}
			}

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
				if (editor == field) {
					closeEditor();
				}
			}
		});
		editor = field;
		table.add(field);
		field.requestFocusInWindow();
		field.selectAll();
		table.repaint(rect);
	}

	private void commit(int row, JTextField field) {
		var newName = field.getText();
		var oldName = String.valueOf(table.getValueAt(row, NAME_COLUMN));
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
		var menu = new JPopupMenu();
		menu.setFont(Setting.SYS_FONT.getFont());
		for (var type : TYPES) {
			var item = new javax.swing.JMenuItem(type.name());
			item.setEnabled(!type.name().equals(current));
			item.addActionListener(e -> typeSetter.accept(row, type));
			menu.add(item);
		}
		var rect = table.getCellRect(row, column, true);
		menu.show(table, rect.x, rect.y + rect.height);
	}
}
