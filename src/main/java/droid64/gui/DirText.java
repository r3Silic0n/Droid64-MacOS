package droid64.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JTextArea;

import droid64.d64.CbmFile;
import droid64.d64.DiskImage;

/** Writes the directory of one or more Commodore disk / tape images as plain text. */
final class DirText {

	private static final String LINE = "================================================================";

	/** Result of collecting the images. */
	static final class Sources {
		final List<File> images = new ArrayList<>();
		final List<String> skipped = new ArrayList<>();
	}

	private DirText() {
	}

	/**
	 * Collect the Commodore images from files and folders. A folder gives the images directly in it (not in sub folders),
	 * everything else in it (hidden files, ._ files, non Commodore files) is skipped.
	 * @param files files and folders
	 * @return images and skipped names
	 */
	static Sources collect(List<File> files) {
		var result = new Sources();
		for (var f : files) {
			if (f.isDirectory()) {
				var list = f.listFiles();
				if (list == null) {
					continue;
				}
				Arrays.sort(list, Comparator.comparing(x -> x.getName().toLowerCase()));
				for (var x : list) {
					if (x.isFile() && !x.getName().startsWith(".") && Setting.isImageFileName(x)) {
						result.images.add(x);
					}
				}
			} else if (f.isFile()) {
				if (!f.getName().startsWith(".") && Setting.isImageFileName(f)) {
					result.images.add(f);
				} else {
					result.skipped.add(f.getName());
				}
			}
		}
		return result;
	}

	/**
	 * @param images the images
	 * @param skipped names of selected files that are not Commodore images
	 * @return the text
	 */
	static String build(List<File> images, List<String> skipped) {
		var sb = new StringBuilder();
		if (images.size() > 1) {
			sb.append("Directory listing of ").append(images.size()).append(" images").append('\n').append('\n');
		}
		int failed = 0;
		for (var image : images) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			try {
				sb.append(listImage(image));
			} catch (Exception | OutOfMemoryError e) {	//NOSONAR
				failed++;
				sb.append(LINE).append('\n').append(image.getName()).append('\n').append(LINE).append('\n');
				sb.append("! cannot be read: ").append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()).append('\n');
			}
		}
		if (!skipped.isEmpty()) {
			sb.append('\n').append("Skipped (not a Commodore disk or tape image): ").append(String.join(", ", skipped)).append('\n');
		}
		if (failed > 0) {
			sb.append('\n').append(failed).append(" image(s) could not be read.\n");
		}
		return sb.toString();
	}

	/**
	 * @param file image
	 * @return listing of one image
	 * @throws Exception when it cannot be read
	 */
	static String listImage(File file) throws Exception {
		var img = DiskImage.getDiskImage(file, new ConsoleStream(new JTextArea()));
		img.readBAM();
		img.readDirectory();
		var sb = new StringBuilder();
		sb.append(LINE).append('\n');
		sb.append(file.getName()).append("   (").append(DiskImage.getImageTypeName(img.getImageFormat())).append(")\n");
		sb.append(LINE).append('\n');
		if (img.isCpmImage()) {
			for (int i = 0; i < img.getCbmFileSize(); i++) {
				var f = img.getCbmFile(i);
				if (f != null) {
					sb.append(f.getName()).append('\n');
				}
			}
			return sb.toString();
		}
		var bam = img.getBam();
		var name = bam != null && bam.getDiskName() != null ? bam.getDiskName() : "";
		var id = bam != null && bam.getDiskId() != null ? bam.getDiskId() : "";
		sb.append(("0 \"" + pad(ascii(name), 16) + "\" " + ascii(id).trim()).stripTrailing()).append('\n');
		int files = 0;
		for (int i = 0; i < img.getCbmFileSize(); i++) {
			var f = img.getCbmFile(i);
			if (f == null || f.isFileScratched()) {
				continue;
			}
			files++;
			sb.append(entry(f)).append('\n');
		}
		int free = 0;
		try {
			free = img.getBlocksFree();
		} catch (RuntimeException e) {	//NOSONAR
			free = -1;
		}
		if (free >= 0 && !(img instanceof droid64.d64.T64) && !(img instanceof droid64.d64.LNX)) {
			sb.append(free).append(" BLOCKS FREE.").append('\n');
		} else {
			sb.append(files).append(files == 1 ? " FILE." : " FILES.").append('\n');
		}
		return sb.toString();
	}

	private static String entry(CbmFile f) {
		var type = f.getFileType() != null ? f.getFileType().name() : "???";
		var name = ascii(f.getName());
		var sb = new StringBuilder();
		sb.append(pad(Integer.toString(f.getSizeInBlocks()), 5));
		sb.append('"').append(name).append('"');
		sb.append(" ".repeat(Math.max(0, 16 - name.length()))).append(' ');
		sb.append(f.isFileClosed() ? "" : "*").append(type).append(f.isFileLocked() ? "<" : "");
		return sb.toString();
	}

	private static String pad(String s, int width) {
		return s.length() >= width ? s : s + " ".repeat(width - s.length());
	}

	/** PETSCII name as readable ASCII: capitals and printable characters stay, shifted letters become capitals, the rest a question mark. */
	static String ascii(String s) {
		var sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			if (c == 0xA0 || c == 0) {
				sb.append(' ');
			} else if (c == 0x5C) {
				sb.append('\u00a3');	// pound sign
			} else if (c >= 0x20 && c <= 0x7E) {
				sb.append(c);
			} else if (c >= 0xC1 && c <= 0xDA) {
				sb.append((char) (c - 0xC1 + 'A'));
			} else if (c == 0x60 || c == 0x62 || c == 0x63 || c == 0x64 || c == 0x77 || c == 0x78 || c == 0xC0) {
				sb.append('-');		// horizontal line graphics
			} else {
				sb.append('?');
			}
		}
		return sb.toString();
	}
}
