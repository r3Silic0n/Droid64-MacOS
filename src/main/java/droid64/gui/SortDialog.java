package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import droid64.d64.CbmFile;

/** Dialog to choose how the files of a disk image are sorted, with a preview of the result. */
public class SortDialog {

	private final List<CbmFile> files;
	/** file positions (in the directory) that take part in the sort */
	private final List<Integer> positions;
	private List<Integer> order = new ArrayList<>();
	private boolean accepted = false;

	private final JRadioButton byName = new JRadioButton("Name", true);
	private final JRadioButton byLength = new JRadioButton("Length (blocks)");
	private final JRadioButton byType = new JRadioButton("Type (DEL, SEQ, PRG ...)");
	private final JRadioButton ascending = new JRadioButton("Ascending", true);
	private final JRadioButton descending = new JRadioButton("Descending");
	private final PreviewModel model = new PreviewModel();

	/**
	 * @param files the files in directory order
	 * @param positions the directory positions of the files that are sorted (scratched files are left out)
	 */
	public SortDialog(List<CbmFile> files, List<Integer> positions) {
		this.files = files;
		this.positions = positions;
	}

	/**
	 * Show the dialog.
	 * @return the new order as indices into the positions list (the first is the file that goes first),
	 *         or null if cancelled
	 */
	public List<Integer> show(Frame parent) {
		var dialog = new JDialog(parent, "Sort files", true);

		var group = new ButtonGroup();
		group.add(byName);
		group.add(byLength);
		group.add(byType);
		var dirGroup = new ButtonGroup();
		dirGroup.add(ascending);
		dirGroup.add(descending);
		java.awt.event.ActionListener update = e -> refresh();
		for (var b : new JRadioButton[] { byName, byLength, byType, ascending, descending }) {
			b.addActionListener(update);
		}

		var keyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		keyPanel.setBorder(BorderFactory.createTitledBorder("Sort by"));
		keyPanel.add(byName);
		keyPanel.add(byLength);
		keyPanel.add(byType);
		var dirPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		dirPanel.setBorder(BorderFactory.createTitledBorder("Order"));
		dirPanel.add(ascending);
		dirPanel.add(descending);
		var options = new JPanel(new BorderLayout());
		options.add(keyPanel, BorderLayout.CENTER);
		options.add(dirPanel, BorderLayout.EAST);

		var table = new JTable(model);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setBackground(Setting.DIR_BG.getColor());
		table.setForeground(Setting.DIR_FG.getColor());
		table.setFocusable(false);
		table.setRowSelectionAllowed(false);
		var cbm = Setting.CBM_FONT.getFont();
		var font = cbm != null ? cbm.deriveFont(12f) : table.getFont();
		table.setFont(font);
		table.setRowHeight(Math.max(6, Math.round(font.getSize2D() * 25f / 32f)));
		table.getColumnModel().getColumn(0).setPreferredWidth(40);
		table.getColumnModel().getColumn(1).setPreferredWidth(50);
		table.getColumnModel().getColumn(2).setPreferredWidth(240);
		table.getColumnModel().getColumn(3).setPreferredWidth(40);
		table.getColumnModel().getColumn(4).setPreferredWidth(50);
		var scroll = new JScrollPane(table);
		scroll.getViewport().setBackground(Setting.DIR_BG.getColor());
		var previewPanel = new JPanel(new BorderLayout());
		previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
		previewPanel.add(scroll, BorderLayout.CENTER);

		var ok = new JButton("OK");
		var cancel = new JButton("Cancel");
		ok.addActionListener(e -> {
			accepted = true;
			dialog.dispose();
		});
		cancel.addActionListener(e -> dialog.dispose());
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(cancel);
		buttons.add(ok);

		var content = new JPanel(new BorderLayout());
		content.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		content.add(options, BorderLayout.NORTH);
		content.add(previewPanel, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);
		content.setPreferredSize(new Dimension(680, 480));

		refresh();
		dialog.setContentPane(content);
		dialog.getRootPane().setDefaultButton(ok);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		return accepted ? order : null;
	}

	private void refresh() {
		Comparator<Integer> cmp;
		if (byLength.isSelected()) {
			cmp = Comparator.comparingInt((Integer i) -> files.get(positions.get(i)).getSizeInBlocks());
		} else if (byType.isSelected()) {
			cmp = Comparator.comparingInt((Integer i) -> files.get(positions.get(i)).getFileType() != null
					? files.get(positions.get(i)).getFileType().ordinal() : 99);
		} else {
			cmp = (a, b) -> files.get(positions.get(a)).compareTo(files.get(positions.get(b)));
		}
		if (descending.isSelected()) {
			cmp = cmp.reversed();
		}
		// equal keys: keep the current order, so the sort is stable
		var list = new ArrayList<Integer>();
		for (int i = 0; i < positions.size(); i++) {
			list.add(i);
		}
		list.sort(cmp);
		order = list;
		model.fireTableDataChanged();
	}

	private class PreviewModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] names = { "New", "Was", "Name", "Ty", "Bl" };

		@Override
		public int getRowCount() {
			return order.size();
		}

		@Override
		public int getColumnCount() {
			return names.length;
		}

		@Override
		public String getColumnName(int column) {
			return names[column];
		}

		@Override
		public Object getValueAt(int row, int column) {
			int idx = order.get(row);
			var file = files.get(positions.get(idx));
			switch (column) {
			case 0:
				return Integer.toString(row + 1);
			case 1:
				return idx == row ? "" : Integer.toString(idx + 1);
			case 2:
				return "\"" + file.getName() + "\"";
			case 3:
				return file.getFileType() != null ? file.getFileType().name() : "";
			default:
				return Integer.toString(file.getSizeInBlocks());
			}
		}
	}
}
