package droid64.sid;

import java.io.ByteArrayOutputStream;

/**
 * Builds a C64 program that plays a SID tune on its own (LOAD, RUN).
 * <p>
 * The player loop is the one used by the usual ripper / composer release files (for example
 * Cleve's "inna end"): a BASIC line SYS 2061, init the tune, then once per frame wait for raster
 * line $80, flash the border and background while the play routine runs (shows the raster time
 * of the music) and loop. Tunes with CIA timing wait for CIA 1 timer A instead of the raster.
 * Tunes without a play routine (RSID) only get their init called; they run from their own
 * interrupt.
 * <p>
 * The music keeps its own load address: if it starts a little after the player it is padded
 * with zero bytes, otherwise the music is stored right after the player and copied to its
 * address before the init call.
 */
public final class MusicPrgExporter {

	private static final int BASIC_START = 0x0801;
	private static final int CODE_START = 0x080D;	// SYS 2061
	private static final int MAX_PADDING = 0x1000;

	private MusicPrgExporter() {
	}

	/**
	 * @param t the tune
	 * @param song the tune number to start (1 based)
	 * @return the program file (with load address $0801)
	 * @throws IllegalArgumentException with a readable reason when the tune cannot be made into a program
	 */
	public static byte[] build(SidTune t, int song) {
		if (t == null || t.data == null || t.data.length == 0) {
			throw new IllegalArgumentException("There is no music data.");
		}
		if (t.isMus()) {
			throw new IllegalArgumentException("Compute's Sidplayer (MUS) files need their own player and cannot be saved as a program.");
		}
		int load = t.loadAddress;
		int len = t.data.length;
		int end = load + len - 1;
		int init = t.initAddress != 0 ? t.initAddress : load;
		int play = t.playAddress;
		if (end > 0xffff) {
			throw new IllegalArgumentException("The music does not fit into the C64 memory.");
		}
		if (overlaps(load, end, 0xd000, 0xdfff)) {
			throw new IllegalArgumentException(String.format("The music lies at $%04X-$%04X, over the I/O area $D000-$DFFF, so it cannot be played from a program.", load, end));
		}
		boolean underRom = overlaps(load, end, 0xa000, 0xbfff) || overlaps(load, end, 0xe000, 0xffff);
		if (play == 0 && overlaps(load, end, 0xe000, 0xffff)) {
			throw new IllegalArgumentException("This tune runs from its own interrupt and lies under the KERNAL ROM; that cannot be started from a program.");
		}
		int s = Math.max(1, Math.min(song, Math.max(1, t.songs)));
		boolean cia = play != 0 && t.ciaSpeed(s);

		// first try without the copy routine: the music follows the player directly
		var code = player(false, 0, 0, 0, underRom, s - 1, init, play, cia);
		int codeEnd = CODE_START + code.length;
		boolean move = !(load >= codeEnd && load - codeEnd <= MAX_PADDING);
		int dataStart = codeEnd;
		if (move) {
			// the copy routine makes the player longer; the length does not depend on the addresses
			int moveCodeLen = player(true, 0, 0, 1, underRom, s - 1, init, play, cia).length;
			dataStart = CODE_START + moveCodeLen;
			if (load <= dataStart) {
				throw new IllegalArgumentException(String.format(
						"The music starts at $%04X, inside the BASIC start of the program ($0801-$%04X), so it cannot be started with RUN.", load, dataStart));
			}
			code = player(true, dataStart + len - 1, end, len, underRom, s - 1, init, play, cia);
		}
		var out = new ByteArrayOutputStream();
		out.write(BASIC_START & 0xff);
		out.write(BASIC_START >> 8);
		// 10 SYS2061
		out.writeBytes(new byte[] { 0x0b, 0x08, 0x0a, 0x00, (byte) 0x9e, '2', '0', '6', '1', 0, 0, 0 });
		out.writeBytes(code);
		if (!move) {
			for (int a = codeEnd; a < load; a++) {
				out.write(0);
			}
		}
		out.writeBytes(t.data);
		return out.toByteArray();
	}

	private static boolean overlaps(int a0, int a1, int b0, int b1) {
		return a0 <= b1 && a1 >= b0;
	}

	/** Assemble the player (at $080D). */
	private static byte[] player(boolean move, int srcEnd, int dstEnd, int count, boolean underRom, int songIndex, int init, int play, boolean cia) {
		var a = new Asm(CODE_START);
		a.op(0x78);											// SEI
		if (play != 0) {
			a.op(0xa9, 0x7f).op(0x8d, 0x0d, 0xdc);			// LDA #$7F, STA $DC0D (no CIA interrupts)
		}
		if (move) {
			a.op(0xa9, 0x34).op(0x85, 0x01);				// all RAM while copying
			a.op(0xa9, srcEnd & 0xff).op(0x85, 0xfb).op(0xa9, srcEnd >> 8).op(0x85, 0xfc);
			a.op(0xa9, dstEnd & 0xff).op(0x85, 0xfd).op(0xa9, dstEnd >> 8).op(0x85, 0xfe);
			a.op(0xa9, count & 0xff).op(0x85, 0xf7).op(0xa9, (count >> 8) & 0xff).op(0x85, 0xf8);
			a.op(0xa0, 0x00);								// LDY #0
			int loop = a.pc();
			a.op(0xb1, 0xfb).op(0x91, 0xfd);				// LDA ($FB),Y  STA ($FD),Y   (copy from the end down)
			a.op(0xa5, 0xfb).op(0xd0, 0x02).op(0xc6, 0xfc).op(0xc6, 0xfb);
			a.op(0xa5, 0xfd).op(0xd0, 0x02).op(0xc6, 0xfe).op(0xc6, 0xfd);
			a.op(0xa5, 0xf7).op(0xd0, 0x02).op(0xc6, 0xf8).op(0xc6, 0xf7);
			a.op(0xa5, 0xf7).op(0x05, 0xf8);
			a.branch(0xd0, loop);							// BNE loop
		}
		int bank = underRom ? 0x35 : 0x37;					// I/O stays visible; ROMs off if the music lies under them
		if (move || underRom) {
			a.op(0xa9, bank).op(0x85, 0x01);
		}
		if (cia) {
			// default 60 Hz on PAL; the init routine may set its own timer value
			a.op(0xa9, 0x25).op(0x8d, 0x04, 0xdc).op(0xa9, 0x40).op(0x8d, 0x05, 0xdc).op(0xa9, 0x11).op(0x8d, 0x0e, 0xdc);
		}
		a.op(0xa9, songIndex).op(0xa2, 0x00).op(0xa0, 0x00);	// LDA #song  LDX #0  LDY #0
		a.op(0x20, init & 0xff, init >> 8);					// JSR init
		if (play == 0) {
			a.op(0x58);										// CLI: the tune plays from its own interrupt
			int self = a.pc();
			a.op(0x4c, self & 0xff, self >> 8);				// JMP *
			return a.bytes();
		}
		int loop = a.pc();
		if (cia) {
			int wait = a.pc();
			a.op(0xad, 0x0d, 0xdc).op(0x4a);				// LDA $DC0D  LSR
			a.branch(0x90, wait);							// BCC wait (timer A has not run out yet)
		} else {
			a.op(0xa9, 0x80);								// LDA #$80
			int wait = a.pc();
			a.op(0xcd, 0x12, 0xd0);							// CMP $D012
			a.branch(0xd0, wait);							// BNE wait
		}
		a.op(0xee, 0x20, 0xd0).op(0xee, 0x21, 0xd0);		// INC $D020  INC $D021
		a.op(0x20, play & 0xff, play >> 8);					// JSR play
		a.op(0xce, 0x21, 0xd0).op(0xce, 0x20, 0xd0);		// DEC $D021  DEC $D020
		if (!cia) {
			// a very short play routine could still be on line $80: wait for the next line, so it runs only once a frame
			a.op(0xa9, 0x80);								// LDA #$80
			int still = a.pc();
			a.op(0xcd, 0x12, 0xd0);							// CMP $D012
			a.branch(0xf0, still);							// BEQ still
		}
		a.op(0x4c, loop & 0xff, loop >> 8);					// JMP loop
		return a.bytes();
	}

	/** Tiny byte collector for the player code. */
	private static final class Asm {
		private final ByteArrayOutputStream b = new ByteArrayOutputStream();
		private final int origin;

		Asm(int origin) {
			this.origin = origin;
		}

		int pc() {
			return origin + b.size();
		}

		Asm op(int... bytes) {
			for (var x : bytes) {
				b.write(x & 0xff);
			}
			return this;
		}

		void branch(int opcode, int target) {
			int rel = target - (pc() + 2);
			if (rel < -128 || rel > 127) {
				throw new IllegalStateException("branch out of range");
			}
			op(opcode, rel & 0xff);
		}

		byte[] bytes() {
			return b.toByteArray();
		}
	}
}
