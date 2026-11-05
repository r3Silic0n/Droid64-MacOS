package droid64.gui;

import java.io.ByteArrayOutputStream;

/**
 * Builds a self-starting Plus/4 program (BASIC "SYS 4109") which shows a bitmap picture.
 * Attribute (luminance) matrix goes to $4000, video (colour) matrix to $4400 and the bitmap to $6000.
 * A key press returns to BASIC.
 */
public final class Plus4PrgExporter {

	private static final int START = 0x1001;
	private static final int CODE = 0x100d;
	private static final int ROUTINE_LEN = 40;
	/** TED registers saved at start and restored at the end. */
	private static final int[] SAVED = { 0xFF06, 0xFF07, 0xFF12, 0xFF14, 0xFF15, 0xFF16, 0xFF19 };

	private Plus4PrgExporter() {
	}

	/**
	 * @param l picture data
	 * @return PRG file including the load address
	 */
	public static byte[] build(CommodoreGraphics.Plus4Layers l) {
		// pass 1 finds the length of the code, pass 2 puts in the real addresses
		int codeLen = code(l, 0, 0, 0).length;
		int routine = CODE + codeLen;
		int save = routine + ROUTINE_LEN;
		int dataStart = save + SAVED.length;
		var prg = new ByteArrayOutputStream();
		prg.write(START & 0xff);
		prg.write(START >> 8);
		// 10 SYS 4109
		int[] basic = { 0x0b, 0x10, 0x0a, 0x00, 0x9e, '4', '1', '0', '9', 0x00, 0x00, 0x00 };
		for (int b : basic) {
			prg.write(b);
		}
		var c = code(l, routine, save, dataStart);
		prg.write(c, 0, c.length);
		var r = routine(routine);
		prg.write(r, 0, r.length);
		for (int i = 0; i < SAVED.length; i++) {
			prg.write(0);
		}
		prg.write(l.luminance, 0, 1000);
		prg.write(l.color, 0, 1000);
		prg.write(l.bitmap, 0, 8000);
		return prg.toByteArray();
	}

	/** Copy using self modifying code. Operand cells at R+3 (source), R+6 (destination), counter at R+38. */
	private static byte[] routine(int r) {
		int cnt = r + 38;
		var o = new ByteArrayOutputStream();
		emit(o, 0xA0, 0x00);								// R+0   ldy #0
		emit(o, 0xB9, 0x00, 0x00);							// R+2   lp: lda src,y
		emit(o, 0x99, 0x00, 0x00);							// R+5   sta dst,y
		emit(o, 0xC8);										// R+8   iny
		emit(o, 0xD0, 0x06);								// R+9   bne nw
		emit(o, 0xEE, (r + 4) & 0xff, (r + 4) >> 8);		// R+11  inc src hi
		emit(o, 0xEE, (r + 7) & 0xff, (r + 7) >> 8);		// R+14  inc dst hi
		emit(o, 0xAD, cnt & 0xff, cnt >> 8);				// R+17  nw: lda cntlo
		emit(o, 0xD0, 0x03);								// R+20  bne dl
		emit(o, 0xCE, (cnt + 1) & 0xff, (cnt + 1) >> 8);	// R+22  dec cnthi
		emit(o, 0xCE, cnt & 0xff, cnt >> 8);				// R+25  dl: dec cntlo
		emit(o, 0xAD, cnt & 0xff, cnt >> 8);				// R+28  lda cntlo
		emit(o, 0x0D, (cnt + 1) & 0xff, (cnt + 1) >> 8);	// R+31  ora cnthi
		emit(o, 0xD0, 0xDE);								// R+34  bne lp
		emit(o, 0x60);										// R+36  rts
		emit(o, 0x00, 0x00, 0x00);						// R+37  spare, counter (R+38, R+39)
		return o.toByteArray();
	}

	private static void emit(ByteArrayOutputStream o, int... v) {
		for (int x : v) {
			o.write(x);
		}
	}

	private static void sta(ByteArrayOutputStream o, int addr) {
		emit(o, 0x8D, addr & 0xff, addr >> 8);
	}

	private static void lda(ByteArrayOutputStream o, int addr) {
		emit(o, 0xAD, addr & 0xff, addr >> 8);
	}

	private static void ldaImm(ByteArrayOutputStream o, int v) {
		emit(o, 0xA9, v & 0xff);
	}

	private static void copy(ByteArrayOutputStream o, int r, int src, int dst, int len) {
		ldaImm(o, src);
		sta(o, r + 3);
		ldaImm(o, src >> 8);
		sta(o, r + 4);
		ldaImm(o, dst);
		sta(o, r + 6);
		ldaImm(o, dst >> 8);
		sta(o, r + 7);
		ldaImm(o, len);
		sta(o, r + 38);
		ldaImm(o, len >> 8);
		sta(o, r + 39);
		emit(o, 0x20, r & 0xff, r >> 8);
	}

	private static byte[] code(CommodoreGraphics.Plus4Layers l, int routine, int save, int dataStart) {
		var o = new ByteArrayOutputStream();
		for (int i = 0; i < SAVED.length; i++) {
			lda(o, SAVED[i]);
			sta(o, save + i);
		}
		copy(o, routine, dataStart, 0x4000, 1000);
		copy(o, routine, dataStart + 1000, 0x4400, 1000);
		copy(o, routine, dataStart + 2000, 0x6000, 8000);
		ldaImm(o, l.background0 & 0x7f);
		sta(o, 0xFF15);
		sta(o, 0xFF19);
		ldaImm(o, l.background1 & 0x7f);
		sta(o, 0xFF16);
		ldaImm(o, 0x40);									// video matrix at $4400, attributes at $4000
		sta(o, 0xFF14);
		lda(o, 0xFF12);
		emit(o, 0x29, 0xC3, 0x09, 0x18);					// bitmap at $6000 in RAM (bit 2 = 0)
		sta(o, 0xFF12);
		lda(o, 0xFF07);
		if (l.multicolor) {
			emit(o, 0x09, 0x10);
		} else {
			emit(o, 0x29, 0xEF);
		}
		sta(o, 0xFF07);
		ldaImm(o, 0x3B);									// bitmap mode on
		sta(o, 0xFF06);
		emit(o, 0x20, 0xE4, 0xFF, 0xF0, 0xFB);				// wait for a key
		for (int i = 0; i < SAVED.length; i++) {
			lda(o, save + i);
			sta(o, SAVED[i]);
		}
		emit(o, 0x60);
		return o.toByteArray();
	}
}
