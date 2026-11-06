package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

import droid64.d64.DiskImage;

/**
 * The BAM / Sector editor: a grid of all sectors of the disk (the same squares as the OpenCBM sector map),
 * the sector editor, and a check of the image. Click a sector to edit it.
 */
public class GraphicBamDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final transient DiskImage image;
	private final JLabel info = new JLabel(" ");
	private final SectorGrid grid;
	private final SectorEditorPanel editor;
	private final DefaultListModel<ImageCheck.Finding> findingsModel = new DefaultListModel<>();
	private final JList<ImageCheck.Finding> findingsList = new JList<>(findingsModel);
	private final JLabel checkSummary = new JLabel("Press \"Check image\" to check the BAM, the files and the disk errors.");
	private int selTrack;
	private int selSector;

	public GraphicBamDialog(java.awt.Frame owner, DiskPanel panel, DiskImage image, String title) {
		super(owner, "BAM / Sector editor - " + title, false);
		this.image = image;
		this.grid = new SectorGrid(image);
		setLayout(new BorderLayout());
		int firstTrack = image instanceof droid64.d64.D81 ? 40 : Math.min(18, image.getTrackCount());
		this.editor = new SectorEditorPanel(panel, image, firstTrack, 0);
		selTrack = firstTrack;
		selSector = 0;
		grid.setSelected(firstTrack, 0);
		editor.setSectorListener((t, s) -> {
			selTrack = t;
			selSector = s;
			grid.setSelected(t, s);
			updateInfo(-1, -1);
		});
		grid.setClickListener((t, s) -> select(t, s));
		grid.setHoverListener(this::updateInfo);

		var gridScroll = new JScrollPane(grid);
		gridScroll.getViewport().setBackground(grid.getBackground());
		gridScroll.setBorder(null);

		findingsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		findingsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		findingsList.addListSelectionListener(e -> {
			var f = findingsList.getSelectedValue();
			if (!e.getValueIsAdjusting() && f != null && f.track > 0) {
				select(f.track, f.sector);
			}
		});
		var checkButton = new JButton("Check image");
		checkButton.setToolTipText("Check the BAM against the files, the file chains against the directory and the disk errors of the image");
		checkButton.addActionListener(e -> runCheck());
		var head = new JPanel(new BorderLayout(8, 0));
		head.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
		head.add(checkButton, BorderLayout.WEST);
		head.add(checkSummary, BorderLayout.CENTER);
		var findings = new JPanel(new BorderLayout());
		findings.add(head, BorderLayout.NORTH);
		var listScroll = new JScrollPane(findingsList);
		listScroll.setPreferredSize(new Dimension(300, 120));
		findings.add(listScroll, BorderLayout.CENTER);

		var left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, gridScroll, findings);
		left.setResizeWeight(1);
		left.setBorder(null);
		var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editor);
		split.setResizeWeight(0);
		add(split, BorderLayout.CENTER);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				closeIfAllowed();
			}
		});
		var bottom = new JPanel(new BorderLayout());
		bottom.add(info, BorderLayout.CENTER);
		var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		var refresh = new JButton("Refresh");
		var close = new JButton("Close");
		refresh.addActionListener(e -> {
			grid.setMarks(null);
			findingsModel.clear();
			checkSummary.setText("Press \"Check image\" to check the BAM, the files and the disk errors.");
			grid.repaint();
			updateInfo(-1, -1);
		});
		close.addActionListener(e -> closeIfAllowed());
		buttons.add(refresh);
		buttons.add(close);
		bottom.add(buttons, BorderLayout.EAST);
		add(bottom, BorderLayout.SOUTH);
		updateInfo(-1, -1);
		int w = Math.min(grid.getPreferredSize().width + 20, 700) + editor.getPreferredSize().width + 30;
		int h = Math.max(Math.min(grid.getPreferredSize().height + 260, 800), editor.getPreferredSize().height + 110);
		setSize(w, h);
		setLocationRelativeTo(owner);
	}

	private void select(int t, int s) {
		editor.gotoSector(t, s);
		editor.focusEditor();
	}

	private void runCheck() {
		var result = ImageCheck.run(image);
		findingsModel.clear();
		result.findings.forEach(findingsModel::addElement);
		grid.setMarks(result.marks);
		int errors = result.count(ImageCheck.ERROR);
		int warnings = result.count(ImageCheck.WARN);
		String text = errors + warnings == 0
				? "No problems found (" + result.files + " files, " + result.blocksChecked + " blocks)."
				: errors + " errors, " + warnings + " warnings (" + result.files + " files checked).";
		if (!image.hasErrorInfo() && image.getClass().getSimpleName().startsWith("D64")) {
			text += " The image has no disk error info.";
		}
		checkSummary.setText(text);
	}

	private void closeIfAllowed() {
		if (editor.confirmLeave()) {
			dispose();
		}
	}

	private void updateInfo(int t, int s) {
		int free = 0;
		int used = 0;
		for (int tr = 1; tr <= image.getTrackCount(); tr++) {
			for (int se = 0; se < image.getMaxSectors(tr); se++) {
				if (image.isSectorFree(tr, se)) {
					free++;
				} else {
					used++;
				}
			}
		}
		var text = "Free: " + free + "   Used: " + used + "   (click a sector to edit it on the right)";
		if (t > 0) {
			text = "Track " + t + ", sector " + s + ": " + (image.isSectorFree(t, s) ? "free" : "used")
					+ (image.getSectorError(t, s) != 0 ? ", " + ImageCheck.errorText(image.getSectorError(t, s)) : "")
					+ "     " + text;
		}
		info.setText(text);
	}

	public static void show(java.awt.Frame owner, DiskPanel panel, DiskImage image, String title) {
		new GraphicBamDialog(owner, panel, image, title).setVisible(true);
	}
}
