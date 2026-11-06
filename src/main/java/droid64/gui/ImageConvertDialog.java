package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import droid64.d64.DiskImage;
import droid64.d64.DiskImageType;

/** Converts the loaded disk image into a new image of another type (D64, D71, D81): the files are copied one by one. */
public class ImageConvertDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final transient DiskImage image;
	private final transient DiskPanel panel;
	private final transient List<ImageConverter.Item> items;
	private final JComboBox<DiskImageType> targetBox;
	private final JTextField fileField = new JTextField(34);
	private final JCheckBox keepFlags = new JCheckBox("Keep the locked / closed flags", true);
	private final JCheckBox openAfter = new JCheckBox("Open the new image in the list", true);
	private final JProgressBar bar = new JProgressBar(0, 100);
	private final JLabel capacity = new JLabel(" ");
	private final JButton convertButton = new JButton("Convert");
	private File dir;

	private ImageConvertDialog(java.awt.Frame owner, DiskPanel panel, DiskImage image) {
		super(owner, "Image convert", true);
		this.panel = panel;
		this.image = image;
		this.items = ImageConverter.plan(image);
		var current = image.getImageFormat();
		var targets = new java.util.ArrayList<DiskImageType>();
		for (var t : ImageConverter.TARGETS) {
			if (t != current) {
				targets.add(t);
			}
		}
		targetBox = new JComboBox<>(targets.toArray(new DiskImageType[0]));
		// a D64 goes to D81 by default, anything else to D64
		for (var t : targets) {
			if (("D64".equals(current.id) && "D81".equals(t.id)) || (!"D64".equals(current.id) && "D64".equals(t.id))) {
				targetBox.setSelectedItem(t);
			}
		}
		targetBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				var c = super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof DiskImageType) {
					setText(((DiskImageType) value).id + " (" + ((DiskImageType) value).driveName + ")");
				}
				return c;
			}
		});

		var src = image.getFile();
		dir = src != null && src.getAbsoluteFile().getParentFile() != null ? src.getAbsoluteFile().getParentFile()
				: new File(System.getProperty("user.home"));

		var top = new JPanel(new java.awt.GridBagLayout());
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
		var gc = new java.awt.GridBagConstraints();
		gc.insets = new java.awt.Insets(3, 4, 3, 4);
		gc.anchor = java.awt.GridBagConstraints.WEST;
		gc.gridx = 0;
		gc.gridy = 0;
		top.add(new JLabel("Source"), gc);
		gc.gridx = 1;
		gc.gridwidth = 2;
		top.add(new JLabel((src != null ? src.getName() : "disk image") + "   " + current.id + ", "
				+ items.size() + " entries, " + (items.stream().mapToInt(i -> i.file.getSizeInBlocks()).sum()) + " blocks"), gc);
		gc.gridwidth = 1;
		gc.gridx = 0;
		gc.gridy = 1;
		top.add(new JLabel("Convert to"), gc);
		gc.gridx = 1;
		gc.gridwidth = 2;
		top.add(targetBox, gc);
		gc.gridwidth = 1;
		gc.gridx = 0;
		gc.gridy = 2;
		top.add(new JLabel("New file"), gc);
		gc.gridx = 1;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		top.add(fileField, gc);
		gc.gridx = 2;
		gc.fill = java.awt.GridBagConstraints.NONE;
		gc.weightx = 0;
		var browse = new JButton("…");
		browse.setToolTipText("Browse");
		top.add(browse, gc);
		gc.gridx = 1;
		gc.gridy = 3;
		gc.gridwidth = 2;
		top.add(keepFlags, gc);
		gc.gridy = 4;
		top.add(openAfter, gc);
		gc.gridx = 0;
		gc.gridy = 5;
		gc.gridwidth = 1;
		top.add(new JLabel("Fits?"), gc);
		gc.gridx = 1;
		gc.gridwidth = 2;
		gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
		var cap = new JPanel(new BorderLayout(8, 0));
		cap.add(bar, BorderLayout.CENTER);
		cap.add(capacity, BorderLayout.EAST);
		top.add(cap, gc);

		var model = new DefaultTableModel(new Object[] { "", "Name", "Type", "Blocks", "Real", "Note" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		for (var item : items) {
			model.addRow(new Object[] { item.level == ImageConverter.SKIP ? "skip" : item.level == ImageConverter.WARN ? "!" : "ok",
					item.file.getName(), item.file.getFileType(), item.file.getSizeInBlocks(), item.sizeDiffers() ? (Object) item.real : "", item.note });
		}
		var table = new JTable(model);
		// Same look as the file list: the detected machine's palette and the CBM font
		var machine = image.isCpmImage() ? null : MachineDetector.detect(image);
		var bg = Setting.DIR_BG.getColor();
		var fg = Setting.DIR_FG.getColor();
		if (machine != null && machine != C64.Machine.C64) {
			bg = machine.background;
			fg = machine.foreground;
		}
		var cbmFont = Setting.CBM_FONT.getFont();
		if (cbmFont != null && Setting.FONT_SIZE.getInteger() != null) {
			cbmFont = cbmFont.deriveFont((float) Setting.FONT_SIZE.getInteger());
		}
		if (cbmFont != null) {
			table.setFont(cbmFont);
		}
		table.setBackground(bg);
		table.setForeground(fg);
		table.setGridColor(bg);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setRowHeight(Math.max(4, Setting.ROW_HEIGHT.getInteger()));
		table.setShowGrid(false);
		table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
				var l = new JLabel(value != null ? value.toString() : "");
				l.setOpaque(true);
				// status and note are ordinary text, the rest uses the CBM font
				l.setFont(col == 0 || col == 5 ? Setting.SYS_FONT.getFont() != null ? Setting.SYS_FONT.getFont().deriveFont((float) Math.max(7, Math.min(11, Setting.ROW_HEIGHT.getInteger()))) : t.getFont() : t.getFont());
				l.setForeground(t.getForeground());
				l.setBackground(t.getBackground());
				return l;
			}
		});
		table.getColumnModel().getColumn(0).setMaxWidth(46);
		table.getColumnModel().getColumn(3).setMaxWidth(60);
		table.getColumnModel().getColumn(4).setMaxWidth(60);
		var hidden = ImageConverter.hidden(image);
		var hiddenLabel = new JLabel(hidden.entries > 0 ? "Hidden data: " + hidden.entries + " scratched entries still hold "
				+ hidden.blocks + " blocks that the directory does not show. They are not converted." : " ");
		hiddenLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 4, 10));
		var scroll = new JScrollPane(table);
		scroll.getViewport().setBackground(bg);
		scroll.setPreferredSize(new Dimension(560, 220));
		scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10), scroll.getBorder()));

		var cancel = new JButton("Cancel");
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(cancel);
		buttons.add(convertButton);

		setLayout(new BorderLayout());
		add(top, BorderLayout.NORTH);
		var center = new JPanel(new BorderLayout());
		center.add(scroll, BorderLayout.CENTER);
		if (hidden.entries > 0) {
			center.add(hiddenLabel, BorderLayout.SOUTH);
		}
		add(center, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);

		targetBox.addActionListener(e -> targetChanged());
		browse.addActionListener(e -> browse());
		cancel.addActionListener(e -> dispose());
		convertButton.addActionListener(e -> convert());
		getRootPane().setDefaultButton(convertButton);
		targetChanged();
		pack();
		setLocationRelativeTo(owner);
	}

	private DiskImageType target() {
		return (DiskImageType) targetBox.getSelectedItem();
	}

	private void targetChanged() {
		var type = target();
		var base = image.getFile() != null ? image.getFile().getName() : "Converted";
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		fileField.setText(new File(dir, Setting.checkFileNameExtension(type, false, base)).getPath());
		int cap = ImageConverter.capacity(type);
		int need = ImageConverter.blocksNeeded(items);
		int entries = ImageConverter.entriesNeeded(items);
		int maxEntries = ImageConverter.maxEntries(type);
		bar.setValue((int) Math.min(100, need * 100L / Math.max(1, cap)));
		boolean fits = need <= cap && entries <= maxEntries;
		capacity.setText(need + " / " + cap + " blocks, " + entries + " / " + maxEntries + " entries" + (fits ? "" : "   does not fit"));
		convertButton.setEnabled(fits);
	}

	private void browse() {
		var f = FileDialogHelper.openImageFileDialog(dir, new File(fileField.getText()).getName(), true);
		if (f != null) {
			fileField.setText(Setting.checkFileNameExtension(target(), false, f.getPath()));
			if (f.getAbsoluteFile().getParentFile() != null) {
				dir = f.getAbsoluteFile().getParentFile();
			}
		}
	}

	private void convert() {
		var dest = new File(fileField.getText().trim());
		if (dest.getName().isEmpty()) {
			return;
		}
		if (image.getFile() != null && dest.getAbsoluteFile().equals(image.getFile().getAbsoluteFile())) {
			JOptionPane.showMessageDialog(this, "The new file must not be the source image.", "Image convert", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (dest.exists() && JOptionPane.showConfirmDialog(this, dest.getName() + " exists. Replace it?", "Image convert",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		var result = ImageConverter.convert(image, items, target(), dest, keepFlags.isSelected());
		if (result.error != null) {
			JOptionPane.showMessageDialog(this, result.error, "Image convert", JOptionPane.ERROR_MESSAGE);
			return;
		}
		var text = new StringBuilder();
		text.append(result.converted).append(" files converted");
		if (result.skipped > 0) {
			text.append(", ").append(result.skipped).append(" skipped");
		}
		text.append(".\n");
		if (result.problems.isEmpty()) {
			text.append("The new image was read back and every file matches the original.");
		} else {
			text.append("Check after the conversion found problems:\n");
			result.problems.stream().limit(12).forEach(p -> text.append(" - ").append(p).append('\n'));
		}
		JOptionPane.showMessageDialog(this, text.toString(), "Image convert",
				result.problems.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
		dispose();
		if (openAfter.isSelected() && panel != null) {
			panel.openDiskImage(dest, true);
		}
	}

	public static void show(java.awt.Frame owner, DiskPanel panel) {
		var image = panel != null ? panel.getDiskImage() : null;
		if (image == null || !panel.isImageLoaded()) {
			JOptionPane.showMessageDialog(owner, "Load a D64, D71 or D81 disk image in the active list first.", "Image convert",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (!ImageConverter.isTarget(image.getImageFormat()) && !"D80".equals(image.getImageFormat().id)
				&& !"D82".equals(image.getImageFormat().id) && !"D67".equals(image.getImageFormat().id)
				|| image.isCpmImage()) {
			JOptionPane.showMessageDialog(owner, "This kind of image cannot be converted.", "Image convert", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		new ImageConvertDialog(owner, panel, image).setVisible(true);
	}
}
