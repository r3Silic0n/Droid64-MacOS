package droid64.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Window with all the OpenCBM functions: copy a disk to an image or back (with a sector map showing the progress,
 * green = good, orange = readable but faulty, red = unreadable), format, copy files, directory and drive control.
 */
public final class OpenCbmDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private static final String[] TRANSFERS = { "auto", "original", "serial1", "serial2", "parallel" };
	private static final String[] DEVICES = { "8", "9", "10", "11" };

	private static OpenCbmDialog instance;

	private final JComboBox<String> deviceBox = new JComboBox<>(DEVICES);
	private final JComboBox<String> transferBox = new JComboBox<>(TRANSFERS);
	private final JLabel adapterLabel = new JLabel("Looking for adapter…");
	private final JLabel adapterDot = new JLabel("●");
	private final JList<String> functionList;
	private final CardLayout cards = new CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final List<Runnable> busyListeners = new ArrayList<>();
	private volatile Process current;

	/** Show the dialog (only one at a time). */
	public static void show(MainPanel mainPanel) {
		if (instance == null || !instance.isDisplayable()) {
			instance = new OpenCbmDialog(mainPanel);
		}
		instance.setVisible(true);
		instance.toFront();
		instance.detect();
	}

	private OpenCbmDialog(MainPanel mainPanel) {
		super(mainPanel.getParent(), "OpenCBM", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		deviceBox.setSelectedItem(Setting.OPENCBM_DEVICE.getString());
		transferBox.setSelectedItem(Setting.OPENCBM_TRANSFER.getString());
		deviceBox.addActionListener(e -> Setting.OPENCBM_DEVICE.set(String.valueOf(deviceBox.getSelectedItem())));
		transferBox.addActionListener(e -> Setting.OPENCBM_TRANSFER.set(String.valueOf(transferBox.getSelectedItem())));

		var top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
		adapterDot.setForeground(Color.GRAY);
		top.add(adapterDot);
		top.add(adapterLabel);
		top.add(new JLabel("   Drive"));
		top.add(deviceBox);
		top.add(new JLabel("Transfer"));
		top.add(transferBox);
		var detectButton = new JButton("Detect drives");
		detectButton.addActionListener(e -> detect());
		var resetButton = new JButton("Reset bus");
		resetButton.addActionListener(e -> run(List.of("cbmctrl", "reset"), null, line -> { }, rc -> detect()));
		top.add(detectButton);
		top.add(resetButton);

		var names = new String[] { "Disk → image", "Image → disk", "Format", "Copy files", "Directory", "Drive control" };
		var cardNames = new String[] { "read", "write", "format", "files", "dir", "control" };
		functionList = new JList<>(names);
		functionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		functionList.setSelectedIndex(0);
		functionList.setFixedCellHeight(30);
		functionList.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		functionList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && functionList.getSelectedIndex() >= 0) {
				cards.show(cardPanel, cardNames[functionList.getSelectedIndex()]);
			}
		});

		cardPanel.add(new CopyPanel(true), "read");
		cardPanel.add(new CopyPanel(false), "write");
		cardPanel.add(new FormatPanel(), "format");
		cardPanel.add(new FilesPanel(), "files");
		cardPanel.add(new TextToolPanel("Directory", "Read directory", List.of("dir")), "dir");
		cardPanel.add(new ControlPanel(), "control");

		var left = new JScrollPane(functionList);
		left.setPreferredSize(new Dimension(160, 100));
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(left, BorderLayout.WEST);
		getContentPane().add(cardPanel, BorderLayout.CENTER);
		setSize(980, 640);
		setMinimumSize(new Dimension(760, 520));
		setLocationRelativeTo(mainPanel.getParent());

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				stopProcess();
			}
		});
	}

	// ------------------------------------------------------------------ running tools

	private String device() {
		return String.valueOf(deviceBox.getSelectedItem());
	}

	private String transfer() {
		return String.valueOf(transferBox.getSelectedItem());
	}

	private boolean busy() {
		var p = current;
		return p != null && p.isAlive();
	}

	private void fireBusy() {
		for (var l : busyListeners) {
			l.run();
		}
	}

	/** Ask the running tool to stop (SIGINT lets OpenCBM reset the IEC bus), kill it if it does not. */
	private void stopProcess() {
		var p = current;
		if (p == null || !p.isAlive()) {
			return;
		}
		try {
			new ProcessBuilder("kill", "-INT", String.valueOf(p.pid())).start();
		} catch (IOException e) {
			p.destroy();
		}
		var t = new Timer(4000, e -> {
			if (p.isAlive()) {
				p.destroyForcibly();
			}
		});
		t.setRepeats(false);
		t.start();
	}

	/**
	 * Run an OpenCBM tool in the background.
	 * @param command tool name and arguments, the name is resolved to the bundled tool
	 * @param cwd working directory or null
	 * @param onText receives the output, split at line ends and carriage returns (called on the event thread)
	 * @param onExit called with the exit code on the event thread
	 * @return false if another tool is still running
	 */
	private boolean run(List<String> command, File cwd, Consumer<String> onText, java.util.function.IntConsumer onExit) {
		if (busy()) {
			return false;
		}
		var cmd = new ArrayList<>(command);
		cmd.set(0, ExternalProgram.resolveCommand(cmd.get(0)));
		var builder = new ProcessBuilder(cmd).redirectErrorStream(true);
		if (cwd != null) {
			builder.directory(cwd);
		}
		var bundled = ExternalProgram.bundledOpenCbm();
		if (bundled != null) {
			builder.environment().put("OPENCBM_HOME", bundled.getPath());
		}
		final Process process;
		try {
			process = builder.start();
		} catch (IOException e) {
			SwingUtilities.invokeLater(() -> {
				onText.accept("Could not start " + cmd.get(0) + ": " + e.getMessage());
				onExit.accept(127);
			});
			return true;
		}
		current = process;
		fireBusy();
		var thread = new Thread(() -> {
			int rc = -1;
			try (InputStream in = process.getInputStream()) {
				var buf = new ByteArrayOutputStream();
				int b;
				while ((b = in.read()) >= 0) {
					if (b == '\r' || b == '\n') {
						if (buf.size() > 0) {
							final var text = buf.toString("ISO-8859-1");
							buf.reset();
							SwingUtilities.invokeLater(() -> onText.accept(text));
						}
					} else {
						buf.write(b);
					}
				}
				if (buf.size() > 0) {
					final var text = buf.toString("ISO-8859-1");
					SwingUtilities.invokeLater(() -> onText.accept(text));
				}
				rc = process.waitFor();
			} catch (IOException e) {
				// the process ended
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			final int code = rc;
			SwingUtilities.invokeLater(() -> {
				fireBusy();
				onExit.accept(code);
			});
		}, "opencbm");
		thread.setDaemon(true);
		thread.start();
		return true;
	}

	private void detect() {
		adapterDot.setForeground(Color.GRAY);
		adapterLabel.setText("Looking for drives…");
		var found = new ArrayList<String>();
		var all = new StringBuilder();
		if (!run(List.of("cbmctrl", "detect"), null, line -> {
			all.append(line).append(' ');
			if (line.matches("^\\s*\\d+:.*")) {
				found.add(line.trim());
			}
		}, rc -> {
			if (rc == 0 && !found.isEmpty()) {
				adapterDot.setForeground(new Color(0x2E, 0x9E, 0x4D));
				adapterLabel.setText(String.join("   ", found));
			} else if (rc == 0) {
				adapterDot.setForeground(new Color(0xE0, 0x90, 0x20));
				adapterLabel.setText("Adapter found, no drive answered");
			} else {
				adapterDot.setForeground(new Color(0xD0, 0x3A, 0x30));
				var msg = all.toString().trim();
				adapterLabel.setText(msg.isEmpty() ? "No adapter found (exit code " + rc + ")" : "No adapter: " + msg);
			}
		})) {
			adapterLabel.setText("Busy");
		}
	}

	private static String ask(Component owner, String title, String text) {
		return JOptionPane.showConfirmDialog(owner, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION ? "ok" : null;
	}

	private File chooseFile(boolean save, String title, String name) {
		var dir = Setting.OPENCBM_DIR.getString();
		var owner = getOwner() instanceof Frame ? (Frame) getOwner() : null;
		var dlg = new FileDialog(owner, title, save ? FileDialog.SAVE : FileDialog.LOAD);
		if (dir != null && !dir.isEmpty() && new File(dir).isDirectory()) {
			dlg.setDirectory(dir);
		}
		if (name != null) {
			dlg.setFile(name);
		}
		dlg.setVisible(true);
		if (dlg.getFile() == null) {
			return null;
		}
		var f = new File(dlg.getDirectory(), dlg.getFile());
		Setting.OPENCBM_DIR.set(f.getParent());
		return f;
	}

	private static JPanel form() {
		var p = new JPanel(new GridBagLayout());
		p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		return p;
	}

	private static void addRow(JPanel p, int row, String label, Component c) {
		var gl = new GridBagConstraints();
		gl.gridx = 0;
		gl.gridy = row;
		gl.anchor = GridBagConstraints.WEST;
		gl.insets = new Insets(3, 0, 3, 8);
		if (label != null) {
			p.add(new JLabel(label), gl);
		}
		var gc = new GridBagConstraints();
		gc.gridx = 1;
		gc.gridy = row;
		gc.weightx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.anchor = GridBagConstraints.WEST;
		gc.insets = new Insets(3, 0, 3, 0);
		p.add(c, gc);
	}

	private static JTextArea logArea() {
		var a = new JTextArea(8, 60);
		a.setEditable(false);
		a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		return a;
	}

	private static void appendLog(JTextArea a, String line) {
		a.append(line + "\n");
		a.setCaretPosition(a.getDocument().getLength());
	}

	// ------------------------------------------------------------------ sector map

	/** Track x sector map: one small square per sector. */
	static final class SectorMap extends JPanel {

		private static final long serialVersionUID = 1L;
		static final byte NONE = 0;
		static final byte PENDING = 1;
		static final byte OK = 2;
		static final byte WARN = 3;
		static final byte BAD = 4;
		static final byte SKIPPED = 5;
		private static final Color C_OK = new Color(0x3F, 0xAE, 0x5A);
		private static final Color C_WARN = new Color(0xFF, 0xB0, 0x2E);
		private static final Color C_BAD = new Color(0xE5, 0x48, 0x3F);
		private static final Color C_PENDING = new Color(0x3A, 0x41, 0x4C);
		private static final Color C_SKIPPED = new Color(0x26, 0x2B, 0x33);
		private static final Color C_CUR = new Color(0x5B, 0x9B, 0xFF);

		private byte[][] state = new byte[0][];
		private int tracks;
		private int maxSpt;
		private int curTrack;
		private int curSector;
		private boolean blinkOn = true;
		private final Timer blink = new Timer(450, e -> {
			blinkOn = !blinkOn;
			repaint();
		});

		SectorMap() {
			setBackground(new Color(0x1B, 0x1F, 0x25));
			setOpaque(true);
			setPreferredSize(new Dimension(640, 330));
			setMinimumSize(new Dimension(300, 200));
			setGeometry("1541", false);
		}

		/** Set the number of tracks and sectors for a drive type. */
		void setGeometry(String drive, boolean extended) {
			int n;
			switch (drive) {
			case "1571":
				n = 70;
				break;
			case "1581":
				n = 80;
				break;
			case "8050":
				n = 77;
				break;
			case "8250":
				n = 154;
				break;
			default:
				n = extended ? 40 : 35;
				break;
			}
			tracks = n;
			state = new byte[n + 1][];
			maxSpt = 0;
			for (int t = 1; t <= n; t++) {
				int s = sectors(drive, t);
				state[t] = new byte[s];
				java.util.Arrays.fill(state[t], PENDING);
				maxSpt = Math.max(maxSpt, s);
			}
			curTrack = 0;
			curSector = 0;
			repaint();
		}

		private static int sectors(String drive, int track) {
			switch (drive) {
			case "1581":
				return 40;
			case "8050":
			case "8250": {
				int t = track > 77 ? track - 77 : track;
				return t <= 39 ? 29 : t <= 53 ? 27 : t <= 64 ? 25 : 23;
			}
			default: {
				int t = track > 35 && "1571".equals(drive) ? track - 35 : track;
				return t <= 17 ? 21 : t <= 24 ? 19 : t <= 30 ? 18 : 17;
			}
			}
		}

		void reset() {
			for (int t = 1; t <= tracks; t++) {
				java.util.Arrays.fill(state[t], PENDING);
			}
			curTrack = 0;
			repaint();
		}

		void setActive(boolean active) {
			if (active) {
				blink.start();
			} else {
				blink.stop();
				blinkOn = true;
				curTrack = 0;
				repaint();
			}
		}

		/** Make sure the map has room for track and sector. */
		private void ensure(int track, int sector) {
			if (track < 1 || sector < 0) {
				return;
			}
			if (track > tracks) {
				var n = new byte[track + 1][];
				System.arraycopy(state, 0, n, 0, state.length);
				for (int t = tracks + 1; t <= track; t++) {
					n[t] = new byte[0];
				}
				state = n;
				tracks = track;
			}
			if (state[track].length <= sector) {
				var old = state[track];
				state[track] = new byte[sector + 1];
				java.util.Arrays.fill(state[track], PENDING);
				System.arraycopy(old, 0, state[track], 0, old.length);
			}
			maxSpt = Math.max(maxSpt, state[track].length);
		}

		/** Update a track from a copy tool's progress map: ' ' no sector, '.' not copied, '-' waiting, '?' error, '*' copied. */
		void update(int track, String map) {
			if (track < 1) {
				return;
			}
			int count = map.length();
			while (count > 0 && map.charAt(count - 1) == ' ') {
				count--;
			}
			if (count == 0) {
				return;
			}
			ensure(track, count - 1);
			var row = state[track];
			int first = -1;
			for (int i = 0; i < row.length; i++) {
				char c = i < map.length() ? map.charAt(i) : ' ';
				switch (c) {
				case '*':
					if (row[i] != WARN && row[i] != BAD) {
						row[i] = OK;
					}
					break;
				case '?':
					if (row[i] != WARN) {
						row[i] = BAD;
					}
					break;
				case '.':
					row[i] = SKIPPED;
					break;
				case '-':
					if (row[i] != WARN) {
						row[i] = PENDING;
					}
					if (first < 0) {
						first = i;
					}
					break;
				default:
					break;
				}
			}
			curTrack = track;
			curSector = first >= 0 ? first : -1;
			if (first < 0) {
				// the track is done, the next one is being read
				if (track < tracks && state[track + 1].length > 0) {
					curTrack = track + 1;
					curSector = 0;
				} else {
					curTrack = 0;
				}
			}
			repaint();
		}

		/** A sector reported with an error code: 23 (checksum) means the data was read, anything else is unreadable. */
		void error(int track, int sector, int code) {
			ensure(track, sector);
			if (track >= 1 && track <= tracks && sector >= 0 && sector < state[track].length) {
				state[track][sector] = code == 23 ? WARN : BAD;
				repaint();
			}
		}

		void count(int[] out) {
			for (int t = 1; t <= tracks; t++) {
				for (byte b : state[t]) {
					if (b == OK) {
						out[0]++;
					} else if (b == WARN) {
						out[1]++;
					} else if (b == BAD) {
						out[2]++;
					}
				}
			}
		}

		@Override
		protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			var g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			var small = getFont().deriveFont(10f);
			g.setFont(small);
			FontMetrics fm = g.getFontMetrics();
			// legend
			int lx = 12;
			String[] names = { "good", "readable, faulty", "unreadable", "waiting" };
			Color[] cols = { C_OK, C_WARN, C_BAD, C_PENDING };
			for (int i = 0; i < names.length; i++) {
				g.setColor(cols[i]);
				g.fillRect(lx, 9, 10, 10);
				g.setColor(new Color(0xC9, 0xCE, 0xD6));
				g.drawString(names[i], lx + 15, 18);
				lx += 15 + fm.stringWidth(names[i]) + 18;
			}
			int padL = 28;
			int padT = 48;
			int availW = getWidth() - padL - 12;
			int availH = getHeight() - padT - 10;
			if (tracks == 0 || maxSpt == 0 || availW < 10 || availH < 10) {
				g.dispose();
				return;
			}
			int pitch = Math.max(4, Math.min(18, Math.min(availW / tracks, availH / maxSpt)));
			int cell = Math.max(3, pitch - 2);
			int step = pitch >= 12 ? 5 : pitch >= 8 ? 10 : 20;
			g.setColor(new Color(0x8C, 0x94, 0xA1));
			for (int t = 1; t <= tracks; t++) {
				if (t == 1 || t % step == 0) {
					String s = String.valueOf(t);
					g.drawString(s, padL + (t - 1) * pitch + (cell - fm.stringWidth(s)) / 2, padT - 6);
				}
			}
			for (int s = 0; s < maxSpt; s += 5) {
				String str = String.valueOf(s);
				g.drawString(str, padL - 6 - fm.stringWidth(str), padT + s * pitch + cell - 1);
			}
			for (int t = 1; t <= tracks; t++) {
				var row = state[t];
				for (int s = 0; s < row.length; s++) {
					Color c;
					switch (row[s]) {
					case OK:
						c = C_OK;
						break;
					case WARN:
						c = C_WARN;
						break;
					case BAD:
						c = C_BAD;
						break;
					case SKIPPED:
						c = C_SKIPPED;
						break;
					default:
						c = C_PENDING;
						break;
					}
					int x = padL + (t - 1) * pitch;
					int y = padT + s * pitch;
					boolean cur = t == curTrack && s == curSector;
					if (cur) {
						g.setColor(blinkOn ? C_CUR : getBackground());
						g.fillRect(x, y, cell, cell);
						g.setColor(blinkOn ? Color.WHITE : C_CUR);
						g.drawRect(x - 1, y - 1, cell + 1, cell + 1);
					} else {
						g.setColor(c);
						g.fillRect(x, y, cell, cell);
					}
				}
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------------ disk <-> image

	private static final Pattern PROGRESS = Pattern.compile("^\\s*(\\d+): ([ .\\-?*]*?)\\s*(\\d{1,3})%\\s+(\\d+)/(\\d+).*$");
	private static final Pattern ERROR_MSG = Pattern.compile("(?:read|write) error: ([0-9a-fA-F]+)/([0-9a-fA-F]+): (-?\\d+)");

	private final class CopyPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private final boolean toImage;
		private final SectorMap map = new SectorMap();
		private final JTextField file = new JTextField(30);
		private final JComboBox<String> driveType = new JComboBox<>(new String[] { "1541", "1571", "1581", "8050", "8250" });
		private final JSpinner retries = new JSpinner(new SpinnerNumberModel(5, 0, 99, 1));
		private final JCheckBox extended = new JCheckBox("40 tracks");
		private final JCheckBox bamOnly = new JCheckBox("Only allocated blocks");
		private final JCheckBox errorMap = new JCheckBox("Keep the error map in the image", true);
		private final JButton start = new JButton();
		private final JButton stop = new JButton("Stop");
		private final JProgressBar bar = new JProgressBar(0, 100);
		private final JLabel stats = new JLabel(" ");
		private final JTextArea log = logArea();
		private long started;
		private int processed;
		private int total;

		CopyPanel(boolean toImage) {
			super(new BorderLayout(0, 8));
			this.toImage = toImage;
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

			var head = form();
			head.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			var fileRow = new JPanel(new BorderLayout(6, 0));
			fileRow.add(file, BorderLayout.CENTER);
			var browse = new JButton("Browse…");
			browse.addActionListener(e -> {
				var f = chooseFile(toImage, toImage ? "Save disk image as" : "Disk image to write", toImage ? file.getText().isEmpty() ? "disk.d64" : new File(file.getText()).getName() : null);
				if (f != null) {
					file.setText(f.getPath());
				}
			});
			fileRow.add(browse, BorderLayout.EAST);
			addRow(head, 0, toImage ? "Save as" : "Image", fileRow);

			var opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
			opts.add(new JLabel("Drive type"));
			opts.add(driveType);
			opts.add(new JLabel("Retries"));
			opts.add(retries);
			opts.add(extended);
			opts.add(bamOnly);
			if (toImage) {
				opts.add(errorMap);
			}
			addRow(head, 1, null, opts);

			var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			start.setText(toImage ? "Start copy" : "Write to disk");
			buttons.add(start);
			buttons.add(stop);
			stop.setEnabled(false);
			addRow(head, 2, null, buttons);
			driveType.addActionListener(e -> {
				extended.setEnabled("1541".equals(driveType.getSelectedItem()));
				map.setGeometry(String.valueOf(driveType.getSelectedItem()), extended.isSelected());
			});
			extended.addActionListener(e -> map.setGeometry(String.valueOf(driveType.getSelectedItem()), extended.isSelected()));

			var south = new JPanel(new BorderLayout(0, 4));
			var barRow = new JPanel(new BorderLayout(8, 0));
			barRow.add(bar, BorderLayout.CENTER);
			south.add(barRow, BorderLayout.NORTH);
			south.add(stats, BorderLayout.CENTER);
			var logScroll = new JScrollPane(log);
			logScroll.setPreferredSize(new Dimension(100, 110));
			south.add(logScroll, BorderLayout.SOUTH);

			add(head, BorderLayout.NORTH);
			add(map, BorderLayout.CENTER);
			add(south, BorderLayout.SOUTH);

			start.addActionListener(e -> begin());
			stop.addActionListener(e -> stopProcess());
			busyListeners.add(() -> {
				boolean b = busy();
				start.setEnabled(!b);
			});
		}

		private List<String> command(String image) {
			var cmd = new ArrayList<String>();
			var type = String.valueOf(driveType.getSelectedItem());
			switch (type) {
			case "1541":
				cmd.add("d64copy");
				if (extended.isSelected()) {
					cmd.add("-e");
					cmd.add("40");
				}
				break;
			case "1571":
				cmd.add("d64copy");
				cmd.add("-2");
				cmd.add("-d");
				cmd.add("1571");
				break;
			case "1581":
				cmd.add("imgcopy");
				cmd.add("-d");
				cmd.add("1581");
				break;
			default:
				cmd.add("d82copy");
				cmd.add("-d");
				cmd.add(type);
				cmd.add("8050".equals(type) ? "-1" : "-2");
				break;
			}
			if (!"auto".equals(transfer())) {
				cmd.add("-t");
				cmd.add(transfer());
			}
			cmd.add("-r");
			cmd.add(String.valueOf(retries.getValue()));
			if (bamOnly.isSelected()) {
				cmd.add("-b");
			}
			if (toImage && !errorMap.isSelected()) {
				cmd.add("-E");
				cmd.add("never");
			}
			if (toImage) {
				cmd.add(device());
				cmd.add(image);
			} else {
				cmd.add(image);
				cmd.add(device());
			}
			return cmd;
		}

		private void begin() {
			var path = file.getText().trim();
			if (path.isEmpty()) {
				JOptionPane.showMessageDialog(OpenCbmDialog.this, "Choose the image file first.", "OpenCBM", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!toImage && !new File(path).isFile()) {
				JOptionPane.showMessageDialog(OpenCbmDialog.this, "The image file does not exist.", "OpenCBM", JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (!toImage && ask(OpenCbmDialog.this, "Write to disk",
					"This overwrites the whole disk in drive " + device() + ".\nContinue?") == null) {
				return;
			}
			map.reset();
			log.setText("");
			bar.setValue(0);
			stats.setText(" ");
			started = System.currentTimeMillis();
			processed = 0;
			total = 0;
			var cmd = command(path);
			appendLog(log, String.join(" ", cmd));
			map.setActive(true);
			stop.setEnabled(true);
			run(cmd, null, this::onText, rc -> {
				map.setActive(false);
				stop.setEnabled(false);
				start.setEnabled(true);
				var c = new int[3];
				map.count(c);
				bar.setValue(rc == 0 ? 100 : bar.getValue());
				appendLog(log, rc == 0 ? "Finished." : "Ended with exit code " + rc + ".");
				stats.setText(summary(c, rc == 0 ? "Finished" : "Stopped / failed"));
			});
			start.setEnabled(false);
		}

		private void onText(String text) {
			// error messages follow the progress display on the same line, so look for them first
			var e = ERROR_MSG.matcher(text);
			while (e.find()) {
				map.error(Integer.parseInt(e.group(1), 16), Integer.parseInt(e.group(2), 16), Integer.parseInt(e.group(3)));
				appendLog(log, text.substring(e.start() > 0 ? text.lastIndexOf('[', e.start()) >= 0 ? text.lastIndexOf('[', e.start()) : e.start() : 0).trim());
			}
			var m = PROGRESS.matcher(text);
			if (m.matches()) {
				int track = Integer.parseInt(m.group(1));
				map.update(track, m.group(2));
				bar.setValue(Integer.parseInt(m.group(3)));
				processed = Integer.parseInt(m.group(4));
				total = Integer.parseInt(m.group(5));
				var c = new int[3];
				map.count(c);
				stats.setText(summary(c, null));
				return;
			}
			if (e.reset().find()) {
				return;
			}
			if (text.trim().matches("^\\d+: .*") && text.contains("   ")) {
				return;	// track finished line of the progress display
			}
			if (!text.trim().isEmpty()) {
				appendLog(log, text);
			}
		}

		private String summary(int[] c, String state) {
			var sb = new StringBuilder();
			sb.append("<html>");
			if (state != null) {
				sb.append("<b>").append(state).append("</b> &nbsp; ");
			}
			sb.append("Good <b>").append(c[0]).append("</b> &nbsp; Faulty <b style='color:#b06a00'>").append(c[1])
					.append("</b> &nbsp; Unreadable <b style='color:#b3261e'>").append(c[2]).append("</b>");
			double secs = Math.max(1, (System.currentTimeMillis() - started) / 1000.0);
			if (processed > 0 && total > 0) {
				double rate = processed * 256.0 / 1024.0 / secs;
				int left = (int) ((total - processed) / (processed / secs));
				sb.append(String.format(" &nbsp; %.1f KB/s", rate));
				if (state == null) {
					sb.append(String.format(" &nbsp; %d:%02d left", left / 60, left % 60));
				}
			}
			sb.append("</html>");
			return sb.toString();
		}
	}

	// ------------------------------------------------------------------ tools with a text log

	private final class FormatPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		FormatPanel() {
			super(new BorderLayout(0, 8));
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			var f = form();
			var tool = new JComboBox<>(new String[] { "cbmformat – fast", "cbmforng – fast and reliable" });
			var name = new JTextField("NEW DISK", 16);
			var id = new JTextField("01", 3);
			var extended = new JCheckBox("40 tracks");
			var clear = new JCheckBox("Clear (demagnetize), recommended for a new disk");
			var verify = new JCheckBox("Verify every track");
			var original = new JCheckBox("Fill sectors with the original pattern ($4B $01…)");
			var noBump = new JCheckBox("Do not bump the drive head");
			var retries = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
			var go = new JButton("Format…");
			var log = logArea();
			addRow(f, 0, "Tool", tool);
			addRow(f, 1, "Disk name", name);
			addRow(f, 2, "Disk ID", id);
			addRow(f, 3, "Retries (cbmforng)", retries);
			addRow(f, 4, null, extended);
			addRow(f, 5, null, clear);
			addRow(f, 6, null, verify);
			addRow(f, 7, null, original);
			addRow(f, 8, null, noBump);
			addRow(f, 9, null, go);
			add(f, BorderLayout.NORTH);
			add(new JScrollPane(log), BorderLayout.CENTER);
			busyListeners.add(() -> go.setEnabled(!busy()));
			go.addActionListener(e -> {
				if (name.getText().trim().isEmpty() || id.getText().trim().length() != 2) {
					JOptionPane.showMessageDialog(OpenCbmDialog.this, "Give a disk name and a two character ID.", "OpenCBM", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				if (ask(OpenCbmDialog.this, "Format", "This erases everything on the disk in drive " + device() + ".\nContinue?") == null) {
					return;
				}
				var forng = tool.getSelectedIndex() == 1;
				var cmd = new ArrayList<String>();
				cmd.add(forng ? "cbmforng" : "cbmformat");
				if (extended.isSelected()) {
					cmd.add("-x");
				}
				if (clear.isSelected()) {
					cmd.add("-c");
				}
				if (verify.isSelected()) {
					cmd.add("-v");
				}
				if (original.isSelected()) {
					cmd.add("-o");
				}
				if (noBump.isSelected()) {
					cmd.add("-n");
				}
				if (forng && (Integer) retries.getValue() > 0) {
					cmd.add("-r");
					cmd.add(String.valueOf(retries.getValue()));
				}
				cmd.add("-s");
				cmd.add(device());
				cmd.add(name.getText().trim().toUpperCase() + "," + id.getText().trim().toUpperCase());
				log.setText("");
				appendLog(log, String.join(" ", cmd));
				run(cmd, null, line -> appendLog(log, line), rc -> appendLog(log, rc == 0 ? "Finished." : "Ended with exit code " + rc + "."));
			});
		}
	}

	private final class FilesPanel extends JPanel {

		private static final long serialVersionUID = 1L;
		private final DefaultListModel<File> model = new DefaultListModel<>();

		FilesPanel() {
			super(new BorderLayout(0, 8));
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			var f = form();
			var readBox = new javax.swing.JRadioButton("Drive → computer", true);
			var writeBox = new javax.swing.JRadioButton("Computer → drive");
			var group = new javax.swing.ButtonGroup();
			group.add(readBox);
			group.add(writeBox);
			var modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
			modeRow.add(readBox);
			modeRow.add(writeBox);
			addRow(f, 0, "Direction", modeRow);

			var names = new JTextField("*", 30);
			addRow(f, 1, "CBM file names", names);

			var folderRow = new JPanel(new BorderLayout(6, 0));
			var folder = new JTextField(Setting.OPENCBM_DIR.getString(), 30);
			folderRow.add(folder, BorderLayout.CENTER);
			var browse = new JButton("Browse…");
			browse.addActionListener(e -> {
				var d = FolderChooserDialog.choose(OpenCbmDialog.this, "Folder", folder.getText().isEmpty() ? null : new File(folder.getText()));
				if (d != null) {
					folder.setText(d.getPath());
					Setting.OPENCBM_DIR.set(d.getPath());
				}
			});
			folderRow.add(browse, BorderLayout.EAST);
			addRow(f, 2, "Computer folder", folderRow);

			var list = new JList<>(model);
			list.setVisibleRowCount(4);
			var listBox = new JPanel(new BorderLayout(6, 0));
			listBox.add(new JScrollPane(list), BorderLayout.CENTER);
			var listButtons = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
			var addButton = new JButton("Add…");
			addButton.addActionListener(e -> {
				var file = chooseFile(false, "File to write to the disk", null);
				if (file != null) {
					model.addElement(file);
				}
			});
			var removeButton = new JButton("Remove");
			removeButton.addActionListener(e -> {
				int i = list.getSelectedIndex();
				if (i >= 0) {
					model.remove(i);
				}
			});
			listButtons.add(addButton);
			listButtons.add(removeButton);
			listBox.add(listButtons, BorderLayout.EAST);
			addRow(f, 3, "Files to write", listBox);

			var type = new JComboBox<>(new String[] { "P – PRG", "S – SEQ", "U – USR", "D – DEL" });
			addRow(f, 4, "CBM file type", type);
			var go = new JButton("Copy");
			addRow(f, 5, null, go);
			var log = logArea();
			add(f, BorderLayout.NORTH);
			add(new JScrollPane(log), BorderLayout.CENTER);
			busyListeners.add(() -> go.setEnabled(!busy()));
			go.addActionListener(e -> {
				var cmd = new ArrayList<String>();
				cmd.add("cbmcopy");
				if (!"auto".equals(transfer())) {
					cmd.add("-t");
					cmd.add(transfer());
				}
				File cwd = folder.getText().trim().isEmpty() ? null : new File(folder.getText().trim());
				if (readBox.isSelected()) {
					if (cwd == null || !cwd.isDirectory()) {
						JOptionPane.showMessageDialog(OpenCbmDialog.this, "Choose the computer folder for the files.", "OpenCBM", JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					cmd.add("-r");
					cmd.add(device());
					for (var n : names.getText().trim().split("\\s+")) {
						if (!n.isEmpty()) {
							cmd.add(n);
						}
					}
				} else {
					if (model.isEmpty()) {
						JOptionPane.showMessageDialog(OpenCbmDialog.this, "Add the files to write first.", "OpenCBM", JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					cmd.add("-w");
					cmd.add("-f");
					cmd.add(String.valueOf("PSUD".charAt(type.getSelectedIndex())));
					cmd.add(device());
					for (int i = 0; i < model.size(); i++) {
						cmd.add(model.get(i).getPath());
					}
					cwd = null;
				}
				log.setText("");
				appendLog(log, String.join(" ", cmd));
				run(cmd, cwd, line -> appendLog(log, line), rc -> appendLog(log, rc == 0 ? "Finished." : "Ended with exit code " + rc + "."));
			});
		}
	}

	/** One button that runs "cbmctrl &lt;args&gt; &lt;device&gt;" and shows the output. */
	private final class TextToolPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		TextToolPanel(String title, String buttonText, List<String> args) {
			super(new BorderLayout(0, 8));
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			var go = new JButton(buttonText);
			var log = logArea();
			log.setRows(20);
			var head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
			head.add(new JLabel(title));
			head.add(go);
			add(head, BorderLayout.NORTH);
			add(new JScrollPane(log), BorderLayout.CENTER);
			busyListeners.add(() -> go.setEnabled(!busy()));
			go.addActionListener(e -> {
				var cmd = new ArrayList<String>();
				cmd.add("cbmctrl");
				cmd.addAll(args);
				cmd.add(device());
				log.setText("");
				run(cmd, null, line -> appendLog(log, line), rc -> {
					if (rc != 0) {
						appendLog(log, "Exit code " + rc);
					}
				});
			});
		}
	}

	private final class ControlPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		ControlPanel() {
			super(new BorderLayout(0, 8));
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			var log = logArea();
			log.setRows(20);
			var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			var status = new JButton("Drive status");
			var init = new JButton("Initialize");
			var detect = new JButton("Detect drives");
			var unlock = new JButton("Unlock bus");
			buttons.add(status);
			buttons.add(init);
			buttons.add(detect);
			buttons.add(unlock);
			var cmdRow = new JPanel(new BorderLayout(6, 0));
			var cmdField = new JTextField();
			var send = new JButton("Send command");
			cmdRow.add(new JLabel("DOS command "), BorderLayout.WEST);
			cmdRow.add(cmdField, BorderLayout.CENTER);
			cmdRow.add(send, BorderLayout.EAST);
			var north = new JPanel(new BorderLayout(0, 8));
			north.add(buttons, BorderLayout.NORTH);
			north.add(cmdRow, BorderLayout.SOUTH);
			add(north, BorderLayout.NORTH);
			add(new JScrollPane(log), BorderLayout.CENTER);
			Consumer<List<String>> exec = args -> {
				var cmd = new ArrayList<String>();
				cmd.add("cbmctrl");
				cmd.addAll(args);
				appendLog(log, "> " + String.join(" ", cmd));
				run(cmd, null, line -> appendLog(log, line), rc -> {
					if (rc != 0) {
						appendLog(log, "Exit code " + rc);
					}
				});
			};
			status.addActionListener(e -> exec.accept(List.of("status", device())));
			init.addActionListener(e -> exec.accept(List.of("command", device(), "I0")));
			detect.addActionListener(e -> exec.accept(List.of("detect")));
			unlock.addActionListener(e -> exec.accept(List.of("unlock")));
			Runnable sendAction = () -> {
				var text = cmdField.getText().trim();
				if (!text.isEmpty()) {
					exec.accept(List.of("command", device(), text));
				}
			};
			send.addActionListener(e -> sendAction.run());
			cmdField.addActionListener(e -> sendAction.run());
			busyListeners.add(() -> {
				boolean b = busy();
				status.setEnabled(!b);
				init.setEnabled(!b);
				detect.setEnabled(!b);
				unlock.setEnabled(!b);
				send.setEnabled(!b);
			});
		}
	}
}
