package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import droid64.net.FtpClient;

/** FTP browser panel (used in the FTP window and inside the left or right list): connect to a server, browse, download (and open), upload, create folders, rename and delete. */
public final class FtpPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final transient MainPanel mainPanel;
	private final JTextField host = new JTextField(18);
	private final JTextField port = new JTextField(4);
	private final JTextField user = new JTextField(10);
	private final JPasswordField password = new JPasswordField(10);
	private final JButton connectButton = new JButton("Connect");
	private final JTextField localFolder = new JTextField(30);
	private final javax.swing.JComboBox<String> sitesBox = new javax.swing.JComboBox<>();
	private final transient List<String[]> sites = new ArrayList<>();	// name, host, port, user, remote dir
	private final javax.swing.JCheckBox savePassword = new javax.swing.JCheckBox("with password");
	private boolean fillingSites;
	private String startRemoteDir = "";
	private final JLabel pathLabel = new JLabel(" ");
	private final JTextArea log = new JTextArea(5, 60);
	private final JProgressBar bar = new JProgressBar(0, 1000);
	private final transient List<FtpClient.Entry> entries = new ArrayList<>();
	private final JTable table;
	private final JButton[] actions;
	private final transient ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		var t = new Thread(r, "ftp");
		t.setDaemon(true);
		return t;
	});
	private transient FtpClient client;
	private volatile boolean cancelled;
	private String remotePath = "/";

	private final transient Runnable closeAction;
	private final transient java.util.function.Supplier<DiskPanel> openTarget;

	/**
	 * @param closeAction what the Close button does
	 * @param openTarget  the list where "Download and Open" opens the image
	 */
	public FtpPanel(MainPanel mainPanel, Runnable closeAction, java.util.function.Supplier<DiskPanel> openTarget) {
		this.mainPanel = mainPanel;
		this.closeAction = closeAction;
		this.openTarget = openTarget;

		host.setText(Setting.FTP_HOST.getString());
		port.setText(Setting.FTP_PORT.getString());
		user.setText(Setting.FTP_USER.getString());

		var top = new JPanel(new GridBagLayout());
		var c = new GridBagConstraints();
		c.insets = new Insets(2, 3, 2, 3);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;
		c.weightx = 0;
		top.add(new JLabel("Host"), c);
		c.weightx = 1;
		top.add(host, c);
		c.weightx = 0;
		top.add(new JLabel("Port"), c);
		top.add(port, c);
		c.gridy = 1;
		top.add(new JLabel("User"), c);
		c.weightx = 1;
		top.add(user, c);
		c.weightx = 0;
		top.add(new JLabel("Password"), c);
		top.add(password, c);
		c.gridy = 0;
		c.gridheight = 2;
		c.gridx = 4;
		top.add(connectButton, c);
		host.setColumns(8);
		user.setColumns(6);
		password.setColumns(6);

		localFolder.setText(Setting.FTP_LOCAL_DIR.getString());
		var browse = new JButton("Browse...");
		browse.addActionListener(e -> {
			var f = FolderChooserDialog.choose(this, "Folder for downloads", new File(localFolder.getText().trim()));
			if (f != null) {
				localFolder.setText(f.getPath());
				Setting.FTP_LOCAL_DIR.set(f.getPath());
			}
		});
		var saveSite = new JButton("Save site...");
		saveSite.addActionListener(e -> saveSite());
		savePassword.setToolTipText("Store the password with the site. It is only hidden in the settings file, not encrypted.");
		var deleteSite = new JButton("Delete site");
		deleteSite.addActionListener(e -> deleteSite());
		var row2 = new JPanel(new GridBagLayout());
		var sitesRow = new JPanel(new BorderLayout(4, 0));
		sitesRow.add(new JLabel("Sites"), BorderLayout.WEST);
		sitesRow.add(sitesBox, BorderLayout.CENTER);
		var siteButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		siteButtons.add(saveSite);
		siteButtons.add(savePassword);
		siteButtons.add(deleteSite);
		var localRow = new JPanel(new BorderLayout(4, 0));
		localRow.add(new JLabel("Local folder"), BorderLayout.WEST);
		localFolder.setColumns(8);
		localRow.add(localFolder, BorderLayout.CENTER);
		localRow.add(browse, BorderLayout.EAST);
		var rc = new GridBagConstraints();
		rc.gridx = 0;
		rc.weightx = 1;
		rc.fill = GridBagConstraints.HORIZONTAL;
		rc.gridy = 0;
		row2.add(sitesRow, rc);
		rc.gridy = 1;
		row2.add(siteButtons, rc);
		rc.gridy = 2;
		row2.add(localRow, rc);
		sitesBox.addActionListener(e -> siteSelected());
		loadSites();
		var north = new JPanel(new BorderLayout());
		north.add(top, BorderLayout.NORTH);
		north.add(row2, BorderLayout.SOUTH);

		table = new JTable(new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			private final String[] cols = { "Name", "Size", "Date" };

			@Override
			public int getRowCount() {
				return entries.size();
			}

			@Override
			public int getColumnCount() {
				return cols.length;
			}

			@Override
			public String getColumnName(int col) {
				return cols[col];
			}

			@Override
			public Object getValueAt(int row, int col) {
				var e = entries.get(row);
				switch (col) {
				case 0:
					return e.dir ? "[" + e.name + "]" : e.name;
				case 1:
					return e.dir ? "<DIR>" : Long.toString(e.size);
				default:
					return e.date;
				}
			}
		});
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.getColumnModel().getColumn(0).setPreferredWidth(360);
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
					var sel = entries.get(table.getSelectedRow());
					if (sel.dir) {
						changeDir(sel.name);
					} else {
						download(true);
					}
				}
			}
		});

		var center = new JPanel(new BorderLayout(3, 3));
		center.add(pathLabel, BorderLayout.NORTH);
		center.add(new JScrollPane(table), BorderLayout.CENTER);

		var buttons = new JPanel(new java.awt.GridLayout(0, 5, 3, 3));
		actions = new JButton[] {
				button(buttons, "Up", e -> changeDir("..")),
				button(buttons, "Refresh", e -> refresh()),
				button(buttons, "Download", e -> download(false)),
				button(buttons, "Download && Open", e -> download(true)),
				button(buttons, "Upload...", e -> upload()),
				button(buttons, "New Folder", e -> newFolder()),
				button(buttons, "Rename", e -> rename()),
				button(buttons, "Delete", e -> delete()) };
		var closeButton = new JButton("Close");
		closeButton.addActionListener(e -> closeAction.run());
		buttons.add(closeButton);

		log.setEditable(false);
		log.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
		var south = new JPanel(new BorderLayout(3, 3));
		var cancelButton = new JButton("Cancel transfer");
		cancelButton.addActionListener(e -> cancelled = true);
		var barPanel = new JPanel(new BorderLayout(3, 3));
		barPanel.add(bar, BorderLayout.CENTER);
		barPanel.add(cancelButton, BorderLayout.EAST);
		bar.setStringPainted(true);
		bar.setString("");
		south.add(buttons, BorderLayout.NORTH);
		south.add(barPanel, BorderLayout.CENTER);
		south.add(new JScrollPane(log), BorderLayout.SOUTH);
		center.add(south, BorderLayout.SOUTH);

		var content = new JPanel(new BorderLayout(4, 4));
		content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		content.add(north, BorderLayout.NORTH);
		content.add(center, BorderLayout.CENTER);
		setLayout(new BorderLayout());
		add(content, BorderLayout.CENTER);
		setPreferredSize(new Dimension(860, 600));

		connectButton.addActionListener(e -> {
			if (client != null) {
				disconnect();
			} else {
				connect();
			}
		});
		host.addActionListener(e -> connectButton.doClick());
		password.addActionListener(e -> connectButton.doClick());
		enableActions(false);
	}

	/** Close the connection and stop the worker. */
	public void shutdown() {
		disconnect();
		worker.shutdown();
	}

	/** Fill the form from the saved site with this name and connect. */
	public void connectSite(String name) {
		for (int i = 0; i < sites.size(); i++) {
			if (sites.get(i)[FtpSites.NAME].equalsIgnoreCase(name)) {
				sitesBox.setSelectedIndex(i + 1);
				if (client != null) {
					disconnect();
				}
				connect();
				return;
			}
		}
	}

	private JButton button(JPanel p, String label, java.awt.event.ActionListener l) {
		var b = new JButton(label.replace("&&", "&"));
		b.addActionListener(l);
		p.add(b);
		return b;
	}

	private void enableActions(boolean on) {
		for (var b : actions) {
			b.setEnabled(on);
		}
	}

	private void say(String s) {
		SwingUtilities.invokeLater(() -> {
			log.append(s + "\n");
			log.setCaretPosition(log.getDocument().getLength());
		});
	}

	private interface Job {
		void run() throws IOException;
	}

	private void background(String what, Job job) {
		enableActions(false);
		cancelled = false;
		worker.execute(() -> {
			try {
				job.run();
			} catch (IOException | RuntimeException e) {
				say(what + " failed: " + e.getMessage());
				if (client != null && !client.isConnected()) {
					SwingUtilities.invokeLater(this::disconnect);
				}
			} finally {
				SwingUtilities.invokeLater(() -> {
					bar.setValue(0);
					bar.setString("");
					enableActions(client != null);
				});
			}
		});
	}

	private void connect() {
		String h = host.getText().trim();
		if (h.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Enter the host name or address.");
			return;
		}
		int p = 21;
		try {
			p = Integer.parseInt(port.getText().trim());
		} catch (NumberFormatException e) {
			port.setText("21");
		}
		Setting.FTP_HOST.set(h);
		Setting.FTP_PORT.set(Integer.toString(p));
		Setting.FTP_USER.set(user.getText().trim());
		final int fp = p;
		final String fu = user.getText().trim().isEmpty() ? "anonymous" : user.getText().trim();
		final String pw = new String(password.getPassword());
		connectButton.setEnabled(false);
		say("Connecting to " + h + ":" + fp + " ...");
		worker.execute(() -> {
			var cl = new FtpClient();
			try {
				cl.connect(h, fp);
				say(cl.getLastReply());
				cl.login(fu, pw.isEmpty() && fu.equals("anonymous") ? "droid64@localhost" : pw);
				if (!startRemoteDir.isEmpty()) {
					try {
						cl.cwd(startRemoteDir);
					} catch (IOException e) {
						say("Folder " + startRemoteDir + " not found: " + e.getMessage());
					}
				}
				remotePath = cl.pwd();
				var list = cl.list();
				SwingUtilities.invokeLater(() -> {
					client = cl;
					connectButton.setText("Disconnect");
					setEntries(list);
					enableActions(true);
				});
				say("Connected.");
			} catch (IOException | RuntimeException e) {
				say("Connect failed: " + e.getMessage());
				cl.close();
			} finally {
				SwingUtilities.invokeLater(() -> connectButton.setEnabled(true));
			}
		});
	}

	private void disconnect() {
		var cl = client;
		client = null;
		connectButton.setText("Connect");
		enableActions(false);
		if (cl != null) {
			worker.execute(cl::close);
			say("Disconnected.");
		}
		entries.clear();
		((AbstractTableModel) table.getModel()).fireTableDataChanged();
		pathLabel.setText(" ");
	}

	private void setEntries(List<FtpClient.Entry> list) {
		entries.clear();
		entries.addAll(list);
		((AbstractTableModel) table.getModel()).fireTableDataChanged();
		pathLabel.setText("Remote: " + remotePath);
	}

	private void refresh() {
		var cl = client;
		background("List", () -> {
			var list = cl.list();
			remotePath = cl.pwd();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}

	private void changeDir(String name) {
		var cl = client;
		background("Change folder", () -> {
			if (name.equals("..")) {
				cl.cdup();
			} else {
				cl.cwd(name);
			}
			remotePath = cl.pwd();
			var list = cl.list();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}

	private List<FtpClient.Entry> selected(boolean filesOnly) {
		var out = new ArrayList<FtpClient.Entry>();
		for (int r : table.getSelectedRows()) {
			var e = entries.get(table.convertRowIndexToModel(r));
			if (!filesOnly || !e.dir) {
				out.add(e);
			}
		}
		return out;
	}

	private File localDir() {
		var dir = new File(localFolder.getText().trim());
		if (!localFolder.getText().trim().isEmpty() && dir.isDirectory()) {
			Setting.FTP_LOCAL_DIR.set(dir.getPath());
			return dir;
		}
		var f = FolderChooserDialog.choose(this, "Folder for downloads", dir);
		if (f != null) {
			localFolder.setText(f.getPath());
			Setting.FTP_LOCAL_DIR.set(f.getPath());
		}
		return f;
	}

	// ---- saved sites ----

	private static String clean(String v) {
		return FtpSites.clean(v);
	}

	private void loadSites() {
		sites.clear();
		sites.addAll(FtpSites.load());
		refillSites(null);
	}

	private void refillSites(String select) {
		fillingSites = true;
		sitesBox.removeAllItems();
		sitesBox.addItem("(saved sites)");
		for (var s : sites) {
			sitesBox.addItem(s[0]);
		}
		if (select != null) {
			sitesBox.setSelectedItem(select);
		}
		fillingSites = false;
	}

	private void storeSites() {
		FtpSites.store(sites);
		try {
			mainPanel.saveSettingsQuietly();
		} catch (RuntimeException e) {	//NOSONAR
			say("Could not save the settings: " + e.getMessage());
		}
	}

	private void siteSelected() {
		int i = sitesBox.getSelectedIndex() - 1;
		if (fillingSites || i < 0 || i >= sites.size()) {
			return;
		}
		var s = sites.get(i);
		host.setText(s[1]);
		port.setText(s[2]);
		user.setText(s[3]);
		startRemoteDir = s[4];
		if (!s[5].isEmpty()) {
			localFolder.setText(s[5]);
		}
		password.setText(FtpSites.password(s));
		say("Site \"" + s[0] + "\" selected." + (password.getPassword().length == 0 ? " Enter the password and press Connect." : " Press Connect."));
	}

	private void saveSite() {
		var def = sitesBox.getSelectedIndex() > 0 ? (String) sitesBox.getSelectedItem() : host.getText().trim();
		var name = clean(JOptionPane.showInputDialog(this, "Name of the site:", def));
		if (name.isEmpty()) {
			return;
		}
		var rec = new String[] { name, clean(host.getText()), clean(port.getText().isEmpty() ? "21" : port.getText()),
				clean(user.getText()), clean(client != null ? remotePath : startRemoteDir), clean(localFolder.getText()),
				savePassword.isSelected() ? FtpSites.encode(new String(password.getPassword())) : "" };
		boolean replaced = false;
		for (int i = 0; i < sites.size(); i++) {
			if (sites.get(i)[0].equalsIgnoreCase(name)) {
				sites.set(i, rec);
				replaced = true;
			}
		}
		if (!replaced) {
			sites.add(rec);
		}
		storeSites();
		refillSites(name);
		say("Site \"" + name + "\" saved.");
	}

	private void deleteSite() {
		int i = sitesBox.getSelectedIndex() - 1;
		if (i < 0 || i >= sites.size()) {
			return;
		}
		if (JOptionPane.showConfirmDialog(this, "Delete the site \"" + sites.get(i)[0] + "\"?", "FTP", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			sites.remove(i);
			storeSites();
			refillSites(null);
		}
	}

	private void download(boolean open) {
		var files = selected(true);
		if (files.isEmpty()) {
			say("Select a file first.");
			return;
		}
		var dir = localDir();
		if (dir == null) {
			return;
		}
		var cl = client;
		background("Download", () -> {
			File last = null;
			for (var e : files) {
				var target = new File(dir, e.name.replaceAll("[\\\\/:]", "_"));
				if (target.exists() && !confirm("Overwrite " + target.getName() + "?")) {
					continue;
				}
				say("Downloading " + e.name + " ...");
				try (var os = new FileOutputStream(target)) {
					cl.retrieve(e.name, os, progress(e.size));
				}
				say("Saved " + target + " (" + target.length() + " bytes)");
				last = target;
			}
			final File opened = last;
			if (open && opened != null) {
				SwingUtilities.invokeLater(() -> {
					var dp = openTarget.get();
					if (dp != null) {
						dp.openDiskImage(opened, true);
					}
				});
			}
		});
	}

	private boolean confirm(String question) {
		var answer = new boolean[1];
		try {
			SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showConfirmDialog(this, question, "FTP", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
		} catch (Exception e) {	//NOSONAR
			Thread.currentThread().interrupt();
		}
		return answer[0];
	}

	private FtpClient.Progress progress(long total) {
		return new FtpClient.Progress() {
			@Override
			public void bytes(long done) {
				int v = total > 0 ? (int) Math.min(1000, done * 1000 / total) : 0;
				SwingUtilities.invokeLater(() -> {
					bar.setValue(v);
					bar.setString(done / 1024 + " KB" + (total > 0 ? " / " + total / 1024 + " KB" : ""));
				});
			}

			@Override
			public boolean cancelled() {
				return cancelled;
			}
		};
	}

	private void upload() {
		var ch = new JFileChooser(localFolder.getText().trim().isEmpty() ? null : new File(localFolder.getText().trim()));
		ch.setMultiSelectionEnabled(true);
		ch.setDialogTitle("Files to upload");
		if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		var files = ch.getSelectedFiles();
		var cl = client;
		background("Upload", () -> {
			for (var f : files) {
				if (!f.isFile()) {
					continue;
				}
				say("Uploading " + f.getName() + " ...");
				try (var is = new FileInputStream(f)) {
					cl.store(f.getName(), is, progress(f.length()));
				}
				say("Uploaded " + f.getName());
			}
			var list = cl.list();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}

	private void newFolder() {
		var name = JOptionPane.showInputDialog(this, "Name of the new folder:");
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		var cl = client;
		background("Make folder", () -> {
			cl.mkdir(name.trim());
			var list = cl.list();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}

	private void rename() {
		var sel = selected(false);
		if (sel.size() != 1) {
			say("Select one entry to rename.");
			return;
		}
		var from = sel.get(0).name;
		var to = JOptionPane.showInputDialog(this, "New name:", from);
		if (to == null || to.trim().isEmpty() || to.equals(from)) {
			return;
		}
		var cl = client;
		background("Rename", () -> {
			cl.rename(from, to.trim());
			var list = cl.list();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}

	private void delete() {
		var sel = selected(false);
		if (sel.isEmpty()) {
			return;
		}
		if (JOptionPane.showConfirmDialog(this, "Delete " + sel.size() + " selected item(s) on the server?", "FTP", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		var cl = client;
		background("Delete", () -> {
			for (var e : sel) {
				cl.delete(e.name, e.dir);
				say("Deleted " + e.name);
			}
			var list = cl.list();
			SwingUtilities.invokeLater(() -> setEntries(list));
		});
	}
}
