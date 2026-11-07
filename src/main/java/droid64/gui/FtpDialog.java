package droid64.gui;

import java.awt.Component;
import java.awt.GridLayout;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

/** The FTP window ("lister window") and the entry point that opens FTP where the Settings say. */
public final class FtpDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private static FtpDialog instance;
	private final transient FtpPanel panel;

	/**
	 * Open the FTP connection as set in Settings / FTP.
	 * In the own window mode only the list of the saved addresses is shown first.
	 */
	public static void open(MainPanel mainPanel, Component anchor) {
		var where = FtpSites.openIn();
		final var list = !"window".equals(where);
		final var target = list ? mainPanel.getFtpTargetPanel(where) : null;
		var sites = FtpSites.load();
		if (sites.isEmpty() || anchor == null) {
			newConnection(mainPanel, target);
			return;
		}
		var menu = new JPopupMenu("FTP");
		for (var s : sites) {
			var item = new JMenuItem(s[FtpSites.NAME] + "   (" + s[FtpSites.USER] + "@" + s[FtpSites.HOST] + ")");
			item.addActionListener(e -> {
				if (target != null) {
					target.openFtp(s);
				} else {
					show(mainPanel, s[FtpSites.NAME]);
				}
			});
			menu.add(item);
		}
		menu.addSeparator();
		var other = new JMenuItem("New connection...");
		other.addActionListener(e -> newConnection(mainPanel, target));
		menu.add(other);
		menu.show(anchor, 0, anchor.getHeight());
	}

	/** A connection that is not saved yet: in the FTP window, or (target given) as a folder in a list. */
	private static void newConnection(MainPanel mainPanel, DiskPanel target) {
		if (target == null) {
			show(mainPanel, null);
			return;
		}
		var name = new JTextField(Setting.FTP_HOST.getString(), 18);
		var port = new JTextField(Setting.FTP_PORT.getString(), 5);
		var user = new JTextField(Setting.FTP_USER.getString(), 12);
		var pass = new JPasswordField(12);
		var save = new JTextField(12);
		var form = new JPanel(new GridLayout(0, 2, 4, 4));
		form.add(new JLabel("Host"));
		form.add(name);
		form.add(new JLabel("Port"));
		form.add(port);
		form.add(new JLabel("User"));
		form.add(user);
		form.add(new JLabel("Password"));
		form.add(pass);
		form.add(new JLabel("Save as site (optional name)"));
		form.add(save);
		if (JOptionPane.showConfirmDialog(mainPanel.getParent(), form, "FTP connection", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || name.getText().trim().isEmpty()) {
			return;
		}
		var pw = new String(pass.getPassword());
		var site = FtpSites.newSite(save.getText().trim().isEmpty() ? name.getText() : save.getText(), name.getText(), port.getText().trim(),
				user.getText(), pw, "", Setting.FTP_LOCAL_DIR.getString());
		Setting.FTP_HOST.set(site[FtpSites.HOST]);
		Setting.FTP_USER.set(site[FtpSites.USER]);
		if (!save.getText().trim().isEmpty()) {
			var all = FtpSites.load();
			all.removeIf(o -> o[FtpSites.NAME].equalsIgnoreCase(site[FtpSites.NAME]));
			all.add(site);
			FtpSites.store(all);
			mainPanel.saveSettingsQuietly();
		}
		target.openFtp(site);
	}

	/** Show the FTP window, and connect to the saved site if a name is given. */
	public static void show(MainPanel mainPanel, String siteName) {
		if (instance == null || !instance.isDisplayable()) {
			instance = new FtpDialog(mainPanel);
		}
		instance.setVisible(true);
		instance.toFront();
		if (siteName != null) {
			instance.panel.connectSite(siteName);
		}
	}

	private FtpDialog(MainPanel mainPanel) {
		super(mainPanel.getParent(), "FTP Connect", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		panel = new FtpPanel(mainPanel, this::dispose, mainPanel::getActiveDiskPanel);
		setContentPane(panel);
		pack();
		setLocationRelativeTo(mainPanel.getParent());
		WindowBounds.remember(this, Setting.FTP_DIALOG_BOUNDS);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				panel.shutdown();
			}
		});
	}
}
