package droid64.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JTextArea;

import droid64.d64.CbmException;
import droid64.d64.CbmFile;
import droid64.d64.DiskImage;
import droid64.d64.DiskImageType;
import droid64.d64.FileType;

/** Copies the files of a disk image into a new disk image of another type (D64, D71, D81), file by file. */
final class ImageConverter {

	static final int OK = 0;
	static final int WARN = 1;
	static final int SKIP = 2;

	static final DiskImageType[] TARGETS = { DiskImageType.D64, DiskImageType.D71, DiskImageType.D81 };

	/** One directory entry of the source image and what happens to it. */
	static final class Item {
		final int index;
		final CbmFile file;
		int level = OK;
		String note = "";
		/** Blocks in the real sector chain of the file, -1 when not known. */
		int real = -1;

		Item(int index, CbmFile file) {
			this.index = index;
			this.file = file;
		}

		int blocks() {
			return level == SKIP ? 0 : real >= 0 ? real : file.getSizeInBlocks();
		}

		/** True when the directory shows another size than the real chain has. */
		boolean sizeDiffers() {
			return level != SKIP && real >= 0 && real != file.getSizeInBlocks();
		}
	}

	static final class Result {
		String error;
		int converted;
		int skipped;
		final List<String> problems = new ArrayList<>();
	}

	private ImageConverter() {
	}

	static boolean isTarget(DiskImageType type) {
		return Arrays.asList(TARGETS).contains(type);
	}

	/** Blocks a new, empty image of this type can hold. */
	static int capacity(DiskImageType type) {
		switch (type.id) {
		case "D71":
			return 1328;
		case "D81":
			return 3160;
		default:
			return 664;
		}
	}

	static int maxEntries(DiskImageType type) {
		return "D81".equals(type.id) ? 296 : 144;
	}

	/** What the converter does with every entry of the source. */
	static List<Item> plan(DiskImage src) {
		var items = new ArrayList<Item>();
		for (int i = 0; i < src.getCbmFileSize(); i++) {
			var cf = src.getCbmFile(i);
			if (cf == null || cf.getName() == null || cf.isFileScratched()) {
				continue;
			}
			var item = new Item(i, cf);
			if (cf.getFileType() == FileType.CBM) {
				item.level = SKIP;
				item.note = "partition: not converted";
			} else if (cf.getFileType() == FileType.REL) {
				item.level = SKIP;
				item.note = "REL file: not converted yet";
			}
			if (item.level != SKIP && hasChains(src)) {
				item.real = cf.getTrack() == 0 ? 0 : chain(src, cf.getTrack(), cf.getSector());
				if (item.sizeDiffers()) {
					item.level = WARN;
					item.note = "the directory shows " + cf.getSizeInBlocks() + " blocks, the real size is " + item.real;
				}
			}
			items.add(item);
		}
		return items;
	}

	/** Hidden data of an image: scratched directory entries that still point to a sector chain. */
	static final class Hidden {
		int entries;
		int blocks;
	}

	/** Only the block images (not T64, TAP, LNX ...) have sector chains. */
	private static boolean hasChains(DiskImage image) {
		return firstDirectorySector(image) != null;
	}

	private static int[] firstDirectorySector(DiskImage image) {
		switch (image.getImageFormat().id) {
		case "D64":
		case "D71":
		case "D67":
			return new int[] { 18, 1 };
		case "D81":
			return new int[] { 40, 3 };
		case "D80":
		case "D82":
			return new int[] { 39, 1 };
		default:
			return null;
		}
	}

	/** Number of blocks of a sector chain (stops at a bad link or a loop). */
	static int chain(DiskImage image, int track, int sector) {
		var seen = new java.util.HashSet<Long>();
		int t = track;
		int s = sector;
		int blocks = 0;
		while (t != 0) {
			long key = (long) t << 16 | s;
			if (t < 1 || t > image.getTrackCount() || s < 0 || s >= image.getMaxSectors(t) || !seen.add(key)) {
				break;
			}
			blocks++;
			try {
				var b = image.getBlock(t, s);
				t = b[0] & 0xff;
				s = b[1] & 0xff;
			} catch (CbmException | RuntimeException e) {
				break;
			}
		}
		return blocks;
	}

	/** Scratched entries ("hidden files") that still have a sector chain: data that the directory does not show. */
	static Hidden hidden(DiskImage image) {
		var h = new Hidden();
		var first = firstDirectorySector(image);
		if (first == null) {
			return h;
		}
		var seen = new java.util.HashSet<Long>();
		int t = first[0];
		int s = first[1];
		while (t != 0 && seen.add((long) t << 16 | s) && seen.size() < 200) {
			byte[] b;
			try {
				b = image.getBlock(t, s);
			} catch (CbmException | RuntimeException e) {
				break;
			}
			for (int e = 0; e < 8; e++) {
				int o = e * 32;
				int type = b[o + 2] & 0xff;
				int tr = b[o + 3] & 0xff;
				int se = b[o + 4] & 0xff;
				if (type == 0 && tr != 0 && tr <= image.getTrackCount() && se < image.getMaxSectors(tr)) {
					int blocks = chain(image, tr, se);
					if (blocks > 0) {
						h.entries++;
						h.blocks += blocks;
					}
				}
			}
			t = b[0] & 0xff;
			s = b[1] & 0xff;
		}
		return h;
	}

	static int blocksNeeded(List<Item> items) {
		return items.stream().mapToInt(Item::blocks).sum();
	}

	static int entriesNeeded(List<Item> items) {
		return (int) items.stream().filter(i -> i.level != SKIP).count();
	}

	private static byte[] dataOf(DiskImage image, int index, CbmFile file) throws CbmException {
		try {
			return image.getFileData(index);
		} catch (CbmException | RuntimeException e) {
			if (file.getFileType() == FileType.DEL) {
				return new byte[0];
			}
			throw e;
		}
	}

	/** Create the new image file with the files of the source and check the result by reading it back. */
	static Result convert(DiskImage src, List<Item> items, DiskImageType target, File dest, boolean keepFlags) {
		var result = new Result();
		try {
			if (dest.exists() && !dest.delete()) {
				result.error = "The file could not be replaced: " + dest;
				return result;
			}
			var img = target.getInstance(new ConsoleStream(new JTextArea()));
			var srcId = src.getBam() != null && src.getBam().getDiskId() != null ? src.getBam().getDiskId() : "";
			var newId = (srcId + "  ").substring(0, 2) + " " + target.dosVersion;
			var srcName = src.getBam() != null && src.getBam().getDiskName() != null ? src.getBam().getDiskName() : "";
			if (!img.saveNewImage(dest, srcName, newId)) {
				result.error = "The new disk image could not be created.";
				return result;
			}
			img.setFile(dest);
			for (var item : items) {
				if (item.level == SKIP) {
					result.skipped++;
					continue;
				}
				var data = dataOf(src, item.index, item.file);
				var copy = new CbmFile(item.file);
				if (!keepFlags) {
					copy.setFileLocked(false);
					copy.setFileClosed(true);
				}
				if (!img.saveFile(copy, true, data)) {
					result.error = "\"" + item.file.getName() + "\" could not be written (the disk is full?).";
					return result;
				}
				result.converted++;
			}
			if (!img.save()) {
				result.error = "The new disk image could not be saved.";
				return result;
			}
			verify(src, items, dest, result);
		} catch (CbmException | RuntimeException e) {
			result.error = "Conversion failed: " + e.getMessage();
		}
		return result;
	}

	/** Read the new image back and compare every file with the source. */
	private static void verify(DiskImage src, List<Item> items, File dest, Result result) throws CbmException {
		var check = DiskImage.getDiskImage(dest, new ConsoleStream(new JTextArea()));
		check.readBAM();
		check.readDirectory();
		var back = new ArrayList<Integer>();
		for (int i = 0; i < check.getCbmFileSize(); i++) {
			var cf = check.getCbmFile(i);
			if (cf != null && cf.getName() != null && !cf.isFileScratched()) {
				back.add(i);
			}
		}
		int k = 0;
		for (var item : items) {
			if (item.level == SKIP) {
				continue;
			}
			if (k >= back.size()) {
				result.problems.add(item.file.getName() + ": missing in the new image");
				continue;
			}
			int idx = back.get(k++);
			var a = dataOf(src, item.index, item.file);
			var b = dataOf(check, idx, check.getCbmFile(idx));
			if (!Arrays.equals(a, b)) {
				result.problems.add(item.file.getName() + ": the data differs from the original");
			} else if (!Arrays.equals(item.file.getNameAsBytes(), check.getCbmFile(idx).getNameAsBytes())) {
				result.problems.add(item.file.getName() + ": the name differs from the original");
			}
		}
		if (check.getErrors() != null && check.getErrors() > 0) {
			result.problems.add("The new image has " + check.getErrors() + " BAM / directory errors");
		}
	}
}
