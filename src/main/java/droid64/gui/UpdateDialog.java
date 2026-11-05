package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import droid64.sid.Hvsc;
import droid64.update.HvscUpdater;
import droid64.update.Log;
import droid64.update.ViceUpdater;

/** Window with progress and log for the HVSC and VICE updates; also the update checks. */
public final class UpdateDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** A task which runs in the background. */
	private interface Task {
		void run(Log log) throws Exception;	//NOSONAR
	}

	private final JTextArea text = new JTextArea(12, 60);
	private final JProgressBar bar = new JProgressBar(0, 1000);
	private final JButton cancel = new JButton("Cancel");
	private volatile boolean cancelled;

	private UpdateDialog(Component parent, String title) {
		super(SwingUtilities.getWindowAncestor(parent), title, ModalityType.MODELESS);
		text.setEditable(false);
		text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		var p = new JPanel(new BorderLayout(6, 6));
		p.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
		p.add(new JScrollPane(text), BorderLayout.CENTER);
		var south = new JPanel(new BorderLayout(6, 6));
		south.add(bar, BorderLayout.CENTER);
		south.add(cancel, BorderLayout.EAST);
		p.add(south, BorderLayout.SOUTH);
		setContentPane(p);
		pack();
		setSize(new Dimension(640, 360));
		setLocationRelativeTo(parent);
		cancel.addActionListener(e -> {
			if (cancel.getText().equals("Close")) {
				dispose();
			} else {
				cancelled = true;
			}
		});
	}

	private void run(Task task, Consumer<Boolean> after) {
		setVisible(true);
		var log = new Log() {
			@Override
			public void log(String m) {
				SwingUtilities.invokeLater(() -> text.append(m + "\n"));
			}

			@Override
			public void progress(long done, long total) {
				SwingUtilities.invokeLater(() -> {
					bar.setIndeterminate(total <= 0);
					if (total > 0) {
						bar.setValue((int) (1000L * done / total));
						bar.setString(done / 1024 / 1024 + " / " + total / 1024 / 1024 + " MB");
						bar.setStringPainted(true);
					}
				});
			}

			@Override
			public boolean cancelled() {
				return cancelled;
			}
		};
		var t = new Thread(() -> {
			boolean ok = false;
			try {
				task.run(log);
				ok = true;
			} catch (Exception e) {	//NOSONAR
				log.log("FAILED: " + e.getMessage());
			}
			final boolean success = ok;
			SwingUtilities.invokeLater(() -> {
				bar.setIndeterminate(false);
				bar.setValue(success ? 1000 : 0);
				cancel.setText("Close");
				if (after != null) {
					after.accept(success);
				}
			});
		}, "update");
		t.setDaemon(true);
		t.start();
	}

	// ---- HVSC ----

	/** @return the music root of the configured HVSC folder, or null */
	static File hvscRoot() {
		return Hvsc.musicRoot(Setting.HVSC_DIR.getString());
	}

	/** Incremental update of the HVSC collection. */
	public static void updateHvsc(Component parent) {
		var root = hvscRoot();
		if (root == null) {
			JOptionPane.showMessageDialog(parent, "Set the HVSC folder first (it must contain the MUSICIANS folder),\nor use \"Download full HVSC\".", "HVSC", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		new UpdateDialog(parent, "HVSC update").run(log -> {
			log.log("Installed version: " + HvscUpdater.installedVersion(root));
			var c = HvscUpdater.check(root, Setting.HVSC_MIRRORS.getString());
			if (!c.newer()) {
				log.log(c.mirror == null && c.installed < 0 ? "Cannot determine the installed version." : "HVSC is up to date (or the mirrors cannot be reached).");
				return;
			}
			log.log("New version available: " + c.latest);
			int v = HvscUpdater.update(root, Setting.HVSC_MIRRORS.getString(), log);
			log.log("HVSC is now version " + v + ".");
		}, null);
	}

	/** Download the complete HVSC into the given folder and set it as the HVSC folder. */
	public static void downloadFullHvsc(Component parent) {
		var fc = new javax.swing.JFileChooser();
		fc.setDialogTitle("Folder for the new HVSC collection (a C64Music folder is created in it)");
		fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(parent) != javax.swing.JFileChooser.APPROVE_OPTION) {
			return;
		}
		var dir = fc.getSelectedFile();
		new UpdateDialog(parent, "Download HVSC").run(log -> {
			var root = HvscUpdater.installFull(dir, Setting.HVSC_MIRRORS.getString(), log);
			Setting.HVSC_DIR.set(root.getPath());
			log.log("HVSC folder set to " + root + " (press OK in the settings to keep it).");
		}, null);
	}

	// ---- VICE ----

	private static String viceReferencePath() {
		for (var s : new Setting[] { Setting.VICE_X64, Setting.VICE_X128, Setting.VICE_XPLUS4, Setting.VICE_XVIC }) {
			var v = s.getString();
			if (v != null && !v.trim().isEmpty()) {
				return v;
			}
		}
		return "";
	}

	/** Install the newest VICE macOS package (also when none is set up yet) and set the four paths. */
	public static void updateVice(Component parent) {
		new UpdateDialog(parent, "VICE update").run(log -> {
			var ref = viceReferencePath();
			var inst = ViceUpdater.installedVersion(Setting.VICE_X64.getString() == null || Setting.VICE_X64.getString().isEmpty() ? ref : Setting.VICE_X64.getString());
			log.log("Installed VICE: " + (inst == null ? "unknown / not set" : inst));
			var l = ViceUpdater.latest(ViceUpdater.flavorOf(ref));
			if (l == null) {
				throw new java.io.IOException("No VICE macOS package found on the download page.");
			}
			log.log("Latest VICE: " + l.version);
			if (inst != null && ViceUpdater.compare(l.version, inst) <= 0) {
				log.log("VICE is up to date.");
				return;
			}
			var target = new File(System.getProperty("user.home"), "Applications");
			var paths = ViceUpdater.install(l, target, log);
			var map = new java.util.LinkedHashMap<String, Setting>();
			map.put("x64sc", Setting.VICE_X64);
			map.put("x128", Setting.VICE_X128);
			map.put("xplus4", Setting.VICE_XPLUS4);
			map.put("xvic", Setting.VICE_XVIC);
			for (var e : map.entrySet()) {
				if (paths.containsKey(e.getKey())) {
					e.getValue().set(paths.get(e.getKey()));
				}
			}
			log.log("The emulator paths are set (press OK in the settings to keep them). The previous VICE stays where it was.");
		}, null);
	}

	// ---- check at startup ----

	/** Check in the background (at most once a day) and ask before installing. */
	public static void startupCheck(Component parent) {
		if (!Boolean.TRUE.equals(Setting.UPDATE_AUTO.getBoolean())) {
			return;
		}
		int today = (int) (System.currentTimeMillis() / 86_400_000L);
		if (Setting.UPDATE_LAST.getInteger() != null && Setting.UPDATE_LAST.getInteger() == today) {
			return;
		}
		var t = new Thread(() -> {
			String hv = null;
			String vi = null;
			try {
				var root = hvscRoot();
				if (root != null) {
					var c = HvscUpdater.check(root, Setting.HVSC_MIRRORS.getString());
					if (c.newer()) {
						hv = "HVSC " + c.installed + " -> " + c.latest;
					}
				}
				var ref = viceReferencePath();
				var inst = ViceUpdater.installedVersion(ref);
				if (inst != null) {
					var l = ViceUpdater.latest(ViceUpdater.flavorOf(ref));
					if (l != null && ViceUpdater.compare(l.version, inst) > 0) {
						vi = "VICE " + inst + " -> " + l.version;
					}
				}
				Setting.UPDATE_LAST.set(today);
				Setting.save();
			} catch (Exception e) {	//NOSONAR
				return;	// no network: try again next time
			}
			if (hv == null && vi == null) {
				return;
			}
			final var h = hv;
			final var v = vi;
			SwingUtilities.invokeLater(() -> {
				var msg = "Updates are available:\n" + (h != null ? "  " + h + "\n" : "") + (v != null ? "  " + v + "\n" : "") + "\nInstall now?";
				if (JOptionPane.showConfirmDialog(parent, msg, "Updates", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					if (h != null) {
						updateHvsc(parent);
					}
					if (v != null) {
						updateVice(parent);
					}
				}
			});
		}, "update-check");
		t.setDaemon(true);
		t.start();
	}
}
