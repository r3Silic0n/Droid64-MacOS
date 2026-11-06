package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import droid64.d64.Utility;
import droid64.db.DaoFactory;
import droid64.db.DaoFactoryImpl;

/**
 * <pre style='font-family:Sans,Arial,Helvetica'>
 * Created on 30.06.2004
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
 * </pre>
 *
 * @author wolf
 */
public class SettingsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final String LBL_FOREGROUND = Utility.getMessage(Resources.DROID64_SETTINGS_FOREGROUND);
	private static final String LBL_BACKGROUND = Utility.getMessage(Resources.DROID64_SETTINGS_BACKGROUND);
	private static final String BROWSELABEL = "...";

	// GUI
	private final JCheckBox exitConfirmCheckBox = new JCheckBox(
			Utility.getMessage(Resources.DROID64_SETTINGS_CONFIRMEXIT));
	private final JCheckBox rememberWindowCheckBox = new JCheckBox(
			Utility.getMessage(Resources.DROID64_SETTINGS_REMEMBERWINDOW));
	private final JCheckBox hideConsoleCheckBox = new JCheckBox(
			Utility.getMessage(Resources.DROID64_SETTINGS_HIDECONSOLE));
	private final JCheckBox bookmarkBarCheckBox = new JCheckBox(
			Utility.getMessage(Resources.DROID64_SETTINGS_BOOKMARKBAR));
	private final JComboBox<UIManager.LookAndFeelInfo> lookAndFeelBox = new JComboBox<>(GuiHelper.getLookAndFeels().toArray(new UIManager.LookAndFeelInfo[0]));
	private final JSpinner rowHeightSpinner = new JSpinner(
			new SpinnerNumberModel((int) Setting.ROW_HEIGHT.getInteger(), 4, 256, 1));
	private final JSpinner localRowHeightSpinner = new JSpinner(
			new SpinnerNumberModel((int) Setting.LOCAL_ROW_HEIGHT.getInteger(), 8, 256, 1));

	// Files
	private final JTextField extRemoval = new JTextField(
			Setting.EXT_REMOVAL.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD64 = new JTextField(
			Setting.FILE_EXT_D64.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD67 = new JTextField(
			Setting.FILE_EXT_D67.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD71 = new JTextField(
			Setting.FILE_EXT_D71.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD80 = new JTextField(
			Setting.FILE_EXT_D80.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD81 = new JTextField(
			Setting.FILE_EXT_D81.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD82 = new JTextField(
			Setting.FILE_EXT_D82.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD88 = new JTextField(
			Setting.FILE_EXT_D88.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtLNX = new JTextField(
			Setting.FILE_EXT_LNX.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtT64 = new JTextField(
			Setting.FILE_EXT_T64.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD64gz = new JTextField(
			Setting.FILE_EXT_D64_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD67gz = new JTextField(
			Setting.FILE_EXT_D67_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD71gz = new JTextField(
			Setting.FILE_EXT_D71_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD80gz = new JTextField(
			Setting.FILE_EXT_D80_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD81gz = new JTextField(
			Setting.FILE_EXT_D81_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD82gz = new JTextField(
			Setting.FILE_EXT_D82_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtD88gz = new JTextField(
			Setting.FILE_EXT_D88_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtLNXgz = new JTextField(
			Setting.FILE_EXT_LNX_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	private final JTextField fileExtT64gz = new JTextField(
			Setting.FILE_EXT_T64_GZ.getList().stream().collect(Collectors.joining(Setting.DELIM)));
	// Colors
	private final JComboBox<String> colourBox = new JComboBox<>(COLORS);

	private Font cbmFont = Setting.CBM_FONT.getFont();
	private Font sysFont = Setting.SYS_FONT.getFont();
	private Font consoleFont = Setting.CONSOLE_FONT.getFont();


	// Database settings
	private final JCheckBox useJdbcCheckBox = new JCheckBox(Utility.getMessage(Resources.DROID64_SETTINGS_JDBC_USEDB));
	private final JTextField jdbcDriver = new JTextField(Setting.JDBC_DRIVER.getString());
	private final JTextField jdbcUrl = new JTextField(Setting.JDBC_URL.getString());
	private final JTextField jdbcUser = new JTextField(Setting.JDBC_USER.getString());
	private final JPasswordField jdbcPassword = new JPasswordField(Setting.JDBC_PASS.getString());
	private final JFormattedTextField maxRows = SearchPanel.getNumericField(Setting.MAX_ROWS.getInteger(), 8);
	private final JComboBox<String> limitTypeBox = new JComboBox<>(DaoFactory.getLimitNames());
	private final List<String> jdbcDriverClasses = GuiHelper.getClassNames(java.sql.Driver.class);
	private final JTextField excludeImageFiles = new JTextField(Setting.EXCLUDED_IMAGE_FILES.getString());

	/** Colors */
	private static final String[] COLORS = { "gray", "red", "green", "blue", "light-blue", "dark-grey", "cyan" };

	private final JTextArea status = new JTextArea();

	private static final String ARGUMENT_TOOLTIP = Utility.getMessage(Resources.PLUGIN_ARGUMENTS_TOOLTIP);

	private static String sqlSetupScript = null;
	private final MainPanel mainPanel;
	private final String title;

	/**
	 * Constructor
	 *
	 * @param title     String
	 * @param mainPanel MainPanel
	 */
	public SettingsPanel(String title, MainPanel mainPanel) {
		this.title = title;
		this.mainPanel = mainPanel;
			
		lookAndFeelBox.setRenderer(new BasicComboBoxRenderer() {

			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				var text = Optional.ofNullable(value)
						.filter(UIManager.LookAndFeelInfo.class::isInstance)
						.map(UIManager.LookAndFeelInfo.class::cast)
						.map(UIManager.LookAndFeelInfo::getName)
						.orElse(String.valueOf(value));
				setText(text);
				return this;
			}
		});

		setLayout(new BorderLayout());
		var tabPane = new JTabbedPane();
		tabPane.addTab(Utility.getMessage(Resources.DROID64_SETTINGS_TAB_GUI), new JScrollPane(drawGuiPanel()));
		tabPane.addTab(Utility.getMessage(Resources.DROID64_SETTINGS_TAB_VICE), new JScrollPane(drawVicePanel()));
		tabPane.addTab(Utility.getMessage(Resources.DROID64_SETTINGS_TAB_FILES), new JScrollPane(drawFilesPanel()));
		tabPane.addTab(Utility.getMessage(Resources.DROID64_SETTINGS_TAB_DATABASE), new JScrollPane(drawDatabasePanel()));

		add(tabPane, BorderLayout.CENTER);

		add(drawGeneralPanel(), BorderLayout.SOUTH);
		java.awt.event.ActionListener viewSwitch = e -> mainPanel.applyViewSwitches(hideConsoleCheckBox.isSelected(),
				bookmarkBarCheckBox.isSelected());
		hideConsoleCheckBox.addActionListener(viewSwitch);
		bookmarkBarCheckBox.addActionListener(viewSwitch);
		addHierarchyListener(e -> GuiHelper.hierarchyListenerResizer(SwingUtilities.getWindowAncestor(this)));
	}

	public void showDialog() {
		JOptionPane.showMessageDialog(mainPanel.getParent(), this, title, JOptionPane.PLAIN_MESSAGE);
		copyValuesToSettings(mainPanel);
		try {
			Setting.save();
		} catch (IOException e) {
			mainPanel.appendConsole(e.getMessage());
		}
	}

	/**
	 * Setup up general settings panel
	 *
	 * @return JPanel
	 */
	private JPanel drawGeneralPanel() {
		var txtPanel = new JPanel();
		txtPanel.setAlignmentX(CENTER_ALIGNMENT);
		txtPanel.add(new JLabel(Utility.getMessage(Resources.DROID64_SETTINGS_SAVEMESSAGE)));
		var generalPanel = new JPanel(new BorderLayout());
		generalPanel.add(txtPanel, BorderLayout.NORTH);
		return generalPanel;
	}
	
	/** One row of the "Other emulators" list: name, program, arguments and a remove button. Saved as you type. */
	private JPanel emulatorRow(JPanel rowsPanel, int index, java.util.Set<Integer> usedSlots, JButton addButton) {
		final int slot = Setting.MAX_PLUGINS + index;
		var prg = Setting.getExternalProgram(slot);
		var nameField = new JTextField(prg != null && prg.getLabel() != null ? prg.getLabel() : "", 12);
		nameField.setToolTipText("Name shown in the Plugins menu");
		var argsField = new JTextField(prg != null && prg.getArguments() != null ? prg.getArguments() : "{Image}", 18);
		argsField.setToolTipText(ARGUMENT_TOOLTIP);
		var pathPanel = new FilePathPanel(new File(prg != null && prg.getCommand() != null ? prg.getCommand() : ""),
				JFileChooser.FILES_ONLY, null);
		pathPanel.setToolTipText("Path to the emulator, or to its .app bundle");
		final boolean[] removed = { false };
		Runnable store = () -> {
			if (removed[0]) {
				return;
			}
			var cmd = pathPanel.getPath();
			var label = nameField.getText().trim();
			Setting.setExternalProgram(slot, new ExternalProgram(cmd == null ? "" : cmd, argsField.getText(), label, label, true));
		};
		pathPanel.addTextListener(text -> store.run());
		javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent ev) { store.run(); }
			public void removeUpdate(javax.swing.event.DocumentEvent ev) { store.run(); }
			public void changedUpdate(javax.swing.event.DocumentEvent ev) { store.run(); }
		};
		nameField.getDocument().addDocumentListener(dl);
		argsField.getDocument().addDocumentListener(dl);
		var remove = new JButton("Remove");
		var row = new JPanel(new BorderLayout(4, 0));
		remove.addActionListener(ev -> {
			removed[0] = true;
			Setting.setExternalProgram(slot, new ExternalProgram("", "", "", "", true));
			usedSlots.remove(index);
			rowsPanel.remove(row);
			addButton.setEnabled(true);
			rowsPanel.revalidate();
			rowsPanel.repaint();
		});
		var left = new JPanel(new BorderLayout(4, 0));
		left.add(nameField, BorderLayout.WEST);
		left.add(pathPanel, BorderLayout.CENTER);
		var right = new JPanel(new BorderLayout(4, 0));
		right.add(argsField, BorderLayout.CENTER);
		right.add(remove, BorderLayout.EAST);
		row.add(left, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return row;
	}

	/** Paths to the VICE emulators started by the VICE buttons. */
	private JPanel drawVicePanel() {
		var panel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new java.awt.Insets(3, 4, 3, 4);
		System.setProperty("apple.awt.use-file-dialog-packages", "true");
		var names = new String[] { "VICE 64 (x64sc)", "VICE 128 (x128)", "VICE Plus/4 (xplus4)", "VICE VC20 (xvic)" };
		var settings = new Setting[] { Setting.VICE_X64, Setting.VICE_X128, Setting.VICE_XPLUS4, Setting.VICE_XVIC };
		for (int i = 0; i < names.length; i++) {
			final var setting = settings[i];
			var current = setting.getString();
			var fp = new FilePathPanel(new File(current == null ? "" : current), JFileChooser.FILES_ONLY,
					f -> setting.set(f.getPath()));
			fp.setToolTipText("Path to the emulator, or to its .app bundle. Leave empty to use the command from the PATH.");
			// typed or pasted paths count too
			fp.addTextListener(text -> setting.set(text));
			GuiHelper.addToGridBag(0, i, 0.0, 0.0, 1, gbc, panel, new JLabel(names[i]));
			GuiHelper.addToGridBag(1, i, 1.0, 0.0, 1, gbc, panel, fp);
		}
		// other emulators (right-click menu / Plugins)
		int extraRow = names.length + 20;
		var extraHeading = new JLabel("Other emulators: name, program, arguments (shown in the right-click menu, Plugins)");
		extraHeading.setFont(extraHeading.getFont().deriveFont(java.awt.Font.BOLD));
		GuiHelper.addToGridBag(0, extraRow++, 1.0, 0.0, 2, gbc, panel, new javax.swing.JSeparator());
		GuiHelper.addToGridBag(0, extraRow++, 1.0, 0.0, 2, gbc, panel, extraHeading);
		var rowsPanel = new JPanel();
		rowsPanel.setLayout(new javax.swing.BoxLayout(rowsPanel, javax.swing.BoxLayout.Y_AXIS));
		var usedSlots = new java.util.TreeSet<Integer>();
		var addButton = new JButton("Add emulator");
		for (int e = 0; e < Setting.EXTRA_EMULATORS; e++) {
			if (Setting.getExternalProgram(Setting.MAX_PLUGINS + e) != null) {
				usedSlots.add(e);
				rowsPanel.add(emulatorRow(rowsPanel, e, usedSlots, addButton));
			}
		}
		addButton.addActionListener(ev -> {
			for (int e = 0; e < Setting.EXTRA_EMULATORS; e++) {
				if (!usedSlots.contains(e)) {
					usedSlots.add(e);
					rowsPanel.add(emulatorRow(rowsPanel, e, usedSlots, addButton));
					addButton.setEnabled(usedSlots.size() < Setting.EXTRA_EMULATORS);
					rowsPanel.revalidate();
					rowsPanel.repaint();
					break;
				}
			}
		});
		if (usedSlots.isEmpty()) {
			addButton.doClick();
		}
		var rowsHolder = new JPanel(new BorderLayout());
		rowsHolder.add(rowsPanel, BorderLayout.NORTH);
		var rowsScroll = new JScrollPane(rowsHolder, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		rowsScroll.setPreferredSize(new Dimension(400, 104));
		rowsScroll.setBorder(BorderFactory.createEmptyBorder());
		GuiHelper.addToGridBag(0, extraRow, 1.0, 0.0, 2, gbc, panel, rowsScroll);
		extraRow++;
		var addPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		addPanel.add(addButton);
		GuiHelper.addToGridBag(0, extraRow, 1.0, 0.0, 2, gbc, panel, addPanel);
		// HVSC: the collection has to exist on this machine
		var hv = new FilePathPanel(new File(Setting.HVSC_DIR.getString() == null ? "" : Setting.HVSC_DIR.getString()),
				JFileChooser.DIRECTORIES_ONLY, f -> Setting.HVSC_DIR.set(f.getPath()));
		hv.setToolTipText("Folder of your local HVSC (High Voltage SID Collection): the one containing DOCUMENTS/Songlengths.md5, or the C64Music folder. "
				+ "The music player uses it to recognise SID files, to get the real tune lengths and the STIL information. Leave empty to switch it off.");
		hv.addTextListener(text -> Setting.HVSC_DIR.set(text));
		GuiHelper.addToGridBag(0, names.length, 0.0, 0.0, 1, gbc, panel, new JLabel("HVSC folder (C64Music)"));
		GuiHelper.addToGridBag(1, names.length, 1.0, 0.0, 1, gbc, panel, hv);
		// updates
		var mirrors = new JTextField(Setting.HVSC_MIRRORS.getString(), 40);
		mirrors.setToolTipText("Download folders of the HVSC (separated by ;). They must contain HVSC_Update_NN.7z and HVSC_NN-all-of-it.7z.");
		mirrors.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				Setting.HVSC_MIRRORS.set(mirrors.getText());
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				Setting.HVSC_MIRRORS.set(mirrors.getText());
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				Setting.HVSC_MIRRORS.set(mirrors.getText());
			}
		});
		GuiHelper.addToGridBag(0, names.length + 1, 0.0, 0.0, 1, gbc, panel, new JLabel("HVSC download mirrors"));
		GuiHelper.addToGridBag(1, names.length + 1, 1.0, 0.0, 1, gbc, panel, mirrors);
		var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		var hvscUpdate = new javax.swing.JButton("Update HVSC");
		hvscUpdate.addActionListener(e -> UpdateDialog.updateHvsc(this));
		var hvscFull = new javax.swing.JButton("Download full HVSC...");
		hvscFull.addActionListener(e -> UpdateDialog.downloadFullHvsc(this));
		var viceUpdate = new javax.swing.JButton("Install / update VICE");
		viceUpdate.addActionListener(e -> UpdateDialog.updateVice(this));
		buttons.add(hvscUpdate);
		buttons.add(hvscFull);
		buttons.add(viceUpdate);
		GuiHelper.addToGridBag(0, names.length + 2, 0.0, 0.0, 1, gbc, panel, new JLabel("Updates"));
		GuiHelper.addToGridBag(1, names.length + 2, 1.0, 0.0, 1, gbc, panel, buttons);
		var auto = new javax.swing.JCheckBox("Check for HVSC and VICE updates at startup (once a day)", Boolean.TRUE.equals(Setting.UPDATE_AUTO.getBoolean()));
		auto.addActionListener(e -> Setting.UPDATE_AUTO.set(auto.isSelected()));
		GuiHelper.addToGridBag(1, names.length + 3, 1.0, 0.0, 1, gbc, panel, auto);
		GuiHelper.addToGridBag(0, names.length + 4, 1.0, 0.8, 2, gbc, panel, new JPanel());
		return panel;
	}

	private JPanel drawFilesPanel() {
		var guiPanel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;

		var fp1 = new FilePathPanel(Setting.DEFAULT_IMAGE_DIR.getFile(), JFileChooser.DIRECTORIES_ONLY,
				Setting.DEFAULT_IMAGE_DIR::set);
		var fp2 = new FilePathPanel(Setting.DEFAULT_IMAGE_DIR2.getFile(), JFileChooser.DIRECTORIES_ONLY,
				Setting.DEFAULT_IMAGE_DIR2::set);

		GuiHelper.addToGridBag(0, 0, 0.0, 0.0, 1, gbc, guiPanel,
				new JLabel(Utility.getMessage(Resources.DROID64_SETTINGS_LEFTDIR)));
		GuiHelper.addToGridBag(1, 0, 1.0, 0.0, 2, gbc, guiPanel, fp1);

		GuiHelper.addToGridBag(0, 1, 0.0, 0.0, 1, gbc, guiPanel,
				new JLabel(Utility.getMessage(Resources.DROID64_SETTINGS_RIGHTDIR)));
		GuiHelper.addToGridBag(1, 1, 1.0, 0.0, 2, gbc, guiPanel, fp2);

		GuiHelper.addToGridBag(0, 2, 0.0, 0.0, 1, gbc, guiPanel,
				new JLabel(Utility.getMessage(Resources.DROID64_SETTINGS_EXT_REMOVAL)));
		GuiHelper.addToGridBag(1, 2, 1.0, 0.0, 1, gbc, guiPanel, extRemoval);
		GuiHelper.addToGridBag(2, 2, 1.0, 0.0, 1, gbc, guiPanel, new JPanel());

		addFields(3, Resources.DROID64_SETTINGS_EXT_D64, fileExtD64, fileExtD64gz, guiPanel, gbc);
		addFields(4, Resources.DROID64_SETTINGS_EXT_D67, fileExtD67, fileExtD67gz, guiPanel, gbc);
		addFields(5, Resources.DROID64_SETTINGS_EXT_D71, fileExtD71, fileExtD71gz, guiPanel, gbc);
		addFields(6, Resources.DROID64_SETTINGS_EXT_D80, fileExtD80, fileExtD80gz, guiPanel, gbc);
		addFields(7, Resources.DROID64_SETTINGS_EXT_D81, fileExtD81, fileExtD81gz, guiPanel, gbc);
		addFields(8, Resources.DROID64_SETTINGS_EXT_D82, fileExtD82, fileExtD82gz, guiPanel, gbc);
		addFields(9, Resources.DROID64_SETTINGS_EXT_D88, fileExtD88, fileExtD88gz, guiPanel, gbc);
		addFields(10, Resources.DROID64_SETTINGS_EXT_T64, fileExtT64, fileExtT64gz, guiPanel, gbc);
		addFields(11, Resources.DROID64_SETTINGS_EXT_LNX, fileExtLNX, fileExtLNXgz, guiPanel, gbc);

		GuiHelper.addToGridBag(0, 12, 1.0, 0.8, 3, gbc, guiPanel, new JPanel());
		return guiPanel;
	}

	private void addFields(int row, String propertyKey, JComponent field1, JComponent field2, JPanel panel,
			GridBagConstraints gbc) {
		GuiHelper.addToGridBag(0, row, 0.0, 0.0, 1, gbc, panel, new JLabel(Utility.getMessage(propertyKey)));
		GuiHelper.addToGridBag(1, row, 0.5, 0.0, 1, gbc, panel, field1);
		GuiHelper.addToGridBag(2, row, 0.5, 0.0, 1, gbc, panel, field2);
	}

	/**
	 * Setup panel with GUI settings
	 *
	 * @param mainPanel
	 * @return JPanel
	 */
	private JPanel drawGuiPanel() {
		var guiPanel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;

		exitConfirmCheckBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_CONFIRMEXIT_TOOLTIP));
		exitConfirmCheckBox.setSelected(Setting.ASK_QUIT.getBoolean());
		rememberWindowCheckBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_REMEMBERWINDOW_TOOLTIP));
		rememberWindowCheckBox.setSelected(Boolean.TRUE.equals(Setting.REMEMBER_WINDOW.getBoolean()));

		hideConsoleCheckBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_HIDECONSOLE_TOOLTIP));
		hideConsoleCheckBox.setSelected(Setting.HIDECONSOLE.getBoolean());

		bookmarkBarCheckBox.setSelected(Setting.BOOKMARK_BAR.getBoolean());

		lookAndFeelBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_LOOKFEEL_TOOLTIP));
		lookAndFeelBox.setEditable(false);
		lookAndFeelBox.setSelectedIndex(0);
		final var curLaf = NativeFileDialog.IS_MAC && Setting.DEFAULT_LOOK_AND_FEEL_CLASS.equals(Setting.LOOK_AND_FEEL.getString())
				? UIManager.getSystemLookAndFeelClassName() : Setting.LOOK_AND_FEEL.getString();
		var laf=GuiHelper.getLookAndFeels().stream().filter(f->f.getClassName().equals(curLaf)).findFirst().orElse(null);
		lookAndFeelBox.setSelectedItem(laf);
		lookAndFeelBox.addActionListener(e -> {
			var sel = lookAndFeelBox.getSelectedItem();
			if (sel instanceof UIManager.LookAndFeelInfo) {
				Setting.LOOK_AND_FEEL.set(((UIManager.LookAndFeelInfo) sel).getClassName());
				mainPanel.applyLookAndFeel();
				SwingUtilities.updateComponentTreeUI(this);
				var w = SwingUtilities.getWindowAncestor(this);
				if (w != null) {
					SwingUtilities.updateComponentTreeUI(w);
					w.pack();
				}
			}
		});

		rowHeightSpinner.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_GRIDSPACING_TOOLTIP));

		localRowHeightSpinner.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_GRIDSPACING_TOOLTIP));

		var fontSizeSpinner = new JSpinner(
				new SpinnerNumberModel(Setting.FONT_SIZE.getInteger().intValue(), 8, 144, 1));
		var localFontSizeSpinner = new JSpinner(
				new SpinnerNumberModel(Setting.LOCAL_FONT_SIZE.getInteger().intValue(), 8, 144, 1));
		fontSizeSpinner.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_FONTSIZE_TOOLTIP));
		localFontSizeSpinner.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_FONTSIZE_TOOLTIP));

		// Changes are applied immediately, no need to confirm the dialog.
		fontSizeSpinner.addChangeListener(event -> {
			int size = Integer.parseInt(fontSizeSpinner.getValue().toString());
			Setting.FONT_SIZE.set(size);
			// keep the C64 font cell square: row pitch = 25/32 of the font size
			rowHeightSpinner.setValue(Math.max(4, Math.round(size * 25f / 32f)));
			mainPanel.refreshLists();
		});
		localFontSizeSpinner.addChangeListener(event -> {
			int size = Integer.parseInt(localFontSizeSpinner.getValue().toString());
			Setting.LOCAL_FONT_SIZE.set(size);
			localRowHeightSpinner.setValue(Math.max(8, size + 6));
			mainPanel.refreshLists();
		});
		rowHeightSpinner.addChangeListener(event -> {
			Setting.ROW_HEIGHT.set(Integer.parseInt(rowHeightSpinner.getValue().toString()));
			mainPanel.refreshLists();
		});
		localRowHeightSpinner.addChangeListener(event -> {
			Setting.LOCAL_ROW_HEIGHT.set(Integer.parseInt(localRowHeightSpinner.getValue().toString()));
			mainPanel.refreshLists();
		});

		// system font
		var sysFontField = new JTextField(OldParameter.getFontAsDisplayString(sysFont));
		var sysBundledFont = new JCheckBox(Utility.EMPTY, sysFont == null);
		sysFontField.setEditable(false);
		var browseSysFontButton = new JButton(BROWSELABEL);
		browseSysFontButton.addActionListener(event -> {
			Font font = new FontChooser(mainPanel.getParent(), "Select system font").show(sysFont);
			if (font != null) {
				sysFont = font;
				sysFontField.setText(OldParameter.getFontAsDisplayString(font));
				sysBundledFont.setEnabled(false);
			}
		});
		sysBundledFont.addItemListener(event -> {
			if (sysBundledFont.isSelected()) {
				sysFont = null;
				sysFontField.setText(Utility.EMPTY);
			} else if (sysFont == null) {
				sysBundledFont.setSelected(true);
			}
		});

		// system font
		var consoleFontField = new JTextField(OldParameter.getFontAsDisplayString(consoleFont));
		var consoleBundledFont = new JCheckBox(Utility.EMPTY, consoleFont == null);
		consoleFontField.setEditable(false);
		var browseConsoleFontButton = new JButton(BROWSELABEL);
		browseConsoleFontButton.addActionListener(event -> {
			Font font = new FontChooser(mainPanel.getParent(), "Select console font").show(consoleFont);
			if (font != null) {
				consoleFont = font;
				consoleFontField.setText(OldParameter.getFontAsDisplayString(font));
				consoleBundledFont.setEnabled(false);
			}
		});
		consoleBundledFont.addItemListener(event -> {
			if (consoleBundledFont.isSelected()) {
				consoleFont = null;
				consoleFontField.setText(Utility.EMPTY);
			} else if (consoleFont == null) {
				consoleBundledFont.setSelected(true);
			}
		});

		// commodore font
		var cbmFontField = new JTextField(OldParameter.getFontAsDisplayString(cbmFont));
		var cbmBundledFont = new JCheckBox(Utility.EMPTY, cbmFont == null);
		cbmFontField.setEditable(false);
		var browseCbmFontButton = new JButton(BROWSELABEL);
		browseCbmFontButton.addActionListener(event -> {
			var font = new FontChooser(mainPanel.getParent(), "Select Commodore font").show(cbmFont);
			if (font != null) {
				cbmFont = font;
				cbmFontField.setText(OldParameter.getFontAsDisplayString(font));
				cbmBundledFont.setSelected(false);
			}
		});
		cbmBundledFont.addItemListener(event -> {
			if (cbmBundledFont.isSelected()) {
				cbmFont = null;
				cbmFontField.setText(Utility.EMPTY);
			} else if (cbmFont == null) {
				cbmBundledFont.setSelected(true);
			}
		});
		// font panels
		var sysFontPanelButtons = new JPanel(new BorderLayout());
		sysFontPanelButtons.add(sysBundledFont, BorderLayout.WEST);
		sysFontPanelButtons.add(browseSysFontButton, BorderLayout.EAST);
		var sysFontFilePanel = new JPanel(new BorderLayout());
		sysFontFilePanel.add(sysFontField, BorderLayout.CENTER);
		sysFontFilePanel.add(sysFontPanelButtons, BorderLayout.EAST);

		var consoleFontPanelButtons = new JPanel(new BorderLayout());
		consoleFontPanelButtons.add(consoleBundledFont, BorderLayout.WEST);
		consoleFontPanelButtons.add(browseConsoleFontButton, BorderLayout.EAST);
		var consoleFontFilePanel = new JPanel(new BorderLayout());
		consoleFontFilePanel.add(consoleFontField, BorderLayout.CENTER);
		consoleFontFilePanel.add(consoleFontPanelButtons, BorderLayout.EAST);

		var cbmFontPanelButtons = new JPanel(new BorderLayout());
		cbmFontPanelButtons.add(cbmBundledFont, BorderLayout.WEST);
		cbmFontPanelButtons.add(browseCbmFontButton, BorderLayout.EAST);
		var cbmFontFilePanel = new JPanel(new BorderLayout());
		cbmFontFilePanel.add(cbmFontField, BorderLayout.CENTER);
		cbmFontFilePanel.add(cbmFontPanelButtons, BorderLayout.EAST);

		var fontSizePanel1 = new JPanel(new BorderLayout());
		fontSizePanel1.add(fontSizeSpinner, BorderLayout.WEST);
		fontSizePanel1.add(new JPanel(), BorderLayout.CENTER);

		var fontSizePanel2 = new JPanel(new BorderLayout());
		fontSizePanel2.add(localFontSizeSpinner, BorderLayout.WEST);
		fontSizePanel2.add(new JPanel(), BorderLayout.CENTER);

		var checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checkboxPanel.add(exitConfirmCheckBox);
		checkboxPanel.add(rememberWindowCheckBox);
		checkboxPanel.add(hideConsoleCheckBox);
		checkboxPanel.add(bookmarkBarCheckBox);

		var localRowHeightPanel = new JPanel(new BorderLayout());
		localRowHeightPanel.add(rowHeightSpinner, BorderLayout.WEST);
		localRowHeightPanel.add(new JPanel(), BorderLayout.CENTER);
		var imageRowHeightPanel = new JPanel(new BorderLayout());
		imageRowHeightPanel.add(localRowHeightSpinner, BorderLayout.WEST);
		imageRowHeightPanel.add(new JPanel(), BorderLayout.CENTER);

		GuiHelper.addToGridBag(0, 0, 0.0, 0.0, 1, gbc, guiPanel, new JPanel());
		GuiHelper.addToGridBag(1, 0, 1.0, 0.0, 1, gbc, guiPanel, checkboxPanel);
		GuiHelper.addToGridBag(2, 0, 0.0, 0.0, 1, gbc, guiPanel, new JPanel());

		addField(1, Resources.DROID64_SETTINGS_LOOKFEEL, lookAndFeelBox, guiPanel, gbc);
		addField(2, Resources.DROID64_SETTINGS_GRIDSPACING_IMAGE, localRowHeightPanel, guiPanel, gbc);
		addField(3, Resources.DROID64_SETTINGS_GRIDSPACING_LOCAL, imageRowHeightPanel, guiPanel, gbc);
		addField(4, Resources.DROID64_SETTINGS_FONTSIZE_IMAGE, fontSizePanel1, guiPanel, gbc);
		addField(5, Resources.DROID64_SETTINGS_FONTSIZE_LOCAL, fontSizePanel2, guiPanel, gbc);
		addField(6, Resources.DROID64_SETTINGS_SYSFONT, sysFontFilePanel, guiPanel, gbc);
		addField(7, Resources.DROID64_SETTINGS_CBMFONT, cbmFontFilePanel, guiPanel, gbc);
		addField(8, Resources.DROID64_SETTINGS_CONSOLEFONT, consoleFontFilePanel, guiPanel, gbc);
		addColorSection(guiPanel, gbc, 9);
		return guiPanel;
	}

	private void addField(int row, String propertyKey, JComponent component, JPanel panel, GridBagConstraints gbc) {
		GuiHelper.addToGridBag(0, row, 0.0, 0.0, 1, gbc, panel, new JLabel(Utility.getMessage(propertyKey)));
		GuiHelper.addToGridBag(1, row, 1.0, 0.0, 1, gbc, panel, component);
		GuiHelper.addToGridBag(2, row, 0.0, 0.0, 1, gbc, panel, new JPanel());
	}

	private void copyValuesToSettings(final MainPanel mainPanel) {
		Setting.ASK_QUIT.set(exitConfirmCheckBox.isSelected());
		Setting.REMEMBER_WINDOW.set(rememberWindowCheckBox.isSelected());
		Setting.BOOKMARK_BAR.set(bookmarkBarCheckBox.isSelected());
		Setting.HIDECONSOLE.set(hideConsoleCheckBox.isSelected());
		Setting.ROW_HEIGHT.set(Integer.parseInt(rowHeightSpinner.getValue().toString()));
		Setting.LOOK_AND_FEEL.set(Optional.ofNullable(lookAndFeelBox.getSelectedItem())
				.map(UIManager.LookAndFeelInfo.class::cast)
				.map(UIManager.LookAndFeelInfo::getClassName)
				.orElse(Setting.DEFAULT_LOOK_AND_FEEL_CLASS));
		Setting.LOCAL_ROW_HEIGHT.set(Integer.parseInt(localRowHeightSpinner.getValue().toString()));
		Setting.USE_DB.set(useJdbcCheckBox.isSelected());
		Setting.JDBC_DRIVER.set(jdbcDriver.getText());
		Setting.JDBC_URL.set(jdbcUrl.getText());
		Setting.JDBC_USER.set(jdbcUser.getText());
		Setting.JDBC_PASS.set(String.valueOf(jdbcPassword.getPassword()));
		Setting.JDBC_LIMIT_TYPE.set(limitTypeBox.getSelectedIndex());
		Setting.EXCLUDED_IMAGE_FILES.set(excludeImageFiles.getText());
		Setting.SYS_FONT.set(sysFont);
		Setting.CBM_FONT.set(cbmFont);
		Setting.CONSOLE_FONT.set(consoleFont);


		Setting.EXT_REMOVAL.set(extRemoval.getText());
		Setting.FILE_EXT_D64.set(fileExtD64.getText());
		Setting.FILE_EXT_D67.set(fileExtD67.getText());
		Setting.FILE_EXT_D71.set(fileExtD71.getText());
		Setting.FILE_EXT_D80.set(fileExtD80.getText());
		Setting.FILE_EXT_D81.set(fileExtD81.getText());
		Setting.FILE_EXT_D82.set(fileExtD82.getText());
		Setting.FILE_EXT_D88.set(fileExtD88.getText());
		Setting.FILE_EXT_LNX.set(fileExtLNX.getText());
		Setting.FILE_EXT_T64.set(fileExtT64.getText());

		Setting.FILE_EXT_D64_GZ.set(fileExtD64gz.getText());
		Setting.FILE_EXT_D67_GZ.set(fileExtD67gz.getText());
		Setting.FILE_EXT_D71_GZ.set(fileExtD71gz.getText());
		Setting.FILE_EXT_D80_GZ.set(fileExtD80gz.getText());
		Setting.FILE_EXT_D81_GZ.set(fileExtD81gz.getText());
		Setting.FILE_EXT_D82_GZ.set(fileExtD82gz.getText());
		Setting.FILE_EXT_D88_GZ.set(fileExtD88gz.getText());
		Setting.FILE_EXT_LNX_GZ.set(fileExtLNXgz.getText());
		Setting.FILE_EXT_T64_GZ.set(fileExtT64gz.getText());

		try {
			Setting.MAX_ROWS.set(Integer.parseInt(maxRows.getText()));
		} catch (NumberFormatException e) {
			maxRows.setValue(25L);
		}
	}

	/** Colour swatch that is an ordinary button of the current look and feel, with the colour as its icon. */
	private static final class SwatchButton extends JButton {
		private static final long serialVersionUID = 1L;
		private Color color;

		SwatchButton(String text, Color color) {
			super(text);
			setColor(color);
		}

		Color getColor() {
			return color;
		}

		void setColor(Color c) {
			this.color = c;
			setIcon(new javax.swing.Icon() {
				@Override
				public void paintIcon(Component comp, java.awt.Graphics g, int x, int y) {
					g.setColor(color);
					g.fillRect(x, y, getIconWidth(), getIconHeight());
					g.setColor(Color.DARK_GRAY);
					g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
				}

				@Override
				public int getIconWidth() {
					return 26;
				}

				@Override
				public int getIconHeight() {
					return 14;
				}
			});
			repaint();
		}
	}

	private final java.util.List<Runnable> colorSwatchRefreshers = new java.util.ArrayList<>();

	/** A swatch that changes the setting at once when a colour is chosen. */
	private SwatchButton swatch(String text, Setting setting) {
		var b = new SwatchButton(text, setting.getColor());
		b.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
		b.addActionListener(ae -> {
			var c = JColorChooser.showDialog(b, text, b.getColor());
			if (c != null) {
				b.setColor(c);
				setting.set(c);
				mainPanel.refreshLists();
			}
		});
		colorSwatchRefreshers.add(() -> b.setColor(setting.getColor()));
		return b;
	}

	private void addColorPair(int row, String label, Setting fg, Setting bg, JPanel panel, GridBagConstraints gbc) {
		addColorPair(row, label, LBL_FOREGROUND, fg, LBL_BACKGROUND, bg, panel, gbc);
	}

	private void addColorPair(int row, String label, String text1, Setting s1, String text2, Setting s2, JPanel panel,
			GridBagConstraints gbc) {
		var pair = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		pair.add(swatch(text1, s1));
		pair.add(swatch(text2, s2));
		GuiHelper.addToGridBag(0, row, 0.0, 0.0, 1, gbc, panel, new JLabel(label));
		GuiHelper.addToGridBag(1, row, 1.0, 0.0, 1, gbc, panel, pair);
		GuiHelper.addToGridBag(2, row, 0.0, 0.0, 1, gbc, panel, new JPanel());
	}

	/**
	 * Colour settings below the look and feel settings. Every change is applied at once.
	 * @return the next free row
	 */
	private int addColorSection(JPanel panel, GridBagConstraints gbc, int startRow) {
		int row = startRow;
		colorSwatchRefreshers.clear();
		GuiHelper.addToGridBag(0, row++, 1.0, 0.0, 3, gbc, panel, new javax.swing.JSeparator());
		var heading = new JLabel(Utility.getMessage(Resources.DROID64_SETTINGS_TAB_COLORS));
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		GuiHelper.addToGridBag(0, row++, 1.0, 0.0, 3, gbc, panel, heading);

		colourBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_SCHEME_TOOLTIP));
		colourBox.setEditable(false);
		colourBox.setSelectedIndex(Setting.COLOUR.getInteger() < COLORS.length ? Setting.COLOUR.getInteger() : 0);
		colourBox.addActionListener(ae -> {
			Setting.COLOUR.set(colourBox.getSelectedIndex());
			mainPanel.applyLookAndFeel();
			SwingUtilities.updateComponentTreeUI(this);
		});
		addField(row++, Resources.DROID64_SETTINGS_COLOR_THEME, colourBox, panel, gbc);

		addColorPair(row++, Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_CBM) + " (C64)", Setting.DIR_FG, Setting.DIR_BG, panel, gbc);
		for (var m : new Object[][] {
				{ "Commodore 128 (40)", Setting.MACHINE_C128_FG, Setting.MACHINE_C128_BG },
				{ "Commodore 128 (80)", Setting.MACHINE_C128VDC_FG, Setting.MACHINE_C128VDC_BG },
				{ "VIC-20", Setting.MACHINE_VIC20_FG, Setting.MACHINE_VIC20_BG },
				{ "Plus/4, C16", Setting.MACHINE_PLUS4_FG, Setting.MACHINE_PLUS4_BG },
				{ "PET / CBM", Setting.MACHINE_PET_FG, Setting.MACHINE_PET_BG } }) {
			addColorPair(row++, (String) m[0], (Setting) m[1], (Setting) m[2], panel, gbc);
		}
		addColorPair(row++, Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_CPM), Setting.DIR_CPM_FG, Setting.DIR_CPM_BG, panel, gbc);
		addColorPair(row++, Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_LOCAL), Setting.DIR_LOCAL_FG, Setting.DIR_LOCAL_BG, panel, gbc);
		addColorPair(row++, Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_BORDER),
				Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_ACTIVE), Setting.BORDER_ACTIVE,
				Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_INACTIVE), Setting.BORDER_INACTIVE, panel, gbc);

		var colorResetButton = new JButton(Utility.getMessage(Resources.DROID64_SETTINGS_COLOR_RESET));
		colorResetButton.addActionListener(ae -> {
			for (var st : new Setting[] { Setting.DIR_BG, Setting.DIR_FG, Setting.DIR_LOCAL_BG, Setting.DIR_LOCAL_FG,
					Setting.DIR_CPM_BG, Setting.DIR_CPM_FG, Setting.BORDER_ACTIVE, Setting.BORDER_INACTIVE,
					Setting.MACHINE_C128_BG, Setting.MACHINE_C128_FG, Setting.MACHINE_C128VDC_BG, Setting.MACHINE_C128VDC_FG,
					Setting.MACHINE_VIC20_BG, Setting.MACHINE_VIC20_FG, Setting.MACHINE_PLUS4_BG, Setting.MACHINE_PLUS4_FG,
					Setting.MACHINE_PET_BG, Setting.MACHINE_PET_FG }) {
				st.reset();
			}
			colorSwatchRefreshers.forEach(Runnable::run);
			mainPanel.refreshLists();
		});
		GuiHelper.addToGridBag(1, row++, 0.0, 0.0, 1, gbc, panel, colorResetButton);
		GuiHelper.addToGridBag(0, row, 0.5, 0.8, 3, gbc, panel, new JPanel());
		return row + 1;
	}

	/**
	 * Create the panel with database settings.
	 *
	 * @return JPanel
	 */
	private JPanel drawDatabasePanel() {
		var dbPanel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;

		useJdbcCheckBox.setSelected(Setting.USE_DB.getBoolean());
		var testConnectionButton = new JButton(Utility.getMessage(Resources.DROID64_SETTINGS_JDBC_TEST));
		status.setFont(new Font("Verdana", Font.PLAIN, status.getFont().getSize()));
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setEditable(false);
		useJdbcCheckBox.addActionListener(ae -> {
			boolean enabled = useJdbcCheckBox.isSelected();
			jdbcDriver.setEnabled(enabled);
			jdbcUrl.setEnabled(enabled);
			jdbcUser.setEnabled(enabled);
			jdbcPassword.setEnabled(enabled);
			maxRows.setEnabled(enabled);
			testConnectionButton.setEnabled(enabled);
		});

		testConnectionButton.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_JDBC_TEST_TOOLTIP));
		testConnectionButton.addActionListener(ae -> {
			var errStr = DaoFactoryImpl.testConnection(jdbcDriver.getText(), jdbcUrl.getText(), jdbcUser.getText(),
					new String(jdbcPassword.getPassword()));
			status.setText(errStr == null ? Utility.getMessage(Resources.DROID64_SETTINGS_OK) : errStr);
		});

		boolean jdbcEnabled = useJdbcCheckBox.isSelected();
		jdbcDriver.setEnabled(jdbcEnabled);
		jdbcUrl.setEnabled(jdbcEnabled);
		jdbcUser.setEnabled(jdbcEnabled);
		jdbcPassword.setEnabled(jdbcEnabled);
		maxRows.setEnabled(jdbcEnabled);
		testConnectionButton.setEnabled(jdbcEnabled);

		limitTypeBox.setToolTipText(Utility.getMessage(Resources.DROID64_SETTINGS_JDBC_LIMIT_TOOLTIP));
		limitTypeBox.setEditable(false);
		limitTypeBox.setSelectedIndex(0);
		limitTypeBox.setSelectedIndex(Setting.JDBC_LIMIT_TYPE.getInteger() < DaoFactory.getLimitNames().length
				? Setting.JDBC_LIMIT_TYPE.getInteger()
				: 0);

		excludeImageFiles.setToolTipText(Utility.getResource(Resources.DROID64_SETTINGS_EXCLUDE_IMAGE_FILES_TOOLTIP));

		var jdbcDriverBrowse = new JButton(BROWSELABEL);
		jdbcDriverBrowse.setEnabled(!jdbcDriverClasses.isEmpty());
		jdbcDriverBrowse.addActionListener(e -> browseJdbcDrivers());

		if (jdbcDriver.getText().isEmpty() && jdbcDriverClasses.size() == 1) {
			jdbcDriver.setText(jdbcDriverClasses.get(0));
		}

		var sqlString = Utility.getMessage("jdbc.feature.message", Utility.getMessage(Resources.JDBC_MYSQL_URL),
				Utility.getMessage(Resources.JDBC_POSTGRESQL_URL));
		var sql = getSqlSetupScript(mainPanel);
		var viewSqlButton = new JButton(Utility.getMessage(Resources.DROID64_SETTINGS_JDBC_SQL));
		viewSqlButton.addActionListener(
				ae -> new TextViewPanel(mainPanel).show(sql, "DroiD64", "SQL database setup", Utility.MIMETYPE_TEXT));

		var messageTextArea = new JTextArea(10, 45);
		messageTextArea.setBackground(new Color(230, 230, 230));
		messageTextArea.setEditable(false);
		messageTextArea.setWrapStyleWord(true);
		messageTextArea.setLineWrap(true);
		messageTextArea.setText(sqlString);
		messageTextArea.setCaretPosition(0);

		var buttonPanel = new JPanel();
		buttonPanel.add(viewSqlButton);
		buttonPanel.add(testConnectionButton);
		buttonPanel.add(new JPanel());

		GuiHelper.addToGridBag(0, 0, 0.0, 0.0, 1, gbc, dbPanel, new JPanel());
		GuiHelper.addToGridBag(1, 0, 0.5, 0.0, 1, gbc, dbPanel, useJdbcCheckBox);
		GuiHelper.addToGridBag(2, 0, 0.0, 0.0, 1, gbc, dbPanel, new JPanel());

		var p = new JPanel(new BorderLayout());
		p.add(jdbcDriver, BorderLayout.CENTER);
		p.add(jdbcDriverBrowse, BorderLayout.EAST);

		addField(1, Resources.DROID64_SETTINGS_JDBC_CLASS, p, dbPanel, gbc);
		addField(2, Resources.DROID64_SETTINGS_JDBC_URL, jdbcUrl, dbPanel, gbc);
		addField(3, Resources.DROID64_SETTINGS_JDBC_USER, jdbcUser, dbPanel, gbc);
		addField(4, Resources.DROID64_SETTINGS_JDBC_PASS, jdbcPassword, dbPanel, gbc);
		addField(5, Resources.DROID64_SETTINGS_JDBC_ROWS, maxRows, dbPanel, gbc);
		addField(6, Resources.DROID64_SETTINGS_JDBC_LIMIT, limitTypeBox, dbPanel, gbc);

		addField(7, Resources.DROID64_SETTINGS_EXCLUDE_IMAGE_FILES, excludeImageFiles, dbPanel, gbc);

		GuiHelper.addToGridBag(0, 8, 0.0, 0.0, 1, gbc, dbPanel, new JPanel());
		GuiHelper.addToGridBag(1, 8, 0.5, 0.0, 2, gbc, dbPanel, buttonPanel);

		addField(9, Resources.DROID64_SETTINGS_JDBC_STATUS, status, dbPanel, gbc);

		GuiHelper.addToGridBag(0, 10, 0.0, 0.0, 1, gbc, dbPanel, new JPanel());
		gbc.fill = GridBagConstraints.BOTH;
		GuiHelper.addToGridBag(1, 10, 0.5, 0.9, 1, gbc, dbPanel, new JScrollPane(messageTextArea));

		return dbPanel;
	}

	private void browseJdbcDrivers() {
		var classModel = new DefaultListModel<String>();
		jdbcDriverClasses.forEach(classModel::addElement);
		var classList = new JList<String>(classModel);
		classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		classList.setSelectedValue(Setting.JDBC_DRIVER.getString(), true);
		if (JOptionPane.OK_OPTION == JOptionPane.showConfirmDialog(this, new JScrollPane(classList),
				"Choose JDBC driver", JOptionPane.OK_CANCEL_OPTION)) {
			var drv = classList.getSelectedValue();
			if (drv != null) {
				jdbcDriver.setText(drv);
			}
		}
	}


	private JTextArea getTextArea(String toolTip) {
		var area = new JTextArea(4, 20);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setToolTipText(toolTip);
		if (!NativeFileDialog.IS_MAC) {
			area.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		}
		area.setText(Utility.EMPTY);
		return area;
	}

	private JTextField getTextField(String toolTipPropertyKey) {
		var field = new JTextField();
		field.setToolTipText(Utility.getMessage(toolTipPropertyKey));
		if (!NativeFileDialog.IS_MAC) {
			field.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		}
		field.setText(Utility.EMPTY);
		return field;
	}

	private static String getSqlSetupScript(final MainPanel mainPanel) {
		if (sqlSetupScript == null) {
			try (var in = SettingsPanel.class.getResourceAsStream("/setup_database.sql");
					var input = new BufferedReader(new InputStreamReader(in))) {
				var buf = new StringBuilder();
				for (var line = input.readLine(); line != null; line = input.readLine()) {
					buf.append(line);
					buf.append('\n');
				}
				sqlSetupScript = buf.toString();
			} catch (IOException e) { // NOSONAR
				mainPanel.appendConsole("Failed to find SQL setup script.\n" + e.getMessage());
			}
		}
		return sqlSetupScript;
	}
}
