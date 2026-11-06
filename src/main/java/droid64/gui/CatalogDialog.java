package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/** Window of the disk / tape catalog: scan folders, search files, open the image that holds a file. */
public final class CatalogDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int MAX_ROWS = 20000;
	private static CatalogDialog instance;

	private static final class Row {
		final Catalog.Entry entry;
		final Catalog.Item item;

		Row(Catalog.Entry entry, Catalog.Item item) {
			this.entry = entry;
			this.item = item;
		}
	}

	private final transient MainPanel mainPanel;
	private final transient Catalog catalog = new Catalog();
	private final transient List<Row> rows = new ArrayList<>();
	private final JTextField search = new JTextField(24);
	private final JComboBox<String> media = new JComboBox<>(new String[] { "All media", "Disks", "Tapes" });
	private final JLabel status = new JLabel(" ");
	private final JButton scanButton = new JButton("Scan folder...");
	private final JButton rescanButton = new JButton("Rescan all");
	private final JButton removeButton = new JButton("Remove folder...");
	private final JButton stopButton = new JButton("Stop");
	private final JButton openButton = new JButton("Open");
	private volatile boolean cancel;
	private volatile boolean scanning;

	private final AbstractTableModel model = new AbstractTableModel() {
		private static final long serialVersionUID = 1L;
		private final String[] names = { "", "Name", "Type", "Blocks", "Image", "Folder" };

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return names.length;
		}

		@Override
		public String getColumnName(int c) {
			return names[c];
		}

		@Override
		public Class<?> getColumnClass(int c) {
			return c == 0 ? Icon.class : c == 3 ? Integer.class : String.class;
		}

		@Override
		public Object getValueAt(int r, int c) {
			var row = rows.get(r);
			var e = row.entry;
			switch (c) {
			case 0:
				return e.tape ? MediaIcon.TAPE : MediaIcon.DISK;
			case 1:
				if (row.item != null) {
					return DirText.ascii(row.item.name);
				}
				return e.diskName.isEmpty() ? e.imageName() : DirText.ascii(e.diskName).trim();
			case 2:
				return row.item != null ? row.item.type : e.format + (e.machine.isEmpty() ? "" : "  " + e.machine);
			case 3:
				return row.item != null ? (Object) row.item.blocks : null;
			case 4:
				return e.imageName();
			default:
				return e.folder();
			}
		}
	};
	private final JTable table = new JTable(model);

	private CatalogDialog(MainPanel mainPanel) {
		super(mainPanel.getParent(), "Catalog", ModalityType.MODELESS);
		this.mainPanel = mainPanel;
		catalog.load();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		var top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		top.add(new JLabel("Search"));
		top.add(search);
		top.add(media);
		top.add(scanButton);
		top.add(rescanButton);
		top.add(removeButton);
		top.add(stopButton);
		stopButton.setEnabled(false);
		search.setToolTipText("File name, disk name or image name. Several words must all match.");
		scanButton.setToolTipText("Add a folder to the catalog (sub folders and zip files are included)");
		rescanButton.setToolTipText("Read the catalogued folders again; only changed images are read");
		removeButton.setToolTipText("Remove a folder and its images from the catalog");

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(Math.max(18, table.getRowHeight()));
		table.setAutoCreateRowSorter(true);
		var cm = table.getColumnModel();
		cm.getColumn(0).setMaxWidth(28);
		cm.getColumn(0).setMinWidth(28);
		cm.getColumn(1).setPreferredWidth(200);
		cm.getColumn(2).setPreferredWidth(160);
		cm.getColumn(3).setPreferredWidth(60);
		cm.getColumn(4).setPreferredWidth(200);
		cm.getColumn(5).setPreferredWidth(300);
		table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) {
					openSelected();
				}
			}
		});

		var bottom = new JPanel(new BorderLayout());
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
		var close = new JButton("Close");
		close.addActionListener(e -> dispose());
		openButton.setToolTipText("Open the image in the active list and select the file");
		openButton.addActionListener(e -> openSelected());
		buttons.add(openButton);
		buttons.add(close);
		status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		bottom.add(status, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.EAST);

		var content = new JPanel(new BorderLayout());
		content.add(top, BorderLayout.NORTH);
		content.add(new JScrollPane(table), BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		setContentPane(content);

		search.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				refresh();
			}
		});
		media.addActionListener(e -> refresh());
		scanButton.addActionListener(e -> chooseAndScan());
		rescanButton.addActionListener(e -> startScan(new ArrayList<>(catalog.roots())));
		removeButton.addActionListener(e -> removeFolder());
		stopButton.addActionListener(e -> cancel = true);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				cancel = true;
				if (instance == CatalogDialog.this) {
					instance = null;
				}
			}
		});
		setSize(1000, 600);
		WindowBounds.remember(this, Setting.CATALOG_BOUNDS);
		setLocationRelativeTo(mainPanel.getParent());
		refresh();
	}

	/** Show the catalog window (one at a time). */
	public static void show(MainPanel mainPanel) {
		if (instance != null && instance.isDisplayable()) {
			instance.toFront();
			return;
		}
		instance = new CatalogDialog(mainPanel);
		instance.setVisible(true);
	}

	// ---- list ----

	private void refresh() {
		rows.clear();
		var words = DirText.ascii(search.getText()).toLowerCase().trim().split("\\s+");
		boolean empty = search.getText().trim().isEmpty();
		int mode = media.getSelectedIndex();
		for (var e : catalog.entries()) {
			if (mode == 1 && e.tape || mode == 2 && !e.tape) {
				continue;
			}
			if (rows.size() >= MAX_ROWS) {
				break;
			}
			if (empty) {
				rows.add(new Row(e, null));
				continue;
			}
			var imageHay = (DirText.ascii(e.diskName) + " " + e.imageName() + " " + e.folder() + " " + e.machine).toLowerCase();
			if (matches(imageHay, words)) {
				rows.add(new Row(e, null));
			}
			for (var i : e.files) {
				if (rows.size() >= MAX_ROWS) {
					break;
				}
				if (matches((DirText.ascii(i.name) + " " + i.type).toLowerCase(), words)) {
					rows.add(new Row(e, i));
				}
			}
		}
		model.fireTableDataChanged();
		int files = 0;
		for (var e : catalog.entries()) {
			files += e.files.size();
		}
		status.setText(catalog.size() + " images, " + files + " files in " + catalog.roots().size() + " folders. Showing "
				+ rows.size() + (rows.size() >= MAX_ROWS ? " (limit)" : "") + ".");
	}

	private static boolean matches(String hay, String[] words) {
		for (var w : words) {
			if (!w.isEmpty() && !hay.contains(w)) {
				return false;
			}
		}
		return true;
	}

	// ---- scanning ----

	private File chooseFolder() {
		if (NativeFileDialog.IS_MAC) {
			return NativeFileDialog.choose(this, "Choose a folder to add to the catalog", Setting.DEFAULT_IMAGE_DIR.getFile(), null, false, true, null);
		}
		var chooser = new JFileChooser(Setting.DEFAULT_IMAGE_DIR.getFile());
		chooser.setDialogTitle("Choose a folder to add to the catalog");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	private void chooseAndScan() {
		var dir = chooseFolder();
		if (dir != null) {
			var list = new ArrayList<String>();
			list.add(dir.getPath());
			startScan(list);
		}
	}

	private void setBusy(boolean busy) {
		scanning = busy;
		scanButton.setEnabled(!busy);
		rescanButton.setEnabled(!busy);
		removeButton.setEnabled(!busy);
		stopButton.setEnabled(busy);
	}

	private void startScan(List<String> folders) {
		if (scanning || folders.isEmpty()) {
			return;
		}
		cancel = false;
		setBusy(true);
		var t = new Thread(() -> {
			int found = 0;
			int added = 0;
			int removed = 0;
			int failed = 0;
			for (var folder : folders) {
				var r = catalog.scan(new File(folder), p -> SwingUtilities.invokeLater(() -> status.setText("Reading " + p)), () -> cancel);
				found += r.found;
				added += r.added;
				removed += r.removed;
				failed += r.failed;
			}
			String note = "Scan finished: " + found + " images found, " + added + " new or changed, " + removed + " removed"
					+ (failed > 0 ? ", " + failed + " could not be read" : "") + (cancel ? " (stopped)" : "") + ".";
			try {
				catalog.save();
			} catch (java.io.IOException ex) {
				mainPanel.appendConsole("Catalog: " + ex.getMessage());
			}
			SwingUtilities.invokeLater(() -> {
				setBusy(false);
				refresh();
				status.setText(status.getText() + "  " + note);
			});
		}, "catalog-scan");
		t.setDaemon(true);
		t.start();
	}

	private void removeFolder() {
		var roots = catalog.roots();
		if (roots.isEmpty()) {
			JOptionPane.showMessageDialog(this, "The catalog has no folders.", "Catalog", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		var sel = JOptionPane.showInputDialog(this, "Remove this folder and its images from the catalog:", "Catalog",
				JOptionPane.PLAIN_MESSAGE, null, roots.toArray(), roots.get(0));
		if (sel != null) {
			catalog.removeRoot(sel.toString());
			try {
				catalog.save();
			} catch (java.io.IOException ex) {
				mainPanel.appendConsole("Catalog: " + ex.getMessage());
			}
			refresh();
		}
	}

	// ---- open ----

	private void openSelected() {
		int r = table.getSelectedRow();
		if (r < 0) {
			return;
		}
		var row = rows.get(table.convertRowIndexToModel(r));
		var panel = mainPanel.getActiveDiskPanel();
		if (panel == null) {
			return;
		}
		var location = new File(row.entry.location);
		if (!location.exists()) {
			JOptionPane.showMessageDialog(this, "The file is not there any more:\n" + location + "\nRescan the folder to update the catalog.",
					"Catalog", JOptionPane.WARNING_MESSAGE);
			return;
		}
		panel.openCatalogItem(location, row.entry.inner, row.item != null ? row.item.name : null);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(600, 300);
	}
}
