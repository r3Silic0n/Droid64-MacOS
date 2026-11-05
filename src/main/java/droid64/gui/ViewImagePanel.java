package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**<pre style='font-family:sans-serif;'>
 * Created on 2015-Oct-15
 *
 *   droiD64 - A graphical file manager for D64 files
 *   Copyright (C) 2015 Henrik Wetterström
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
 *   http://droid64.sourceforge.net
 *
 * @author Henrik
 * </pre>
 */
public class ViewImagePanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final MainPanel mainPanel;
	private final ImagePanel imgPanel = new ImagePanel();
	private final String title;
	private final JButton closeButton = new JButton("Close");
	private JDialog dialog;
	private List<byte[]> dataList;
	private List<String> nameList;
	private int currentIndex = 0;
	private final JComboBox<CommodoreGraphics.Format> formatBox = new JComboBox<>(CommodoreGraphics.Format.values());
	private final JComboBox<String> zoomBox = new JComboBox<>(new String[] { "1x", "2x", "3x" });
	private final JLabel infoLabel = new JLabel(" ");
	private boolean updating = false;
	private final JButton prgButton = new JButton("Save PRG");
	private final DefaultListModel<String> fileModel = new DefaultListModel<>();
	private final JList<String> fileList = new JList<>(fileModel);
	private final JScrollPane fileScroll = new JScrollPane(fileList);
	private java.util.function.IntFunction<byte[]> loader;

	public ViewImagePanel(String title, MainPanel mainPanel) {
		this.mainPanel = mainPanel;
		this.title = title;
		this.dialog = new JDialog(mainPanel.getParent(), title, true);
		initGUI();
		addHierarchyListener(e -> GuiHelper.hierarchyListenerResizer(SwingUtilities.getWindowAncestor(this)));
	}

	public void show(List<byte[]> dataList, List<String> nameList) throws IOException {
		this.dataList = dataList != null ? dataList : new ArrayList<>();
		this.nameList = nameList != null ? nameList : new ArrayList<>();
		currentIndex = 0;
		showPicture(getNextImage());
		showImage();
	}

	/**
	 * Show the files of a list; the picture follows the selection in the list (cursor keys or mouse).
	 * @param names file names
	 * @param loader gets the data of file number i
	 * @param start file number to show first
	 */
	public void show(List<String> names, java.util.function.IntFunction<byte[]> loader, int start) {
		this.loader = null;
		fileModel.clear();
		names.forEach(fileModel::addElement);
		this.loader = loader;
		fileScroll.setVisible(true);
		this.dataList = null;
		int first = Math.max(0, Math.min(start, names.size() - 1));
		fileList.setSelectedIndex(first);
		fileList.ensureIndexIsVisible(first);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowOpened(java.awt.event.WindowEvent e) {
				fileList.requestFocusInWindow();
			}
		});
		showImage();
	}

	private void showListEntry(int index) {
		try {
			byte[] data = null;
			try {
				data = loader.apply(index);
			} catch (RuntimeException e) {	//NOSONAR
				data = null;
			}
			showPicture(new CbmPicture(data, fileModel.get(index)));
		} catch (IOException e) {
			mainPanel.appendConsole("Failed to get image: " + e.getMessage());
		}
	}

	public void show(Image img, String name) {
		imgPanel.setImage(img, name);
		showImage();
	}

	private void showImage() {
		closeButton.addActionListener(e -> dialog.dispose());

		dialog.setTitle(title);
		dialog.setContentPane(this);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.pack();
		dialog.setLocationRelativeTo(mainPanel.getParent());
		dialog.setVisible(true);
	}

	private void initGUI() {
		setLayout(new BorderLayout());
		var topPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		topPanel.add(new JLabel("Format"));
		topPanel.add(formatBox);
		topPanel.add(new JLabel("Zoom"));
		topPanel.add(zoomBox);
		var nextButton = new JButton("Next");
		nextButton.setToolTipText("Show the next file (or click the picture)");
		nextButton.addActionListener(ae -> clickedImage());
		topPanel.add(nextButton);
		topPanel.add(infoLabel);
		add(topPanel, BorderLayout.NORTH);
		zoomBox.setSelectedIndex(1);
		imgPanel.setScale(2);
		formatBox.addActionListener(ae -> {
			if (!updating) {
				redraw();
			}
		});
		zoomBox.addActionListener(ae -> {
			imgPanel.setScale(zoomBox.getSelectedIndex() + 1);
			dialog.pack();
		});
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileScroll.setPreferredSize(new java.awt.Dimension(230, 200));
		fileScroll.setVisible(false);
		fileList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && loader != null && fileList.getSelectedIndex() >= 0) {
				showListEntry(fileList.getSelectedIndex());
			}
		});
		add(fileScroll, BorderLayout.WEST);
		add(new JScrollPane(drawImagePanel()), BorderLayout.CENTER);

		var printButton = new JButton("Print");
		printButton.setMnemonic('p');
		printButton.setToolTipText("Print");
		printButton.addActionListener(ae -> print(imgPanel));

		var saveButton = new JButton("Save PNG");
		saveButton.setMnemonic('s');
		saveButton.addActionListener(ae -> saveImage(imgPanel));

		prgButton.setToolTipText("Save the picture as a C64 or Plus/4 program that shows it (LOAD \"NAME\",8 then RUN, any key returns to BASIC)");
		prgButton.addActionListener(ae -> savePrg());

		var buttonPanel = new JPanel();
		buttonPanel.add(printButton);
		buttonPanel.add(saveButton);
		buttonPanel.add(prgButton);
		buttonPanel.add(closeButton);

		add(buttonPanel, BorderLayout.SOUTH);
	}

	private JPanel drawImagePanel() {
		var panel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.VERTICAL;
		imgPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		imgPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				clickedImage();
			}
		});
		GuiHelper.addToGridBag(0, 0, 0.0, 0.0, gbc, panel, imgPanel);
		return panel;
	}

	private void clickedImage() {
		if (loader != null) {
			int n = fileModel.size();
			if (n > 0) {
				fileList.setSelectedIndex((fileList.getSelectedIndex() + 1) % n);
				fileList.ensureIndexIsVisible(fileList.getSelectedIndex());
			}
			return;
		}
		try {
			showPicture(getNextImage());
		} catch (IOException e) {
			mainPanel.appendConsole("Failed to get image: " + e.getMessage());
		}
	}

	private CbmPicture shown;

	private void showPicture(CbmPicture picture) throws IOException {
		shown = picture;
		if (picture != null) {
			picture.setFormat((CommodoreGraphics.Format) formatBox.getSelectedItem());
			imgPanel.setImage(picture);
			infoLabel.setText(picture.getName() + " - " + picture.getDescription());
			var res = picture.getResult();
			prgButton.setEnabled(res != null && (res.layers != null || res.plus4 != null || res.eci != null));
		}
	}

	/** Draw the current picture again, for example in another format. */
	private void redraw() {
		try {
			showPicture(shown);
		} catch (IOException e) {
			mainPanel.appendConsole("Failed to get image: " + e.getMessage());
		}
	}

	private void saveImage(ImagePanel imgPanel) {
		try {
			var base = imgPanel.getName() == null ? "picture" : fileBase(imgPanel.getName());
			var filename = FileDialogHelper.openTextFileDialog("Save PNG file", null, base + ".png", true, new String[]{ ".png" });
			if (filename != null) {
				if (!filename.toLowerCase().endsWith(".png")) {
					filename += ".png";
				}
				ImageIO.write(imgPanel.getImage(), "png", new File(filename));
			}
		} catch (IOException e) {	//NOSONAR
			mainPanel.appendConsole("Failed to save PNG image.\n"+e.getMessage());
		}
	}

	/** File name for saving: no [graphic] marks, only plain characters. */
	private static String fileBase(String name) {
		var n = name.replaceAll("\\[[^\\]]*\\]", "").replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
		return n.isEmpty() ? "picture" : n;
	}

	private void savePrg() {
		var res = shown != null ? shown.getResult() : null;
		if (res == null || (res.layers == null && res.plus4 == null && res.eci == null)) {
			return;
		}
		try {
			var base = shown.getName() == null ? "picture" : fileBase(shown.getName());
			var filename = FileDialogHelper.openTextFileDialog(res.plus4 != null ? "Save Plus/4 program" : "Save C64 program", null, base + ".prg", true, new String[]{ ".prg" });
			if (filename != null) {
				if (!filename.toLowerCase().endsWith(".prg")) {
					filename += ".prg";
				}
				java.nio.file.Files.write(new File(filename).toPath(), res.eci != null ? EciPrgExporter.build(res.eci) : res.plus4 != null ? Plus4PrgExporter.build(res.plus4) : C64PrgExporter.build(res.layers));
				mainPanel.appendConsole("Saved program " + filename);
			}
		} catch (IOException e) {	//NOSONAR
			mainPanel.appendConsole("Failed to save C64 program.\n" + e.getMessage());
		}
	}

	private CbmPicture getNextImage() {
		if (dataList == null || dataList.isEmpty()) {
			return null;
		}
		currentIndex = currentIndex % dataList.size();
		var data = dataList.get(currentIndex);
		String name = currentIndex < nameList.size() ? nameList.get(currentIndex) : null;
		var cbm = new CbmPicture(data, name);
		currentIndex++;
		return cbm;
	}

	private void print(ImagePanel imgPanel) {
		var job = PrinterJob.getPrinterJob();
		job.setPageable(new PrintPageable(imgPanel.getImage(), imgPanel.getName(), mainPanel));
		if (job.printDialog()) {
			try {
				job.print();
			} catch (PrinterException e) {	//NOSONAR
				mainPanel.appendConsole("Failed to print image.\n"+e.getMessage());
			}
		}
	}

	protected void setDialog(JDialog dialog) {
		this.dialog = dialog;
	}
}
