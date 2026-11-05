package droid64.gui;

import droid64.d64.DiskImage;
import droid64.d64.FileType;

/** Guesses which Commodore computer a disk image was made for, from its first startable program. */
public final class MachineDetector {

	private MachineDetector() {
	}

	/** Opcodes with a 16 bit absolute operand. */
	private static final boolean[] ABS = new boolean[256];
	static {
		int[] ops = { 0x0D, 0x0E, 0x19, 0x1D, 0x1E, 0x20, 0x2C, 0x2D, 0x2E, 0x39, 0x3D, 0x3E, 0x4C, 0x4D, 0x4E, 0x59, 0x5D, 0x5E,
			0x6D, 0x6E, 0x79, 0x7D, 0x7E, 0x8C, 0x8D, 0x8E, 0x99, 0x9D, 0xAC, 0xAD, 0xAE, 0xB9, 0xBC, 0xBD, 0xBE,
			0xCC, 0xCD, 0xCE, 0xD9, 0xDD, 0xDE, 0xEC, 0xED, 0xEE, 0xF9, 0xFD, 0xFE };
		for (int o : ops) {
			ABS[o] = true;
		}
	}

	/**
	 * Look at the first startable program (PRG with a BASIC start address) of the image.
	 * @param image disk image
	 * @return machine, or null when it can not be told
	 */
	public static C64.Machine detect(DiskImage image) {
		if (image == null) {
			return null;
		}
		try {
			for (int i = 0; i < image.getFilesUsedCount(); i++) {
				var f = image.getCbmFile(i);
				if (f == null || f.getFileType() != FileType.PRG || f.isFileScratched()) {
					continue;
				}
				byte[] data = image.getFileData(i);
				if (data == null || data.length < 4) {
					continue;
				}
				int load = (data[0] & 0xff) | (data[1] & 0xff) << 8;
				if (load == 0x0801 || load == 0x1001 || load == 0x1201 || load == 0x1C01 || load == 0x0401) {
					return detect(data, load);
				}
			}
		} catch (Exception e) {	//NOSONAR
			// unknown
		}
		return null;
	}

	/**
	 * @param data program including the load address
	 * @param load load address
	 * @return machine
	 */
	static C64.Machine detect(byte[] data, int load) {
		int c64 = 0;
		int c128 = 0;
		int plus4 = 0;
		int vic = 0;
		int pet = 0;
		switch (load) {
		case 0x0801: c64 += 3; break;
		case 0x1C01: c128 += 6; break;
		case 0x1001: plus4 += 2; vic += 2; break;
		case 0x1201: vic += 5; break;
		default: pet += 6; break;
		}
		for (int i = 2; i + 2 < data.length; i++) {
			if (!ABS[data[i] & 0xff]) {
				continue;
			}
			int lo = data[i + 1] & 0xff;
			int hi = data[i + 2] & 0xff;
			if (hi == 0xFF && lo <= 0x3F) {
				plus4 += 2;			// TED registers (also C128 MMU $FF00-$FF04)
				if (lo <= 0x04 && load == 0x1C01) {
					c128 += 1;
				}
			} else if (hi == 0xD0 && lo <= 0x2E || hi == 0xD4 && lo <= 0x1C || hi == 0xDC || hi == 0xDD) {
				c64 += 1;
				if (load == 0x1C01) {
					c128 += 1;
				}
			} else if (hi == 0xD5 || hi == 0xD6) {
				c128 += 3;
			} else if (hi == 0x90 && lo <= 0x0F || hi == 0x91 && lo >= 0x10 && lo <= 0x2F) {
				vic += 2;
			} else if (hi == 0xE8 && lo >= 0x10 && lo <= 0x4F) {
				pet += 3;
			}
		}
		// Plus/4 programs never touch VIC/SID, C64 programs never touch TED.
		C64.Machine best = C64.Machine.PLUS4;
		int bestScore = plus4;
		if (vic > bestScore) {
			best = C64.Machine.VIC20;
			bestScore = vic;
		}
		if (c128 > bestScore) {
			best = C64.Machine.C128;
			bestScore = c128;
		}
		if (c64 > bestScore) {
			best = C64.Machine.C64;
			bestScore = c64;
		}
		if (pet > bestScore) {
			best = C64.Machine.PET;
		}
		return best;
	}
}
