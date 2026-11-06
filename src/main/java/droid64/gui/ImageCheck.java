package droid64.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import droid64.d64.CbmException;
import droid64.d64.CbmFile;
import droid64.d64.DiskImage;
import droid64.d64.FileType;
import droid64.d64.ValidationError;

/** Checks a disk image: BAM against the files, file chains against the directory, and the error info of the image. */
final class ImageCheck {

	static final byte OK = 0;
	static final byte WARN = 1;
	static final byte ERROR = 2;

	/** One problem found in the image. */
	static final class Finding {
		final int track;
		final int sector;
		final byte severity;
		final String text;

		Finding(int track, int sector, byte severity, String text) {
			this.track = track;
			this.sector = sector;
			this.severity = severity;
			this.text = text;
		}

		@Override
		public String toString() {
			return (severity == ERROR ? "Error   " : "Warning ") + (track > 0 ? String.format("%2d/%-2d  ", track, sector) : "        ") + text;
		}
	}

	final List<Finding> findings = new ArrayList<>();
	/** problem level per track and sector, for the sector map */
	final byte[][] marks;
	int files;
	int blocksChecked;

	private ImageCheck(DiskImage image) {
		marks = new byte[image.getTrackCount() + 1][];
		for (int t = 1; t <= image.getTrackCount(); t++) {
			marks[t] = new byte[image.getMaxSectors(t)];
		}
	}

	private void add(int track, int sector, byte severity, String text) {
		findings.add(new Finding(track, sector, severity, text));
		if (track >= 1 && track < marks.length && sector >= 0 && sector < marks[track].length && severity > marks[track][sector]) {
			marks[track][sector] = severity;
		}
	}

	int count(byte severity) {
		int n = 0;
		for (var f : findings) {
			if (f.severity == severity) {
				n++;
			}
		}
		return n;
	}

	static String errorText(int code) {
		switch (code) {
		case 20:
			return "20 header block not found";
		case 21:
			return "21 no sync mark";
		case 22:
			return "22 data block not found";
		case 23:
			return "23 data checksum error";
		case 25:
			return "25 write verify error";
		case 26:
			return "26 write protect on";
		case 27:
			return "27 header checksum error";
		case 29:
			return "29 disk ID mismatch";
		default:
			return code + " error";
		}
	}

	static ImageCheck run(DiskImage image) {
		var r = new ImageCheck(image);
		var owner = new HashMap<Long, String>();

		// 1. BAM and directory against the files (the same check as Validate)
		image.validate(new ArrayList<>());
		for (var e : image.getValidationErrorList()) {
			boolean warn = e.getError() == ValidationError.Error.ERROR_USED_SECTOR_IS_FREE
					|| e.getError() == ValidationError.Error.ERROR_UNUSED_SECTOR_IS_ALLOCATED
					|| e.getError() == ValidationError.Error.ERROR_BAM_FREE_SECTOR_MISMATCH;
			r.add(e.getTrack(), e.getSector(), warn ? WARN : ERROR,
					e.getError() + (e.getFileName() != null ? " (" + e.getFileName() + ")" : ""));
		}

		// 2. the chain of every file against the block count of the directory
		for (int n = 0; n < image.getCbmFileSize(); n++) {
			var cf = image.getCbmFile(n);
			if (cf == null || cf.getName() == null || cf.isFileScratched() || cf.getTrack() == 0
					|| cf.getFileType() == FileType.DEL || cf.getFileType() == FileType.REL
					|| cf.getFileType() == FileType.CBM) {
				continue;
			}
			r.files++;
			var seen = new HashSet<Long>();
			int t = cf.getTrack();
			int s = cf.getSector();
			int blocks = 0;
			while (t != 0) {
				long key = (long) t << 16 | s;
				if (t < 1 || t > image.getTrackCount() || s < 0 || s >= image.getMaxSectors(t) || !seen.add(key)) {
					break;
				}
				owner.put(key, cf.getName());
				blocks++;
				byte[] b;
				try {
					b = image.getBlock(t, s);
				} catch (CbmException e) {
					break;
				}
				t = b[0] & 0xff;
				s = b[1] & 0xff;
			}
			r.blocksChecked += blocks;
			if (blocks != cf.getSizeInBlocks()) {
				r.add(cf.getTrack(), cf.getSector(), WARN, cf.getName() + ": the directory says " + cf.getSizeInBlocks()
						+ " blocks, the chain has " + blocks);
			}
		}

		// 3. sectors that the disk copy found unreadable or faulty
		if (image.hasErrorInfo()) {
			for (int t = 1; t <= image.getTrackCount(); t++) {
				for (int s = 0; s < image.getMaxSectors(t); s++) {
					int code = image.getSectorError(t, s);
					if (code != 0) {
						var file = owner.get((long) t << 16 | s);
						r.add(t, s, code == 23 ? WARN : ERROR, "Disk error " + errorText(code) + (file != null ? " in " + file : ""));
					}
				}
			}
		}
		return r;
	}

}
