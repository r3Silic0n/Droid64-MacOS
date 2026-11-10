package droid64.gui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import droid64.d64.Utility;

public class ListTableCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final EntryTableModel model;

	public ListTableCellRenderer(EntryTableModel model) {
		this.model = model;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		var text = value != null ? value.toString() : Utility.EMPTY;
		var label = new JLabel(text);
		label.setOpaque(true);
		label.setFont(table.getFont());
		if (model.getMode() == EntryTableModel.MODE_LOCAL) {
			if (model.isFile(row)) {
				if (model.isImageFile(row)) {
					label.setForeground(Color.RED);
					label.setBackground(Setting.DIR_LOCAL_BG.getColor());
				} else {
					label.setForeground(Setting.DIR_LOCAL_FG.getColor());
					label.setBackground(Setting.DIR_LOCAL_BG.getColor());
				}
			} else {
				label.setForeground(Color.BLUE);
				label.setBackground(Setting.DIR_LOCAL_BG.getColor());
			}
			if (column == 3) {
				label.setHorizontalAlignment(SwingConstants.RIGHT);
			}
		} else {
			label.setForeground(table.getForeground());
			label.setBackground(table.getBackground());
			if (model.isDeleted(row)) {
				// deleted files are shown dimmed
				var fg = table.getForeground();
				var bg = table.getBackground();
				label.setForeground(new Color((fg.getRed() + bg.getRed() * 2) / 3, (fg.getGreen() + bg.getGreen() * 2) / 3, (fg.getBlue() + bg.getBlue() * 2) / 3));
			}
		}

		if (isSelected) {
			var tmp = label.getForeground();
			label.setForeground(label.getBackground());
			label.setBackground(tmp);
		}

		return label;
	}
}
