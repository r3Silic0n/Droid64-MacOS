package droid64.gui;

import java.awt.Component;

import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

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
		if ("left".equals(where) || "right".equals(where) || "active".equals(where)) {
			var dp = mainPanel.getFtpTargetPanel(where);
			if (dp != null) {
				dp.showFtp();
				return;
			}
		}
		var sites = FtpSites.load();
		if (sites.isEmpty() || anchor == null) {
			show(mainPanel, null);
			return;
		}
		var menu = new JPopupMenu("FTP");
		for (var s : sites) {
			var item = new JMenuItem(s[FtpSites.NAME] + "   (" + s[FtpSites.USER] + "@" + s[FtpSites.HOST] + ")");
			item.addActionListener(e -> show(mainPanel, s[FtpSites.NAME]));
			menu.add(item);
		}
		menu.addSeparator();
		var other = new JMenuItem("New connection...");
		other.addActionListener(e -> show(mainPanel, null));
		menu.add(other);
		menu.show(anchor, 0, anchor.getHeight());
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
