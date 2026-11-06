package droid64.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import droid64.d64.CbmException;
import droid64.d64.D64;
import droid64.d64.D71;
import droid64.d64.D81;
import droid64.d64.DiskImage;

/** Reads and writes the directory of a D64, D71 or D81 image as raw entries, for directory art. */
final class DirArtWriter {

	static final int NAME_LENGTH = 16;
	static final int TYPE_DEL = 0;
	static final String[] TYPE_NAMES = { "DEL", "SEQ", "PRG", "USR", "REL" };

	/** How the new entries are combined with the entries that are already on the disk. */
	enum Mode {
		REPLACE("Replace the whole directory"), APPEND("Add after the existing entries"), PREPEND("Add before the existing entries"), UPDATE("Update the entries read from the disk, in place");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** One directory entry: the 30 bytes after the two link bytes of the first entry in a sector. */
	static final class Row {
		/** type, track, sector, name(16), rel track/sector, record length, unused, blocks lo/hi. */
		final byte[] raw = new byte[30];
		/** The bytes when the row was created or read from disk, to find out what the user has changed. */
		private final byte[] baseline = new byte[30];
		/** True if the row was read from the disk. */
		private final boolean fromDisk;
		/** Position of the entry in the list returned by read(), or -1 for a new row. */
		int slot = -1;

		Row() {
			fromDisk = false;
			raw[0] = (byte) (0x80 | TYPE_DEL);
			Arrays.fill(raw, 3, 3 + NAME_LENGTH, (byte) 0x20);
			System.arraycopy(raw, 0, baseline, 0, 30);
		}

		Row(byte[] source) {
			this(source, false);
		}

		Row(byte[] source, boolean fromDisk) {
			this.fromDisk = fromDisk;
			System.arraycopy(source, 0, raw, 0, 30);
			System.arraycopy(source, 0, baseline, 0, 30);
		}

		boolean isChanged() {
			return !Arrays.equals(raw, baseline);
		}

		/** A row that has to be written: it was edited, or it is an existing entry of the disk. */
		boolean isNeeded() {
			return fromDisk || isChanged();
		}

		byte[] getName() {
			return Arrays.copyOfRange(raw, 3, 3 + NAME_LENGTH);
		}

		void setName(byte[] name) {
			Arrays.fill(raw, 3, 3 + NAME_LENGTH, (byte) 0xa0);
			System.arraycopy(name, 0, raw, 3, Math.min(NAME_LENGTH, name.length));
		}

		int getTypeCode() {
			return raw[0] & 0x07;
		}

		void setTypeCode(int type) {
			raw[0] = (byte) ((raw[0] & 0xf8) | (type & 7));
			if ((raw[0] & 0x80) == 0) {
				raw[0] |= (byte) 0x80;
			}
		}

		int getBlocks() {
			return (raw[28] & 0xff) | ((raw[29] & 0xff) << 8);
		}

		void setBlocks(int blocks) {
			raw[28] = (byte) (blocks & 0xff);
			raw[29] = (byte) ((blocks >> 8) & 0xff);
		}

		Row copy() {
			var r = new Row(raw);
			r.slot = slot;
			return r;
		}
	}

	private DirArtWriter() {
	}

	/**
	 * The rows to write: everything up to the last row that was changed (or that is an existing entry).
	 * Unchanged blank rows before that stay as empty DEL entries so the art keeps its position;
	 * unchanged rows after it are left out.
	 */
	static List<Row> rowsToWrite(List<Row> rows) {
		int last = -1;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).isNeeded()) {
				last = i;
			}
		}
		return new ArrayList<>(rows.subList(0, last + 1));
	}

	static boolean isSupported(DiskImage image) {
		return image instanceof D64 || image instanceof D71 || image instanceof D81;
	}

	private static int dirTrack(DiskImage image) {
		return image instanceof D81 ? 40 : 18;
	}

	private static int firstDirSector(DiskImage image) {
		return image instanceof D81 ? 3 : 1;
	}

	/** Maximum number of directory entries that fit on the directory track. */
	static int maxEntries(DiskImage image) {
		if (image == null) {
			return 144;
		}
		return (image.getMaxSectors(dirTrack(image)) - firstDirSector(image)) * 8;
	}

	/** Sector numbers of the directory chain. */
	private static List<Integer> chain(DiskImage image) throws CbmException {
		var list = new ArrayList<Integer>();
		int track = dirTrack(image);
		int sector = firstDirSector(image);
		while (track == dirTrack(image) && list.size() < 100 && !list.contains(sector)) {
			list.add(sector);
			var block = image.getBlock(track, sector);
			track = block[0] & 0xff;
			sector = block[1] & 0xff;
		}
		return list;
	}

	/** Read the used entries of the directory. Scratched entries are skipped. */
	static List<Row> read(DiskImage image) throws CbmException {
		var rows = new ArrayList<Row>();
		for (int sector : chain(image)) {
			var block = image.getBlock(dirTrack(image), sector);
			for (int e = 0; e < 8; e++) {
				int base = e * 32;
				if ((block[base + 2] & 0xff) != 0) {
					var row = new Row(Arrays.copyOfRange(block, base + 2, base + 32), true);
					row.slot = rows.size();
					rows.add(row);
				}
			}
		}
		return rows;
	}

	/** Disk name (16 bytes) from the header sector. */
	static byte[] readDiskName(DiskImage image) throws CbmException {
		var block = image.getBlock(dirTrack(image), 0);
		int pos = image instanceof D81 ? 0x04 : 0x90;
		return Arrays.copyOfRange(block, pos, pos + NAME_LENGTH);
	}

	/** Length of the disk ID field in the header: ID (2), shifted space, DOS type (2). */
	static final int ID_LENGTH = 5;

	/** Disk ID (5 bytes: ID, shifted space, DOS type) from the header sector. */
	static byte[] readDiskId(DiskImage image) throws CbmException {
		var block = image.getBlock(dirTrack(image), 0);
		int pos = image instanceof D81 ? 0x16 : 0xa2;
		return Arrays.copyOfRange(block, pos, pos + ID_LENGTH);
	}

	/**
	 * Write entries to the directory of the image (in memory; save the image afterwards).
	 * @param diskName new disk name, or null to leave the header as it is
	 * @param diskId new disk ID (5 bytes: ID, shifted space, DOS type), or null
	 */
	static void write(DiskImage image, List<Row> rows, Mode mode, byte[] diskName, byte[] diskId) throws CbmException {
		if (!isSupported(image)) {
			throw new CbmException("Directory art can only be written to D64, D71 and D81 images.");
		}
		var all = new ArrayList<Row>();
		if (mode == Mode.APPEND || mode == Mode.UPDATE) {
			all.addAll(read(image));
		}
		if (mode == Mode.UPDATE) {
			for (var r : rows) {
				if (r.slot >= 0 && r.slot < all.size()) {
					all.set(r.slot, r.copy());
				} else {
					all.add(r.copy());
				}
			}
		} else {
			rows.forEach(r -> all.add(r.copy()));
		}
		if (mode == Mode.PREPEND) {
			all.addAll(read(image));
		}
		if (all.size() > maxEntries(image)) {
			throw new CbmException("Too many entries: " + all.size() + ". This disk can have at most " + maxEntries(image) + ".");
		}
		int track = dirTrack(image);
		int needed = Math.max(1, (all.size() + 7) / 8);
		var oldChain = chain(image);
		var newChain = new ArrayList<Integer>();
		for (int i = 0; i < needed && i < oldChain.size(); i++) {
			newChain.add(oldChain.get(i));
		}
		for (int s = firstDirSector(image); newChain.size() < needed && s < image.getMaxSectors(track); s++) {
			if (!newChain.contains(s) && !oldChain.contains(s) && image.isSectorFree(track, s)) {
				newChain.add(s);
				image.markSectorUsed(track, s);
			}
		}
		if (newChain.size() < needed) {
			throw new CbmException("There are not enough free sectors on track " + track + " for " + all.size() + " entries.");
		}
		for (int i = needed; i < oldChain.size(); i++) {
			image.markSectorFree(track, oldChain.get(i));
		}
		for (int i = 0; i < newChain.size(); i++) {
			var block = new byte[DiskImage.BLOCK_SIZE];
			if (i + 1 < newChain.size()) {
				block[0] = (byte) track;
				block[1] = (byte) (int) newChain.get(i + 1);
			} else {
				block[0] = 0;
				block[1] = (byte) 0xff;
			}
			for (int e = 0; e < 8; e++) {
				int index = i * 8 + e;
				if (index < all.size()) {
					System.arraycopy(all.get(index).raw, 0, block, e * 32 + 2, 30);
				}
			}
			image.writeBlock(track, newChain.get(i), block);
		}
		if (diskName != null || diskId != null) {
			var header = image.getBlock(track, 0);
			if (diskName != null) {
				int pos = image instanceof D81 ? 0x04 : 0x90;
				Arrays.fill(header, pos, pos + NAME_LENGTH, (byte) 0xa0);
				System.arraycopy(diskName, 0, header, pos, Math.min(NAME_LENGTH, diskName.length));
			}
			if (diskId != null && diskId.length >= 2) {
				int pos = image instanceof D81 ? 0x16 : 0xa2;
				System.arraycopy(diskId, 0, header, pos, Math.min(ID_LENGTH, diskId.length));
			}
			image.writeBlock(track, 0, header);
		}
	}
}
