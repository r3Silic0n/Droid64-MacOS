package droid64.gui;

import java.io.ByteArrayOutputStream;

/**
 * Builds a self-starting C64 program (BASIC "SYS 2061") which shows a bitmap picture.
 * The picture is copied to VIC bank 1 ($4400 screen, $6000 bitmap); a key press returns to BASIC.
 * Interlaced pictures use bank 1 and bank 2 and flicker between the two bitmaps.
 */
public final class C64PrgExporter {

	private static final int START = 0x0801;
	private static final int CODE = 0x080d;

	private C64PrgExporter() {
	}

	/**
	 * @param layers picture data
	 * @return PRG file including the load address
	 */
	public static byte[] build(CommodoreGraphics.Layers layers) {
		boolean interlace = layers.bitmap2 != null;
		boolean multi = layers.multicolor;
		// pass 1 finds the length of the code, pass 2 puts in the real addresses
		int codeLen = code(layers, 0, 0).length;
		int routine = CODE + codeLen;
		int dataStart = routine + COPY.length;
		var prg = new ByteArrayOutputStream();
		prg.write(START & 0xff);
		prg.write(START >> 8);
		// 10 SYS 2061
		int[] basic = { 0x0b, 0x08, 0x0a, 0x00, 0x9e, '2', '0', '6', '1', 0x00, 0x00, 0x00 };
		for (int b : basic) {
			prg.write(b);
		}
		var codeBytes = code(layers, routine, dataStart);
		prg.write(codeBytes, 0, codeBytes.length);
		prg.write(COPY, 0, COPY.length);
		prg.write(layers.bitmap, 0, 8000);
		if (interlace) {
			prg.write(layers.bitmap2, 0, 8000);
		}
		prg.write(layers.screen, 0, 1000);
		if (multi && layers.color != null) {
			prg.write(layers.color, 0, 1000);
		}
		return prg.toByteArray();
	}

	/** Copies $F7/$F8 bytes from ($FB) to ($FD). */
	private static final byte[] COPY = bytes(
			0xA0, 0x00,			// ldy #0
			0xB1, 0xFB,			// lp: lda ($FB),y
			0x91, 0xFD,			// sta ($FD),y
			0xC8,				// iny
			0xD0, 0x04,			// bne nw
			0xE6, 0xFC,			// inc $FC
			0xE6, 0xFE,			// inc $FE
			0xA5, 0xF7,			// nw: lda $F7
			0xD0, 0x02,			// bne dl
			0xC6, 0xF8,			// dec $F8
			0xC6, 0xF7,			// dl: dec $F7
			0xA5, 0xF7,			// lda $F7
			0x05, 0xF8,			// ora $F8
			0xD0, 0xE7,			// bne lp
			0x60);				// rts

	private static byte[] bytes(int... v) {
		var b = new byte[v.length];
		for (int i = 0; i < v.length; i++) {
			b[i] = (byte) v[i];
		}
		return b;
	}

	private static void emit(ByteArrayOutputStream o, int... v) {
		for (int x : v) {
			o.write(x);
		}
	}

	private static void copy(ByteArrayOutputStream o, int routine, int src, int dst, int len) {
		emit(o, 0xA9, src & 0xff, 0x85, 0xFB, 0xA9, src >> 8, 0x85, 0xFC);
		emit(o, 0xA9, dst & 0xff, 0x85, 0xFD, 0xA9, dst >> 8, 0x85, 0xFE);
		emit(o, 0xA9, len & 0xff, 0x85, 0xF7, 0xA9, len >> 8, 0x85, 0xF8);
		emit(o, 0x20, routine & 0xff, routine >> 8);
	}

	private static byte[] code(CommodoreGraphics.Layers l, int routine, int dataStart) {
		boolean interlace = l.bitmap2 != null;
		boolean multi = l.multicolor;
		var o = new ByteArrayOutputStream();
		int bitmap1 = dataStart;
		int bitmap2 = bitmap1 + 8000;
		int screen = interlace ? bitmap2 + 8000 : bitmap1 + 8000;
		int color = screen + 1000;
		// VIC bank 1: $4000-$7FFF
		emit(o, 0xAD, 0x00, 0xDD, 0x29, 0xFC, 0x09, 0x02, 0x8D, 0x00, 0xDD);
		// The program image reaches up to $4F90 for interlaced pictures, so the data that
		// would be overwritten by the screen at $4400 is copied away first.
		copy(o, routine, bitmap1, 0x6000, 8000);
		if (interlace) {
			copy(o, routine, bitmap2, 0xA000, 8000);
			copy(o, routine, screen, 0x8400, 1000);
		}
		if (multi) {
			copy(o, routine, color, 0xD800, 1000);
		}
		copy(o, routine, screen, 0x4400, 1000);
		int bg = multi ? l.background & 15 : 0;
		emit(o, 0xA9, bg, 0x8D, 0x20, 0xD0, 0x8D, 0x21, 0xD0);
		emit(o, 0xA9, 0x3B, 0x8D, 0x11, 0xD0);
		emit(o, 0xA9, multi ? 0x18 : 0x08, 0x8D, 0x16, 0xD0);
		emit(o, 0xA9, 0x18, 0x8D, 0x18, 0xD0);
		if (interlace) {
			emit(o, 0xAD, 0x12, 0xD0, 0xC9, 0xF8, 0xD0, 0xF9);			// w: wait for raster line 248
			emit(o, 0xAD, 0x00, 0xDD, 0x49, 0x03, 0x8D, 0x00, 0xDD);		// switch VIC bank
			emit(o, 0xAD, 0x12, 0xD0, 0xC9, 0xF8, 0xF0, 0xF9);			// wait until the line is over
			emit(o, 0xA5, 0xC6, 0xF0, 0xE6);								// key buffer empty: again
			emit(o, 0x20, 0xE4, 0xFF);									// flush key
		} else {
			emit(o, 0x20, 0xE4, 0xFF, 0xF0, 0xFB);						// wait for a key
		}
		// back to the normal screen
		emit(o, 0xA9, 0x1B, 0x8D, 0x11, 0xD0, 0xA9, 0xC8, 0x8D, 0x16, 0xD0, 0xA9, 0x15, 0x8D, 0x18, 0xD0);
		emit(o, 0xAD, 0x00, 0xDD, 0x09, 0x03, 0x8D, 0x00, 0xDD);
		emit(o, 0xA9, 0x0E, 0x8D, 0x20, 0xD0, 0xA9, 0x06, 0x8D, 0x21, 0xD0);
		emit(o, 0x20, 0x81, 0xFF, 0x60);
		return o.toByteArray();
	}
}
