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
import javax.swing.JDialog;
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

/** FTP browser: connect to a server, browse, download (and open), upload, create folders, rename and delete. */
public final class FtpDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final transient MainPanel mainPanel;
	private final JTextField host = new JTextField(18);
	private final JTextField port = new JTextField(4);
	private final JTextField user = new JTextField(10);
	private final JPasswordField password = new JPasswordField(10);
	private final JButton connectButton = new JButton("Connect");
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

	private static FtpDialog instance;

	/** Show the dialog (only one at a time). */
	public static void show(MainPanel mainPanel) {
		if (instance == null || !instance.isDisplayable()) {
			instance = new FtpDialog(mainPanel);
		}
		instance.setVisible(true);
		instance.toFront();
	}

	private FtpDialog(MainPanel mainPanel) {
		super(mainPanel.getParent(), "FTP Connect", ModalityType.MODELESS);
		this.mainPanel = mainPanel;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		host.setText(Setting.FTP_HOST.getString());
		port.setText(Setting.FTP_PORT.getString());
		user.setText(Setting.FTP_USER.getString());

		var top = new JPanel(new GridBagLayout());
		var c = new GridBagConstraints();
		c.insets = new Insets(2, 3, 2, 3);
		c.gridy = 0;
		top.add(new JLabel("Host"), c);
		top.add(host, c);
		top.add(new JLabel("Port"), c);
		top.add(port, c);
		top.add(new JLabel("User"), c);
		top.add(user, c);
		top.add(new JLabel("Password"), c);
		top.add(password, c);
		top.add(connectButton, c);

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

		var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
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
		closeButton.addActionListener(e -> dispose());
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
		content.add(top, BorderLayout.NORTH);
		content.add(center, BorderLayout.CENTER);
		setContentPane(content);
		setPreferredSize(new Dimension(860, 600));
		pack();
		setLocationRelativeTo(mainPanel.getParent());

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
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				disconnect();
				worker.shutdown();
			}
		});
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
		var dir = Setting.FTP_LOCAL_DIR.getString();
		if (dir != null && !dir.isEmpty() && new File(dir).isDirectory()) {
			return new File(dir);
		}
		var ch = new JFileChooser();
		ch.setDialogTitle("Folder for downloads");
		ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			Setting.FTP_LOCAL_DIR.set(ch.getSelectedFile().getPath());
			return ch.getSelectedFile();
		}
		return null;
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
					var dp = mainPanel.getActiveDiskPanel();
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
		var ch = new JFileChooser(Setting.FTP_LOCAL_DIR.getString().isEmpty() ? null : new File(Setting.FTP_LOCAL_DIR.getString()));
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
