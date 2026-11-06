package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import droid64.sid.Hvsc;
import droid64.sid.SidChip;
import droid64.sid.SidDetector;
import droid64.sid.SidEngine;
import droid64.sid.SidTextScanner;
import droid64.sid.SidTune;

/**
 * SID music player: lists the files of the disk, recognises SID music (PSID/RSID files and
 * PRG files containing a player), shows the information of the tune, plays it and saves it as a SID file.
 */
public class MusicPlayerDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final int RATE = 44100;

	/** One file of the disk. */
	private static final class Item {
		final int index;
		final String name;
		volatile boolean checked;
		volatile SidTune tune;		// null if not music
		volatile List<String> texts = new ArrayList<>();
		volatile Hvsc.Result hvsc;	// null for PRG music
		File file;			// HVSC browser: the file or folder
		boolean dir;
		boolean up;
		String found = "";	// result of "Find in HVSC"

		Item(int index, String name) {
			this.index = index;
			this.name = name;
		}
	}

	private final DefaultListModel<Item> model = new DefaultListModel<>();
	private final JList<Item> list = new JList<>(model);
	private final JCheckBox musicOnly = new JCheckBox("Music only", true);
	private final List<Item> all = new ArrayList<>();
	private final IntFunction<byte[]> loader;
	private final JLabel status = new JLabel(" ");
	private final JLabel infoTitle = new JLabel("-");
	private final JLabel infoAuthor = new JLabel("-");
	private final JLabel infoReleased = new JLabel("-");
	private final JLabel infoFormat = new JLabel("-");
	private final JLabel infoAddr = new JLabel("-");
	private final JLabel infoSize = new JLabel("-");
	private final JLabel infoSpeed = new JLabel("-");
	private final JLabel infoText = new JLabel("-");
	private final JLabel infoHvsc = new JLabel("-");
	private final javax.swing.JTextArea stilArea = new javax.swing.JTextArea(4, 30);
	private final JSlider seek = new JSlider(0, 300, 0);
	private boolean seekAdjusting;
	private final JLabel timeLabel = new JLabel("0:00");
	private final JButton playButton = new JButton(TransportIcon.PLAY);
	private final JButton stopButton = new JButton(TransportIcon.STOP);
	private final JButton prevSong = new JButton(TransportIcon.PREVIOUS);
	private final JButton nextSong = new JButton(TransportIcon.NEXT);
	private final JLabel songLabel = new JLabel("1 / 1");
	private final JSlider volume = new JSlider(0, 100, 70);
	private final JComboBox<SidChip.Model> modelBox = new JComboBox<>(SidChip.Model.values());
	private final JComboBox<String> clockBox = new JComboBox<>(new String[] { "Auto", "PAL", "NTSC" });
	private final JCheckBox[] voiceBoxes = { new JCheckBox("V1", true), new JCheckBox("V2", true), new JCheckBox("V3", true) };
	private final JCheckBox filterBox = new JCheckBox("Filter", true);
	private final JButton saveButton = new JButton("Save as SID...");
	private final JComboBox<String> sourceBox = new JComboBox<>(new String[] { "Disk", "HVSC" });
	private final JButton findButton = new JButton("Find in HVSC");
	private final List<Item> hvscItems = new ArrayList<>();
	private File hvscRoot;
	private File hvscDir;
	private static File lastHvscDir;
	private boolean sourceChanging;
	private final JButton scanButton = new JButton("Scan PRG files");
	private final Meter meter = new Meter(false);
	private final Meter miniMeter = new Meter(true);
	private static MusicPlayerDialog instance;

	/** @return the open player window, or null */
	public static MusicPlayerDialog getOpenInstance() {
		return instance != null && instance.isDisplayable() ? instance : null;
	}

	/** Bring the window to the front. */
	public void bringToFront() {
		if (mini != null && mini.isVisible()) {
			mini.toFront();
			return;
		}
		toFront();
		requestFocus();
	}

	// ---- mini player ----

	private JDialog mini;
	private final JLabel miniTitle = new JLabel(" ");
	private final JLabel miniAuthor = new JLabel(" ");
	private final JLabel miniTime = new JLabel("0:00");
	private final JSlider miniSeek = new JSlider(0, 300, 0);
	private final JButton miniPlay = new JButton(TransportIcon.PLAY);
	private final JButton miniPrev = new JButton(TransportIcon.PREVIOUS);
	private final JButton miniNext = new JButton(TransportIcon.NEXT);
	private final JLabel miniSong = new JLabel(" ");
	private boolean miniSeekAdjusting;
	private static java.awt.Point miniLocation;

	private void buildMini() {
		mini = new JDialog(getOwner(), "Mini player", ModalityType.MODELESS);
		mini.setAlwaysOnTop(true);
		mini.setResizable(false);
		mini.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		mini.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				hideMini();
			}
		});
		miniTitle.setFont(miniTitle.getFont().deriveFont(Font.BOLD, 14f));
		miniAuthor.setFont(miniAuthor.getFont().deriveFont(12f));
		var expand = new JButton("Full player");
		expand.setToolTipText("Back to the full player window");
		expand.addActionListener(e -> hideMini());
		expand.setFocusable(false);
		for (var b : new JButton[] { miniPlay, miniPrev, miniNext }) {
			b.setFocusable(false);
		}
		miniPlay.addActionListener(e -> playPause());
		miniPrev.addActionListener(e -> changeSong(-1));
		miniNext.addActionListener(e -> changeSong(1));
		miniPrev.setToolTipText("Previous tune");
		miniNext.setToolTipText("Next tune");
		miniSeek.setEnabled(false);
		miniSeek.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				miniSeekAdjusting = true;
				if (miniSeek.getUI() instanceof javax.swing.plaf.basic.BasicSliderUI) {
					miniSeek.setValue(((javax.swing.plaf.basic.BasicSliderUI) miniSeek.getUI()).valueForXPosition(e.getX()));
				}
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				miniSeekAdjusting = false;
				var p = player;
				if (p != null) {
					p.seekTo = (long) miniSeek.getValue() * RATE;
				}
			}
		});
		var miniVolume = new JSlider(0, 100, volume.getValue());
		miniVolume.setPreferredSize(new Dimension(90, 22));
		miniVolume.addChangeListener(e -> volume.setValue(miniVolume.getValue()));
		volume.addChangeListener(e -> miniVolume.setValue(volume.getValue()));

		var texts = new JPanel(new GridBagLayout());
		var g = new GridBagConstraints();
		g.gridx = 0;
		g.weightx = 1;
		g.anchor = GridBagConstraints.WEST;
		g.fill = GridBagConstraints.HORIZONTAL;
		texts.add(miniTitle, g);
		texts.add(miniAuthor, g);
		var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		buttons.add(miniPrev);
		buttons.add(miniSong);
		buttons.add(miniPlay);
		buttons.add(miniNext);
		buttons.add(miniVolume);
		var time = new JPanel(new BorderLayout(6, 0));
		time.add(miniSeek, BorderLayout.CENTER);
		time.add(miniTime, BorderLayout.EAST);
		var south = new JPanel(new BorderLayout(0, 2));
		south.add(time, BorderLayout.NORTH);
		south.add(buttons, BorderLayout.CENTER);
		var top = new JPanel(new BorderLayout(8, 0));
		top.add(texts, BorderLayout.CENTER);
		top.add(expand, BorderLayout.EAST);
		var content = new JPanel(new BorderLayout(0, 6));
		content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		content.add(top, BorderLayout.NORTH);
		content.add(south, BorderLayout.CENTER);
		miniMeter.setPreferredSize(new Dimension(86, 86));
		var wrap = new JPanel(new BorderLayout(8, 0));
		wrap.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		content.setBorder(null);
		wrap.add(miniMeter, BorderLayout.WEST);
		wrap.add(content, BorderLayout.CENTER);
		mini.setContentPane(wrap);
		mini.setSize(480, 140);
	}

	private void showMini() {
		if (mini == null) {
			buildMini();
		}
		updateMini();
		if (miniLocation != null) {
			mini.setLocation(miniLocation);
		} else {
			mini.setLocationRelativeTo(this);
		}
		setVisible(false);
		mini.setVisible(true);
	}

	private void hideMini() {
		if (mini != null) {
			miniLocation = mini.getLocation();
			mini.setVisible(false);
		}
		setVisible(true);
		toFront();
	}

	private void updateMini() {
		if (mini == null || !mini.isVisible()) {
			return;
		}
		miniTitle.setText(infoTitle.getText());
		miniAuthor.setText(infoAuthor.getText());
		miniTime.setText(timeLabel.getText());
		miniSong.setText(songLabel.getText());
		miniPlay.setIcon(playButton.getIcon());
		miniPlay.setEnabled(playButton.isEnabled());
		miniPrev.setEnabled(prevSong.isEnabled());
		miniNext.setEnabled(nextSong.isEnabled());
		miniSeek.setEnabled(seek.isEnabled());
		if (!miniSeekAdjusting) {
			miniSeek.setMaximum(seek.getMaximum());
			miniSeek.setValue(seek.getValue());
		}
	}

	private Item current;
	private int song = 1;
	private volatile Player player;
	private final Timer ticker;
	private volatile boolean closed;
	private Thread scanner;

	public MusicPlayerDialog(Component parentComponent, String title, List<String> names, IntFunction<byte[]> loader, int start) {
		super(SwingUtilities.getWindowAncestor(parentComponent), title, ModalityType.MODELESS);
		this.loader = loader;
		for (int i = 0; i < names.size(); i++) {
			all.add(new Item(i, names.get(i)));
		}
		instance = this;
		initGui();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				closed = true;
				if (instance == MusicPlayerDialog.this) {
					instance = null;
				}
				stop();
				ticker.stop();
				if (mini != null) {
					mini.dispose();
				}
			}
		});
		ticker = new Timer(50, e -> tick());
		ticker.start();
		pack();
		setSize(Math.max(getWidth(), 820), Math.max(getHeight(), 480));
		setLocationRelativeTo(parentComponent);
		startScan(start);
	}

	// ---- layout ----

	private void initGui() {
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
				var it = (Item) value;
				var c = (DefaultListCellRenderer) super.getListCellRendererComponent(l, value, index, sel, focus);
				c.setText(it.up ? "[..]" : it.dir ? "[" + it.name + "]" : (it.tune != null ? "♪ " : "   ") + it.name);
				if (it.tune != null || it.dir) {
					c.setFont(c.getFont().deriveFont(Font.BOLD));
				}
				return c;
			}
		});
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				select(list.getSelectedValue());
			}
		});
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					int i = list.locationToIndex(e.getPoint());
					if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) {
						list.setSelectedIndex(i);
						activate();
					}
				}
			}
		});
		list.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
					activate();
				}
			}
		});
		var left = new JPanel(new BorderLayout());
		var sc = new JScrollPane(list);
		sc.setPreferredSize(new Dimension(230, 300));
		left.add(sc, BorderLayout.CENTER);
		var leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		leftTop.add(new JLabel("Source"));
		leftTop.add(sourceBox);
		leftTop.add(musicOnly);
		sourceBox.setToolTipText("Disk: the files of the disk image.  HVSC: browse your local HVSC collection (folder set in Settings / Emulators / HVSC).");
		sourceBox.addActionListener(e -> switchSource());
		left.add(leftTop, BorderLayout.NORTH);
		musicOnly.addActionListener(e -> refreshList());
		var leftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		scanButton.setToolTipText("Search all PRG files for SID music players (stops when finished)");
		scanButton.addActionListener(e -> startScan(-1));
		leftBottom.add(scanButton);
		findButton.setToolTipText("Look for the selected PRG music in your HVSC collection (same data at the same addresses)");
		findButton.addActionListener(e -> findInHvsc());
		findButton.setEnabled(false);
		leftBottom.add(findButton);
		left.add(leftBottom, BorderLayout.SOUTH);

		var right = new JPanel(new GridBagLayout());
		var g = new GridBagConstraints();
		g.insets = new Insets(2, 6, 2, 6);
		g.anchor = GridBagConstraints.WEST;
		g.fill = GridBagConstraints.HORIZONTAL;
		int row = 0;
		row = infoRow(right, g, row, "Title", infoTitle);
		row = infoRow(right, g, row, "Author", infoAuthor);
		row = infoRow(right, g, row, "Released", infoReleased);
		row = infoRow(right, g, row, "Format", infoFormat);
		row = infoRow(right, g, row, "Addresses", infoAddr);
		row = infoRow(right, g, row, "Size", infoSize);
		row = infoRow(right, g, row, "Speed / SIDs", infoSpeed);
		row = infoRow(right, g, row, "Text in file", infoText);
		row = infoRow(right, g, row, "HVSC", infoHvsc);
		stilArea.setEditable(false);
		stilArea.setLineWrap(true);
		stilArea.setWrapStyleWord(true);
		stilArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		var stilScroll = new JScrollPane(stilArea);
		stilScroll.setPreferredSize(new Dimension(300, 70));
		row = infoRow(right, g, row, "STIL", stilScroll);
		infoTitle.setFont(infoTitle.getFont().deriveFont(Font.BOLD, 15f));

		prevSong.addActionListener(e -> changeSong(-1));
		nextSong.addActionListener(e -> changeSong(1));

		g.gridx = 0;
		g.gridy = row++;
		g.gridwidth = 2;
		g.weighty = 1;
		g.fill = GridBagConstraints.BOTH;
		meter.setPreferredSize(new Dimension(400, 130));
		right.add(meter, g);
		g.weighty = 0;
		g.fill = GridBagConstraints.HORIZONTAL;

		g.gridy = row++;
		seek.setEnabled(false);
		seek.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				seekAdjusting = true;
				int v = seek.getUI() instanceof javax.swing.plaf.basic.BasicSliderUI
						? ((javax.swing.plaf.basic.BasicSliderUI) seek.getUI()).valueForXPosition(e.getX()) : seek.getValue();
				seek.setValue(v);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				seekAdjusting = false;
				var p = player;
				if (p != null) {
					p.seekTo = (long) seek.getValue() * RATE;
				}
			}
		});
		seek.addChangeListener(e -> {
			if (seekAdjusting) {
				timeLabel.setText(fmt(seek.getValue()));
			}
		});
		right.add(seek, g);

		var transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		transport.add(playButton);
		transport.add(stopButton);
		transport.add(javax.swing.Box.createHorizontalStrut(8));
		transport.add(prevSong);
		transport.add(songLabel);
		transport.add(nextSong);
		transport.add(javax.swing.Box.createHorizontalStrut(8));
		transport.add(timeLabel);
		transport.add(new JLabel("  Volume"));
		volume.setPreferredSize(new Dimension(110, 24));
		transport.add(volume);
		prevSong.setToolTipText("Previous tune");
		nextSong.setToolTipText("Next tune");
		playButton.setToolTipText("Play");
		stopButton.setToolTipText("Stop");
		playButton.setFocusable(false);
		stopButton.setFocusable(false);
		playButton.addActionListener(e -> playPause());
		stopButton.addActionListener(e -> stop());
		volume.addChangeListener(e -> applyLive());
		g.gridy = row++;
		right.add(transport, g);

		var opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		opts.add(new JLabel("Chip"));
		opts.add(modelBox);
		opts.add(new JLabel("Clock"));
		opts.add(clockBox);
		for (var b : voiceBoxes) {
			opts.add(b);
			b.addActionListener(e -> applyLive());
		}
		opts.add(filterBox);
		filterBox.addActionListener(e -> applyLive());
		modelBox.addActionListener(e -> restartIfPlaying());
		clockBox.addActionListener(e -> restartIfPlaying());
		g.gridy = row++;
		right.add(opts, g);

		var bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		saveButton.addActionListener(e -> saveAsSid());
		bottom.add(saveButton);
		var miniButton = new JButton("Mini player");
		miniButton.setToolTipText("Small always-on-top player window, so music can play while you work in DroiD64");
		miniButton.addActionListener(e -> showMini());
		bottom.add(miniButton);
		var close = new JButton("Close");
		close.addActionListener(e -> dispose());
		bottom.add(close);
		g.gridy = row++;
		right.add(bottom, g);

		status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		var content = new JPanel(new BorderLayout());
		content.add(left, BorderLayout.WEST);
		content.add(right, BorderLayout.CENTER);
		content.add(status, BorderLayout.SOUTH);
		setContentPane(content);
		enableControls(false);
	}

	private static int infoRow(JPanel p, GridBagConstraints g, int row, String label, Component value) {
		g.gridwidth = 1;
		g.gridx = 0;
		g.gridy = row;
		g.weightx = 0;
		var l = new JLabel(label + ":");
		l.setForeground(Color.GRAY);
		p.add(l, g);
		g.gridx = 1;
		g.weightx = 1;
		p.add(value, g);
		return row + 1;
	}

	private void enableControls(boolean on) {
		for (Component c : new Component[] { playButton, stopButton, prevSong, nextSong, saveButton }) {
			c.setEnabled(on);
		}
	}

	// ---- scanning ----

	private void startScan(int selectIndex) {
		if (scanner != null && scanner.isAlive()) {
			return;
		}
		scanButton.setEnabled(false);
		scanner = new Thread(() -> {
			int found = 0;
			int n = all.size();
			for (int i = 0; i < n && !closed; i++) {
				var it = all.get(i);
				if (it.checked) {
					continue;
				}
				final int k = i;
				SwingUtilities.invokeLater(() -> status.setText("Scanning " + (k + 1) + "/" + n + " : " + it.name));
				try {
					byte[] d = loader.apply(it.index);
					it.tune = detect(it, d);
				} catch (RuntimeException e) {	//NOSONAR
					it.tune = null;
				}
				it.checked = true;
				if (it.tune != null) {
					found++;
					SwingUtilities.invokeLater(this::refreshList);
				}
			}
			final int f = found;
			SwingUtilities.invokeLater(() -> {
				status.setText(f + " music file(s) found.");
				scanButton.setEnabled(true);
				refreshList();
				if (selectIndex >= 0) {
					selectInitial(selectIndex);
				}
			});
		}, "music-scan");
		scanner.setDaemon(true);
		scanner.start();
	}

	private boolean initialDone;

	private void selectInitial(int index) {
		if (initialDone) {
			return;
		}
		initialDone = true;
		Item pick = null;
		if (index >= 0 && index < all.size() && all.get(index).tune != null) {
			pick = all.get(index);
		} else {
			for (var it : all) {
				if (it.tune != null) {
					pick = it;
					break;
				}
			}
		}
		if (pick != null) {
			list.setSelectedValue(pick, true);
		}
	}

	private static SidTune detect(Item it, byte[] d) {
		if (d == null || d.length < 10) {
			return null;
		}
		if (SidTune.isSidFile(d)) {
			Hvsc.setRoot(Setting.HVSC_DIR.getString());
			it.hvsc = Hvsc.lookup(d);
			return SidTune.parse(d);
		}
		var f = SidDetector.probe(d);
		if (f == null) {
			return null;
		}
		var t = SidTune.fromPrg(d, f.init, f.play, it.name);
		var txt = SidTextScanner.scan(d);
		t.author = txt.author;
		t.released = txt.released;
		it.texts = txt.texts;
		return t;
	}

	private void refreshList() {
		var sel = list.getSelectedValue();
		model.clear();
		for (var it : hvscMode() ? hvscItems : all) {
			if (hvscMode() || !musicOnly.isSelected() || it.tune != null) {
				model.addElement(it);
			}
		}
		if (sel != null) {
			list.setSelectedValue(sel, true);
		}
	}

	// ---- HVSC browser ----

	/** Start with the HVSC browser (when the folder is set), e.g. if the lister has no files. */
	public void startInHvsc() {
		if (Hvsc.musicRoot(Setting.HVSC_DIR.getString()) != null) {
			sourceBox.setSelectedIndex(1);
		} else {
			status.setText("No files here. Set the HVSC folder in Settings / Emulators / HVSC to browse your SID collection.");
		}
	}

	private void switchSource() {
		if (sourceChanging) {
			return;
		}
		boolean hv = sourceBox.getSelectedIndex() == 1;
		if (hv) {
			hvscRoot = Hvsc.musicRoot(Setting.HVSC_DIR.getString());
			if (hvscRoot == null) {
				status.setText("HVSC folder not found: set it in Settings / Emulators / HVSC.");
				sourceChanging = true;
				sourceBox.setSelectedIndex(0);
				sourceChanging = false;
				return;
			}
			hvscDir = lastHvscDir != null && lastHvscDir.isDirectory() && lastHvscDir.getPath().startsWith(hvscRoot.getPath()) ? lastHvscDir : hvscRoot;
			loadHvscDir();
		}
		musicOnly.setEnabled(!hv);
		scanButton.setEnabled(!hv);
		if (!hv) {
			setTitle(getTitle().replaceAll(" \\[HVSC.*\\]$", ""));
		}
		stop();
		refreshList();
		if (model.size() > 0) {
			list.setSelectedIndex(hv && model.size() > 1 && hvscItems.get(0).up ? 1 : 0);
		}
	}

	private void loadHvscDir() {
		lastHvscDir = hvscDir;
		hvscItems.clear();
		if (!hvscDir.equals(hvscRoot)) {
			var u = new Item(-1, "..");
			u.up = true;
			u.dir = true;
			u.file = hvscDir.getParentFile();
			hvscItems.add(u);
		}
		var files = hvscDir.listFiles();
		if (files == null) {
			return;
		}
		java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		for (int pass = 0; pass < 2; pass++) {
			for (var f : files) {
				boolean d = f.isDirectory();
				if ((pass == 0) == d && (d || f.getName().toLowerCase().endsWith(".sid")) && !f.getName().startsWith(".")) {
					var it = new Item(-1, f.getName());
					it.file = f;
					it.dir = d;
					hvscItems.add(it);
				}
			}
		}
		setTitle(getTitle().replaceAll(" \\[.*\\]$", "") + " [HVSC " + hvscDir.getPath().substring(hvscRoot.getPath().length()).replace('\\', '/') + "/]");
	}

	private boolean hvscMode() {
		return sourceBox.getSelectedIndex() == 1;
	}

	/** Double click or Enter: open the folder, or play the tune. */
	private void activate() {
		if (current == null) {
			return;
		}
		if (current.dir) {
			if (current.file != null) {
				hvscDir = current.file;
				loadHvscDir();
				refreshList();
				if (model.size() > 0) {
					list.setSelectedIndex(model.size() > 1 && hvscItems.get(0).up ? 1 : 0);
				}
			}
		} else if (current.tune != null) {
			start();
		}
	}

	private SidTune ensureDetected(Item it) {
		if (!it.checked && !it.dir) {
			try {
				byte[] d = it.file != null ? Files.readAllBytes(it.file.toPath()) : loader.apply(it.index);
				it.tune = detect(it, d);
			} catch (IOException | RuntimeException e) {	//NOSONAR
				it.tune = null;
			}
			it.checked = true;
		}
		return it.tune;
	}

	/** Look for the selected PRG music in the HVSC collection by comparing the data. */
	private void findInHvsc() {
		var it = current;
		var root = Hvsc.musicRoot(Setting.HVSC_DIR.getString());
		if (it == null || it.tune == null || root == null) {
			return;
		}
		final var t = it.tune;
		findButton.setEnabled(false);
		infoHvsc.setText("searching...");
		var th = new Thread(() -> {
			var hits = new ArrayList<String>();
			int[] count = { 0 };
			try (var walk = Files.walk(root.toPath())) {
				var it2 = walk.filter(x -> x.toString().toLowerCase().endsWith(".sid")).iterator();
				while (it2.hasNext() && !closed) {
					var f = it2.next();
					if (++count[0] % 2000 == 0) {
						final int n = count[0];
						SwingUtilities.invokeLater(() -> status.setText("Searching HVSC... " + n + " files"));
					}
					try {
						var raw = Files.readAllBytes(f);
						if (!SidTune.isSidFile(raw)) {
							continue;
						}
						var sid = SidTune.parse(raw);
						int pct = matchPercent(t, sid);
						if (pct >= 90) {
							hits.add(pct + "% " + f.toString().substring(root.toString().length()).replace('\\', '/'));
						}
					} catch (IOException | RuntimeException e) {	//NOSONAR
						// skip unreadable or broken files
					}
				}
			} catch (IOException e) {	//NOSONAR
				// ignore
			}
			final int total = count[0];
			SwingUtilities.invokeLater(() -> {
				hits.sort(java.util.Comparator.reverseOrder());
				it.found = hits.isEmpty() ? "No match among " + total + " HVSC files (same data at the same addresses)" : "Looks like: " + String.join("  |  ", hits.subList(0, Math.min(3, hits.size())));
				status.setText("HVSC search finished.");
				if (current == it) {
					infoHvsc.setText("<html>" + it.found.replace("&", "&amp;") + "</html>");
					findButton.setEnabled(true);
				}
			});
		}, "hvsc-find");
		th.setDaemon(true);
		th.start();
	}

	/** Percentage of the SID data which equals the PRG at the same addresses (0 if less than 60% overlaps). */
	static int matchPercent(SidTune prg, SidTune sid) {
		int s0 = sid.loadAddress;
		int s1 = s0 + sid.data.length;
		int p0 = prg.loadAddress;
		int p1 = p0 + prg.data.length;
		int from = Math.max(s0, p0);
		int to = Math.min(s1, p1);
		if (to <= from || (to - from) * 10 < sid.data.length * 6) {
			return 0;
		}
		int eq = 0;
		for (int a = from; a < to; a++) {
			if (sid.data[a - s0] == prg.data[a - p0]) {
				eq++;
			}
		}
		return (int) (100L * eq / sid.data.length);
	}

	// ---- selection and info ----

	private void select(Item it) {
		stop();
		current = it;
		if (it != null && it.file != null && !it.dir && !it.checked) {
			ensureDetected(it);
		}
		findButton.setEnabled(it != null && it.tune != null && it.tune.source == SidTune.Source.PRG && Hvsc.musicRoot(Setting.HVSC_DIR.getString()) != null);
		if (it == null || it.tune == null) {
			enableControls(false);
			for (var l : new JLabel[] { infoTitle, infoAuthor, infoReleased, infoFormat, infoAddr, infoSize, infoSpeed, infoText, infoHvsc }) {
				l.setText("-");
			}
			stilArea.setText("");
			songLabel.setText("- / -");
			if (it != null) {
				status.setText(it.dir ? "Folder - double-click to open." : it.checked ? "'" + it.name + "' does not look like SID music." : "Not scanned yet.");
			}
			return;
		}
		var t = it.tune;
		song = Math.max(1, Math.min(t.startSong, t.songs));
		infoTitle.setText(t.title.isEmpty() ? it.name : t.title);
		infoAuthor.setText(t.author.isEmpty() ? "(unknown)" : t.author);
		infoReleased.setText(t.released.isEmpty() ? "(unknown)" : t.released);
		infoFormat.setText(describeFormat(t));
		infoAddr.setText(String.format("load $%04X  init $%04X  play $%04X", t.loadAddress, t.initAddress, t.playAddress));
		infoSize.setText(t.data.length + " bytes ($" + Integer.toHexString(t.loadAddress).toUpperCase() + "-$"
				+ Integer.toHexString(t.endAddress()).toUpperCase() + ")");
		int sids = 1 + (t.secondSid != 0 ? 1 : 0) + (t.thirdSid != 0 ? 1 : 0);
		infoText.setText(it.texts == null || it.texts.isEmpty() ? "-" : "<html>" + String.join("<br>", it.texts.subList(0, Math.min(3, it.texts.size()))).replace("&", "&amp;").replace("<br>", "<br>") + "</html>");
		infoSpeed.setText((t.ciaSpeed(song) ? "CIA timer (60 Hz)" : "vertical blank") + ",  " + sids + " SID" + (sids > 1 ? "s" : ""));
		modelBox.setSelectedItem(t.modelFlag() == 2 ? SidChip.Model.MOS8580 : SidChip.Model.MOS6581);
		clockBox.setSelectedIndex(0);
		showHvsc();
		if (!it.found.isEmpty()) {
			infoHvsc.setText("<html>" + it.found.replace("&", "&amp;") + "</html>");
		}
		updateSongLabel();
		enableControls(true);
		status.setText("Press Play.");
	}

	private static String describeFormat(SidTune t) {
		var sb = new StringBuilder();
		if (t.source == SidTune.Source.PRG) {
			sb.append("Plain PRG with built-in player (detected)");
		} else {
			sb.append(t.version >= 2 ? "PSID v" + t.version : "PSID");
			if (t.isMus()) {
				sb.append(" (MUS)");
			}
		}
		int c = t.clockFlag();
		if (c != 0) {
			sb.append(", ").append(c == 1 ? "PAL" : c == 2 ? "NTSC" : "PAL/NTSC");
		}
		return sb.toString();
	}

	/** HVSC row, STIL text and the length of the selected tune. */
	private void showHvsc() {
		var h = current == null ? null : current.hvsc;
		if (current == null || current.tune == null || current.tune.source == SidTune.Source.PRG) {
			infoHvsc.setText("n/a (PRG file, not a SID file)");
			stilArea.setText("");
		} else if (h == null || h.status == Hvsc.Status.UNAVAILABLE) {
			infoHvsc.setText("HVSC folder not set (Settings / Emulators / HVSC)");
			stilArea.setText("");
		} else if (h.status == Hvsc.Status.NOT_FOUND) {
			infoHvsc.setText("<html><b style='color:#c04000'>NOT in HVSC</b> (new, modified or never released tune)</html>");
			stilArea.setText("");
		} else {
			infoHvsc.setText("<html><b style='color:#208020'>In HVSC</b>: " + h.path.replace("&", "&amp;") + "</html>");
			var st = h.stilFor(song);
			stilArea.setText(st);
			stilArea.setCaretPosition(0);
		}
	}

	/** @return the known length of the current tune in milliseconds, or -1 */
	private long knownLengthMs() {
		var h = current == null ? null : current.hvsc;
		return h != null && h.status == Hvsc.Status.FOUND ? h.lengthMs(song) : -1;
	}

	private void updateSongLabel() {
		int songs = current != null && current.tune != null ? current.tune.songs : 1;
		songLabel.setText(song + " / " + songs);
		prevSong.setEnabled(song > 1);
		nextSong.setEnabled(song < songs);
	}

	private void changeSong(int d) {
		if (current == null || current.tune == null) {
			return;
		}
		int s = Math.max(1, Math.min(current.tune.songs, song + d));
		if (s == song) {
			return;
		}
		boolean was = player != null;
		stop();
		song = s;
		showHvsc();
		updateSongLabel();
		if (was) {
			start();
		}
	}

	// ---- playback ----

	/** The play button shows a pause symbol while the music is playing. */
	private void setPlayIcon(boolean playing) {
		playButton.setIcon(playing ? TransportIcon.PAUSE : TransportIcon.PLAY);
		playButton.setToolTipText(playing ? "Pause" : "Play");
	}

	private void playPause() {
		if (player != null) {
			if (player.paused) {
				player.paused = false;
				setPlayIcon(true);
			} else {
				player.paused = true;
				setPlayIcon(false);
			}
			return;
		}
		start();
	}

	private void restartIfPlaying() {
		if (player != null) {
			stop();
			start();
		}
	}

	private void start() {
		if (current == null || current.tune == null) {
			return;
		}
		stop();
		boolean pal;
		switch (clockBox.getSelectedIndex()) {
		case 1:
			pal = true;
			break;
		case 2:
			pal = false;
			break;
		default:
			pal = current.tune.clockFlag() != 2;
		}
		var tune = current.tune;
		var eng = new SidEngine(tune, song, pal, (SidChip.Model) modelBox.getSelectedItem(), RATE);
		eng.start();
		if (eng.hasFailed()) {
			status.setText("Cannot play: " + eng.getProblem());
			return;
		}
		var p = new Player(eng, tune, pal, (SidChip.Model) modelBox.getSelectedItem());
		player = p;
		applyLive();
		setPlayIcon(true);
		long len = knownLengthMs();
		seek.setMaximum(len > 0 ? (int) ((len + 999) / 1000) : 300);
		seek.setEnabled(true);
		p.start();
	}

	private void stop() {
		var p = player;
		player = null;
		if (p != null) {
			p.running = false;
		}
		setPlayIcon(false);
		timeLabel.setText("0:00");
		seek.setValue(0);
		seek.setMaximum(300);
		seek.setEnabled(false);
		meter.reset();
		miniMeter.reset();
	}

	/** The known length of the tune is over: next tune of the file, else next music file, else stop. */
	private void advanceAfterEnd() {
		if (current == null || current.tune == null) {
			stop();
			return;
		}
		if (song < current.tune.songs) {
			changeSong(1);
			return;
		}
		int i = model.indexOf(current);
		for (int j = i + 1; i >= 0 && j < model.size(); j++) {
			var nx = model.get(j);
			if (!nx.dir && (nx.tune != null || ensureDetected(nx) != null)) {
				list.setSelectedIndex(j);
				list.ensureIndexIsVisible(j);
				start();
				return;
			}
		}
		stop();
		status.setText("Finished.");
	}

	private void applyLive() {
		var p = player;
		if (p != null) {
			p.applySettings();
		}
	}

	private static String fmt(long sec) {
		return String.format("%d:%02d", sec / 60, sec % 60);
	}

	private void tick() {
		updateMini();
		var p = player;
		if (p == null) {
			return;
		}
		if (p.error != null) {
			status.setText(p.error);
			stop();
			return;
		}
		long sec = p.samplesPlayed / RATE;
		long lenMs = knownLengthMs();
		if (lenMs > 0 && p.seekTo < 0 && !seekAdjusting && p.samplesPlayed * 1000 / RATE >= lenMs) {
			advanceAfterEnd();
			return;
		}
		if (!seekAdjusting) {
			timeLabel.setText(lenMs > 0 ? fmt(sec) + " / " + fmt((lenMs + 500) / 1000) : fmt(sec));
			if (lenMs <= 0 && sec + 30 > seek.getMaximum()) {
				seek.setMaximum((int) sec + 120);
			}
			if (p.seekTo < 0) {
				seek.setValue((int) sec);
			}
		}
		if (p.engine.hasFailed()) {
			status.setText("Playback stopped: " + p.engine.getProblem());
			stop();
			return;
		}
		status.setText(p.seekTo >= 0 ? "Seeking..." : p.paused ? "Paused" : "Playing" + (p.engine.isAlive() ? "" : " (play routine did not return)"));
		meter.update(p.engine);
		if (mini != null && mini.isVisible()) {
			miniMeter.update(p.engine);
		}
	}

	/** Audio thread. */
	private final class Player extends Thread {
		volatile SidEngine engine;
		volatile boolean running = true;
		volatile boolean paused;
		volatile long samplesPlayed;
		volatile long seekTo = -1;
		volatile String error;
		private final SidTune tune;
		private final boolean pal;
		private final SidChip.Model chipModel;

		Player(SidEngine engine, SidTune tune, boolean pal, SidChip.Model chipModel) {
			super("sid-player");
			this.engine = engine;
			this.tune = tune;
			this.pal = pal;
			this.chipModel = chipModel;
			setDaemon(true);
		}

		void applySettings() {
			int mask = 0;
			for (int i = 0; i < 3; i++) {
				if (!voiceBoxes[i].isSelected()) {
					mask |= 1 << i;
				}
			}
			boolean filter = filterBox.isSelected();
			double gain = volume.getValue() / 70.0;
			var e = engine;
			for (int i = 0; i < e.getChipCount(); i++) {
				e.getChip(i).setMuteMask(mask);
				e.getChip(i).setFilterEnabled(filter);
			}
			e.setGain(gain);
		}

		/** Move to a position by running the emulation silently (about 50 times faster than real time). */
		private void doSeek(long target) {
			if (target < samplesPlayed) {
				var e = new SidEngine(tune, song, pal, chipModel, RATE);
				e.start();
				engine = e;
				samplesPlayed = 0;
				applySettings();
			}
			short[] scratch = new short[RATE / 10];
			while (running && samplesPlayed < target && !engine.hasFailed()) {
				int n = (int) Math.min(scratch.length, target - samplesPlayed);
				engine.read(scratch, 0, n);
				samplesPlayed += n;
			}
		}

		@Override
		public void run() {
			SourceDataLine line = null;
			try {
				var fmt = new AudioFormat(RATE, 16, 1, true, false);
				line = AudioSystem.getSourceDataLine(fmt);
				line.open(fmt, RATE / 4 * 2);
				line.start();
				short[] buf = new short[RATE / 50];
				byte[] out = new byte[buf.length * 2];
				while (running) {
					long target = seekTo;
					if (target >= 0) {
						doSeek(target);
						line.flush();
						seekTo = -1;
						continue;
					}
					if (paused) {
						line.flush();
						Thread.sleep(30);
						continue;
					}
					engine.read(buf, 0, buf.length);
					for (int i = 0; i < buf.length; i++) {
						out[2 * i] = (byte) buf[i];
						out[2 * i + 1] = (byte) (buf[i] >> 8);
					}
					line.write(out, 0, out.length);
					samplesPlayed += buf.length;
					if (engine.hasFailed()) {
						break;
					}
				}
			} catch (LineUnavailableException | IllegalArgumentException e) {
				error = "No audio output available: " + e.getMessage();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				if (line != null) {
					line.stop();
					line.close();
				}
			}
		}
	}

	// ---- save ----

	private void saveAsSid() {
		if (current == null || current.tune == null) {
			return;
		}
		var t = current.tune;
		var copy = SidTune.parse(t.toPsid());
		if (copy == null) {
			copy = t;
		}
		var title = new JTextField(copy.title.isEmpty() ? current.name : copy.title, 28);
		var author = new JTextField(copy.author, 28);
		var released = new JTextField(copy.released, 28);
		var songs = new JTextField(String.valueOf(copy.songs), 4);
		var p = new JPanel(new GridBagLayout());
		var g = new GridBagConstraints();
		g.insets = new Insets(2, 4, 2, 4);
		g.anchor = GridBagConstraints.WEST;
		int r = 0;
		for (var pair : new Object[][] { { "Title", title }, { "Author", author }, { "Released", released }, { "Number of tunes", songs } }) {
			g.gridx = 0;
			g.gridy = r;
			p.add(new JLabel((String) pair[0]), g);
			g.gridx = 1;
			p.add((Component) pair[1], g);
			r++;
		}
		if (JOptionPane.showConfirmDialog(this, p, "Save as SID", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		copy.title = title.getText();
		copy.author = author.getText();
		copy.released = released.getText();
		try {
			copy.songs = Math.max(1, Math.min(256, Integer.parseInt(songs.getText().trim())));
		} catch (NumberFormatException e) {
			copy.songs = t.songs;
		}
		copy.startSong = Math.min(copy.startSong, copy.songs);
		var fc = new JFileChooser();
		String base = current.name.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
		if (base.isEmpty()) {
			base = "music";
		}
		fc.setSelectedFile(new File(base.toLowerCase().endsWith(".sid") ? base : base + ".sid"));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			Files.write(fc.getSelectedFile().toPath(), copy.toPsid());
			status.setText("Saved " + fc.getSelectedFile().getName());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ---- level meters and scope ----

	private static final class Meter extends JPanel {
		private static final long serialVersionUID = 1L;
		private final double[] level = new double[3];	// smoothed levels which are drawn
		private final double[] peak = new double[3];
		private final short[] scope = new short[1024];
		private final float[] shown = new float[512];	// triggered, smoothed scope trace
		private final float[] ghost = new float[512];	// previous frame, drawn faint (afterglow)
		private int chips = 1;
		private boolean live;
		private transient SidEngine engine;
		private final javax.swing.Timer animation = new javax.swing.Timer(16, e -> animate());
		private long lastNanos;

		private boolean compact;

		/** @param compact small square variant for the mini player: three bars above a waveform */
		Meter(boolean compact) {
			this.compact = compact;
			setBackground(Color.BLACK);
			setDoubleBuffered(true);
		}

		void reset() {
			live = false;
			engine = null;
			animation.stop();
			java.util.Arrays.fill(level, 0);
			java.util.Arrays.fill(peak, 0);
			java.util.Arrays.fill(scope, (short) 0);
			java.util.Arrays.fill(shown, 0f);
			java.util.Arrays.fill(ghost, 0f);
			repaint();
		}

		/** Called from the player timer: remember the engine; the meter polls it at about 60 frames per second itself. */
		void update(SidEngine e) {
			live = true;
			engine = e;
			if (!animation.isRunning()) {
				lastNanos = System.nanoTime();
				animation.start();
			}
		}

		private void animate() {
			var e = engine;
			if (e == null) {
				return;
			}
			long now = System.nanoTime();
			double dt = Math.min(0.1, (now - lastNanos) / 1e9);
			lastNanos = now;
			for (int i = 0; i < 3; i++) {
				double target = Math.max(0, Math.min(1, e.getChip(0).getVoiceLevel(i)));
				if (target > level[i]) {
					level[i] += (target - level[i]) * (1 - Math.exp(-dt / 0.012));	// quick attack
				} else {
					level[i] += (target - level[i]) * (1 - Math.exp(-dt / 0.090));	// smooth fall
				}
				if (level[i] >= peak[i]) {
					peak[i] = level[i];
				} else {
					peak[i] = Math.max(level[i], peak[i] - dt * 0.6);	// peak marker falls slowly
				}
			}
			int pos = e.getScopePos() & 1023;
			var src = e.getScope();
			// newest sample at the end
			for (int i = 0; i < 1024; i++) {
				scope[i] = src[(pos + i) & 1023];
			}
			chips = e.getChipCount();
			// trigger on a rising zero crossing so that the trace stands still instead of scrolling
			int t = 0;
			for (int i = 1; i < 512; i++) {
				if (scope[i - 1] < 0 && scope[i] >= 0) {
					t = i;
					break;
				}
			}
			System.arraycopy(shown, 0, ghost, 0, 512);
			for (int i = 0; i < 512; i++) {
				shown[i] += (scope[t + i] - shown[i]) * 0.55f;
			}
			repaint();
		}

		private static java.awt.geom.Path2D.Float tracePath(float[] data, int x0, int sw, int h) {
			var path = new java.awt.geom.Path2D.Float();
			for (int x = 0; x < sw; x++) {
				double pos = x * (data.length - 1.0) / Math.max(1, sw - 1);
				int i0 = (int) pos;
				int i1 = Math.min(data.length - 1, i0 + 1);
				double v = data[i0] + (data[i1] - data[i0]) * (pos - i0);
				float y = (float) (h / 2.0 - v * (h / 2.0 - 8) / 32768.0);
				if (x == 0) {
					path.moveTo(x0, y);
				} else {
					path.lineTo(x0 + x, y);
				}
			}
			return path;
		}

		private void paintCompact(java.awt.Graphics2D g, int w, int h) {
			int barsH = h * 11 / 20;
			int gap = 4;
			int barW = (w - 8 - 2 * gap) / 3;
			for (int i = 0; i < 3; i++) {
				int x = 4 + i * (barW + gap);
				double bh = level[i] * (barsH - 4);
				g.setColor(new Color(40, 40, 40));
				g.fillRect(x, 4, barW, barsH - 4);
				g.setPaint(new java.awt.GradientPaint(0, 4, new Color(150, 240, 90), 0, barsH, new Color(40, 170, 70)));
				g.fill(new java.awt.geom.Rectangle2D.Double(x, barsH - bh, barW, bh));
				g.setColor(new Color(220, 255, 180));
				int py = (int) Math.round(barsH - peak[i] * (barsH - 4));
				g.fillRect(x, Math.min(py, barsH - 2), barW, 2);
			}
			if (live) {
				int top = barsH + 2;
				int th = h - top - 2;
				g.translate(0, top);
				g.setStroke(new java.awt.BasicStroke(1.3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g.setColor(new Color(120, 255, 120));
				g.draw(tracePath(shown, 4, w - 8, th + 8));
				g.translate(0, -top);
			}
		}

		@Override
		protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			var g = (java.awt.Graphics2D) g0.create();
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
			int w = getWidth();
			int h = getHeight();
			if (compact) {
				paintCompact(g, w, h);
				g.dispose();
				return;
			}
			int barW = 38;
			int barH = h - 24;
			for (int i = 0; i < 3; i++) {
				int x = 8 + i * (barW + 6);
				double bh = level[i] * barH;
				g.setColor(new Color(40, 40, 40));
				g.fillRect(x, 6, barW, barH);
				g.setPaint(new java.awt.GradientPaint(0, 6, new Color(150, 240, 90), 0, 6 + barH, new Color(40, 170, 70)));
				g.fill(new java.awt.geom.Rectangle2D.Double(x, 6 + barH - bh, barW, bh));
				g.setColor(new Color(220, 255, 180));
				int py = (int) Math.round(6 + barH - peak[i] * barH);
				g.fillRect(x, Math.min(py, 6 + barH - 2), barW, 2);
				g.setColor(Color.LIGHT_GRAY);
				g.drawString("V" + (i + 1), x + 12, h - 6);
			}
			int x0 = 8 + 3 * (barW + 6) + 10;
			int sw = w - x0 - 8;
			if (sw < 20) {
				g.dispose();
				return;
			}
			g.setColor(new Color(30, 60, 30));
			g.drawLine(x0, h / 2, x0 + sw, h / 2);
			if (live) {
				g.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g.setColor(new Color(120, 255, 120, 70));
				g.draw(tracePath(ghost, x0, sw, h));
				g.setColor(new Color(120, 255, 120));
				g.draw(tracePath(shown, x0, sw, h));
				if (chips > 1) {
					g.setColor(Color.GRAY);
					g.drawString(chips + " SIDs (meters: first chip)", x0 + 4, 14);
				}
			}
			g.dispose();
		}
	}
}
