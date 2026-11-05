package droid64.gui;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.io.FilenameFilter;

import javax.swing.SwingUtilities;

/**
 * Uses the native macOS open/save panel (java.awt.FileDialog) instead of Swing's JFileChooser.
 */
public final class NativeFileDialog {

	public static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");

	private NativeFileDialog() {
	}

	/**
	 * @return selected file or null if cancelled
	 */
	public static File choose(Component owner, String title, File directory, String name, boolean save,
			boolean directoriesOnly, FilenameFilter filter) {
		Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		if (w == null && owner instanceof Window) {
			w = (Window) owner;
		}
		var fd = w instanceof Frame ? new FileDialog((Frame) w, title, save ? FileDialog.SAVE : FileDialog.LOAD)
				: new FileDialog((Frame) null, title, save ? FileDialog.SAVE : FileDialog.LOAD);
		if (directory != null) {
			if (directory.isDirectory()) {
				fd.setDirectory(directory.getAbsolutePath());
			} else if (directory.getParentFile() != null) {
				fd.setDirectory(directory.getParentFile().getAbsolutePath());
			}
		}
		if (name != null) {
			fd.setFile(name);
		}
		if (filter != null && !save) {
			fd.setFilenameFilter(filter);
		}
		var old = System.getProperty("apple.awt.fileDialogForDirectories");
		System.setProperty("apple.awt.fileDialogForDirectories", Boolean.toString(directoriesOnly));
		try {
			fd.setVisible(true);
		} finally {
			System.setProperty("apple.awt.fileDialogForDirectories", old == null ? "false" : old);
		}
		if (fd.getFile() == null) {
			return null;
		}
		return new File(fd.getDirectory(), fd.getFile());
	}
}
