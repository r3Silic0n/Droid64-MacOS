package droid64.gui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.ColorUIResource;

import droid64.DroiD64;
import droid64.cfg.Bookmark;
import droid64.cfg.BookmarkType;
import droid64.d64.CbmException;
import droid64.d64.DiskImage;
import droid64.d64.DiskImageType;
import droid64.d64.Utility;
import droid64.db.DaoFactory;
import droid64.db.DaoFactoryImpl;
import droid64.db.DatabaseException;
import droid64.db.DiskList;

/**<pre>
 * Created on 21.06.2004
 *
 *   droiD64 - A graphical filemanager for D64 files
 *   Copyright (C) 2004 Wolfram Heyer
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
 *   eMail: wolfvoz@users.sourceforge.net
 *   http://droid64.sourceforge.net
 * @author wolf
 *</pre>
 */
public class MainPanel implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final int COLOUR_POWER_1 = 5;
	private static final int COLOUR_POWER_2 = 35;

	public static final Insets BUTTON_MARGINS = new Insets(1, 4, 1, 4);
	/** Left disk panel */
	private final DiskPanel diskPanel1;
	/** Right disk panel */
	private final DiskPanel diskPanel2;
	/** True when scanning for disk images is running. */
	private boolean scannerActive = false;
	private final transient HashMap<Object, Object> colorHashMap = new HashMap<>();
	/** The position of the divider in the splitPane. */
	private int dividerLoc = -1;
	/** The size of the divider in the splitPane. */
	private int dividerSize = 10;

	/** Number of columns of buttons. */
	private static final int NUM_BUTTON_COLUMNS = 8;
	/** Number of rows of buttons. */
	private static final int NUM_BUTTON_ROWS = 4;
	/** Map containing all buttons */
	private final Map<ActionButton,JComponent> buttonMap = new TreeMap<>();

	// Labels
	private static final String LBL_NODISK = "noDisk";
	private static final String LBL_INSERTERROR = "insertError";

	// BUTTONS
	private JButton unloadDiskButton ;
	private JButton showBamButton;
	private JButton validateDiskButton;
	private JButton renameDiskButton;
	private JButton upButton;
	private JButton downButton;
	private JButton sortButton;
	private JButton copyButton;
	private JButton moveButton;
	private JButton delPRGButton;
	private JButton renamePRGButton;

	private JButton ftpButton;
	private JButton searchButton;

	/** The plugin buttons. Used to be able to change the label from settings. */
	private final JButton[] pluginButtons = new JButton[Setting.MAX_PLUGINS];
	private JToggleButton consoleHideButton;
	private final JFrame parent;
	/** The menu shown when database is used */
	private final JMenu searchMenu = new JMenu(Utility.getMessage(Resources.DROID64_MENU_SEARCH));
	/** The console */
	private final JTextArea consoleText = new JTextArea();
	private final transient ConsoleStream consoleStream = new ConsoleStream(consoleText);
	private final transient OutputStreamWriter console = new OutputStreamWriter(consoleStream);
	/** The split pane used for the console at the bottom and the rest in the upper half. */
	private final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

	private static String releaseNotes = null;
	private static String manual = null;
	private JMenu bookmarkMenu = null;
	private JToolBar bookmarkBar = new JToolBar();

	/**
	 * Constructor.
	 * @param parent parent frame
	 */
	public MainPanel(JFrame parent) {
		this.parent = parent;
		FileDialogHelper.setOwner(parent);
		parent.setTitle(DroiD64.PROGNAME + " - MacOS (v" + DroiD64.VERSION + ")");

		doSettings(parent);
		installMacIntegration(parent);
		diskPanel1 = new DiskPanel(this, consoleStream);
		diskPanel2 = new DiskPanel(this, consoleStream);
		drawPanel(parent);
		diskPanel1.setDirectory(Setting.DEFAULT_IMAGE_DIR.getFile());
		diskPanel2.setDirectory(Setting.DEFAULT_IMAGE_DIR2.getFile());
		diskPanel1.setOtherDiskPanelObject(diskPanel2);
		diskPanel2.setOtherDiskPanelObject(diskPanel1);
		diskPanel1.setActive(true);
		// Setup GUI
		setupMenuBar(parent);
		parent.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		parent.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent e) {
						exitThisProgram();
					}
				});
		saveDefaultValues();
		doSettings(parent);
		setMainWindowSize(parent);
		restoreDiskPanel(diskPanel1, Setting.RESTORE_DISK1, Setting.LAST_PATH1, Setting.DEFAULT_IMAGE_DIR.getFile());
		restoreDiskPanel(diskPanel2, Setting.RESTORE_DISK2, Setting.LAST_PATH2, Setting.DEFAULT_IMAGE_DIR2.getFile());

		hideConsole(Setting.HIDECONSOLE.getBoolean());
	}

	/**
	 * Setup menu
	 * @param parent parent frame
	 */
	public void setupMenuBar(final JFrame parent) {
		var menubar = new JMenuBar();
		menubar.add(createProgramMenu(parent));
		menubar.add(diskPanel1.createDiskImageMenu(Resources.DROID64_MENU_DISK_1, "1", Setting.RESTORE_DISK1));
		menubar.add(diskPanel2.createDiskImageMenu(Resources.DROID64_MENU_DISK_2, "2", Setting.RESTORE_DISK2));
		menubar.add(createBookmarkMenu());
		menubar.add(createSearchMenu(parent));
		menubar.add(createToolsMenu(parent));
		menubar.add(createHelpMenu(parent));
		parent.setJMenuBar(menubar);
		menubar.revalidate();
		menubar.repaint();
	}

	/** Tools: BAM / sector editor, graphics and PETSCII viewers, DIR art editor. */
	private JMenu createToolsMenu(final JFrame parent) {
		var menu = new JMenu("Tools");
		menu.setMnemonic('T');
		var bam = new JMenuItem("BAM / Sector editor...");
		bam.addActionListener(e -> {
			var dp = getActiveDiskPanel();
			var img = dp != null ? dp.getDiskImage() : null;
			if (img == null) {
				showToolsMessage("Load a disk image in the active list first.");
			} else {
				GraphicBamDialog.show(parent, dp, img, new File(String.valueOf(dp.getCurrentPath())).getName());
			}
		});
		var gfx = new JMenuItem("Graphics viewer (charset, sprites, bitmap)...");
		gfx.addActionListener(e -> {
			var dp = getActiveDiskPanel();
			var data = dp != null ? dp.getSelectedFileData() : null;
			if (data == null) {
				showToolsMessage("Select a file in the active list first.");
			} else {
				GraphicsViewerDialog.show(parent, dp.getSelectedFileName(), data);
			}
		});
		var screen = new JMenuItem("PETSCII screen viewer (SEQ / PRG)...");
		screen.addActionListener(e -> {
			var dp = getActiveDiskPanel();
			var data = dp != null ? dp.getSelectedFileData() : null;
			if (data == null) {
				showToolsMessage("Select a file in the active list first.");
			} else {
				PetsciiScreenDialog.show(parent, dp.getSelectedFileName(), data);
			}
		});
		var dirArt = new JMenuItem("DIR art editor...");
		dirArt.addActionListener(e -> {
			var dp = getActiveDiskPanel();
			DirArtEditorDialog.show(parent, dp, dp != null ? dp.getDiskImage() : null);
		});
		menu.add(bam);
		menu.addSeparator();
		menu.add(gfx);
		menu.add(screen);
		menu.addSeparator();
		menu.add(dirArt);
		return menu;
	}

	private void showToolsMessage(String text) {
		JOptionPane.showMessageDialog(getParent(), text, "Tools", JOptionPane.INFORMATION_MESSAGE);
	}

	protected DiskPanel getActiveDiskPanel() {
		if (diskPanel1 != null && diskPanel1.isActive()) {
			return diskPanel1;
		} else if (diskPanel2 != null && diskPanel2.isActive()) {
			return diskPanel2;
		} else {
			return null;
		}
	}

	protected DiskPanel getInactiveDiskPanel() {
		if (diskPanel1 != null && diskPanel1.isActive()) {
			return diskPanel2;
		} else if (diskPanel2 != null && diskPanel2.isActive()) {
			return diskPanel1;
		} else {
			return null;
		}
	}

	/**
	 * Setup main panel
	 */
	private void drawPanel(final JFrame parent) {
		var dirListPanel = new JPanel(new GridLayout(1,2));
		dirListPanel.add(diskPanel1);
		dirListPanel.add(diskPanel2);
		// Create all buttons into the buttonMap
		createDiskOperationButtons(parent);
		createFileOperationButtons(parent);
		createViewFileButtons();
		createOtherButtons(parent);
		// Put buttons in GUI
		var buttonPanel = new JPanel(new GridLayout(NUM_BUTTON_ROWS, NUM_BUTTON_COLUMNS));
		buttonMap.entrySet().forEach(entry -> buttonPanel.add(entry.getValue()));

		var listButtonPanel = new JPanel(new BorderLayout());
		listButtonPanel.add(bookmarkBar, BorderLayout.NORTH);
        listButtonPanel.add(dirListPanel, BorderLayout.CENTER);
		listButtonPanel.add(buttonPanel, BorderLayout.SOUTH);

		splitPane.setContinuousLayout(true);
		splitPane.setTopComponent(listButtonPanel);
		splitPane.setBottomComponent(createConsolePanel());
		// the console keeps a fixed, compact height; extra window height goes to the file lists
		splitPane.setResizeWeight(1.0);
		parent.setContentPane(splitPane);
	}

	private static final Color BASE_BUTTON_COLOR = new Color(238, 238, 238);

	private Color adjustedColor(Color color, int red, int green, int blue) {
		int newRed = Math.min(Math.max(0, color.getRed() + red), 255);
		int newGreen = Math.min(Math.max(0, color.getGreen() + green), 255);
		int newBlue = Math.min(Math.max(0, color.getBlue() + blue), 255);
		return new Color(newRed, newGreen, newBlue);
	}

	private JToggleButton createToggleButton(ActionButton buttonKey, ActionListener listener) {
		var button = new FlatStyle.ToggleButton(Utility.getMessage(buttonKey.getLabel() + ".label"));
		button.setToolTipText(Utility.getMessage(buttonKey.getLabel() + ".tooltip"));
		button.setMargin(BUTTON_MARGINS);
		button.addActionListener(listener);
		buttonMap.put(buttonKey, button);
		setButtonColor(button, buttonKey);
		return button;
	}

	private JButton createButton(String label, ActionButton buttonKey, String toolTip, ActionListener listener) {
		var button = new FlatStyle.Button(label);
		if (buttonKey!=null) {
			setButtonColor(button, buttonKey);
			buttonMap.put(buttonKey, button);
		}
		button.setToolTipText(toolTip);
		button.setMargin(BUTTON_MARGINS);
		button.addActionListener(listener);
		return button;
	}

	private JButton createButton(ActionButton buttonKey, ActionListener listener) {
		var button = new FlatStyle.Button(Utility.getMessage(buttonKey.getLabel() + ".label"));
		button.setToolTipText(Utility.getMessage(buttonKey.getLabel()  + ".tooltip"));
		button.setMargin(BUTTON_MARGINS);
		button.addActionListener(listener);
		button.setActionCommand(buttonKey.getLabel());
		setButtonColor(button, buttonKey);
		buttonMap.put(buttonKey, button);
		return button;
	}

	private void setButtonColor(JComponent button, ActionButton buttonKey) {
		switch (buttonKey) {
		case LOAD_DISK_BUTTON:
		case UNLOAD_DISK_BUTTON:
		case NEW_DISK_BUTTON:
		case BAM_BUTTON:
		case VALIDATE_DISK_BUTTON:
		case RENAME_DISK_BUTTON:
		case DIR_ART_BUTTON:
		case MIRROR_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, -20, -20, 20));
			break;
		case UP_BUTTON:
		case DOWN_BUTTON:
		case SORT_FILES_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, -20, 20, -20));
			break;
		case COPY_FILE_BUTTON:
		case MOVE_FILE_BUTTON:
		case DELETE_FILE_BUTTON:
		case RENAME_FILE_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, 20, -20, 20));
			break;
		case HEX_EDIT_BUTTON:
		case VIEW_IMAGE_BUTTON:
		case PLAY_MUSIC_BUTTON:
		case VIEW_TEXT_BUTTON:
		case VIEW_BASIC_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, 20, 20, -20));
			break;
		case FTP_BUTTON:
		case PLUGIN_1_BUTTON:
		case PLUGIN_2_BUTTON:
		case PLUGIN_3_BUTTON:
		case PLUGIN_4_BUTTON:
		case PLUGIN_5_BUTTON:
		case PLUGIN_7_BUTTON:
		case PLUGIN_8_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, -20, 20, 20));
			break;
		case HIDE_CONSOLE_BUTTON:
		case SETTINGS_BUTTON:
		case EXIT_BUTTON:
		case SEARCH_BUTTON:
			button.setBackground(adjustedColor(BASE_BUTTON_COLOR, 30, -20, -20));
			break;
		default:
			button.setBackground(BASE_BUTTON_COLOR);
			break;
		}
	}

	private void unloadDiskImage() {
		var diskPanel = getActiveDiskPanel();
		if  (diskPanel != null) {
			diskPanel.unloadDisk();
			setButtonState();
		}
	}

	private void enableButtons(boolean enabled, JButton... buttons) {
		for (var button : buttons) {
			button.setEnabled(enabled);
		}
	}

	public void setButtonState() {
		var activePanel = getActiveDiskPanel();
		var inactivePanel = getInactiveDiskPanel();
		unloadDiskButton.setText("Unload Disk");
		enableButtons(activePanel != null && activePanel.isImageLoaded(), unloadDiskButton);

		boolean tape = activePanel != null && activePanel.isTapeLoaded();
		enableButtons(activePanel != null && activePanel.isImageLoaded() && !tape, showBamButton);
		var dirArt = buttonMap.get(ActionButton.DIR_ART_BUTTON);
		if (dirArt != null) {
			dirArt.setEnabled(!tape);
		}
		enableButtons(inactivePanel != null && (inactivePanel.isWritableImageLoaded() || (!inactivePanel.isImageLoaded() && !inactivePanel.isZipFileLoaded())), copyButton);

		if (activePanel != null && activePanel.isZipFileLoaded()) {
			enableButtons(false, showBamButton, validateDiskButton, renameDiskButton, upButton, downButton, sortButton, moveButton, delPRGButton, renamePRGButton);
		} else if (activePanel != null && activePanel.isWritableImageLoaded()) {
			enableButtons(true, validateDiskButton, renameDiskButton, upButton, downButton, sortButton, moveButton, delPRGButton, renamePRGButton);
		} else {
			if (activePanel == null || !activePanel.isImageLoaded()) {
				enableButtons(true, delPRGButton, renamePRGButton);
				enableButtons(false, moveButton, validateDiskButton, renameDiskButton, upButton, downButton, sortButton);
			} else {
				enableButtons(false, moveButton, validateDiskButton, renameDiskButton, upButton, downButton, sortButton, delPRGButton, renamePRGButton);
			}
		}
	}

	private void createOtherButtons(final JFrame parent) {
		ActionListener pluginButtonListener = event -> {
			var list = Setting.getExternalPrograms();
			for (int cnt = 0; cnt < list.size(); cnt ++){
				if (event.getSource() == pluginButtons[cnt] ){
					var diskPanel = getActiveDiskPanel();
					var prg = list.get(cnt);
					if (prg != null && isOpenCbmProgram(prg)) {
						OpenCbmDialog.show(this);
					} else if (diskPanel != null && prg != null) {
						diskPanel.doExternalProgram(prg);
					}
				}
			}
		};

		var externalPrograms = Setting.getExternalPrograms();
		// plugin number -> button position (independent of the order the buttons are declared in)
		var pluginActionButtons = Arrays.asList(ActionButton.PLUGIN_1_BUTTON, ActionButton.PLUGIN_2_BUTTON, ActionButton.PLUGIN_3_BUTTON,
				null, null, ActionButton.PLUGIN_7_BUTTON, ActionButton.PLUGIN_8_BUTTON,
				ActionButton.PLUGIN_4_BUTTON);
		for (var i = 0; i < pluginButtons.length && i < externalPrograms.size(); i++) {
			var ep = externalPrograms.get(i);
			var label = ep != null ? ep.getLabel() : null;
			var tooltip = ep != null ? ep.getDescription() : null;
			if (ep != null && isOpenCbmProgram(ep)) {
				label = "OpenCBM";
				tooltip = "OpenCBM: copy, format and control real Commodore drives";
			}
			var actionButton = i < pluginActionButtons.size() ? pluginActionButtons.get(i) : null;
			if (ep != null && isOpenCbmProgram(ep)) {
				// the OpenCBM window has the place of the old "CBM dir" button, which it replaces
				actionButton = ActionButton.PLUGIN_5_BUTTON;
			}
			pluginButtons[i] = createButton(label != null ? label : "", actionButton, tooltip, pluginButtonListener);
		}
		consoleHideButton = createToggleButton(ActionButton.HIDE_CONSOLE_BUTTON,
				ae-> hideConsole(consoleHideButton.isSelected()));
		consoleHideButton.setSelected(Setting.HIDECONSOLE.getBoolean());
		createButton(ActionButton.SETTINGS_BUTTON, ae -> showSettings(parent));
		createButton(ActionButton.EXIT_BUTTON, ae -> exitThisProgram());
	}

	/** The old "d64copy" plugin slot opens the OpenCBM window. */
	private static boolean isOpenCbmProgram(ExternalProgram ep) {
		var cmd = ep.getCommand();
		return "opencbm".equalsIgnoreCase(cmd) || "d64copy".equals(cmd);
	}

	private void hideConsole(boolean hide) {
		if (consoleHideButton != null) {
			consoleHideButton.setText(hide ? "View Console" : "Hide Console");
			consoleHideButton.setToolTipText(hide ? "Show the console pane." : "Hide the console pane.");
			consoleHideButton.setSelected(hide);
		}
		if (splitPane.getBottomComponent() != null) {
			splitPane.getBottomComponent().setVisible(!hide);
			if (!hide) {
				if (dividerSize > 0) {
					splitPane.setDividerLocation(dividerLoc);
					splitPane.setDividerSize(dividerSize);
				} else {
					splitPane.setDividerLocation(-1);
					splitPane.setDividerSize(new JSplitPane().getDividerSize());
				}
			} else {
				dividerLoc = splitPane.getDividerLocation();
				dividerSize = splitPane.getDividerSize();
				splitPane.setDividerSize(0);
			}
		}
	}

	private void createDiskOperationButtons(final JFrame parent) {
		createButton(ActionButton.NEW_DISK_BUTTON, ae -> {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null) {
				diskPanel.newDiskImage();
			}
		});
		createButton(ActionButton.LOAD_DISK_BUTTON, ae -> {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null) {
				// start where the last disk image was loaded from, or in the user's home folder
				var last = Setting.LAST_LOAD_DIR.getString();
				File startDir = last != null && !last.isEmpty() && new File(last).isDirectory()
						? new File(last) : new File(System.getProperty("user.home"));
				File imgFile = FileDialogHelper.openImageFileDialog(startDir, null, false);
				if (imgFile != null && imgFile.getParentFile() != null && imgFile.getParentFile().isDirectory()) {
					Setting.LAST_LOAD_DIR.set(imgFile.getParentFile().getAbsolutePath());
					saveSettingsQuietly();
				}
				diskPanel.openDiskImage(imgFile, true);
			}
		});
		unloadDiskButton = createButton(ActionButton.UNLOAD_DISK_BUTTON, ae -> unloadDiskImage());
		showBamButton = createButton(ActionButton.BAM_BUTTON, ae ->  {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null && diskPanel.isImageLoaded()) {
				diskPanel.showBAM();
			} else {
				showErrorMessage(parent, LBL_NODISK);
			}
		});
		renameDiskButton = createButton(ActionButton.RENAME_DISK_BUTTON, ae -> {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null && diskPanel.isImageLoaded()) {
				diskPanel.renameDisk();
			} else {
				showErrorMessage(parent, LBL_NODISK);
				return;
			}
		});
		validateDiskButton = createButton(ActionButton.VALIDATE_DISK_BUTTON, ae -> {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null && diskPanel.isImageLoaded()) {
				diskPanel.validateDisk();
			} else {
				showErrorMessage(parent, LBL_NODISK);
				return;
			}
		});
		createButton(ActionButton.MIRROR_BUTTON, ae -> {
			var diskPanel = getActiveDiskPanel();
			if (diskPanel != null) {
				diskPanel.openOtherPanel();
			}
		});
	}

	private void createViewFileButtons() {
		var viewTextButton = createButton(ActionButton.VIEW_TEXT_BUTTON, null);
		var hexViewButton = createButton(ActionButton.PLAY_MUSIC_BUTTON, null);
		var basicViewButton = createButton(ActionButton.VIEW_BASIC_BUTTON, null);
		var hexEditButton = createButton(ActionButton.HEX_EDIT_BUTTON, null);
		var imageViewButton = createButton(ActionButton.VIEW_IMAGE_BUTTON, null);
		ActionListener viewListener = event -> {
			try {
			var diskPanel = getActiveDiskPanel();
			var cmd = event.getActionCommand();
			if (diskPanel == null || cmd == null) {
				return;
			} else if (viewTextButton.getActionCommand().equals(cmd)) {
				diskPanel.showFile();
			} else if (hexViewButton.getActionCommand().equals(cmd)) {
				diskPanel.playMusic();
			} else if (hexEditButton.getActionCommand().equals(cmd)) {
				diskPanel.hexEditFile();
			} else if (basicViewButton.getActionCommand().equals(cmd)) {
				diskPanel.basicViewFile();
			} else if (imageViewButton.getActionCommand().equals(cmd)) {
				diskPanel.imageViewFile();
			}
			} catch (Exception |OutOfMemoryError ex) {
				GuiHelper.showException(getParent(), "Error", ex, "Failed to open file.");
			}
		};
		viewTextButton.addActionListener(viewListener);
		hexViewButton.addActionListener(viewListener);
		basicViewButton.addActionListener(viewListener);
		hexEditButton.addActionListener(viewListener);
		imageViewButton.addActionListener(viewListener);
	}

	private void createFileOperationButtons(final JFrame parent) {
		upButton = createButton(ActionButton.UP_BUTTON, event -> {
			var diskPanel = getActiveDiskPanel();
			if (upButton.getActionCommand().equals(event.getActionCommand())) {
				if (diskPanel != null && diskPanel.isImageLoaded()) {
					diskPanel.moveFile(true, (event.getModifiers() & java.awt.event.ActionEvent.SHIFT_MASK) != 0);
				} else {
					appendConsole(Utility.getMessage(Resources.DROID64_ERROR_NOIMAGEMOUNTED));
				}
			}
		});
		downButton = createButton(ActionButton.DOWN_BUTTON, event -> {
			var diskPanel = getActiveDiskPanel();
			if (downButton.getActionCommand().equals(event.getActionCommand())) {
				if (diskPanel != null && diskPanel.isImageLoaded()) {
					diskPanel.moveFile(false, (event.getModifiers() & java.awt.event.ActionEvent.SHIFT_MASK) != 0);
				} else {
					appendConsole(Utility.getMessage(Resources.DROID64_ERROR_NOIMAGEMOUNTED));
				}
			}
		});
		sortButton = createButton(ActionButton.SORT_FILES_BUTTON, event -> {
			if (sortButton.getActionCommand().equals(event.getActionCommand())) {
				var diskPanel = getActiveDiskPanel();
				if (diskPanel != null) {
					diskPanel.sortFiles();
				}
			}
		});
		copyButton = createButton(ActionButton.COPY_FILE_BUTTON, event -> {
			if (copyButton.getActionCommand().equals(event.getActionCommand())) {
				var disk1 = getActiveDiskPanel();
				var disk2 = getInactiveDiskPanel();
				if (disk1 != null && disk2 != null) {
					disk1.copyFile();
				}
			}
		});
		renamePRGButton = createButton(ActionButton.RENAME_FILE_BUTTON, event -> {
			if (renamePRGButton.getActionCommand().equals(event.getActionCommand())) {
				var diskPanel = getActiveDiskPanel();
				if (diskPanel != null) {
					diskPanel.renameFile();
				}
			}
		});
		delPRGButton = createButton(ActionButton.DELETE_FILE_BUTTON, event -> {
			if (delPRGButton.getActionCommand().equals(event.getActionCommand())) {
				var diskPanel = getActiveDiskPanel();
				if (diskPanel != null) {
					diskPanel.deleteFile();
				}
			}
		});
		moveButton = createButton(ActionButton.MOVE_FILE_BUTTON, event -> {
			if (moveButton.getActionCommand().equals(event.getActionCommand())) {
				var disk1 = getActiveDiskPanel();
				if (disk1 != null && getInactiveDiskPanel() != null) {
					disk1.moveFile();
				}
			}
		});
		createButton(ActionButton.DIR_ART_BUTTON, event -> {
			var diskPanel = getActiveDiskPanel();
			DirArtEditorDialog.show(parent, diskPanel, diskPanel != null ? diskPanel.getDiskImage() : null);
		});
		ftpButton = createButton(ActionButton.FTP_BUTTON, event -> {
			if (ftpButton.getActionCommand().equals(event.getActionCommand())) {
				FtpDialog.show(this);
			}
		});
		searchButton = createButton(ActionButton.SEARCH_BUTTON, event -> {
			if (searchButton.getActionCommand().equals(event.getActionCommand())) {
				new SearchPanel(DroiD64.PROGNAME + " - Search", this).showDialog();
			}
		});
	}

	private JPanel createConsolePanel() {
		var consolePanel = new JPanel(new BorderLayout());
		var border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		consolePanel.setBorder(BorderFactory.createTitledBorder(border, Utility.getMessage(Resources.DROID64_CONSOLE)));

		var popMenu = new JPopupMenu();
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_PROGRAM_CLEARCONSOLE, 'c', event -> consoleText.setText(Utility.EMPTY));
		GuiHelper.addMenuItem(popMenu, Resources.DROID64_MENU_PROGRAM_SAVECONSOLE, 'a', event -> saveConsole());

		consoleText.setEditable(false);
		consoleText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, consoleText.getFont().getSize()));
		consoleText.setComponentPopupMenu(popMenu);
		consolePanel.add(new JScrollPane(consoleText), BorderLayout.CENTER);
		consolePanel.setPreferredSize(new Dimension(100, 120));
		return consolePanel;
	}

	public void appendConsole(String message) {
		if (!Utility.EMPTY.equals(message)) {
			try {
				console.write(message);
				console.write('\n');
				console.flush();
			} catch (IOException ignore) { /* ignored */ }
		}
	}

	public void formatConsole(String message, Object...args) {
		appendConsole(String.format(message, args));
	}

	private void showErrorMessage(JFrame parent, String error){
		if (LBL_NODISK.equals(error)) {
			appendConsole("\nNo disk image file selected. Aborting.");
			JOptionPane.showMessageDialog(parent,
					Utility.getMessage(Resources.DROID64_INFO_NOIMAGELOADED),
					DroiD64.PROGNAME + " - No disk",
					JOptionPane.ERROR_MESSAGE);
		} else if (LBL_INSERTERROR.equals(error)) {
			appendConsole("\nInserting error. Aborting.\n");
			JOptionPane.showMessageDialog(parent,
					"An error occurred while inserting file into disk.\n"+
							"Look up console report message for further information.",
							DroiD64.PROGNAME + " - Failure while inserting file",
							JOptionPane.ERROR_MESSAGE );
		}
	}

	/**
	 * Create a help drag-down menu
	 * @return JMenu
	 */
	private JMenu createHelpMenu(JFrame parent) {
		var menu = new JMenu(Utility.getMessage(Resources.DROID64_MENU_HELP));
		menu.setMnemonic('h');
		ActionListener listener = event -> {
			var cmd = event.getActionCommand();
			if (Resources.DROID64_MENU_HELP_ABOUT.equals(cmd)) {
				showHelp();
			} else if (Resources.DROID64_MENU_HELP_TODO.equals(cmd)) {
				new BugsPanel().showDialog(parent);
			} else if (Resources.DROID64_MENU_HELP_RELEASENOTES.equals(cmd)) {
				new TextViewPanel(this).show(getReleaseNotes(), DroiD64.PROGNAME, Utility.getMessage(Resources.DROID64_MENU_HELP_RELEASENOTES), Utility.MIMETYPE_HTML);
			} else if (Resources.DROID64_MENU_HELP_MANUAL.equals(cmd)) {
				new TextViewPanel(this).show(getManual(), DroiD64.PROGNAME, Utility.getMessage(Resources.DROID64_MENU_HELP_MANUAL), Utility.MIMETYPE_HTML);
			} else if (Resources.DROID64_MENU_HELP_CONTACT.equals(cmd)) {
				var info = new JTextArea(Utility.getMessage(Resources.DROID64_MENU_HELP_CONTACT_MSG));
				info.setEditable(false);
				info.setLineWrap(true);
				var iPanel = new JPanel(new BorderLayout());
				iPanel.add(new JScrollPane(info), BorderLayout.CENTER);
				iPanel.addHierarchyListener(e -> GuiHelper.hierarchyListenerResizer(SwingUtilities.getWindowAncestor(iPanel)));
				GuiHelper.setSize(info, 3, 4);
				JOptionPane.showMessageDialog(parent, iPanel, Utility.getMessage(Resources.DROID64_MENU_HELP_CONTACT), JOptionPane.INFORMATION_MESSAGE);
			}
		};
		menu.add(GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_HELP_ABOUT, 'a', listener));
		menu.add(GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_HELP_TODO, 'b', listener));
		menu.add(GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_HELP_RELEASENOTES, 'r', listener));
		menu.add(GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_HELP_MANUAL, 'm', listener));
		menu.add(GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_HELP_CONTACT, 'c', listener));
		return menu;
	}

	/**
	 * Create a help drag-down menu (just for testing)
	 * @param parent
	 * @return JMenu
	 */
	private JMenu createProgramMenu(final JFrame parent) {
		var menu = new JMenu(Utility.getMessage(Resources.DROID64_MENU_PROGRAM));
		menu.setMnemonic('P');
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_PROGRAM_SETTINGS, 's', event -> showSettings(parent));
		menu.addSeparator();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_PROGRAM_CLEARCONSOLE, 'c', event -> consoleText.setText(Utility.EMPTY));
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_PROGRAM_SAVECONSOLE, 'a', event -> saveConsole());
		menu.addSeparator();
		GuiHelper.addMenuItem(menu, Resources.DROID64_MENU_PROGRAM_EXIT, 'x', event -> exitThisProgram());
		return menu;
	}

	private void saveConsole() {
		var active = getActiveDiskPanel();
		var fileName = FileDialogHelper.openTextFileDialog(active != null ? active.getCurrentImagePath() : null, "console.txt", true);
		if (fileName != null) {
			try {
				Utility.writeFile(new File(fileName), consoleText.getText());
			} catch (CbmException e) {	//NOSONAR
				appendConsole("Failed to save console.\n"+e.getMessage());
			}
		}
	}

	private void setupBookmarkBar(BookmarkTree bt) {
		bookmarkBar.removeAll();
		bt.getButtons().forEach(bookmarkBar::add);
		bookmarkBar.setVisible(Boolean.TRUE.equals(Setting.BOOKMARK_BAR.getBoolean()));
	}

	private JMenu createBookmarkMenu() {
		if (bookmarkMenu == null) {
			bookmarkMenu = new JMenu("Bookmarks");
			bookmarkMenu.setMnemonic('B');
		} else {
			bookmarkMenu.removeAll();
		}
		var manageBookmarks = new JMenuItem("Manage bookmarks...", 'M');
		var addBookmark = new JMenuItem("Add bookmark...", 'A');

		bookmarkMenu.add(manageBookmarks);
		bookmarkMenu.add(addBookmark);
		bookmarkMenu.addSeparator();

		var bookmarkFile = Setting.getBookmarkFile();
		appendConsole("Loading bookmarks from " + bookmarkFile.getAbsolutePath());

		var bt = new BookmarkTree(this, consoleStream);
		bt.load(bookmarkFile);
		bt.getMenuItems().forEach(bookmarkMenu::add);

		manageBookmarks.addActionListener(e -> {
			bt.showTree();
			createBookmarkMenu();
			});

		addBookmark.addActionListener(e -> {
			var dp = getActiveDiskPanel();
			var p = dp.getCurrentPath();
			if (!Utility.isEmpty(p)) {
				var f = new File(p);
				var name = GuiHelper.getStringDialog(getParent(), "Add bookmark", "Name of bookmark", f.getName());
				if (!Utility.isEmpty(name)) {
					var b = new Bookmark();
					b.setPath(f.getAbsolutePath());
					b.setName(name);
					b.setCreated(new Date());
					b.setBookmarkType(Setting.isImageFileName(f) ? BookmarkType.DISKIMAGE : BookmarkType.DIRECTORY);
					b.setSelectedNo(dp.getSelectedRow());
					b.setPluginNo(-1);
					b.setDiskImageType(dp.getDiskImageType());
					b.setZipped(dp.isZipFileLoaded());
					bt.addEntry(b, null);
					createBookmarkMenu();
				}
			}
		});

		setupBookmarkBar(bt);
		return bookmarkMenu;
	}

	/**
	 * Setup search menu. Requires database.
	 * @param parent
	 * @return JMenu
	 */
	private JMenu createSearchMenu(final JFrame parent) {
		var mainPanel = this;
		searchMenu.setMnemonic('S');

		var searchMenuItem = new JMenuItem("Search...", 's');
		searchMenu.add (searchMenuItem);
		var scanMenuItem = new JMenuItem("Scan for disk images...", 'i');
		searchMenu.add (scanMenuItem);
		var syncMenuItem = new JMenuItem("Sync database and files", 'y');
		searchMenu.add (syncMenuItem);
		var exportMenuItem = new JMenuItem("Export database to XML..", 'e');
		searchMenu.add (exportMenuItem);

		searchMenuItem.addActionListener(ae -> new SearchPanel(DroiD64.PROGNAME+" - Search", mainPanel).showDialog());
		scanMenuItem.addActionListener(ae -> showScanForImages(parent));
		syncMenuItem.addActionListener(ae -> syncDatabase());
		exportMenuItem.addActionListener(ae -> exportDatabase());

		searchMenu.setEnabled(Setting.USE_DB.getBoolean());
		searchMenu.setToolTipText(Boolean.TRUE.equals(Setting.USE_DB.getBoolean()) ? null : "You must configure and enable database to use search.");
		searchMenu.setVisible(Setting.USE_DB.getBoolean());
		return searchMenu;
	}

	/**
	 * Good bye?
	 */
	/** Native macOS app menu integration: Settings... (Cmd+,), About and Quit (Cmd+Q). */
	private void installMacIntegration(JFrame frame) {
		if (!NativeFileDialog.IS_MAC || !java.awt.Desktop.isDesktopSupported()) {
			return;
		}
		var desktop = java.awt.Desktop.getDesktop();
		try {
			if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
				desktop.setPreferencesHandler(e -> SwingUtilities.invokeLater(() -> showSettings(frame)));
			}
			if (desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)) {
				desktop.setAboutHandler(e -> SwingUtilities.invokeLater(this::showHelp));
			}
			if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
				desktop.setQuitHandler((e, response) -> {
					if (confirmQuit(frame)) {
						saveSessionState();
						response.performQuit();
					} else {
						response.cancelQuit();
					}
				});
			}
		} catch (UnsupportedOperationException | SecurityException ex) {
			appendConsole("macOS integration failed: " + ex);
		}
	}

	private void restoreDiskPanel(DiskPanel panel, Setting option, Setting lastPath, File defaultDir) {
		if (Boolean.TRUE.equals(option.getBoolean())) {
			var path = lastPath.getString();
			var file = path != null && !path.isEmpty() ? new File(path) : null;
			if (file != null && file.exists()) {
				panel.openDiskImage(file, true);
				return;
			}
			// the earlier location is gone: the root of the user's folder
			panel.loadLocalDirectory(new File(System.getProperty("user.home")));
			return;
		}
		panel.loadLocalDirectory(defaultDir);
	}

	void saveSettingsQuietly() {
		try {
			Setting.save();
		} catch (java.io.IOException | RuntimeException e) {
			appendConsole("Failed to save settings: " + e.getMessage());
		}
	}

	/** Remember where the two lists are, for the next start. */
	private void saveSessionState() {
		Setting.LAST_PATH1.set(diskPanel1.getCurrentLocation());
		Setting.LAST_PATH2.set(diskPanel2.getCurrentLocation());
		if (Boolean.TRUE.equals(Setting.REMEMBER_WINDOW.getBoolean()) && parent != null && parent.isShowing()) {
			var b = parent.getBounds();
			Setting.WINDOW_BOUNDS.set(b.x + "," + b.y + "," + b.width + "," + b.height + "|" + screenSignature());
		}
		saveSettingsQuietly();
	}

	/** Ask for quit confirmation, with a check box to switch the question on or off. */
	private boolean confirmQuit(java.awt.Component owner) {
		if (!Boolean.TRUE.equals(Setting.ASK_QUIT.getBoolean())) {
			return true;
		}
		javax.swing.JCheckBox askAgain = new javax.swing.JCheckBox("Ask before quitting", true);
		Object[] content = { "Really quit?", askAgain };
		boolean ok = JOptionPane.showConfirmDialog(owner, content, "Leaving this program...",
				JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
		if (ok && !askAgain.isSelected()) {
			Setting.ASK_QUIT.set(false);
			saveSettingsQuietly();
		}
		return ok;
	}

	private void exitThisProgram() {
		if (confirmQuit(parent)) {
			saveSessionState();
			parent.dispose();
			System.exit(0);	//NOSONAR
		}
	}

	/**
	 * Show, edit and save settings.
	 */
	private void showSettings(JFrame parent) {
		doSettings(parent);
		new SettingsPanel(DroiD64.PROGNAME+" - Settings", this).showDialog();
		doSettings(parent);
	}

	/**
	 * Recursively search a folder for D64 images.
	 */
	private synchronized void showScanForImages(JFrame parent) {
		if (scannerActive) {
			GuiHelper.showErrorMessage(parent, "Scan failed", "Disk scanner is already active.");
		} else {
			File dir = chooseScanDirectory();
			if (dir != null) {
				var excludePattern = Utility.isEmpty(Setting.EXCLUDED_IMAGE_FILES.getString()) ? null : Pattern.compile(Setting.EXCLUDED_IMAGE_FILES.getString());
				appendConsole("Scan for disk images in "+dir);
				var scanner = new Thread() {
					@Override
					public void run() {
						try {
							int numDisks = scanForD64Files(dir, excludePattern);
							GuiHelper.showInfoMessage(parent, "Scan completed", "Done scanning.%nFound %d disk images in %s.",numDisks, dir );
						} finally {
							scannerActive = false;
						}
					}
				};
				scannerActive = true;
				scanner.start();
			}
		}
	}

	private File chooseScanDirectory() {
		if (NativeFileDialog.IS_MAC) {
			return NativeFileDialog.choose(parent, "Choose directory to recursively scan for images",
					Setting.DEFAULT_IMAGE_DIR.getFile(), null, false, true, null);
		}
		var chooser = new JFileChooser(Setting.DEFAULT_IMAGE_DIR.getFile());
		chooser.setToolTipText("Select directory to start scanning for disk images in.");
		chooser.setDialogTitle("Choose directory to recursively scan for images");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	/**
	 * Get all stored disks from database, and verify that files still exists in file system.
	 */
	private void syncDatabase() {
		try {
			var myHostName = Utility.getHostName();
			var map = DaoFactory.getDaoFactory().getDiskDao().getAllDisks(false)
				.filter(d -> d.getHostName() == null || d.getHostName().equals(myHostName))
				.map(disk -> {
					// map from disk to boolean which is true if it was deleted
					var f = new File(disk.getFilePath() + File.separator + disk.getFileName());
					if (!f.exists() || !f.isFile()) {
						try {
							appendConsole("Removing info for " + f.getPath());
							disk.setDelete();
							DaoFactory.getDaoFactory().getDiskDao().delete(disk);
							return Boolean.TRUE;
						} catch (DatabaseException ignored) { /* ignored */ }
					}
					return Boolean.FALSE;
				})
				.collect(Collectors.groupingBy(b -> b, Collectors.counting()));
			GuiHelper.showInfoMessage(parent, "Sync completed", "Sync done.%nRemoved %d of %d disk(s) from database.",
					Optional.ofNullable(map.get(true)).orElse(0L), Optional.ofNullable(map.get(false)).orElse(0L));
		} catch (DatabaseException e) {	//NOSONAR
			GuiHelper.showException(parent, "Sync failed", e, "Sync failed.");
		}
	}

	private File chooseXmlExportFile() {
		if (NativeFileDialog.IS_MAC) {
			return NativeFileDialog.choose(parent, "Export database to XML", Setting.DEFAULT_IMAGE_DIR.getFile(), "droid64.xml", true, false, null);
		}
		var chooser = new JFileChooser(Setting.DEFAULT_IMAGE_DIR.getFile());
		chooser.setToolTipText("Select XML file.");
		chooser.setDialogTitle("Export database to XML");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileFilter(new FileNameExtensionFilter("XML file", "xml"));
		return chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	private void exportDatabase() {
		try {
			File f = chooseXmlExportFile();
			if (f != null) {
				appendConsole("Exporting database to " + f);
				var count = DiskList.export(f);
				appendConsole("Exported database with " + count + " disk images.");
			}
		} catch (DatabaseException | OutOfMemoryError e) {	//NOSONAR
			GuiHelper.showException(parent, "Export failed", e, "Export failed.");
		}
	}

	/**
	 * Apply settings to GUI
	 */
	private void doSettings(JFrame parent ) {
		setLookAndFeel(parent, Setting.LOOK_AND_FEEL.getString());
		searchMenu.setEnabled(Setting.USE_DB.getBoolean());
		searchMenu.setVisible(Setting.USE_DB.getBoolean());
		setDefaultFonts();
		consoleText.setFont(Setting.CONSOLE_FONT.getFont());
		bookmarkBar.setVisible(Boolean.TRUE.equals(Setting.BOOKMARK_BAR.getBoolean()));
		hideConsole(Setting.HIDECONSOLE.getBoolean());

		if (Boolean.TRUE.equals(Setting.USE_DB.getBoolean())) {
			try {
				DaoFactoryImpl.initialize(Setting.JDBC_DRIVER.getString(), Setting.JDBC_URL.getString(),
						Setting.JDBC_USER.getString(), Setting.JDBC_PASS.getString(),
						Setting.MAX_ROWS.getInteger(), Setting.JDBC_LIMIT_TYPE.getInteger());
			} catch (DatabaseException e) {	//NOSONAR
				appendConsole("Load settings failed: "+e.getMessage());
			}
		}
	}

	/** Re-apply fonts, row heights and column widths of both lists (used for live settings preview). */
	public void refreshLists() {
		setDefaultFonts();
		if (diskPanel1 != null) {
			diskPanel1.refreshLayout();
		}
		if (diskPanel2 != null) {
			diskPanel2.refreshLayout();
		}
	}

	private void setDefaultFonts() {
		GuiHelper.setDefaultFonts();
		if (diskPanel1 != null) {
			diskPanel1.setTableColors();
		}
		if (diskPanel2 != null) {
			diskPanel2.setTableColors();
		}
	}

	private void showHelp() {
		new ShowHelpPanel().showDialog(parent);
	}

	private void setLookAndFeel(JFrame parent, String lookAndFeel){

		final var wantedLaf = NativeFileDialog.IS_MAC && Setting.DEFAULT_LOOK_AND_FEEL_CLASS.equals(Setting.LOOK_AND_FEEL.getString())
				? UIManager.getSystemLookAndFeelClassName() : Setting.LOOK_AND_FEEL.getString();
		GuiHelper.getLookAndFeels().stream()
			.filter(f->f.getClassName().equals(wantedLaf))
			.findFirst()
			.map(a->a.getClassName()).ifPresent(plaf->{
				try {
					UIManager.setLookAndFeel(plaf);
				} catch (InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException | ClassNotFoundException e) {
					appendConsole("Look and feel failed: "+e);
				}
			});

		try {
			if (System.getProperty("os.name", "").startsWith("Mac") && java.awt.Taskbar.isTaskbarSupported()) {
				// set the Dock icon explicitly, so a stale icon cache of the Dock cannot show an old icon while the program runs
				var dockUrl = getClass().getResource("/dockicon.png");
				if (dockUrl != null && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
					java.awt.Taskbar.getTaskbar().setIconImage(new ImageIcon(dockUrl).getImage());
				}
			}
			// on macOS the icon of the application bundle (.icns) is used; a frame icon would replace the Dock icon while running
			var iconURL = System.getProperty("os.name", "").startsWith("Mac") ? null : getClass().getResource("/favicon.png");
			if (iconURL != null) {
				var icon = new ImageIcon(iconURL);
				parent.setIconImage(icon.getImage());
			}
		} catch (Exception e) {
			appendConsole("Icon image failed: "+e);
		}
		for (var entry : colorHashMap.entrySet()) {
			if (!(entry.getValue() instanceof javax.swing.plaf.ColorUIResource)) {
				continue;
			}
			var key = entry.getKey();
			var cr = (ColorUIResource) entry.getValue();
			switch (Setting.COLOUR.getInteger()) {
			case 0:	// gray (normal, no change to default values)
				UIManager.put(key, cr);
				break;
			case 1:		// red
				putColor(key, cr.getRed()+COLOUR_POWER_1, cr.getGreen()-COLOUR_POWER_1, cr.getBlue()-COLOUR_POWER_1);
				break;
			case 2:		// green
				putColor(key, cr.getRed()-COLOUR_POWER_1, cr.getGreen()+COLOUR_POWER_1, cr.getBlue()-COLOUR_POWER_1);
				break;
			case 3:		// blue
				putColor(key, cr.getRed()-COLOUR_POWER_1, cr.getGreen()-COLOUR_POWER_1, cr.getBlue()+COLOUR_POWER_1);
				break;
			case 4:		// gray-light
				putColor(key, cr.getRed()+COLOUR_POWER_2, cr.getGreen()+COLOUR_POWER_2, cr.getBlue()+COLOUR_POWER_2 + 10);
				break;
			case 5:		// gray-light
				putColor(key, cr.getRed()-COLOUR_POWER_2, cr.getGreen()-COLOUR_POWER_2, cr.getBlue()-COLOUR_POWER_2 + 10);
				break;
			case 6:		// cyan
				putColor(key, cr.getRed()-COLOUR_POWER_1, cr.getGreen()+COLOUR_POWER_2, cr.getBlue()+COLOUR_POWER_2 + 10);
				break;
			default:	// Unknown
				break;
			}
		}
		setDefaultFonts();
		SwingUtilities.updateComponentTreeUI(parent);
		parent.invalidate();
		parent.repaint();
	}

	private void putColor(Object key, int red, int green, int blue) {
		UIManager.put(key, new ColorUIResource(Utility.trimIntByte(red), Utility.trimIntByte(green), Utility.trimIntByte(blue)));
	}

	private void saveDefaultValues(){
		colorHashMap.clear();
		UIManager.getDefaults().entrySet().stream()
			.filter(e -> e.getValue() instanceof javax.swing.plaf.ColorUIResource)
			.forEach(entry -> colorHashMap.put(entry.getKey(), entry.getValue()));
	}

	public DiskPanel getLeftDiskPanel() {
		return this.diskPanel1;
	}
	public DiskPanel getRightDiskPanel() {
		return this.diskPanel2;
	}

	public JFrame getParent() {
		return parent;
	}

	/** A text describing the screens, to detect a changed resolution or screen setup. */
	private static String screenSignature() {
		var sb = new StringBuilder();
		for (var device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			var b = device.getDefaultConfiguration().getBounds();
			sb.append(b.x).append(' ').append(b.y).append(' ').append(b.width).append(' ').append(b.height).append(';');
		}
		return sb.toString();
	}

	/** Restore the saved window position and size, if enabled and the screens are the same as when saved. */
	private boolean restoreWindowBounds(JFrame frame) {
		if (!Boolean.TRUE.equals(Setting.REMEMBER_WINDOW.getBoolean())) {
			return false;
		}
		try {
			var saved = Setting.WINDOW_BOUNDS.getString();
			if (saved == null || !saved.contains("|")) {
				return false;
			}
			var parts = saved.split("\\|", 2);
			if (!parts[1].equals(screenSignature())) {
				return false;
			}
			var v = parts[0].split(",");
			var bounds = new java.awt.Rectangle(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()),
					Integer.parseInt(v[2].trim()), Integer.parseInt(v[3].trim()));
			if (bounds.width < 400 || bounds.height < 300) {
				return false;
			}
			for (var device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
				if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
					frame.setBounds(bounds);
					return true;
				}
			}
		} catch (RuntimeException e) {
			appendConsole("Could not restore window position: " + e);
		}
		return false;
	}

	private void setMainWindowSize(JFrame frame) {
		frame.pack();
		frame.setMinimumSize(new Dimension(64, 64));
		var usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		if (restoreWindowBounds(frame)) {
			return;
		}
		var splits = Setting.getWindow();
		if (splits.length < 4) {
			frame.setSize(Math.min(Math.max(frame.getWidth(), 1000), usable.width),
					Math.min(Math.max(frame.getHeight(), 700), usable.height));
			GuiHelper.setLocation(frame, 4, 4);
		} else {
			// never start smaller than the default height, so both file lists and the console are readable
			frame.setSize(splits[0], Math.min(Math.max(splits[1], 700), usable.height));
			frame.setLocation(splits[2], splits[3]);
		}
	}

	/**
	 * Recursively scan dir for D64 images and add to database.
	 * @param dir directory to start searching in.
	 * @param excludePattern the regexp
	 * @return number of found disk images
	 */
	protected int scanForD64Files(File dir, Pattern excludePattern) {
		if (dir == null || dir.getName().startsWith(".")) {
			return 0;
		}

		appendConsole("Scanning " + dir);
		int diskCount = 0;
		try (var stream = Files.newDirectoryStream(dir.toPath(), p -> !p.getFileName().startsWith("."))) {
			Stream.Builder<Path> builder = Stream.builder();
			stream.forEach(builder::add);
			var files = builder.build().sorted().map(Path::toFile).collect(Collectors.toList());
			for (var file : files) {
				if (file.isDirectory()) {
					diskCount += scanForD64Files(file, excludePattern);
				} else if (file.isFile() && Setting.getDiskImageType(file) != DiskImageType.UNDEFINED
						&& (excludePattern == null || !excludePattern.matcher(file.getAbsolutePath()).matches())) {
					saveDiskToDatabase(file, dir);
					diskCount++;
				}
			}
			return diskCount;
		} catch (IOException e) {
			appendConsole("Error: " + e.getMessage());
			return diskCount;
		}
	}

	private void saveDiskToDatabase(File file, File dir) {
		try {
			var diskImage =  DiskImage.getDiskImage(file, consoleStream);
			diskImage.readBAM();
			diskImage.readDirectory();
			var disk = diskImage.getDisk();
			disk.setFilePath(dir.getAbsolutePath());
			disk.setFileName(file.getName());
			disk.setHostName(Utility.getHostName());
			DaoFactory.getDaoFactory().getDiskDao().save(disk);
			appendConsole("Saved info for " + file);
		} catch (DatabaseException | CbmException e) {	//NOSONAR
			appendConsole(file +" : "+e.getMessage());
		}
	}

	public void setPluginButtonLabel(int num, String label) {
		if (num < pluginButtons.length && pluginButtons[num] != null) {
			pluginButtons[num].setText("d64copy".equals(label) ? "OpenCBM" : label);
		}
	}

	protected static String getReleaseNotes() {
		if (releaseNotes == null) {
			releaseNotes = getResource("resources/releasenotes.html");
		}
		return releaseNotes;
	}

	protected static String getManual() {
		if (manual == null) {
			manual = getResource("resources/manual.html");
		}
		return manual;
	}

	private static String getResource(String resourceFile) {
		try (var in = Setting.class.getResourceAsStream(resourceFile); var scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
			return scanner.useDelimiter("\\Z").next();
		} catch (Exception e) {	//NOSONAR
			return "Failed to read " + resourceFile + " resource: \n"+e.getMessage();
		}
	}

	public void setConsoleFont(Font font) {
		consoleText.setFont(font);
	}

	/**
	 * Parse the DroiD64 command line.
	 *
	 * Currently not much, but it accepts two optional paths and attempts to
	 * open the first in the left panel and the second in the right.
	 * @param args the arguments
	 */
	public void parseCommandLine(String[] args) {
		for (int i=0; args != null && i < args.length; i++) {
			if (Utility.isEmpty(args[i])) {
				continue;
			}
			var f = new File(args[i]);
			if (f.isDirectory() || f.isFile()) {
				switch (i) {
				case 0:
					getLeftDiskPanel().openDiskImage(f, true);
					break;
				case 1:
					getRightDiskPanel().openDiskImage(f, true);
					break;
				default:
					// ignored
				}
			}
		}
	}
}
