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
		int pos;
		byte[] payload = new byte[0];
	}

	/** One decoded program. */
	static final class Program {
		String name;
		int start;
		byte[] data;
		byte[] header = new byte[0];
		/** turbo data with a checksum error, incomplete, or not found */
		boolean damaged;
		/** index of the pulse where the header block starts */
		int pos;
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
		long damagedCount = programs.stream().filter(q -> q.damaged).count();
		if (damagedCount > 0) {
			feedbackStream.append("TAP: turbo data missing or with checksum errors (tape damaged?): ");
			feedbackStream.append(programs.stream().filter(q -> q.damaged).map(q -> q.name).collect(java.util.stream.Collectors.joining(", "))).append("\n");
		}
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
			blk.pos = sync;
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
		var programs = assemble(blocks);
		if (!plus4) {
			applyTurbo(programs, p);
		}
		return programs;
	}


	// ---- turbo loader (the "0x02 pilot" loader which sits in the header block of every file) ----

	/** start of the turbo loader code in the 192 byte header block (it is executed from the tape buffer) */
	private static final int[] LOADER = { 0xa9, 0x08, 0x85, 0xa3, 0x20, 0x61, 0x03, 0x66, 0xbd };
	private static final int TURBO_ONE = 288;	// cycles: longer pulses are 1 bits (timer B latch of the loader)

	/** One block of turbo data. */
	private static final class TurboBlock {
		int pos;
		int start;
		byte[] data;
		boolean ok;
	}

	private static boolean hasTurboLoader(Program p) {
		if (p.data.length > 2 || p.header.length < 21 + LOADER.length) {
			return false;
		}
		for (int i = 0; i < LOADER.length; i++) {
			if ((p.header[21 + i] & 255) != LOADER[i]) {
				return false;
			}
		}
		return true;
	}

	/** Replace the two byte stubs of the programs with the data of the turbo blocks which follow them. */
	private static void applyTurbo(List<Program> programs, int[] p) {
		if (programs.isEmpty() || programs.stream().noneMatch(Tap::hasTurboLoader)) {
			return;
		}
		var blocks = scanTurbo(p);
		for (int k = 0; k < programs.size(); k++) {
			var prog = programs.get(k);
			if (!hasTurboLoader(prog)) {
				continue;
			}
			int next = k + 1 < programs.size() ? programs.get(k + 1).pos : Integer.MAX_VALUE;
			TurboBlock found = null;
			for (var b : blocks) {
				if (b.pos > prog.pos && b.pos < next) {
					found = b;
					break;
				}
			}
			if (found == null) {
				prog.data = new byte[0];
				prog.damaged = true;
			} else {
				prog.start = found.start;
				prog.data = found.data;
				prog.damaged = !found.ok;
			}
		}
	}

	/**
	 * Find the turbo blocks: a leader of $02 bytes, a $01 marker (after a count down), end address, start address
	 * (both high byte first), the data, and a checksum (sum of the data bytes). Bits come LSB first, a pulse longer
	 * than the timer value of the loader is a 1.
	 */
	private static List<TurboBlock> scanTurbo(int[] p) {
		int n = p.length;
		var bits = new byte[n];
		for (int i = 0; i < n; i++) {
			bits[i] = (byte) (p[i] >= TURBO_ONE && p[i] < 400000 ? 1 : 0);
		}
		var out = new ArrayList<TurboBlock>();
		int i = 0;
		while (i + 64 < n) {
			if (!isPilot(bits, i)) {
				i++;
				continue;
			}
			int j = i;
			int reps = 0;
			while (j + 8 <= n && isPilot(bits, j)) {
				j += 8;
				reps++;
			}
			if (reps < 8) {
				i = j;
				continue;
			}
			int k = 0;
			while (k < 40 && j + 8 <= n && byteAt(bits, j) != 1) {
				j += 8;
				k++;
			}
			if (j + 8 > n || byteAt(bits, j) != 1) {
				i = j;
				continue;
			}
			j += 8;
			if (j + 32 > n) {
				break;
			}
			int end = byteAt(bits, j) << 8 | byteAt(bits, j + 8);
			int start = byteAt(bits, j + 16) << 8 | byteAt(bits, j + 24);
			j += 32;
			int len = end - start;
			if (len <= 0 || len > 65536) {
				i = j;
				continue;
			}
			var b = new TurboBlock();
			b.pos = i;
			b.start = start;
			int avail = Math.min(len, (n - j) / 8);
			b.data = new byte[avail];
			int sum = 0;
			for (int q = 0; q < avail; q++) {
				b.data[q] = (byte) byteAt(bits, j + 8 * q);
				sum += b.data[q] & 255;
			}
			b.ok = avail == len && j + 8 * len + 8 <= n && byteAt(bits, j + 8 * len) == (sum & 255);
			out.add(b);
			i = j + 8 * avail + 8;
		}
		return out;
	}

	private static boolean isPilot(byte[] bits, int i) {
		return i + 8 <= bits.length && bits[i] == 0 && bits[i + 1] == 1 && bits[i + 2] == 0 && bits[i + 3] == 0
				&& bits[i + 4] == 0 && bits[i + 5] == 0 && bits[i + 6] == 0 && bits[i + 7] == 0;
	}

	private static int byteAt(byte[] bits, int j) {
		int v = 0;
		for (int k = 0; k < 8; k++) {
			v |= bits[j + k] << k;
		}
		return v;
	}

	/** Combine the two copies of every block and group header + data into programs. */
	private static List<Program> assemble(List<Block> blocks) {
		var logical = new ArrayList<byte[]>();
		var logicalPos = new ArrayList<Integer>();
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
			logicalPos.add(b.pos);
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
				cur.header = b.clone();
				cur.pos = logicalPos.get(k);
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
