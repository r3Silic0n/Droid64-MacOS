package droid64.gui;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a self-starting C64 program (BASIC "SYS 2061") which shows an ECI picture
 * (two interlaced FLI multicolor frames, 16 KB each) on a PAL C64.
 * <p>
 * Frame 1 lives in VIC bank 1 ($4000), frame 2 in bank 3 ($C000); the program flips the bank every frame.
 * A stable double raster interrupt gives cycle exact timing, then the FLI routine forces a (late) bad line
 * on every raster line and selects the screen RAM of the line (8 per frame).
 * The timing was tuned with VICE x64sc (PAL). Any key restarts the computer.
 */
public final class EciPrgExporter {

	private static final int START = 0x0801;
	private static final int CODE = 0x080d;
	private static final int RASTER = 47;
	private static final int FIRST = 52;
	private static final int LAST = 250;
	private static final int PAD = 11;
	private static final int LOOP_X = 9;
	private static final int SHIFT = -4;
	private static final int DSH = -1;

	private EciPrgExporter() {
	}

	/** Tiny 6502 assembler which also counts cycles. */
	private static final class Asm {
		final int org;
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		final Map<String, Integer> labels = new HashMap<>();
		final List<Object[]> fixes = new ArrayList<>();
		int cyc;

		Asm(int org) {
			this.org = org;
		}

		int pc() {
			return org + b.size();
		}

		void label(String n) {
			labels.put(n, pc());
		}

		void e(int cycles, int... bytes) {
			for (int v : bytes) {
				b.write(v);
			}
			cyc += cycles;
		}

		void abs(int op, int addr, int cycles) {
			e(cycles, op, addr & 0xff, addr >> 8);
		}

		void absL(int op, String l, int cycles) {
			fixes.add(new Object[] { b.size() + 1, l, 'a' });
			e(cycles, op, 0, 0);
		}

		void rel(int op, String l) {
			fixes.add(new Object[] { b.size() + 1, l, 'r' });
			e(3, op, 0);
		}

		void immL(String l, boolean hi) {
			fixes.add(new Object[] { b.size() + 1, l, hi ? 'h' : 'l' });
			e(2, 0xa9, 0);
		}

		void ldaI(int v) {
			e(2, 0xa9, v);
		}

		void ldxI(int v) {
			e(2, 0xa2, v);
		}

		void ldyI(int v) {
			e(2, 0xa0, v);
		}

		void sta(int a) {
			abs(0x8d, a, 4);
		}

		void stx(int a) {
			abs(0x8e, a, 4);
		}

		void lda(int a) {
			abs(0xad, a, 4);
		}

		void cmp(int a) {
			abs(0xcd, a, 4);
		}

		void inc(int a) {
			abs(0xee, a, 6);
		}

		void eorI(int v) {
			e(2, 0x49, v);
		}

		void andI(int v) {
			e(2, 0x29, v);
		}

		void oraI(int v) {
			e(2, 0x09, v);
		}

		void cmpI(int v) {
			e(2, 0xc9, v);
		}

		void cpxI(int v) {
			e(2, 0xe0, v);
		}

		void jmp(int a) {
			abs(0x4c, a, 3);
		}

		void jmp(String l) {
			absL(0x4c, l, 3);
		}

		void beq(String l) {
			rel(0xf0, l);
		}

		void bne(String l) {
			rel(0xd0, l);
		}

		void bpl(String l) {
			rel(0x10, l);
		}

		void bcc(String l) {
			rel(0x90, l);
		}

		void nop() {
			e(2, 0xea);
		}

		void bitZp() {
			e(3, 0x24, 0xea);
		}

		void dex() {
			e(2, 0xca);
		}

		void dey() {
			e(2, 0x88);
		}

		void tsx() {
			e(2, 0xba);
		}

		void txs() {
			e(2, 0x9a);
		}

		void cli() {
			e(2, 0x58);
		}

		void sei() {
			e(2, 0x78);
		}

		void rti() {
			e(6, 0x40);
		}

		void txa() {
			e(2, 0x8a);
		}

		void clc() {
			e(2, 0x18);
		}

		void adcI(int v) {
			e(2, 0x69, v);
		}

		void iny() {
			e(2, 0xc8);
		}

		void inx() {
			e(2, 0xe8);
		}

		void ldaZpY(int z) {
			e(5, 0xb1, z);
		}

		void staZpY(int z) {
			e(6, 0x91, z);
		}

		void ldaAbsX(int a) {
			abs(0xbd, a, 4);
		}

		void staAbsX(int a) {
			abs(0x9d, a, 5);
		}

		void staZp(int z) {
			e(3, 0x85, z);
		}

		void ldaZp(int z) {
			e(3, 0xa5, z);
		}

		void delay(int n) {
			if (n < 0 || n == 1) {
				throw new IllegalStateException("bad delay " + n);
			}
			while (n > 0) {
				if (n % 2 == 1 || n == 3) {
					bitZp();
					n -= 3;
				} else {
					nop();
					n -= 2;
				}
			}
		}

		byte[] resolve() {
			var out = b.toByteArray();
			for (var f : fixes) {
				int o = (Integer) f[0];
				int v = labels.get((String) f[1]);
				switch ((char) f[2]) {
				case 'a':
					out[o] = (byte) v;
					out[o + 1] = (byte) (v >> 8);
					break;
				case 'l':
					out[o] = (byte) v;
					break;
				case 'h':
					out[o] = (byte) (v >> 8);
					break;
				default:
					int d = v - (org + o + 1);
					if (d < -128 || d > 127) {
						throw new IllegalStateException("branch too far " + f[1]);
					}
					out[o] = (byte) d;
					break;
				}
			}
			return out;
		}
	}

	private static Asm program(int src) {
		var a = new Asm(CODE);
		a.sei();
		a.ldaI(0x34);
		a.staZp(0x01);
		a.ldaI(0);
		a.staZp(0xfb);
		a.staZp(0xfd);
		a.ldxI(127);
		// copy the 128 pages (frame 1 to $4000-$7FFF, frame 2 to $C000-$FFFF), highest page first
		a.label("mvl");
		a.clc();
		a.txa();
		a.adcI(src >> 8);
		a.staZp(0xfc);
		a.clc();
		a.txa();
		a.adcI(0x40);
		a.staZp(0xfe);
		a.cpxI(64);
		a.bcc("lo");
		a.ldaZp(0xfe);
		a.clc();
		a.adcI(0x40);
		a.staZp(0xfe);
		a.label("lo");
		a.ldyI(0);
		a.label("mvc");
		a.ldaZpY(0xfb);
		a.staZpY(0xfd);
		a.iny();
		a.bne("mvc");
		a.dex();
		a.bpl("mvl");
		a.ldaI(0x35);
		a.staZp(0x01);
		a.ldxI(0);
		a.label("cl");
		for (int k = 0; k < 4; k++) {
			a.ldaAbsX(0x5c00 + k * 256);
			a.staAbsX(0xd800 + k * 256);
		}
		a.inx();
		a.bne("cl");
		a.ldaI(0x7f);
		a.sta(0xdc0d);
		a.sta(0xdd0d);
		a.lda(0xdc0d);
		a.lda(0xdd0d);
		a.ldaI(0);
		a.sta(0xd015);
		a.sta(0xd020);
		a.sta(0xd021);
		a.ldaI(0x18);
		a.sta(0xd016);
		a.ldaI(0x80);
		a.sta(0xd018);
		a.ldaI(0x3b);
		a.sta(0xd011);
		a.lda(0xdd00);
		a.andI(0xfc);
		a.oraI(2);
		a.sta(0xdd00);
		a.immL("rti_stub", false);
		a.sta(0xfffa);
		a.immL("rti_stub", true);
		a.sta(0xfffb);
		a.immL("irq1", false);
		a.sta(0xfffe);
		a.immL("irq1", true);
		a.sta(0xffff);
		a.ldaI(RASTER);
		a.sta(0xd012);
		a.ldaI(1);
		a.sta(0xd01a);
		a.sta(0xd019);
		a.cli();
		// wait for a key (first for the release of RETURN), with a short debounce
		a.ldaI(0);
		a.sta(0xdc00);
		a.label("rel");
		a.lda(0xdc01);
		a.cmpI(0xff);
		a.bne("rel");
		a.label("wk");
		a.lda(0xdc01);
		a.cmpI(0xff);
		a.beq("wk");
		a.ldyI(40);
		a.label("db");
		a.ldxI(0);
		a.label("db2");
		a.dex();
		a.bne("db2");
		a.dey();
		a.bne("db");
		a.lda(0xdc01);
		a.cmpI(0xff);
		a.beq("wk");
		a.sei();
		a.ldaI(0x37);
		a.staZp(0x01);
		a.jmp(0xfce2);
		a.label("rti_stub");
		a.rti();
		// first raster interrupt: arm the second one on the next line
		a.label("irq1");
		a.immL("irq2", false);
		a.sta(0xfffe);
		a.immL("irq2", true);
		a.sta(0xffff);
		a.inc(0xd012);
		a.ldaI(1);
		a.sta(0xd019);
		a.tsx();
		a.cli();
		for (int i = 0; i < 30; i++) {
			a.nop();
		}
		a.label("irq2");
		a.txs();
		a.ldxI(LOOP_X);
		a.label("dl");
		a.dex();
		a.bne("dl");
		a.bitZp();
		a.lda(0xd012);
		a.cmp(0xd012);
		a.beq("fx");
		a.label("fx");
		a.cyc = 0;
		// stable from here
		a.immL("irq1", false);
		a.sta(0xfffe);
		a.immL("irq1", true);
		a.sta(0xffff);
		a.ldaI(1);
		a.sta(0xd019);
		a.ldaI(RASTER);
		a.sta(0xd012);
		a.delay(141 + SHIFT - a.cyc);
		for (int n = FIRST; n <= LAST; n++) {
			int l = (n - 51 + DSH) & 7;
			a.ldxI(0x80 + 0x10 * l);
			a.ldaI(0x38 | (n & 7));
			a.stx(0xd018);
			a.sta(0xd011);
			a.delay(PAD);
		}
		a.ldaI(0x3b);
		a.sta(0xd011);
		a.lda(0xdd00);
		a.eorI(2);
		a.sta(0xdd00);
		a.ldaI(0x80);
		a.sta(0xd018);
		a.rti();
		return a;
	}

	/**
	 * @param eci 32768 bytes: frame 1 (bitmap, colour RAM at +$1C00, 8 screens at +$2000), frame 2 likewise
	 * @return PRG file including the load address
	 */
	public static byte[] build(byte[] eci) {
		var data = java.util.Arrays.copyOf(eci, 0x8000);
		int len1 = program(0x2000).pc();
		int src = (len1 + 255) & ~255;
		var a = program(src);
		var code = a.resolve();
		var prg = new ByteArrayOutputStream();
		prg.write(START & 0xff);
		prg.write(START >> 8);
		int[] basic = { 0x0b, 0x08, 0x0a, 0x00, 0x9e, '2', '0', '6', '1', 0x00, 0x00, 0x00 };
		for (int v : basic) {
			prg.write(v);
		}
		prg.write(code, 0, code.length);
		int pad = src - (CODE + code.length);
		for (int i = 0; i < pad; i++) {
			prg.write(0);
		}
		prg.write(data, 0, data.length);
		return prg.toByteArray();
	}
}
