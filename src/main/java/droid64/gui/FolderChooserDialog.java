package droid64.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * Folder chooser: the folder shown in the path field is the choice (also when it is empty), new folders can be
 * created, the list is refreshed when the window is activated again.
 */
public final class FolderChooserDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final JTextField path = new JTextField(40);
	private final DefaultListModel<String> model = new DefaultListModel<>();
	private final JList<String> list = new JList<>(model);
	private File current;
	private File result;

	private FolderChooserDialog(Component owner, String title, File start) {
		super(owner == null ? null : SwingUtilities.getWindowAncestor(owner), title, ModalityType.APPLICATION_MODAL);
		var content = new JPanel(new BorderLayout(6, 6));
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		var top = new JPanel(new BorderLayout(4, 4));
		var up = new JButton("Up");
		var home = new JButton("Home");
		var refresh = new JButton("Refresh");
		var nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		nav.add(up);
		nav.add(home);
		nav.add(refresh);
		top.add(nav, BorderLayout.NORTH);
		top.add(path, BorderLayout.CENTER);
		content.add(top, BorderLayout.NORTH);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		content.add(new JScrollPane(list), BorderLayout.CENTER);

		var south = new JPanel(new BorderLayout());
		var newFolder = new JButton("New Folder...");
		var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.add(newFolder);
		var select = new JButton("Select this folder");
		var cancel = new JButton("Cancel");
		var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.add(cancel);
		right.add(select);
		south.add(left, BorderLayout.WEST);
		south.add(right, BorderLayout.EAST);
		content.add(south, BorderLayout.SOUTH);
		setContentPane(content);

		up.addActionListener(e -> {
			if (current.getParentFile() != null) {
				go(current.getParentFile());
			}
		});
		home.addActionListener(e -> go(new File(System.getProperty("user.home"))));
		refresh.addActionListener(e -> go(current));
		path.addActionListener(e -> {
			var f = new File(path.getText().trim());
			if (f.isDirectory()) {
				go(f);
			} else {
				JOptionPane.showMessageDialog(this, "Not a folder: " + f);
				path.setText(current.getPath());
			}
		});
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
					go(new File(current, list.getSelectedValue()));
				}
			}
		});
		newFolder.addActionListener(e -> {
			var name = JOptionPane.showInputDialog(this, "Name of the new folder:");
			if (name != null && !name.trim().isEmpty()) {
				var dir = new File(current, name.trim());
				if (dir.mkdirs()) {
					go(dir);
				} else {
					JOptionPane.showMessageDialog(this, "Could not create " + dir, "New Folder", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		select.addActionListener(e -> {
			result = current;
			dispose();
		});
		cancel.addActionListener(e -> dispose());
		getRootPane().setDefaultButton(select);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowActivated(java.awt.event.WindowEvent e) {
				go(current);
			}
		});

		File s = start;
		while (s != null && !s.isDirectory()) {
			s = s.getParentFile();
		}
		go(s != null ? s : new File(System.getProperty("user.home")));
		setSize(520, 420);
		setLocationRelativeTo(owner);
	}

	private void go(File dir) {
		if (dir == null || !dir.isDirectory()) {
			return;
		}
		current = dir;
		path.setText(dir.getPath());
		model.clear();
		var subs = dir.listFiles(f -> f.isDirectory() && !f.isHidden());
		if (subs != null) {
			var names = new ArrayList<String>();
			Arrays.stream(subs).forEach(f -> names.add(f.getName()));
			names.sort(String::compareToIgnoreCase);
			names.forEach(model::addElement);
		}
	}

	/** @return the chosen folder, or null if cancelled */
	public static File choose(Component owner, String title, File start) {
		var d = new FolderChooserDialog(owner, title, start);
		d.setVisible(true);
		return d.result;
	}

	/** For tests. */
	static Window noop() {
		return null;
	}
}
