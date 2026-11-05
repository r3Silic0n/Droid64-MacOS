package droid64.d64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import droid64.gui.ConsoleStream;

/**
 * Read-only view of a TAP tape image: the programs recorded with the standard KERNAL tape routines are decoded
 * and presented like the files of a T64 image. Turbo loader data cannot be decoded, but the tape as a whole
 * can still be started in an emulator from its TAP file.
 */
public class Tap extends T64 {

	private static final long serialVersionUID = 1L;
	private static final String SIGNATURE = "C64-TAPE-RAW";
	/** C16 / Plus/4 tapes: version 2, every pulse is a half wave. */
	private static final String SIGNATURE_C16 = "C16-TAPE-RAW";

	/** One decoded KERNAL block. */
	private static final class Block {
		boolean first;
		boolean ok;
		byte[] payload = new byte[0];
	}

	/** One decoded program. */
	static final class Program {
		String name;
		int start;
		byte[] data;
		boolean seq;
	}

	private byte[] tapData;
	private transient int turboBlocks;

	public Tap(DiskImageType imageFormat, ConsoleStream consoleStream) {
		super(imageFormat, consoleStream);
	}

	public Tap(DiskImageType imageFormat, byte[] imageData, ConsoleStream consoleStream) {
		super(imageFormat, build(decode(imageData)), consoleStream);
		this.tapData = imageData;
	}

	@Override
	protected DiskImage readImage(File file) throws CbmException {
		bam = new CbmBam(1, 1);
		readImage(file, DiskImageType.TAP);
		tapData = cbmDisk;
		var programs = decode(tapData);
		cbmDisk = build(programs);
		int entryCapacity = Utility.getInt16(cbmDisk, 0x22);
		filesUsedCount = Utility.getInt16(cbmDisk, 0x24);
		initCbmFile(entryCapacity);
		feedbackStream.append("TAP: ").append(programs.size()).append(" KERNAL program(s) found.");
		boolean stubsOnly = !programs.isEmpty() && programs.stream().allMatch(q -> q.data.length <= 2);
		feedbackStream.append(programs.isEmpty() ? " No standard programs (turbo tape?): start the tape as a whole in the emulator.\n"
				: stubsOnly ? " Turbo tape: only the names are standard; the program data cannot be extracted, start the tape in the emulator.\n"
				: " Turbo loader data, if any, is not listed.\n");
		return this;
	}

	@Override
	public boolean saveAs(File file) {
		feedbackStream.append("TAP images are read-only.\n");
		return false;
	}

	@Override
	public boolean save() {
		return saveAs(null);
	}

	/** @return machine of the tape: 0 C64, 1 VIC-20, 2 C16/Plus4, 3 PET */
	public int getMachineId() {
		if (isPlus4(tapData)) {
			return 2;
		}
		if (tapData != null && tapData.length > 20 && (tapData[12] & 255) >= 2) {
			return tapData[13] & 255;
		}
		return 0;
	}

	/** @return the original TAP file contents */
	public byte[] getTapData() {
		return tapData;
	}

	// ---- decoding ----

	/** @return the pulse lengths in cycles */
	static int[] pulses(byte[] d) {
		if (d == null || d.length < 20 || !(new String(d, 0, SIGNATURE.length()).equals(SIGNATURE) || isPlus4(d))) {
			return new int[0];
		}
		int version = d[12] & 255;
		int size = Math.min(Utility.getInt32(d, 16), d.length - 20);
		var out = new int[size];
		int n = 0;
		for (int i = 0; i < size; i++) {
			int b = d[20 + i] & 255;
			if (b != 0) {
				out[n++] = b * 8;
			} else if (version >= 1 && i + 3 < size) {
				out[n++] = (d[21 + i] & 255) | (d[22 + i] & 255) << 8 | (d[23 + i] & 255) << 16;
				i += 3;
			} else {
				out[n++] = 256 * 8;
			}
		}
		if (isPlus4(d)) {
			return joinHalfWaves(out, n);
		}
		return java.util.Arrays.copyOf(out, n);
	}

	/** @return true for a C16 / Plus/4 tape image */
	static boolean isPlus4(byte[] d) {
		return d != null && d.length >= 20 && new String(d, 0, SIGNATURE_C16.length()).equals(SIGNATURE_C16);
	}

	/**
	 * Two half waves make one pulse. The two halves of a wave are about the same length, so a stray half wave
	 * (which would shift every following pair) is dropped; a long gap stands alone.
	 */
	private static int[] joinHalfWaves(int[] h, int n) {
		var out = new int[n + 2];
		int m = 0;
		int i = 0;
		while (i < n) {
			if (h[i] > 3000) {
				out[m++] = h[i++];
			} else if (i + 1 < n && h[i + 1] <= 3000 && h[i + 1] * 100L < h[i] * 170L && h[i] * 100L < h[i + 1] * 170L) {
				out[m++] = h[i] + h[i + 1];
				i += 2;
			} else {
				i++;
			}
		}
		return java.util.Arrays.copyOf(out, m);
	}

	private static char cls(int cycles, double scale, boolean plus4) {
		double c = cycles / scale;
		if (plus4) {
			return c < 660 ? 'S' : c < 1320 ? 'M' : 'L';	// short : medium : long = 1 : 2 : 4
		}
		return c < 456 ? 'S' : c < 608 ? 'M' : 'L';
	}

	static List<Program> decode(byte[] tap) {
		var p = pulses(tap);
		boolean plus4 = isPlus4(tap);
		var blocks = new ArrayList<Block>();
		int i = 0;
		int n = p.length;
		while (i < n) {
			// leader: a long run of short pulses, then the sync (long, medium)
			int run = 0;
			int sync = -1;
			for (; i < n; i++) {
				char c = cls(p[i], 1.0, plus4);
				if (c == 'S') {
					run++;
				} else {
					if (run >= 40 && c == 'L' && i + 1 < n && cls(p[i + 1], 1.0, plus4) == 'M') {
						sync = i;
						break;
					}
					run = 0;
				}
			}
			if (sync < 0) {
				break;
			}
			double sum = 0;
			for (int k = sync - 30; k < sync; k++) {
				sum += p[k];
			}
			double scale = sum / 30 / (plus4 ? 440.0 : 384.0);
			if (scale < 0.7 || scale > 1.4) {
				scale = 1.0;
			}
			i = sync;
			var bytes = new ByteArrayOutputStream();
			boolean ok = true;
			boolean ended = false;
			while (i + 1 < n && !ended) {
				char a = cls(p[i], scale, plus4);
				char b = cls(p[i + 1], scale, plus4);
				if (a == 'L' && b == 'M') {
					i += 2;
					int v = 0;
					int ones = 0;
					boolean bad = false;
					for (int bit = 0; bit < 9; bit++) {
						if (i + 1 >= n) {
							bad = true;
							break;
						}
						char x = cls(p[i], scale, plus4);
						char y = cls(p[i + 1], scale, plus4);
						i += 2;
						int val;
						if (x == 'S' && y == 'M') {
							val = 0;
						} else if (x == 'M' && y == 'S') {
							val = 1;
						} else {
							bad = true;
							break;
						}
						if (bit < 8) {
							v |= val << bit;
						}
						ones += val;
					}
					if (bad) {
						ok = false;
						break;
					}
					if (ones % 2 == 0) {
						ok = false;	// odd parity expected
					}
					bytes.write(v);
				} else {
					if (a == 'L' && b == 'S') {
						i += 2;
					} else {
						ok = false;
					}
					ended = true;
				}
			}
			var raw = bytes.toByteArray();
			var blk = new Block();
			if (raw.length >= 10 && (raw[0] == (byte) 0x89 || raw[0] == 0x09)) {
				blk.first = raw[0] == (byte) 0x89;
				blk.payload = java.util.Arrays.copyOfRange(raw, 9, raw.length - 1);
				int x = 0;
				for (byte q : blk.payload) {
					x ^= q & 255;
				}
				blk.ok = ok && x == (raw[raw.length - 1] & 255);
				blocks.add(blk);
			} else if (raw.length > 0) {
				// something else (turbo data starts like this)
				continue;
			}
		}
		return assemble(blocks);
	}

	/** Combine the two copies of every block and group header + data into programs. */
	private static List<Program> assemble(List<Block> blocks) {
		var logical = new ArrayList<byte[]>();
		for (int k = 0; k < blocks.size(); k++) {
			var b = blocks.get(k);
			var best = b;
			if (b.first && k + 1 < blocks.size() && !blocks.get(k + 1).first) {
				if (!b.ok && blocks.get(k + 1).ok) {
					best = blocks.get(k + 1);
				}
				k++;
			}
			logical.add(best.payload);
		}
		var out = new ArrayList<Program>();
		Program cur = null;
		var seq = new ByteArrayOutputStream();
		for (int k = 0; k < logical.size(); k++) {
			var b = logical.get(k);
			boolean header = b.length == 192 && (b[0] == 1 || b[0] == 3 || b[0] == 4 || b[0] == 5);
			if (cur != null && cur.seq && !header) {
				if (b.length > 1) {
					seq.write(b, 1, b.length - 1);
				}
				continue;
			}
			if (cur != null && cur.seq) {
				cur.data = trimZeros(seq.toByteArray());
				seq.reset();
				out.add(cur);
				cur = null;
			}
			if (header && b[0] != 5) {
				cur = new Program();
				cur.start = (b[1] & 255) | (b[2] & 255) << 8;
				int end = (b[3] & 255) | (b[4] & 255) << 8;
				cur.name = new String(b, 5, 16, java.nio.charset.StandardCharsets.ISO_8859_1).replaceAll("[\\x00-\\x1f\\x80-\\x9f]", "").trim();
				if (b[0] == 4) {
					cur.seq = true;
					cur.start = 0;
				} else {
					if (k + 1 < logical.size()) {
						var d = logical.get(++k);
						int len = Math.max(0, Math.min(d.length, end - cur.start));
						cur.data = java.util.Arrays.copyOf(d, len > 0 ? len : d.length);
					} else {
						cur.data = new byte[0];
					}
					out.add(cur);
					cur = null;
				}
			}
		}
		if (cur != null && cur.seq) {
			cur.data = trimZeros(seq.toByteArray());
			out.add(cur);
		}
		return out;
	}

	private static byte[] trimZeros(byte[] d) {
		int n = d.length;
		while (n > 0 && d[n - 1] == 0) {
			n--;
		}
		return java.util.Arrays.copyOf(d, n);
	}

	/** Build a T64 image from the programs. */
	private static byte[] build(List<Program> programs) {
		int n = Math.max(1, programs.size());
		int dataStart = 0x40 + 32 * n;
		var out = new ByteArrayOutputStream();
		var head = new byte[dataStart];
		var sig = "C64S tape file\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
		System.arraycopy(sig, 0, head, 0, sig.length);
		head[0x20] = 0x01;
		head[0x21] = 0x01;
		head[0x22] = (byte) n;
		head[0x23] = (byte) (n >> 8);
		head[0x24] = (byte) programs.size();
		head[0x25] = (byte) (programs.size() >> 8);
		var title = "TAPE".getBytes();
		java.util.Arrays.fill(head, 0x28, 0x40, (byte) 0x20);
		System.arraycopy(title, 0, head, 0x28, title.length);
		int offset = dataStart;
		for (int k = 0; k < programs.size(); k++) {
			var p = programs.get(k);
			int pos = 0x40 + 32 * k;
			int len = p.data.length;
			head[pos] = 1;
			head[pos + 1] = (byte) (p.seq ? 1 : 2);
			int end = p.start + len;
			head[pos + 2] = (byte) p.start;
			head[pos + 3] = (byte) (p.start >> 8);
			head[pos + 4] = (byte) end;
			head[pos + 5] = (byte) (end >> 8);
			for (int q = 0; q < 4; q++) {
				head[pos + 8 + q] = (byte) (offset >> (8 * q));
			}
			java.util.Arrays.fill(head, pos + 16, pos + 32, (byte) 0x20);
			var nm = p.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
			System.arraycopy(nm, 0, head, pos + 16, Math.min(16, nm.length));
			offset += len;
		}
		out.write(head, 0, head.length);
		for (var p : programs) {
			out.write(p.data, 0, p.data.length);
		}
		return out.toByteArray();
	}
}
