package droid64.gui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.IntFunction;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.metal.MetalIconFactory;
import javax.swing.table.TableModel;

import droid64.DroiD64;
import droid64.d64.BasicParser;
import droid64.d64.CbmException;
import droid64.d64.CbmFile;
import droid64.d64.CpmFile;
import droid64.d64.DirEntry;
import droid64.d64.D64;
import droid64.d64.D67;
import droid64.d64.D71;
import droid64.d64.D80;
import droid64.d64.D81;
import droid64.d64.D82;
import droid64.d64.DiskImage;
import droid64.d64.DiskImageType;
import droid64.d64.FileType;
import droid64.d64.Utility;
import droid64.d64.ValidationError;
import droid64.db.DaoFactory;
import droid64.db.DatabaseException;

/**<pre style='font-family:sans-serif;'>
 * Created on 23.06.2004<br>
 *
 *   droiD64 - A graphical filemanager for D64 files<br>
 *   Copyright (C) 2004 Wolfram Heyer<br>
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *   eMail: wolfvoz@users.sourceforge.net <br>
 *   http://droid64.sourceforge.net
 *
 * @author wolf
 * @author henrik
 * </pre>
 */
public class DiskPanel extends JPanel implements TableModelListener {

	private static final long serialVersionUID = 1L;

	private static final String PARENT_DIR = "..";
	private static final String ZIP_EXT = ".zip";

	private DiskPanel otherDiskPanel;
	private DiskImage diskImage = null;
	private final EntryTableModel tableModel = new EntryTableModel();
	private final JTable table = new JTable(tableModel);
	private final MainPanel mainPanel;

	/** Disk label from loaded disk image */
	private final JLabel diskLabel = new JLabel(Utility.getMessage(Resources.DROID64_NODISK));
	/** Name (path) of current directory or loaded image */
	private final JComboBox<String> diskName = new JComboBox<>();
	private final List<String> diskNameHistory = new ArrayList<>();
	/** Path to currently used directory on file system */
	private File currentImagePath = null;

	private final JPanel diskLabelPane = new JPanel();

	/** True when a disk image is loaded */
	private boolean imageLoaded = false;

	/** True when a Zip file has been loaded */
	private boolean zipFileLoaded = false;
	/** Computer the loaded image seems to be made for, null if unknown */
	private C64.Machine diskMachine = null;
	private File directory = new File(".");
	private int rowHeight = 12;
	/** True when this is the active disk panel */
	private boolean active = false;
	/** This is the row of the last opened file in the file list */
	private Integer openedRow = null;
	private final transient ConsoleStream consoleStream;


	public DiskPanel(final MainPanel mainPanel, ConsoleStream consoleStream)  {
		this.mainPanel = mainPanel;
		this.consoleStream = consoleStream;

		tableModel.setMode(EntryTableModel.MODE_LOCAL);
		rowHeight = Setting.FONT_SIZE.getInteger() + 2;

		var dirPanel = drawDirPanel();
		setTableColors();
		setLayout(new BorderLayout());
		add(dirPanel, BorderLayout.CENTER);
		setBorder(BorderFactory.createRaisedBevelBorder());
		setupDragDrop(this);
	}

	public void setActive(boolean active) {
		if (active) {
			setBackground(Setting.BORDER_ACTIVE.getColor());
			this.active = true;
			this.table.grabFocus();
			this.table.requestFocus();
			if (otherDiskPanel != null) {
				otherDiskPanel.active = false;
				otherDiskPanel.setBackground(Setting.BORDER_INACTIVE.getColor());
			}
			mainPanel.setButtonState();
		} else {
			setBackground(Setting.BORDER_INACTIVE.getColor());
			this.active = false;
			if (otherDiskPanel != null) {
				otherDiskPanel.active = true;
				otherDiskPanel.setBackground(Setting.BORDER_ACTIVE.getColor());
				otherDiskPanel.table.grabFocus();
				otherDiskPanel.table.requestFocus();
			}
		}
		setTableColors();
		otherDiskPanel.setTableColors();
	}

	public boolean isActive() {
		return this.active;
	}

	private void setupDragDrop(final DiskPanel diskPanel) {
		var handler = new MyTransferHandler();
		setDropTarget(handler.getDropTarget(diskPanel));
		table.setTransferHandler(handler);
		table.setDragEnabled(true);
		table.setDropMode(javax.swing.DropMode.INSERT_ROWS);
	}

	private JPanel drawDirPanel() {
		final var columnModel = tableModel.getTableColumnModel();
		tableModel.addTableModelListener(this);

		table.setColumnModel(columnModel);
		table.setAutoCreateColumnsFromModel(false);	// keep our fixed column widths
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.setDefaultRenderer(Object.class, new ListTableCellRenderer(tableModel));
		table.setGridColor(Setting.DIR_BG.getColor());
		table.setRowHeight(rowHeight);
		table.setBackground(Setting.DIR_BG.getColor());
		table.setForeground(Setting.DIR_FG.getColor());
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent ev) { /* Not used */ }

			@Override
			public void keyPressed(KeyEvent ev) { /* Not used */ }

			@Override
			public void keyReleased(KeyEvent ev) {
				processFileTableKeyEvent(ev.getKeyCode());
			}
		});
		// hold the left button on a name to rename it in place, on the type to change it
		new LongPressEditor(table,
				row -> imageLoaded && !zipFileLoaded && tableModel.getMode() == EntryTableModel.MODE_CBM && diskImage != null
						&& row < diskImage.getCbmFileSize() && diskImage.getCbmFile(row).getFileType() != FileType.CBM,
				row -> diskImage.getCbmFile(row).getFileType().name(),
				row -> diskImage.getCbmFile(row).getName(),
				this::renameInPlace, (row, type) -> setTypeOfFiles(new int[] { row }, type));
		final boolean[] popupShown = { false };
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent me) {
				setActive(true);
				int row = table.rowAtPoint(me.getPoint());
				if (me.getClickCount() == 2 && row >= 0) {
					doubleClickedRow(row);
				}
				showPopup(me, row);
			}

			@Override
			public void mouseReleased(MouseEvent me) {
				showPopup(me, table.rowAtPoint(me.getPoint()));
			}

			private void showPopup(MouseEvent me, int row) {
				if (me.isPopupTrigger() && !popupShown[0]) {
					popupShown[0] = true;
					if (row >= 0 && !table.isRowSelected(row)) {
						table.setRowSelectionInterval(row, row);
					}
					SwingUtilities.invokeLater(() -> {
						filePopupMenu.show(table, me.getX(), me.getY());
						popupShown[0] = false;
					});
				}
			}
		});

		MouseAdapter mousePressedToActivate = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent me) {
				setActive(true);
			}
		};

		var popMenu = new JPopupMenu();
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_MD5, '5', event -> calcMd5Checksum());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_VIEWHEX, 'h', event -> hexViewFile());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_VIEWBASIC, 'b', event -> basicViewFile());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_VIEWIMAGE, 'i', event -> imageViewFile());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_VIEWTEXT, 't', event -> showFile());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_COPYFILE, 'c', event -> copyFile());
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_RENAMEFILE, 'r', event -> renameFile());
		// the function of the former "VICE prg" button: run the selected program in VICE
		var plugins = Setting.getExternalPrograms();
		if (plugins.size() > 5 && plugins.get(5) != null) {
			var viceProgram = plugins.get(5);
			GuiHelper.addMenuItem(popMenu, viceProgram.getLabel(), null, event -> doExternalProgram(viceProgram));
		}

		var pluginMenu = new JMenu("Plugins");
		pluginMenu.setMnemonic('p');
		for ( ExternalProgram ep : Setting.getExternalPrograms()) {
			if (ep != null) {
				GuiHelper.addMenuItem(pluginMenu, ep.getLabel(), null, event ->	doExternalProgram(ep));
			}
		}
		popMenu.add(pluginMenu);
		var mkdirItem = new javax.swing.JMenuItem("Make directory...");
		mkdirItem.addActionListener(event -> mkdir());
		popMenu.add(mkdirItem);
		popMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
				// only on the file system, not inside a disk image or zip file
				mkdirItem.setVisible(!imageLoaded && !zipFileLoaded);
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { /* not used */ }

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { /* not used */ }
		});
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_DISK_RELOAD, 'r', event -> reloadDiskImage(true));

		filePopupMenu = popMenu;

		diskLabel.setBackground(Setting.DIR_BG.getColor());
		diskLabel.setForeground(Setting.DIR_FG.getColor());
		diskLabel.addMouseListener(mousePressedToActivate);

		diskLabelPane.setBackground(Setting.DIR_BG.getColor());
		diskLabelPane.setForeground(Setting.DIR_FG.getColor());
		diskLabelPane.add(diskLabel);
		diskLabelPane.addMouseListener(mousePressedToActivate);

		diskNameHistory.forEach(diskName::addItem);

		diskName.setFont(Setting.SYS_FONT.getFont());
		diskName.setEditable(true);
		diskName.addMouseListener(mousePressedToActivate);
		diskName.addKeyListener(createDiskNameKeyListener());
		diskName.addActionListener(ev -> {
			File selected = new File((String) diskName.getSelectedItem());
			if (selected.isDirectory()) {
				setDiskName(selected.getName());
				loadLocalDirectory(selected);
			}
			if (!active) {
				setActive(true);
			}
		});
		diskName.addItemListener(ae -> {
			if (!active) {
				setActive(true);
			}
		});
		final var backButton = new JButton(MetalIconFactory.getFileChooserUpFolderIcon());
		backButton.addActionListener(ae -> {
			unloadDisk();
			setActive(true);
		});

		Insets bbMargin = backButton.getInsets();
		bbMargin.left = 1;
		bbMargin.right = 1;
		backButton.setMargin(bbMargin);

		var diskNamePanel = new JPanel(new BorderLayout());
		diskNamePanel.add(diskName, BorderLayout.CENTER);
		diskNamePanel.add(backButton, BorderLayout.EAST);

		var scrollPane = new JScrollPane(table);
		scrollPane.addMouseListener(mousePressedToActivate);
		scrollPane.setComponentPopupMenu(popMenu);

		var dirPanel = new JPanel(new BorderLayout());
		dirPanel.add(diskLabelPane, BorderLayout.NORTH);
		dirPanel.add(scrollPane, BorderLayout.CENTER);
		dirPanel.add(diskNamePanel, BorderLayout.SOUTH);
		dirPanel.setPreferredSize(new Dimension(50, 300));
		return dirPanel;
	}

	private void processFileTableKeyEvent(int keyCode) {
		int row = table.getSelectedRow();
		if (keyCode == KeyEvent.VK_TAB) {
			setActive(!isActive());
		} else if (keyCode == KeyEvent.VK_LEFT) {
			if (imageLoaded) {
				unloadDisk();
			} else if (PARENT_DIR.equals(table.getValueAt(0, 1))) {
				doubleClickedLocalfile(currentImagePath.getParentFile(), 0);
			}
		} else if (keyCode == KeyEvent.VK_HOME) {
			table.getSelectionModel().setSelectionInterval(0, 0);
			table.scrollRectToVisible(table.getCellRect(0, 0, true));
		} else if (keyCode == KeyEvent.VK_END) {
			int lastRow = table.getRowCount() - 1;
			table.getSelectionModel().setSelectionInterval(lastRow, lastRow);
			table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
		} else if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
			table.clearSelection();
			table.invalidate();
			table.repaint();
		} else if (row >= 0) {
			if (keyCode == KeyEvent.VK_ENTER) {
				doubleClickedRow(row - 1);
			} else if (keyCode == KeyEvent.VK_RIGHT && (row > 0 || !PARENT_DIR.equals(table.getValueAt(0, 1)))) {
				doubleClickedRow(row);
			}
		}
	}

	private KeyListener createDiskNameKeyListener() {
		return new KeyListener() {
			@Override
			public void keyTyped(KeyEvent ev) { /* Not used */ }
			@Override
			public void keyPressed(KeyEvent ev) {
				if (!active) {
					setActive(true);
				}
			}
			@Override
			public void keyReleased(KeyEvent ev) {
				if (ev.getKeyCode() == KeyEvent.VK_ENTER) {
					String selected = (String) diskName.getSelectedItem();
					var dir = new File(selected);
					if (dir.isDirectory()) {
						setDiskName(selected);
						loadLocalDirectory(dir);
					}
				}
			}
		};
	}

	protected String getCurrentPath() {
		return (String) diskName.getSelectedItem();
	}

	private void setDiskName(String name) {
		if (name == null) {
			return;
		}
		ActionListener[] listeners = diskName.getActionListeners();
		for (final ActionListener listener : listeners) {
			diskName.removeActionListener(listener);
		}

		if (!new File(name).isDirectory()) {
			if (diskName.getItemCount() > 0) {
				diskName.setSelectedIndex(0);
				diskName.setSelectedItem(name);
			}
			for (final ActionListener listener : listeners) {
				diskName.addActionListener(listener);
			}
			return;
		}
		if (!diskNameHistory.contains(name)) {
			diskNameHistory.add(name);
			diskName.addItem(name);
		}
		for (int i=0; i<diskName.getItemCount(); i++) {
			if (name.equals(diskName.getItemAt(i))) {
				diskName.setSelectedIndex(i);
				break;
			}
		}
		for (final ActionListener listener : listeners) {
			diskName.addActionListener(listener);
		}
	}

	private String getLocalFilename(int row) {
		return this.currentImagePath + File.separator + table.getValueAt(row, 1);
	}

	protected void doubleClickedRow(int row) {
		if (row < 0) {
			return;
		}
		try {
			openedRow = null;
			if (imageLoaded) {
				doubleClickedImageFileEntry(diskImage.getCbmFile(row), row);
			} else if (zipFileLoaded) {
				String entryName = (String) table.getValueAt(row, 1);
				doubleClickedZipFile(entryName, row);
			} else {
				if (PARENT_DIR.equals(table.getValueAt(row, 1))) {
					doubleClickedLocalfile(currentImagePath.getParentFile(), row);
				} else {
					doubleClickedLocalfile(new File(getLocalFilename(row)), row);
				}
			}
		} catch (Exception e) {	//NOSONAR
			mainPanel.appendConsole("DiskPanel: " + e.getClass().getName() + ": " + e.getMessage());
		}
		mainPanel.setButtonState();
	}

	private void doubleClickedImageFileEntry(CbmFile file, int row) throws CbmException {
		if (file != null && file.getFileType() == FileType.CBM) {
			// Open partition
			clearDirTable();
			diskImage.readPartition(file.getTrack(), file.getSector(), file.getSizeInBlocks());
			showDirectory();

			setDiskName(diskImage.getFile() + File.separator + file.getName());
		} else if (file != null){
			hexViewFile(row);
		}
	}

	protected void doubleClickedZipFile(String zipName, int row) throws CbmException {
		mainPanel.appendConsole("doubleClickedZipFile: "+zipName);

		if (Setting.isImageFileName(new File(zipName))) {
			mainPanel.appendConsole("doubleClickedZipFile: is disk image");
			diskImage = DiskImage.getDiskImage(new File(zipName), getFileData(row), consoleStream);
			diskImage.setFile(new File(zipName));
			setDiskName(currentImagePath + File.separator + zipName);
			clearDirTable();
			imageLoaded = true;
			zipFileLoaded = true;
			updateImageFile();
			showDirectory();
		} else if (PARENT_DIR.equals(zipName)) {
			File file = currentImagePath.getParentFile();
			if (file.exists() && file.isDirectory()) {
				loadLocalDirectory(file);
			}
		} else {
			hexViewFile(row);
		}
		mainPanel.appendConsole("doubleClickedZipFile: done");
	}

	protected void doubleClickedLocalfile(File file, int row) {
		if (file.exists()) {
			if (Setting.isImageFileName(file)) {
				openDiskImage(file, true);
				openedRow = Integer.valueOf(row);
			} else if (file.isDirectory()) {
				loadLocalDirectory(file);
			} else if (file.isFile()) {
				if (file.getName().toLowerCase().endsWith(ZIP_EXT)) {
					loadZipFile(file);
				} else if (row >= 0) {
					hexViewFile(row);
				} else {
					addFile(file);
				}
			} else {
				mainPanel.appendConsole("Can't open "+file);
			}
		} else {
			mainPanel.appendConsole("Can't open "+file);
		}
	}

	private void showNoDiskLoadedMessage() {
		mainPanel.appendConsole("\nNo disk image file selected. Aborting.");
		JOptionPane.showMessageDialog(mainPanel.getParent(),
				Utility.getMessage(Resources.DROID64_INFO_NOIMAGELOADED),
				DroiD64.PROGNAME + " - No disk",
				JOptionPane.ERROR_MESSAGE);
	}

	@Override
	public void tableChanged(TableModelEvent e) {
		TableModel model = (TableModel) e.getSource();
		table.setModel(model);
		table.revalidate();
	}

	/**
	 * Execute external program (plugin) and write its stdout and stderr to the console.
	 * @param prg external program to be executed.
	 */
	public void doExternalProgram(ExternalProgram prg) {
		var selectedFiles = getSelectedFiles();
		File imgFile = null;
		if (imageLoaded) {
			if (zipFileLoaded) {
				try {
					var tmpfile = File.createTempFile("droid64_", ".img");
					tmpfile.deleteOnExit();
					imgFile = tmpfile;
					diskImage.saveAs(tmpfile);
					mainPanel.appendConsole("Write temporary image to "+imgFile);
				} catch (IOException e) {	//NOSONAR
					GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to create image tempfile from zip file.");
					return;
				}
			} else {
				imgFile = diskImage.getFile();
			}
		} else {
			imgFile = currentImagePath;
		}
		var otherPath = otherDiskPanel.imageLoaded ? otherDiskPanel.diskImage.getFile(): otherDiskPanel.currentImagePath;
		var imageType = imageLoaded ? diskImage.getImageFormat() : DiskImageType.UNDEFINED;
		if (!imageLoaded && selectedFiles.size() == 1 && new File(selectedFiles.get(0)).isFile()) {
			// a disk image selected in the file system list: start the program with that image
			var type = Setting.getDiskImageType(new File(selectedFiles.get(0)));
			if (!DiskImageType.UNDEFINED.equals(type)) {
				imageType = type;
				imgFile = new File(selectedFiles.get(0));
				selectedFiles = new ArrayList<>();
			}
		}
		if (imageLoaded && !zipFileLoaded && prg.getArguments() != null && prg.getArguments().contains("{ImageFiles}")) {
			// only program files can be started from inside the image, other types would make VICE fail
			var onlyPrg = new ArrayList<String>();
			for (var row : table.getSelectedRows()) {
				var f = diskImage.getCbmFile(row);
				if (f != null && f.getFileType() == FileType.PRG) {
					onlyPrg.add(viceName(f));
				}
			}
			selectedFiles = onlyPrg;
		}
		var execArgs = prg.getExecute(imgFile, selectedFiles, otherPath, directory, imageType);
		File imgParentFile;
		if (imgFile != null) {
			imgParentFile = imgFile.getParentFile();
		} else if (currentImagePath != null) {
			imgParentFile = currentImagePath;
		} else {
			imgParentFile = null;
		}
		prg.runProgram(imgParentFile, execArgs, mainPanel);
	}

	/** Name of a file as VICE expects it after "image:" - VICE maps ASCII to PETSCII (a-z to $41-$5A, A-Z to $C1-$DA). */
	static String viceName(droid64.d64.CbmFile f) {
		var raw = f.getNameAsBytes();
		if (raw == null) {
			return f.getName().toLowerCase();
		}
		int len = raw.length;
		while (len > 0 && (raw[len - 1] & 0xff) == 0xa0) {
			len--;
		}
		var sb = new StringBuilder();
		for (int i = 0; i < len; i++) {
			int b = raw[i] & 0xff;
			if (b >= 0x41 && b <= 0x5a) {
				sb.append((char) (b + 0x20));
			} else if (b >= 0xc1 && b <= 0xda) {
				sb.append((char) (b - 0x80));
			} else if (b >= 0x20 && b <= 0x3f) {
				sb.append((char) b);
			} else {
				sb.append('?');
			}
		}
		return sb.toString();
	}

	protected List<String> getSelectedFiles() {
		var selectedFiles = new ArrayList<String>();
		for (var row : table.getSelectedRows()) {
			final String selectedFile;
			if (imageLoaded) {
				selectedFile = viceName(diskImage.getCbmFile(row));
			} else {
				selectedFile = currentImagePath + File.separator + ((String) tableModel.getValueAt(row, 1));
			}
			selectedFiles.add(selectedFile);
		}
		return selectedFiles;
	}

	public Stream<DirEntry> getDirEntries() {
		return tableModel.getEntries();
	}

	protected int getSelectedRow() {
		return table.getSelectedRow();
	}

	protected void setSelectedRow(int row) {
		if (row > -1 && row < table.getRowCount()) {
			table.setRowSelectionInterval(row, row);
		}
	}

	private void showDirectory() {
		if (imageLoaded) {
			diskMachine = diskImage.isCpmImage() ? null : MachineDetector.detect(diskImage);
			mainPanel.appendConsole("Detected computer: " + (diskMachine != null ? diskMachine : "unknown"));
			mainPanel.appendConsole("Found " + diskImage.getFilesUsedCount() + " files in this "+DiskImage.getImageTypeName(diskImage.getImageFormat()) + " image file.");
			for (var fileNum = 0; fileNum <= diskImage.getFilesUsedCount() - 1;	fileNum++) {
				if (diskImage.getCbmFile(fileNum) != null) {
					tableModel.updateDirEntry(new DirEntry(diskImage.getCbmFile(fileNum), fileNum + 1));
				} else {
					tableModel.updateDirEntry(null);
				}
			}
			diskLabel.setText(getDiskLabel());
			tableModel.setMode(diskImage.isCpmImage() ? EntryTableModel.MODE_CPM : EntryTableModel.MODE_CBM);
			var tcm = table.getTableHeader().getColumnModel();
			for (var i=0; i< tcm.getColumnCount(); i++) {
				tcm.getColumn(i).setHeaderValue(tableModel.getColumnName(i));
			}
			setTableColors();
			table.setColumnModel(tableModel.getTableColumnModel());
			table.revalidate();
			repaint();
		} else {
			mainPanel.appendConsole("no image loaded");
		}
	}

	private String getDiskLabel() {
		if (imageLoaded) {
			return String.format("%s \"%s,%s\" %d BLOCKS FREE [%d]",
				diskImage.getBam().getDiskDosType(), diskImage.getBam().getDiskName(), diskImage.getBam().getDiskId(), diskImage.getBlocksFree(), diskImage.getFilesUsedCount());
		} else {
			return Utility.EMPTY;
		}
	}

	/**
	 * Load contents from a directory on local file system.
	 * @param path to a directory to open
	 */
	public void loadLocalDirectory(File path) {
		var dir = path != null ? path : Setting.DEFAULT_IMAGE_DIR.getFile();
		if (dir.isDirectory()) {
			mainPanel.appendConsole("loadLocalDirectory: "+dir.getAbsolutePath());
			clearDirTable();
			int fileNum = 0;
			var parentFile = dir.getParentFile();
			if (parentFile != null) {
				DirEntry parent = new DirEntry(parentFile, ++fileNum);
				parent.setName(PARENT_DIR);
				tableModel.updateDirEntry(parent);
			}
			this.currentImagePath = dir;
			var files = dir.listFiles();
			if (files != null) {
				for (var file : Utility.sortFiles(files)) {
					if (!file.getName().startsWith(".")) {
						tableModel.updateDirEntry(new DirEntry(file, ++fileNum));
					}
				}
				setDiskName(dir.getPath());
				diskLabel.setText("FILE SYSTEM");
			} else {
				diskLabel.setText("ERROR");
			}
		} else if (dir.getName().toLowerCase().endsWith(ZIP_EXT)) {
			loadZipFile(dir);
			return;
		} else {
			clearDirTable();
			diskLabel.setText("ERROR");
		}
		tableModel.setMode(EntryTableModel.MODE_LOCAL);
		var tcm = table.getTableHeader().getColumnModel();
		for (int i=0; i< tcm.getColumnCount(); i++) {
			tcm.getColumn(i).setHeaderValue(tableModel.getColumnName(i));
		}
		setTableColors();
		table.setColumnModel(tableModel.getTableColumnModel());
		table.revalidate();
		repaint();
	}

	/**
	 * Load a zip file
	 * @param file the zip file to load
	 */
	private void loadZipFile(File file) {
		mainPanel.appendConsole("loadZipFile: "+file.getName());
		try {
			var list = Utility.getZipFileEntries(file, 2);
			clearDirTable();
			var parent = new DirEntry(file.getParentFile(), 1);
			parent.setName(PARENT_DIR);
			tableModel.updateDirEntry(parent);
			list.forEach(tableModel::updateDirEntry);
			tableModel.setMode(EntryTableModel.MODE_LOCAL);
			var tcm = table.getTableHeader().getColumnModel();
			for (int i=0; i< tcm.getColumnCount(); i++) {
				tcm.getColumn(i).setHeaderValue(tableModel.getColumnName(i));
			}
			setDiskName(file.getAbsolutePath());
			diskLabel.setText("ZIP FILE");
			zipFileLoaded = true;
			currentImagePath = file;
			setTableColors();
			table.setColumnModel(tableModel.getTableColumnModel());
			table.revalidate();
			repaint();
		} catch (CbmException e) {	//NOSONAR
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to open zip file.");
		}
	}

	public void showBAM() {
		if (!imageLoaded){
			showNoDiskLoadedMessage();
			return;
		}
		var window = javax.swing.SwingUtilities.getWindowAncestor(this);
		if (window instanceof java.awt.Frame) {
			GraphicBamDialog.show((java.awt.Frame) window, this, diskImage, new java.io.File(String.valueOf(getCurrentPath())).getName());
			return;
		}
		var bamTab = diskImage.getBamTable();
		if (bamTab != null && bamTab.length > 0) {
			var name = String.format("%s \"%s,%s\"", diskImage.getBam().getDiskDosType(), diskImage.getBam().getDiskName(), diskImage.getBam().getDiskId());
			new BAMPanel(mainPanel).show(name, bamTab, diskImage, isWritableImageLoaded());
			diskImage.readBAM();
		} else {
			mainPanel.appendConsole("No BAM available.");
		}
	}

	private void updateImageFile() {
		if (imageLoaded) {
			diskImage.readBAM();
			diskImage.readDirectory();
			if (Boolean.TRUE.equals(Setting.USE_DB.getBoolean())) {
				var disk = diskImage.getDisk();
				var f = diskImage.getFile();
				var excludePattern = Utility.isEmpty(Setting.EXCLUDED_IMAGE_FILES.getString()) ? null : Pattern.compile(Setting.EXCLUDED_IMAGE_FILES.getString());
				if (excludePattern == null || !excludePattern.matcher(f.getAbsolutePath()).matches()) {
					var p = f.getAbsoluteFile().getParentFile();
					var d = p != null ? p.getAbsolutePath() : null;
					disk.setFilePath(d);
					disk.setFileName(f.getName());
					disk.setImageType(diskImage.getImageFormat());
					disk.setHostName(Utility.getHostName());
					try {
						DaoFactory.getDaoFactory().getDiskDao().save(disk);
					} catch (DatabaseException e) {	//NOSONAR
						GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to update database.");
					}
				}
			}
		} else {
			mainPanel.appendConsole("updateImageFile: no image loaded");
		}
	}

	private void logFileSaveFailedAbort(String filename) {
		mainPanel.appendConsole("Failed to save copy of "+filename+".\nAborting copy.");
	}

	private JPopupMenu filePopupMenu;

	public void copyFile() {
		copyFiles();
	}

	/** Copy the selected files to the other panel, then delete them from this one. */
	public void moveFile() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: moving from Zip files not supported.");
			return;
		}
		var names = new java.util.ArrayList<String>();
		for (var row : table.getSelectedRows()) {
			names.add(String.valueOf(tableModel.getValueAt(row, imageLoaded ? 2 : 1)));
		}
		if (names.isEmpty()) {
			return;
		}
		if (copyFiles()) {
			mainPanel.appendConsole("Move: deleting the original " + names.size() + " file(s)");
			deleteFile();
		} else {
			mainPanel.appendConsole("Move aborted: copying failed, nothing was deleted.");
		}
	}

	/** @return true if at least one file was selected and all selected files were copied */
	private boolean copyFiles() {
		if (otherDiskPanel.zipFileLoaded) {
			mainPanel.appendConsole("Error: copying to Zip files not supported.");
			return false;
		}
		boolean failed = false;
		boolean filesCopied = false;
		try {
			boolean success = false;
			if (imageLoaded) {
				for (var row : table.getSelectedRows()) {
					filesCopied = true;
					mainPanel.appendConsole("Disk copy [" + row + "] " + tableModel.getValueAt(row, 2));
					var saveData = diskImage.getFileData(row);
					var source = diskImage.getCbmFile(row);
					CbmFile copy;
					if (source instanceof CpmFile) {
						copy = new CpmFile((CpmFile) source);
					} else {
						copy = new CbmFile(source);
					}
					if (otherDiskPanel.imageLoaded) {
						// Copy file from image to image
						success = otherDiskPanel.diskImage.saveFile(copy, true, saveData);
					} else {
						// Copy file from image to local file system
						var outName = otherDiskPanel.currentImagePath + File.separator + Utility.pcFilename(copy);
						mainPanel.appendConsole("DiskPanel.copyFile: "+outName+" class="+copy.getClass().getName());
						var targetFile = new File(outName);
						success = Utility.writeFileSafe(targetFile, saveData);
					}
					if (!success) {
						logFileSaveFailedAbort(copy.getName());
						failed = true;
						break;
					}
				}
				if (success && otherDiskPanel.imageLoaded) {
					otherDiskPanel.diskImage.save();
				}
			} else if (zipFileLoaded) {
				for (var row = 0; row < table.getRowCount(); row++) {
					if (table.isRowSelected(row)) {
						var filename = (String) tableModel.getValueAt(row, 1);
						var data = getFileData(row);
						filesCopied = true;
						mainPanel.appendConsole("Zip copy [" + row + "] " + filename);
						if (otherDiskPanel.imageLoaded) {
							// Copy file from local file system to disk image
							var cbmFile = new CbmFile();
							cbmFile.setName(Utility.cbmFileName(filename, DiskImage.DISK_NAME_LENGTH));
							cbmFile.setNameAsBytes(cbmFile.getName().getBytes());
							cbmFile.setFileType(CbmFile.getFileTypeFromFileExtension(filename));
							success = otherDiskPanel.diskImage.saveFile(cbmFile, false, data);
							if (!success) {
								logFileSaveFailedAbort(filename);
								failed = true;
								break;
							}
						} else if (otherDiskPanel.zipFileLoaded) {
							mainPanel.appendConsole("Target is a zip file");
						} else {
							// Copy file from file system to file system (no images involved)
							var targetFile = new File(otherDiskPanel.currentImagePath + File.separator + filename);
							success = Utility.writeFileSafe(targetFile, data);
						}
					}
				}
				if (success && otherDiskPanel.imageLoaded) {
					otherDiskPanel.diskImage.save();
				}
			} else {
				// local file system
				for (var row = 0; row < table.getRowCount(); row++) {
					if (table.isRowSelected(row)) {
						var filename = (String) tableModel.getValueAt(row, 1);
						var sourceFile = new File(currentImagePath + File.separator + filename);
						if (sourceFile.isFile()) {
							filesCopied = true;
							mainPanel.appendConsole("Local copy [" + row + "] " + filename);
							if (otherDiskPanel.imageLoaded) {
								// Copy file from local file system to disk image
								var data = Utility.readFile(sourceFile);
								var cbmFile = new CbmFile();
								cbmFile.setName(Utility.cbmFileName(filename, DiskImage.DISK_NAME_LENGTH));
								cbmFile.setNameAsBytes(cbmFile.getName().getBytes());
								cbmFile.setFileType(CbmFile.getFileTypeFromFileExtension(filename));
								cbmFile.setFsFile(sourceFile);
								success = otherDiskPanel.diskImage.saveFile(cbmFile, false, data);
								if (!success) {
									logFileSaveFailedAbort(filename);
									failed = true;
									break;
								}
							} else {
								// Copy file from file system to file system (no images involved)
								var targetFile = new File(otherDiskPanel.currentImagePath + File.separator + filename);
								Utility.writeFileSafe(sourceFile, targetFile);
							}
						} else {
							mainPanel.appendConsole("Error: Is not a plain file (" + filename+")");
						}
					}
				}
				if (success && otherDiskPanel.imageLoaded) {
					otherDiskPanel.diskImage.save();
				}
			}
		} catch (Exception|OutOfMemoryError e) {	//NOSONAR
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to copy file.");
			failed = true;
		}
		if (filesCopied) {
			otherDiskPanel.reloadDiskImage(true);
		}
		return filesCopied && !failed;
	}

	private void addFile(File file) {
		if (imageLoaded) {
			try {
				var data = Utility.readFile(file);
				int needed = (data.length + 253) / 254;
				if (needed > diskImage.getBlocksFree()) {
					GuiHelper.showErrorMessage(mainPanel.getParent(), "Not enough space",
							"Not enough free space on the disk image for %s (needs %d blocks, %d free).",
							file.getName(), needed, diskImage.getBlocksFree());
					return;
				}
				var cbmFile = new CbmFile();
				var cbmName = file.getName().replaceFirst("(?i)\\.(prg|seq|usr|rel|del|p00|s00|u00)$", "").replace('_', ' ');
				cbmFile.setName(Utility.cbmFileName(cbmName, DiskImage.DISK_NAME_LENGTH));
				cbmFile.setNameAsBytes(cbmFile.getName().getBytes());
				cbmFile.setFileType(CbmFile.getFileTypeFromFileExtension(file.getName()));
				boolean success = diskImage.saveFile(cbmFile, false, data);
				if (success) {
					diskImage.save();
				} else {
					GuiHelper.showErrorMessage(mainPanel.getParent(), "Add file failed",
							"Failed to add %s to the disk image (disk full or directory full).", file.getName());
				}
				reloadDiskImage(true);
			} catch (Exception e) { //NOSONAR
				GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to add file.");
			}
		} else if (!zipFileLoaded) {
			var targetFile = new File(currentImagePath + File.separator + file.getName());
			mainPanel.appendConsole("Add file on local filsystem.\n"+targetFile.getAbsolutePath());
			Utility.writeFileSafe(file, targetFile);
			loadLocalDirectory(currentImagePath);
		}
	}

	/** Get data from file.
	 * @param file number in listing
	 * @return data
	 */
	private byte[] getFileData(int fileNum) {
		return getFileData(fileNum, false);
	}

	/**
	 * @param fileNum file number in listing
	 * @param quiet if true a file that cannot be read gives null and no error message (used by the picture viewer
	 *              that steps through all entries, e.g. DEL files without data)
	 */
	private byte[] getFileData(int fileNum, boolean quiet) {
		try {
			if (imageLoaded) {
				return diskImage.getFileData(fileNum);
			} else if (zipFileLoaded) {
				return Utility.getDataFromZipFileEntry(currentImagePath, (String) table.getValueAt(fileNum, 1));
			} else {
				return Files.readAllBytes(new File(getLocalFilename(fileNum)).toPath());
			}
		} catch (Exception e) {	//NOSONAR
			if (quiet) {
				return null;
			}
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to get file data.");
		}
		return new byte[0];
	}

	public void hexViewFile() {
		for (var i : table.getSelectedRows()) {
			mainPanel.appendConsole("Hex view '" + getName(i) + "'");
			hexViewFile(i);
		}
	}

	/** Edit the selected file in the hex editor. Changes are written back into the image or the local file. */
	public void hexEditFile() {
		var rows = table.getSelectedRows();
		if (rows.length == 0) {
			mainPanel.appendConsole("Hex edit: select a file first.");
			return;
		}
		int row = rows[0];
		var data = getFileData(row);
		if (data == null) {
			return;
		}
		var name = getName(row);
		boolean prg = false;
		HexEditorDialog.Saver saver = null;
		if (imageLoaded) {
			var cbm = diskImage.getCbmFile(row);
			prg = cbm.getFileType() == FileType.PRG;
			final var image = diskImage;
			final int length = data.length;
			saver = newData -> {
				var err = saveEditedImageFile(image, row, length, newData);
				if (err == null) {
					SwingUtilities.invokeLater(() -> reloadDiskImage(true));
				}
				return err;
			};
		} else if (!zipFileLoaded) {
			var path = getLocalFilename(row);
			prg = name.toLowerCase().endsWith(".prg");
			saver = newData -> {
				if (!Utility.writeFileSafe(new File(path), newData)) {
					return "Failed to write " + path;
				}
				SwingUtilities.invokeLater(() -> loadLocalDirectory(currentImagePath));
				return null;
			};
		}
		mainPanel.appendConsole("Hex edit '" + name + "'" + (saver == null ? " (read only)" : ""));
		HexEditorDialog.show(mainPanel, name, data, prg, machineOfFile(data), saver);
	}

	/** The computer a file is for: from its load address, else the one of the disk image, else the C64. */
	private C64.Machine machineOfFile(byte[] data) {
		if (data != null && data.length >= 4) {
			int load = (data[0] & 0xff) | (data[1] & 0xff) << 8;
			if (load == 0x0801 || load == 0x1001 || load == 0x1201 || load == 0x1C01 || load == 0x0401) {
				return MachineDetector.detect(data, load);
			}
		}
		return imageLoaded && diskMachine != null ? diskMachine : C64.Machine.C64;
	}

	/** @return null when saved, otherwise the error message */
	static String saveEditedImageFile(DiskImage image, int row, int oldLength, byte[] newData) {
		try {
			var cbm = image.getCbmFile(row);
			boolean blockImage = image instanceof D64 || image instanceof D71 || image instanceof D81
					|| image instanceof D80 || image instanceof D82 || image instanceof D67;
			if (blockImage && newData.length == oldLength && !image.isCpmImage()) {
				// same length: patch the data bytes in the file's own sectors, nothing else on the disk changes
				int track = cbm.getTrack();
				int sector = cbm.getSector();
				int pos = 0;
				for (int guard = 0; track != 0 && guard < 4000; guard++) {
					var block = image.getBlock(track, sector);
					int nextTrack = block[0] & 0xff;
					int nextSector = block[1] & 0xff;
					int len = nextTrack > 0 ? 254 : nextSector - 1;
					if (len < 0 || pos + len > newData.length) {
						return "The file chain does not match the file size.";
					}
					System.arraycopy(newData, pos, block, 2, len);
					image.writeBlock(track, sector, block);
					pos += len;
					track = nextTrack;
					sector = nextSector;
				}
				if (pos != newData.length) {
					return "The file chain does not match the file size.";
				}
			} else {
				// another size: the file is written again with the same name and type
				int needed = (newData.length + 253) / 254;
				int old = (oldLength + 253) / 254;
				if (needed > image.getBlocksFree() + old) {
					return "Not enough free space on the disk image (needs " + needed + " blocks).";
				}
				var copy = new CbmFile(cbm);
				image.deleteFile(cbm);
				if (!image.saveFile(copy, true, newData)) {
					return "Failed to write the file back to the disk image.";
				}
			}
			if (!image.save()) {
				return "Failed to save the disk image.";
			}
			return null;
		} catch (Exception e) {	// NOSONAR
			return "Error: " + e.getMessage();
		}
	}

	public void calcMd5Checksum() {
		try {
			for (var i : table.getSelectedRows()) {
				String sum = Utility.calcMd5Checksum(getFileData(i));
				mainPanel.appendConsole(sum + "  " + getName(i));
			}
		} catch (CbmException e) {
			mainPanel.appendConsole("MD5 failed " + e);
		}
	}

	private void hexViewFile(int fileNum) {
		var data = getFileData(fileNum);
		if (data == null || data.length == 0) {
			mainPanel.appendConsole("No data");
			return;
		}
		String name = getName(fileNum);
		new HexViewPanel(DroiD64.PROGNAME+" - Hex view", mainPanel, name, data, data.length, true).showDialog();
	}

	public void basicViewFile() {
		for (var i : table.getSelectedRows()) {
			var name = getName(i);
			mainPanel.appendConsole("BASIC view '" + name + "'");
			var data = getFileData(i);
			var basic = BasicParser.parseCbmBasicPrg(data);
			if (basic != null && !basic.isEmpty()) {
				new TextViewPanel(mainPanel).show(basic, DroiD64.PROGNAME+" - BASIC view", name, Utility.MIMETYPE_TEXT);
			} else {
				mainPanel.appendConsole("Failed to parse BASIC.");
			}
		}
	}

	/**
	 * Write the directory of disk images to a text file: the loaded image, or the images selected in the file system list
	 * (files and folders), or all images of the current folder when nothing is selected.
	 */
	public void dirText() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Dir Txt: not supported inside a zip file.");
			return;
		}
		var files = new ArrayList<File>();
		File folder;
		if (imageLoaded) {
			if (diskImage == null || diskImage.getFile() == null) {
				return;
			}
			files.add(diskImage.getFile());
			folder = diskImage.getFile().getAbsoluteFile().getParentFile();
		} else {
			folder = currentImagePath;
			var rows = table.getSelectedRows();
			for (var row : rows) {
				if (!PARENT_DIR.equals(table.getValueAt(row, 1))) {
					files.add(new File(getLocalFilename(row)));
				}
			}
			if (files.isEmpty() && currentImagePath != null) {
				files.add(currentImagePath);
			}
		}
		var sources = DirText.collect(files);
		if (sources.images.isEmpty()) {
			JOptionPane.showMessageDialog(mainPanel.getParent(), "No Commodore disk or tape image found in the selection.", "Dir Txt",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String defaultName;
		if (sources.images.size() == 1) {
			var n = sources.images.get(0).getName();
			int dot = n.lastIndexOf('.');
			defaultName = (dot > 0 ? n.substring(0, dot) : n) + ".txt";
		} else {
			defaultName = (folder != null && folder.getName().length() > 0 ? folder.getName() : "Disks") + "-dir.txt";
		}
		var chosen = FileDialogHelper.openTextFileDialog("Save directory listing", folder, defaultName, true, new String[] { ".txt" });
		if (chosen == null) {
			return;
		}
		try {
			mainPanel.getParent().setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
			var text = DirText.build(sources.images, sources.skipped);
			Files.write(new File(chosen).toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			mainPanel.appendConsole("Directory of " + sources.images.size() + " image(s) written to " + chosen);
		} catch (Exception e) {	//NOSONAR
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to write the directory listing.");
		} finally {
			mainPanel.getParent().setCursor(java.awt.Cursor.getDefaultCursor());
		}
	}

	/** Open the SID music player for the files of this disk. */
	public void playMusic() {
		var open = MusicPlayerDialog.getOpenInstance();
		if (open != null) {
			open.bringToFront();
			return;
		}
		int rows = table.getRowCount();
		var names = new ArrayList<String>();
		for (var i = 0; i < rows; i++) {
			names.add(getName(i));
		}
		int start = Math.max(0, table.getSelectedRow());
		mainPanel.appendConsole("Music player");
		// quiet loader: folders, huge or unreadable files are simply not music
		IntFunction<byte[]> quiet = i -> {
			try {
				if (imageLoaded) {
					return diskImage.getFileData(i);
				} else if (zipFileLoaded) {
					return Utility.getDataFromZipFileEntry(currentImagePath, (String) table.getValueAt(i, 1));
				}
				var f = new File(getLocalFilename(i));
				return f.isFile() && f.length() <= 2_000_000 ? Files.readAllBytes(f.toPath()) : null;
			} catch (Exception e) {	//NOSONAR
				return null;
			}
		};
		var dlg = new MusicPlayerDialog(mainPanel.getParent(), DroiD64.PROGNAME + " - Music player", names, quiet, start);
		dlg.setVisible(true);
		if (rows == 0) {
			dlg.startInHvsc();
		}
	}

	public void imageViewFile() {
		try {
			int rows = table.getRowCount();
			if (rows == 0) {
				return;
			}
			var names = new ArrayList<String>();
			for (var i = 0; i < rows; i++) {
				names.add(getName(i));
			}
			int start = Math.max(0, table.getSelectedRow());
			mainPanel.appendConsole("Image view '" + names.get(start) + "'");
			new ViewImagePanel(DroiD64.PROGNAME+" - Image view", mainPanel).show(names, i -> getFileData(i, true), start);
		} catch (Exception e) {	//NOSONAR
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to view image.");
		}
	}

	/**
	 * View the selected files as they look on a C64 screen (SEQ files, PRG text).
	 * Plain PC text files (txt, asc, nfo...) are shown as text instead.
	 */
	public void showFile() {
		for (var i : table.getSelectedRows()) {
			var name = getName(i);
			mainPanel.appendConsole("View '" + name + "'");
			var data = getFileData(i);
			if (data == null || data.length == 0) {
				continue;
			}
			var lower = name.toLowerCase();
			boolean pcText = !imageLoaded && !zipFileLoaded && (lower.endsWith(".txt") || lower.endsWith(".asc")
					|| lower.endsWith(".nfo") || lower.endsWith(".md") || lower.endsWith(".log") || lower.endsWith(".diz"));
			if (pcText) {
				new TextViewPanel(mainPanel).show(data, DroiD64.PROGNAME, name);
				continue;
			}
			boolean prg = false;
			if (imageLoaded && !zipFileLoaded) {
				try {
					prg = diskImage.getCbmFile(i).getFileType() == FileType.PRG;
				} catch (Exception e) {
					prg = false;
				}
			} else {
				prg = lower.endsWith(".prg");
			}
			PetsciiScreenDialog.show(mainPanel.getParent(), name, data, prg);
		}
	}

	/** Show the selected files as plain text (the old View Text). */
	public void showPlainTextFile() {
		for (var i : table.getSelectedRows()) {
			var name = getName(i);
			mainPanel.appendConsole("View '" + name + "'");
			var data = getFileData(i);
			if (data.length > 0) {
				new TextViewPanel(mainPanel).show(data, DroiD64.PROGNAME, name);
			}
		}
	}

	/** "my GAME disk" becomes "MyGameDisk": every word starts with a capital letter, the rest is lower case, no spaces. */
	static String fileNameFromDiskName(String diskName) {
		if (diskName == null) {
			return "";
		}
		var sb = new StringBuilder();
		for (var word : diskName.trim().split("\\s+")) {
			if (!word.isEmpty()) {
				sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
			}
		}
		return sb.toString();
	}

	protected void newDiskImage() {
		mainPanel.appendConsole("New disk image.");
		var result = new RenameDiskImagePanel(mainPanel).showDialog(DroiD64.PROGNAME+" - New disk");
		if (result != null) {
			mainPanel.formatConsole("New Diskname is: \"%s, %s\".", result.getDiskName(), result.getDiskID());
			var defaultName = Setting.checkFileNameExtension(result.getDiskType(), result.isCompressedDisk(), fileNameFromDiskName(result.getDiskName()));
			// start where the last new disk was saved, or in the user's home folder
			var last = Setting.LAST_SAVE_DIR.getString();
			var startDir = last != null && !last.isEmpty() && new File(last).isDirectory()
					? new File(last) : new File(System.getProperty("user.home"));
			var imgFile = FileDialogHelper.openImageFileDialog(startDir, defaultName, true);
			if (imgFile == null) {
				return;
			}
			if (imgFile.getAbsoluteFile().getParentFile() != null) {
				Setting.LAST_SAVE_DIR.set(imgFile.getAbsoluteFile().getParentFile().getPath());
				mainPanel.saveSettingsQuietly();
			}
			var saveName = Setting.checkFileNameExtension(result.getDiskType(), result.isCompressedDisk(), imgFile.getPath());
			if (saveName != null) {
				createNewDisk(result, new File(saveName));
				reloadDiskImage(true);
				mainPanel.setButtonState();
			}
		}
	}

	protected void createNewDisk(RenameResult result, File file) {
		mainPanel.formatConsole("Selected file: \"%s\".", file);

		try {
			if (result.isCpmDisk()) {
				switch(result.getDiskType()) {
				case D64:
					diskImage = DiskImageType.D64_CPM_C128.getInstance(consoleStream);
					break;
				case D71:
					diskImage = DiskImageType.D71_CPM.getInstance(consoleStream);
					break;
				case D81:
					diskImage = DiskImageType.D81_CPM.getInstance(consoleStream);
					break;
				case D64_CPM_C64:
				case D64_CPM_C128:
				case D71_CPM:
				case D81_CPM:
					diskImage = result.getDiskType().getInstance(consoleStream);
					break;
				default:
					mainPanel.appendConsole("Filename with unknown file extension. Can't detect CP/M format.\n");
					return;
				}
			} else {
				diskImage = result.getDiskType().getInstance(consoleStream);
			}
		} catch (Exception | OutOfMemoryError e) {
			mainPanel.appendConsole("Failed to create disk image.\n"+e.getMessage()+"\n");
			return;
		}

		diskImage.setCompressed(result.isCompressedDisk());
		diskImage.saveNewImage(file, result.getDiskName(), result.getDiskID());
		diskImage.setFile(file);
		imageLoaded = true;
	}

	public void renameDisk() {
		mainPanel.appendConsole("Rename disk.");
		if (diskImage == null || diskImage.getBam() == null) {
			return;
		}
		var result = new RenameDiskImagePanel(mainPanel).showDialog(DroiD64.PROGNAME+" - Rename disk", diskImage);
		if (result != null) {
			mainPanel.formatConsole("New diskname is: \"%s, %s\".", result.getDiskName(), result.getDiskID());
			diskImage.renameImage(result.getDiskName(), result.getDiskID());
			reloadDiskImage(true);
		}
	}

	public Integer validateDisk() {
		if (diskImage == null) {
			return 0;
		}
		var errors = diskImage.validate(new ArrayList<>());
		if (errors != null && errors > 0) {
			Integer warnings = diskImage.getWarnings();
			mainPanel.formatConsole("Validated disk and found %d errors and %d warnings.", errors, warnings);
			new ValidationPanel(mainPanel.getParent()).show(diskImage.getValidationErrorList(), this);
		} else {
			mainPanel.appendConsole("Disk validated without finding any warnings or errors.");
		}
		return errors;
	}

	public void unloadDisk() {
		if (isImageLoaded()) {
			if (diskImage.isPartitionOpen()) {
				mainPanel.appendConsole("Unload partition.");
				diskImage.setCurrentPartition(null);
				reloadDiskImage(true);
				return;
			}
			mainPanel.appendConsole("Unload disk.");
			// go back to the folder the image is in (also when the image was reopened at start-up, when no folder is known yet)
			var imageFile = diskImage.getFile();
			if (imageFile != null && imageFile.isFile() && imageFile.getAbsoluteFile().getParentFile() != null) {
				currentImagePath = imageFile.getAbsoluteFile().getParentFile();
			}
			final String imageName = imageFile != null ? imageFile.getName() : null;
			diskImage = null;
			setDiskName(null);
			imageLoaded = false;
			diskLabel.setText(Utility.getMessage("droid64.nodisk"));
			clearDirTable();
			loadLocalDirectory(currentImagePath);
			table.revalidate();
			if (openedRow != null && openedRow < table.getRowCount() && openedRow >= 0) {
				// Select and scroll to last opened file
				table.getSelectionModel().setSelectionInterval(openedRow, openedRow);
				table.scrollRectToVisible(new Rectangle(table.getCellRect(openedRow, 0, true)));
				openedRow = null;
			} else if (imageName != null) {
				// no row remembered (e.g. the image was reopened at start-up): select the image's own row
				for (int r = 0; r < table.getRowCount(); r++) {
					if (imageName.equals(table.getValueAt(r, 1))) {
						table.getSelectionModel().setSelectionInterval(r, r);
						table.scrollRectToVisible(new Rectangle(table.getCellRect(r, 0, true)));
						break;
					}
				}
			}
		} else {
			mainPanel.appendConsole("Load parent.");
			var dirfile = getCurrentImagePath();
			if (dirfile != null && dirfile.getParent() != null) {
				loadLocalDirectory(dirfile.getParentFile());
			}
		}
	}

	public File getCurrentImagePath() {
		return currentImagePath;
	}

	private void setTypeOfFiles(int[] rows, FileType newType) {
		boolean changed = false;
		for (var i : rows) {
			try {
				var cbmFile = diskImage.getCbmFile(i);
				if (cbmFile.getFileType() == FileType.CBM) {
					continue;
				}
				mainPanel.appendConsole("Set type " + newType + ": \"" + cbmFile.getName() + '"');
				diskImage.renameFile(i, cbmFile.getName(), newType);
				changed = true;
			} catch (Exception e) {
				GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to change file type.");
			}
		}
		if (changed) {
			diskImage.save();
			reloadDiskImage(true);
		}
	}

	/** Rename one file after it was edited in the list. The type is kept. */
	private void renameInPlace(int row, String newName) {
		try {
			var cbmFile = diskImage.getCbmFile(row);
			mainPanel.formatConsole("New filename is: \"%s\" %s.\n[%d] \"%s\"", newName, cbmFile.getFileType(), cbmFile.getDirPosition(), cbmFile.getName());
			diskImage.renameFile(row, newName, cbmFile.getFileType());
			diskImage.save();
			reloadDiskImage(true);
			table.clearSelection();
		} catch (Exception e) {
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to rename file.");
		}
	}

	public void renameFile() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: renaming files in Zip files not supported.");
			return;
		}
		boolean filesRenamed = false;
		var selectedRows = table.getSelectedRows();
		for (var i : selectedRows) {
			try {
				RenameResult result = null;
				if (imageLoaded) {
					var cbmFile = diskImage.getCbmFile(i);
					var filename = cbmFile.getName();
					mainPanel.appendConsole("RenameFile: \"" + filename + '"');
					var renamePanel = new RenameFilePanel(mainPanel.getParent());
					renamePanel.setSelectedCount(selectedRows.length);
					result = renamePanel.show(DroiD64.PROGNAME+" - Rename file ", cbmFile);
					if (result != null && result.getBatchFileType() != null) {
						setTypeOfFiles(selectedRows, result.getBatchFileType());
						return;
					}
					if (result != null) {
						filesRenamed = true;
						mainPanel.formatConsole("New filename is: \"%s\" %s.\n[%d] \"%s\"", result.getFileName(), result.getFileType(), cbmFile.getDirPosition(), filename);
						diskImage.renameFile(i, result.getFileName(), result.getFileType());
					}
				} else {
					// local file
					var filename = (String) tableModel.getValueAt(i, 1);
					mainPanel.appendConsole("RenameFile: \"" + filename + '"');
					var oldFile = new File(currentImagePath + File.separator + filename);
					if (oldFile.isFile()) {
						result = new RenameFilePanel(mainPanel.getParent()).show(DroiD64.PROGNAME+" - Rename file ", oldFile);
						if (result != null && !filename.equals(result.getFileName())) {
							var newFile = new File(currentImagePath + File.separator + result.getFileName());
							if (!newFile.exists()) {
								filesRenamed = true;
								mainPanel.appendConsole("New filename is: \"" + result.getFileName() + '"');
								if (!oldFile.renameTo(newFile)) {
									mainPanel.appendConsole("Error: Failed to rename "+newFile.getName());
								}
							} else {
								mainPanel.appendConsole("Error: File "+newFile.getName()+" already exists.");
							}
						}
					}
				}
				if (result == null) {
					break;
				}
			} catch (Exception e) {
				GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to rename file.");
			}
		}
		if (filesRenamed) {
			if (imageLoaded) {
				diskImage.save();
			}
			reloadDiskImage(true);
		}
	}

	public void newFile() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: add new files to Zip files not supported.");
			return;
		}
		try {
			var result = new RenameFilePanel(mainPanel.getParent()).show(DroiD64.PROGNAME+" - New file ", "", FileType.DEL, imageLoaded, false);
			if (result != null) {
				newFile(result);
				reloadDiskImage(true);
			}
		} catch (Exception | OutOfMemoryError e) {
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to create file.");
		}
	}

	public void mkdir() {
		if (imageLoaded && !diskImage.supportsDirectories()) {
			mainPanel.appendConsole("Error: Makedir not supported.");
			return;
		}

		RenameResult result = null;
		try {
			if (imageLoaded) {
				result = new RenameFilePanel(mainPanel.getParent()).show(DroiD64.PROGNAME+" - New Partition ", "", FileType.CBM, true, true);
			} else {
				result = new RenameFilePanel(mainPanel.getParent()).show(DroiD64.PROGNAME+" - Makedir ", null, FileType.CBM, false, false);
			}
		} catch (CbmException e) {
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Mkdir failed.");
		}
		if (result == null) {
			return;
		}
		mainPanel.appendConsole("mkdir: "+result.getFileName());
		if (imageLoaded) {
			try {
				if (null == diskImage.makedir(result.getFileName(), result.getPartitionSectorCount(), result.getDiskID())) {
					mainPanel.appendConsole("Error: failed to create dir " + result.getFileName() + ".");
				} else {
					diskImage.save();
				}
			} catch (Exception e) {
				GuiHelper.showException(mainPanel.getParent(), "Partition error", e, "Failed creating partition %s.",result.getFileName());
			}
		} else {
			var dir = new File(currentImagePath + File.separator + result.getFileName());
			if (dir.exists()) {
				GuiHelper.showErrorMessage(mainPanel.getParent(), "Makedir failed", "Folder with name %s already exists.",dir);
			} else {
				if (!dir.mkdir()) {
					GuiHelper.showErrorMessage(mainPanel.getParent(), "Makedir error", "Failed to create dir %s.",dir);
				}
			}
		}
		reloadDiskImage(true);
	}

	protected void newFile(RenameResult result) {
		var cbmFile = new CbmFile();
		cbmFile.setName(result.getFileName());
		cbmFile.setFileType(result.getFileType());
		cbmFile.setNameAsBytes(cbmFile.getName().getBytes());
		mainPanel.appendConsole("newFile: " + cbmFile.getName());
		diskImage.addDirectoryEntry(cbmFile, 0, 0, false, 0);
		diskImage.save();
	}

	private String getName(int i) {
		return imageLoaded ? diskImage.getCbmFile(i).getName() : getLocalFilename(i);
	}

	public void deleteFile() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: deleting from Zip files not supported.");
			return;
		}
		boolean deletedFiles = false;
		for (int i = 0; i < table.getRowCount(); i++) {
			if (table.isRowSelected(i)) {
				var name = getName(i);
				mainPanel.appendConsole("Delete [" + i + "] " + name);
				try {
					if (imageLoaded) {
						diskImage.deleteFile(diskImage.getCbmFile(i));
					} else {
						Files.delete(new File(name).toPath());
					}
					deletedFiles = true;
				} catch (Exception | OutOfMemoryError e) {
					GuiHelper.showException(mainPanel.getParent(), "Error", e, "Deleting %s failed.", name);
				}
			}
		}
		if (deletedFiles) {
			if (imageLoaded) {
				diskImage.save();
			}
			reloadDiskImage(true);
		}
	}

	private void clearDirTable(){
		tableModel.clear();
		zipFileLoaded = false;
	}

	public void setOtherDiskPanelObject ( DiskPanel otherOne ) {
		otherDiskPanel = otherOne;
	}

	public void openDiskImage(File file, boolean updateList) {
		if (file != null) {
			if (Setting.isImageFileName(file)) {
				try {
					mainPanel.appendConsole("openDiskImage: "+file);
					diskImage = DiskImage.getDiskImage(file, consoleStream);
					imageLoaded = true;
					reloadDiskImage(updateList);
				} catch (Exception | OutOfMemoryError e) {	//NOSONAR
					GuiHelper.showException(mainPanel.getParent(),"Load error", e, "Failed to load disk image.");
					mainPanel.appendConsole("\nError: "+e.getMessage());
					setDiskName(null);
					diskLabel.setText(Utility.getMessage(Resources.DROID64_NODISK));
					imageLoaded = false;
					repaint();
				}
			} else if (file.isDirectory()) {
				loadLocalDirectory(file);
			} else {
				if (isVisible()) {
					GuiHelper.showErrorMessage(mainPanel.getParent(),"Load error","Unknown file type. "+file);
				} else {
					mainPanel.appendConsole("Unknown file type. " + file + '\n');
				}
			}
		} else {
			loadLocalDirectory(currentImagePath);
		}
	}

	public void reloadDiskImage(boolean updateList){
		mainPanel.appendConsole("reloadDiskImage "+imageLoaded+'\n');
		if (imageLoaded){
			if (diskImage != null) {
				try {
					var currentPartition = diskImage.getCurrentPartition();
					diskImage = DiskImage.getDiskImage(diskImage.getFile(), consoleStream);
					setDiskName(diskImage.getFile().getPath());
					clearDirTable();
					updateImageFile();
					if (currentPartition != null) {
						diskImage.setCurrentPartition(currentPartition);
						diskImage.readBAM();
						diskImage.readDirectory();
					}
					if (updateList) {
						showDirectory();
					}
				} catch (Exception | OutOfMemoryError e) {	//NOSONAR
					GuiHelper.showException(mainPanel.getParent(),"Load error", e, "Failed to load disk image.");
					mainPanel.appendConsole("\nError: "+e.getMessage());
					setDiskName(null);
					diskLabel.setText(Utility.getMessage(Resources.DROID64_NODISK));
					imageLoaded = false;
					repaint();
				}
			} else {
				mainPanel.appendConsole("\nError: Loaded image is null");
			}
		} else {
			loadLocalDirectory(currentImagePath);
		}
	}

	public void setTableColors() {
		int mode = tableModel.getMode();
		Color colorFg;
		Color colorBg;
		Color colorGrid;
		Font font;
		if (EntryTableModel.MODE_CBM == mode) {
			var baseBg = Setting.DIR_BG.getColor();
			var baseFg = Setting.DIR_FG.getColor();
			if (imageLoaded && diskMachine != null && diskMachine != C64.Machine.C64) {
				baseBg = diskMachine.background;
				baseFg = diskMachine.foreground;
			}
			colorBg = active ? baseBg : baseBg.darker();
			colorFg = active ? baseFg : baseFg.darker();
			colorGrid = colorBg;
			table.setRowHeight(Setting.ROW_HEIGHT.getInteger());
			font = sizedFont(Setting.CBM_FONT.getFont(), Setting.FONT_SIZE.getInteger());
		} else if (EntryTableModel.MODE_CPM == mode) {
			colorBg = active ? Setting.DIR_CPM_BG.getColor() : Setting.DIR_CPM_BG.getColor().darker();
			colorFg = active ? Setting.DIR_CPM_FG.getColor() : Setting.DIR_CPM_FG.getColor().darker();
			colorGrid = active ? Setting.DIR_CPM_BG.getColor() : Setting.DIR_CPM_BG.getColor().darker();
			table.setRowHeight(Setting.ROW_HEIGHT.getInteger());
			font = sizedFont(Setting.CBM_FONT.getFont(), Setting.FONT_SIZE.getInteger());
		} else if (EntryTableModel.MODE_LOCAL == mode) {
			colorBg = active ? Setting.DIR_LOCAL_BG.getColor() : Setting.DIR_LOCAL_BG.getColor().darker();
			colorFg = active ? Setting.DIR_LOCAL_FG.getColor() : Setting.DIR_LOCAL_FG.getColor().darker();
			colorGrid = Setting.DIR_LOCAL_BG.getColor();
			table.setRowHeight(Setting.LOCAL_ROW_HEIGHT.getInteger());
			font = sizedFont(Setting.SYS_FONT.getFont(), Setting.LOCAL_FONT_SIZE.getInteger());
		} else {
			mainPanel.appendConsole("DiskPanel.setTableColors: unknown mode " + mode);
			return;
		}
		table.setGridColor(colorGrid);
		table.setBackground(colorBg);
		table.setForeground(colorFg);
		diskLabel.setBackground(colorBg);
		diskLabel.setForeground(colorFg);
		diskLabelPane.setBackground(colorBg);
		diskLabelPane.setForeground(colorFg);
		table.setFont(font);
		diskLabel.setFont(font);
		if (EntryTableModel.MODE_LOCAL == mode) {
			table.setIntercellSpacing(new java.awt.Dimension(1, 1));
			table.setRowHeight(Math.max(4, Setting.LOCAL_ROW_HEIGHT.getInteger()));
		} else {
			// No gap between rows, so graphic characters join like on the original disk.
			table.setIntercellSpacing(new java.awt.Dimension(0, 0));
			table.setRowHeight(Math.max(4, Setting.ROW_HEIGHT.getInteger()));
		}
	}

	private static Font sizedFont(Font base, Integer size) {
		return base != null && size != null ? base.deriveFont((float) size) : base;
	}

	/** Apply changed font size / row height settings immediately. */
	public void refreshLayout() {
		table.setColumnModel(tableModel.getTableColumnModel());
		table.setAutoCreateColumnsFromModel(false);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		setTableColors();
		table.revalidate();
		table.repaint();
	}

	private ActionListener createDiskImageMenuActionListener() {
		return event -> {
			String cmd = event.getActionCommand();
			if (Resources.DROID64_MENU_DISK_NEW.equals(cmd)) {
				newDiskImage();
			} else if (Resources.DROID64_MENU_DISK_LOAD.equals(cmd)) {
				openedRow = null;
				openDiskImage(FileDialogHelper.openImageFileDialog(currentImagePath, null, false), true);
			} else if (Resources.DROID64_MENU_DISK_SHOWBAM.equals(cmd)) {
				showBAM();
			} else if (Resources.DROID64_MENU_DISK_UNLOAD.equals(cmd)) {
				unloadDisk();
			} else if (Resources.DROID64_MENU_DISK_COPYFILE.equals(cmd)) {
				copyFile();
			} else if (Resources.DROID64_MENU_DISK_RENAMEDISK.equals(cmd)) {
				renameDisk();
			} else if (Resources.DROID64_MENU_DISK_RENAMEFILE.equals(cmd)) {
				renameFile();
			} else if (Resources.DROID64_MENU_DISK_DELETEFILE.equals(cmd)) {
				deleteFile();
			} else if (Resources.DROID64_MENU_DISK_VIEWTEXT.equals(cmd)) {
				showFile();
			} else if (Resources.DROID64_MENU_DISK_VIEWHEX.equals(cmd)) {
				hexViewFile();
			} else if (Resources.DROID64_MENU_DISK_VIEWBASIC.equals(cmd)) {
				basicViewFile();
			} else if (Resources.DROID64_MENU_DISK_VIEWIMAGE.equals(cmd)) {
				imageViewFile();
			} else if (Resources.DROID64_MENU_DISK_PRINTDIR.equals(cmd)) {
				printDir();
			} else if (Resources.DROID64_MENU_DISK_MD5.equals(cmd)) {
				calcMd5Checksum();
			} else if (Resources.DROID64_MENU_DISK_MIRROR.equals(cmd)) {
				openOtherPanel();
			}
		};
	}

	/** Mirror this list (disk image or directory) onto the other list. */
	protected void openOtherPanel() {
		if (imageLoaded && diskImage != null) {
			otherDiskPanel.openDiskImage(diskImage.getFile(), true);
		} else {
			otherDiskPanel.loadLocalDirectory(currentImagePath);
		}
	}

	/**
	 * create a help drag-down menu (just for testing)
	 * @param propertyKey the propertyKey for the menu title
	 * @param mnemonic menu mnemonic
	 * @return the menu
	 */
	/** @return the opened disk image, or the directory shown, as a path string */
	public String getCurrentLocation() {
		File f = imageLoaded && diskImage != null ? diskImage.getFile() : currentImagePath;
		return f != null ? f.getAbsolutePath() : Utility.EMPTY;
	}

	public JMenu createDiskImageMenu(String propertyKey, String mnemonic, Setting restoreOption) {
		var menu = new JMenu(Utility.getMessage(propertyKey));
		menu.setMnemonic(mnemonic.charAt(0));
		var listener = createDiskImageMenuActionListener();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_NEW, 'n', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_LOAD, 'l', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_SHOWBAM, 'b', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_UNLOAD, 'u', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_COPYFILE, 'c', listener);
		menu.addSeparator();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_RENAMEDISK, 'r', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_RENAMEFILE, 'f', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_DELETEFILE, 'd', listener);
		menu.addSeparator();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_VIEWTEXT,  't', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_VIEWHEX,   'h', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_VIEWBASIC, 'a', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_VIEWIMAGE, 'g', listener);
		menu.addSeparator();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_PRINTDIR, 'p', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_MD5, '5', listener);
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_DISK_MIRROR, 'm', listener);
		menu.addSeparator();
		var restoreItem = new javax.swing.JCheckBoxMenuItem("Reopen last location at startup",
				Boolean.TRUE.equals(restoreOption.getBoolean()));
		restoreItem.addActionListener(event -> restoreOption.set(restoreItem.isSelected()));
		menu.add(restoreItem);

		return menu;
	}

	private void printDir() {
		String title;
		var lines = new ArrayList<String>();
		if (imageLoaded) {
			title = diskImage.getFile().getName();
			var label = String.format("0 \"%-16s\" %-5s", diskImage.getBam().getDiskName(), diskImage.getBam().getDiskId());
			mainPanel.appendConsole("Print image "+ diskImage.getFile());
			lines.add(label);
			for (var i=0; i < diskImage.getFilesUsedCount(); i++) {
				CbmFile file = diskImage.getCbmFile(i);
				if (file instanceof CpmFile) {
					lines.add(((CpmFile)file).asDirString());
				} else {
					lines.add(file.asDirString());
				}
			}
		} else if (zipFileLoaded) {
			title = currentImagePath.getName();
			mainPanel.appendConsole("Print zip file " + currentImagePath);
			int i = 0;
			DirEntry entry;
			do {
				entry = tableModel.getDirEntry(i++);
				if (entry != null && !PARENT_DIR.equals(entry.getName())) {
					String row = String.format("%8d %-20s\t %s", entry.getBlocks(), entry.getName(), entry.getFlags());
					lines.add(row);
				}
			} while (entry != null);
		} else {
			title = directory.getName();
			mainPanel.appendConsole("Print directory "+ directory);
			int i = 0;
			DirEntry entry;
			do {
				entry = tableModel.getDirEntry(i++);
				if (entry != null) {
					String row = String.format("%s\t %8d\t %s", entry.getFlags(), entry.getBlocks(), entry.getName());
					lines.add(row);
				}
			} while (entry != null);
		}
		try {
			var job = PrinterJob.getPrinterJob();
			job.setPageable(new PrintPageable(lines.toArray(new String[lines.size()]), title, imageLoaded, true, mainPanel));
			if (job.printDialog()) {
				job.print();
			}
		} catch (Exception | OutOfMemoryError e) { // NOSONAR
			GuiHelper.showException(mainPanel.getParent(), "Error", e, "Failed to print.");
		}
	}

	/**
	 * @return number of rows
	 */
	public int getRowHeight() {
		return rowHeight;
	}

	/**
	 * @param i height of rows
	 */
	public void setRowHeight(int i) {
		rowHeight = i;
		table.setRowHeight(rowHeight);
		table.revalidate();
	}

	public void setDirectory(File dir) {
		this.directory = dir;
	}

	/** @return the loaded disk image, or null */
	public DiskImage getDiskImage() {
		return imageLoaded ? diskImage : null;
	}

	/** @return data of the first selected file (from an image, zip or the file system), or null */
	public byte[] getSelectedFileData() {
		int row = table.getSelectedRow();
		return row < 0 ? null : getFileData(row);
	}

	/** @return name of the first selected file, or null */
	public String getSelectedFileName() {
		int row = table.getSelectedRow();
		return row < 0 ? null : getName(row);
	}

	/** @return indexes (in the directory) of the selected DEL entries of the loaded disk image */
	public java.util.List<Integer> getSelectedDelEntries() {
		var list = new java.util.ArrayList<Integer>();
		if (imageLoaded && !zipFileLoaded && diskImage != null) {
			for (var row : table.getSelectedRows()) {
				try {
					var f = diskImage.getCbmFile(row);
					if (f != null && f.getFileType() == FileType.DEL) {
						list.add(row);
					}
				} catch (Exception e) {
					// ignore
				}
			}
		}
		return list;
	}

	/** Save the loaded image after it was changed by a tool, and show the changes. */
	public void imageChanged() {
		if (imageLoaded && diskImage != null) {
			diskImage.save();
			reloadDiskImage(true);
		}
	}

	public boolean isImageLoaded() {
		return imageLoaded;
	}

	public boolean isWritableImageLoaded() {
		if (imageLoaded && !zipFileLoaded && diskImage != null && !diskImage.isCpmImage()) {
			return !diskImage.getImageFormat().isReadonly();
		} else {
			return false;
		}
	}

	/** @return true if a TAP tape image is loaded (read-only, no BAM) */
	public boolean isTapeLoaded() {
		return imageLoaded && diskImage != null && diskImage.getImageFormat() == DiskImageType.TAP;
	}

	public boolean isZipFileLoaded() {
		return zipFileLoaded;
	}

	public File getDirectory() {
		return directory;
	}

	public void moveFile(final boolean upwards) {
		moveFile(upwards, false);
	}

	/**
	 * Move the selected files one step, or all the way to the start or end of the list.
	 * @param upwards true to move towards the first entry
	 * @param toEnd true to move to the very start (upwards) or end of the disk image
	 */
	public void moveFile(final boolean upwards, final boolean toEnd) {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: files can't be moved in zip file.");
			return;
		} else if (!imageLoaded) {
			mainPanel.appendConsole(Utility.getMessage(Resources.DROID64_ERROR_NOIMAGEMOUNTED));
			return;
		}
		int[] rows = table.getSelectedRows();
		if (rows.length == 0) {
			return;
		}
		int count = table.getRowCount();
		var newRows = new ArrayList<Integer>();
		boolean moved = false;
		if (upwards) {
			for (int t = 0; t < rows.length; t++) {
				int target = toEnd ? t : Math.max(0, rows[t] - 1);
				int pos = moveRowTo(rows[t], toEnd ? target : (t > 0 && rows[t] - 1 <= newRows.get(t - 1) ? newRows.get(t - 1) + 1 : target));
				moved |= pos != rows[t];
				newRows.add(pos);
			}
		} else {
			for (int t = rows.length - 1; t >= 0; t--) {
				int target = toEnd ? count - 1 - (rows.length - 1 - t) : Math.min(count - 1, rows[t] + 1);
				int pos = moveRowTo(rows[t], target);
				moved |= pos != rows[t];
				newRows.add(pos);
			}
		}
		finishMove(moved, newRows);
	}

	/** Move the given rows so that the first of them ends up at the insert position (drag and drop). */
	public void moveRowsToInsertPosition(int[] rows, int insertAt) {
		if (!imageLoaded || zipFileLoaded || rows == null || rows.length == 0) {
			return;
		}
		java.util.Arrays.sort(rows);
		var newRows = new ArrayList<Integer>();
		boolean moved = false;
		if (insertAt <= rows[0]) {
			for (int t = 0; t < rows.length; t++) {
				int pos = moveRowTo(rows[t], insertAt + t);
				moved |= pos != rows[t];
				newRows.add(pos);
			}
		} else if (insertAt > rows[rows.length - 1]) {
			for (int t = rows.length - 1; t >= 0; t--) {
				int pos = moveRowTo(rows[t], insertAt - 1 - (rows.length - 1 - t));
				moved |= pos != rows[t];
				newRows.add(pos);
			}
		}
		finishMove(moved, newRows);
	}

	private void finishMove(boolean moved, List<Integer> newRows) {
		if (moved) {
			diskImage.save();
		}
		reloadDiskImage(true);
		if (moved) {
			table.clearSelection();
			for (int r : newRows) {
				if (r >= 0 && r < table.getRowCount()) {
					table.addRowSelectionInterval(r, r);
				}
			}
		}
	}

	/** Move one directory entry by repeated swaps with its neighbour, skipping scratched files. */
	private int moveRowTo(int from, int to) {
		int pos = from;
		while (pos != to) {
			boolean up = to < pos;
			int next = -1;
			if (up) {
				for (int j = pos - 1; j >= 0; j--) {
					if (!diskImage.getCbmFile(j).isFileScratched()) {
						next = j;
						break;
					}
				}
			} else {
				for (int j = pos + 1; j < diskImage.getFilesUsedCount(); j++) {
					if (!diskImage.getCbmFile(j).isFileScratched()) {
						next = j;
						break;
					}
				}
			}
			if (next < 0 || (up && next < to) || (!up && next > to)) {
				break;
			}
			var cbmFile1 = new CbmFile(diskImage.getCbmFile(pos));
			var cbmFile2 = new CbmFile(diskImage.getCbmFile(next));
			diskImage.switchFileLocations(cbmFile1, cbmFile2);
			diskImage.readDirectory();
			pos = next;
		}
		return pos;
	}

	public void sortFiles() {
		if (zipFileLoaded) {
			mainPanel.appendConsole("Error: files can't be sorted in zip file.");
			return;
		} else if (!imageLoaded) {
			mainPanel.appendConsole(Utility.getMessage(Resources.DROID64_ERROR_NOIMAGEMOUNTED));
			return;
		}
		var files = new ArrayList<CbmFile>();
		var positions = new ArrayList<Integer>();
		for (int i = 0; i < diskImage.getFilesUsedCount(); i++) {
			var f = diskImage.getCbmFile(i);
			files.add(f);
			if (!f.isFileScratched()) {
				positions.add(i);
			}
		}
		if (positions.size() < 2) {
			return;
		}
		var order = new SortDialog(files, positions).show(mainPanel.getParent());
		if (order == null) {
			return;
		}
		// current content of the sorted slots: ids are the indices into positions
		var current = new ArrayList<Integer>();
		for (int i = 0; i < positions.size(); i++) {
			current.add(i);
		}
		boolean changed = false;
		for (int rank = 0; rank < order.size(); rank++) {
			int want = order.get(rank);
			int from = current.indexOf(want);
			if (from == rank) {
				continue;
			}
			int landed = moveRowTo(positions.get(from), positions.get(rank));
			if (landed != positions.get(rank)) {
				mainPanel.appendConsole("Sort: could not move a file to its place.");
				break;
			}
			current.remove(from);
			current.add(rank, want);
			changed = true;
		}
		if (changed) {
			diskImage.save();
		}
		reloadDiskImage(true);
	}

	public List<ValidationError> repairValidationErrors(List<ValidationError.Error> repairList) {
		if (!imageLoaded) {
			return new ArrayList<>();
		}
		diskImage.validate(repairList);
		diskImage.save();
		reloadDiskImage(true);
		return diskImage.getValidationErrorList();
	}

	public DiskImageType getDiskImageType() {
		return imageLoaded ? diskImage.getDiskImageType() : DiskImageType.UNDEFINED;
	}

	public boolean supportsDirectories() {
		return !imageLoaded && !zipFileLoaded || (diskImage != null && diskImage.supportsDirectories());
	}

	public int getImageLength() {
		return diskImage!=null ? diskImage.size() : -1;
	}

	/** Darg and drop handler class */
	private class MyTransferHandler extends TransferHandler {

		private static final long serialVersionUID = 1L;

		@Override
		public int getSourceActions(JComponent c) {
			return DnDConstants.ACTION_COPY;
		}

		public DropTarget getDropTarget(DiskPanel diskPanel) {
			return new DropTarget() {
				private static final long serialVersionUID = 1L;
				@Override
				public synchronized void drop(DropTargetDropEvent event) {
					try {
						handleDropEvent(diskPanel, event);
					} catch (Exception e) {	//NOSONAR
						mainPanel.appendConsole("Failed to open dropped item.\n"+e.getMessage()+'\n');
					}
				}
			};
		}

		/** rows being dragged inside this list, to reorder files on the disk image */
		private int[] dragRows = null;

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			dragRows = null;
		}

		@Override
		public boolean importData(TransferHandler.TransferSupport info) {
			if (info.isDrop() && info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				try {
					@SuppressWarnings("unchecked")
					var fileList = (List<File>) info.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					dragRows = null;
					javax.swing.SwingUtilities.invokeLater(() -> {
						for (var file : fileList) {
							mainPanel.appendConsole("Dropped file " + file + '\n');
							doubleClickedLocalfile(file, -1);
						}
						setActive(true);
					});
					return true;
				} catch (Exception e) {	//NOSONAR
					mainPanel.appendConsole("Failed to open dropped item.\n" + e.getMessage() + '\n');
					return false;
				}
			}
			if (info.isDrop() && dragRows != null && imageLoaded && !zipFileLoaded
					&& info.getComponent() == table && info.getDropLocation() instanceof JTable.DropLocation) {
				int row = ((JTable.DropLocation) info.getDropLocation()).getRow();
				var rows = dragRows;
				dragRows = null;
				javax.swing.SwingUtilities.invokeLater(() -> moveRowsToInsertPosition(rows, row));
				return true;
			}
			return false;
		}

		@Override
		public Transferable createTransferable(JComponent comp) {
			var dropTable = (JTable) comp;
			dragRows = imageLoaded && !zipFileLoaded ? dropTable.getSelectedRows() : null;
			if (!imageLoaded) {
				int row = dropTable.getSelectedRow();
				var value = getLocalFilename(row);
				if (new File(value).exists()) {
					return new StringSelection(value);
				}
				return null;
			} else {
				return new StringSelection(getSelectedAsString(dropTable));
			}
		}

		@Override
		public boolean canImport(TransferHandler.TransferSupport info) {
			return true;
		}

		private String getSelectedAsString(JTable table) {
			var b = new StringBuilder();
			if (imageLoaded) {
				b.append(String.format("%s \"%-16s\" %s%n",
						diskImage.getBam().getDiskDosType(), diskImage.getBam().getDiskName(), diskImage.getBam().getDiskId()));
			}
			for (int row = 0; row < table.getRowCount(); row++) {
				if (table.isRowSelected(row)) {
					if (imageLoaded) {
						b.append(diskImage.getCbmFile(row).asDirString()).append('\n');
					} else {
						for (int c=0; c<table.getColumnCount(); c++) {
							if (c > 0) {
								b.append('\t');
							}
							b.append(table.getValueAt(row, c));
						}
						b.append('\n');
					}
				}
			}
			if (imageLoaded) {
				b.append(diskImage.getBlocksFree()).append(" BLOCKS FREE.\n");
			}
			return b.toString();
		}

		private void handleDropEvent(final DiskPanel diskPanel, DropTargetDropEvent event) throws UnsupportedFlavorException, IOException {
			var trans = event.getTransferable();
			if (trans.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				mainPanel.appendConsole("Dropped file list: \n");
				event.acceptDrop(DnDConstants.ACTION_COPY);
				@SuppressWarnings("unchecked")
				var fileList = (List<File>) trans.getTransferData(DataFlavor.javaFileListFlavor);
				for (var file : fileList) {
					mainPanel.appendConsole("Dropped file "+file+'\n');
					doubleClickedLocalfile(file, -1);
					diskPanel.setActive(true);
				}
			} else if (trans.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				mainPanel.appendConsole("Dropped image\n");
				event.acceptDrop(DnDConstants.ACTION_COPY);
				var img = (Image) trans.getTransferData(DataFlavor.imageFlavor);
				new ViewImagePanel("Image", mainPanel).show(img, "image");
			} else if (trans.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				event.acceptDrop(DnDConstants.ACTION_COPY);
				var s = (String) trans.getTransferData(DataFlavor.stringFlavor);
				doubleClickedLocalfile(new File(s), -1);
				diskPanel.setActive(true);
				mainPanel.appendConsole("Dropped string: "+s+"\n");
				new TextViewPanel(mainPanel).show(s, "string", "string", "text/text");
			} else {
				mainPanel.appendConsole("Unsupported drop type.\n");
			}
		}
	}

}
