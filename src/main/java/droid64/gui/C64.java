package droid64.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

import droid64.d64.Utility;

/** Shared C64 helpers for the tools: colours and drawing of PETSCII / screen code characters. */
final class C64 {

	/** The 16 C64 colours (Pepto palette). */
	static final Color[] PALETTE = {
			new Color(0x000000), new Color(0xFFFFFF), new Color(0x68372B), new Color(0x70A4B2),
			new Color(0x6F3D86), new Color(0x588D43), new Color(0x352879), new Color(0xB8C76F),
			new Color(0x6F4F25), new Color(0x433900), new Color(0x9A6759), new Color(0x444444),
			new Color(0x6C6C6C), new Color(0x9AD284), new Color(0x6C5EB5), new Color(0x959595) };

	static final String[] COLOR_NAMES = { "Black", "White", "Red", "Cyan", "Purple", "Green", "Blue", "Yellow",
			"Orange", "Brown", "Light red", "Dark grey", "Grey", "Light green", "Light blue", "Light grey" };

	static final int BLUE = 6;
	static final int LIGHT_BLUE = 14;

	/** Machines whose default screen colours the editors can show. */
	enum Machine {
		/** Power-on colours: C64 blue / light blue, C128 dark grey / light green, VDC black / cyan, VIC-20 white / blue, Plus/4 white / black, PET black / green. */
		C64("Commodore 64", 0, PALETTE[BLUE], PALETTE[LIGHT_BLUE]),
		C128("Commodore 128 (40 columns)", 1, PALETTE[11], PALETTE[13]),
		C128_VDC("Commodore 128 (80 columns)", 1, new Color(0x000000), new Color(0x55FFFF)),
		VIC20("VIC-20", 2, new Color(0xFFFFFF), new Color(0x6532F3)),
		PLUS4("Plus/4, C16, C116", 3, new Color(0xFFFFFF), new Color(0x000000)),
		PET("PET / CBM", 4, new Color(0x000000), new Color(0x41FF00));

		final String label;
		/** Index of the character ROM in chargens.bin. */
		final int rom;
		final Color background;
		final Color foreground;

		Machine(String label, int rom, Color background, Color foreground) {
			this.label = label;
			this.rom = rom;
			this.background = background;
			this.foreground = foreground;
		}

		/** A colour between background and foreground, 0 = background, 1 = foreground. */
		Color mix(double amount) {
			int r = (int) Math.round(background.getRed() + (foreground.getRed() - background.getRed()) * amount);
			int g = (int) Math.round(background.getGreen() + (foreground.getGreen() - background.getGreen()) * amount);
			int b = (int) Math.round(background.getBlue() + (foreground.getBlue() - background.getBlue()) * amount);
			return new Color(r, g, b);
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static byte[] chargens;

	/** Character ROM data: 5 machines x 2 sets (upper / lower case) x 128 characters x 8 bytes. */
	private static synchronized byte[] chargens() {
		if (chargens == null) {
			try (var in = C64.class.getResourceAsStream("chargens.bin")) {
				chargens = in == null ? new byte[10240] : in.readAllBytes();
			} catch (java.io.IOException e) {
				chargens = new byte[10240];
			}
		}
		return chargens;
	}

	/**
	 * Draw one character cell with the glyph of the character ROM of a machine.
	 * @param sc screen code 0-255 (128-255 are reversed)
	 * @param lower true for the lower case / upper case character set
	 */
	static void drawScreenCode(Graphics2D g, int sc, int x, int y, int w, int h, Color fg, Color bg, Machine machine, boolean lower) {
		boolean reverse = (sc & 0x80) != 0;
		var back = reverse ? fg : bg;
		var front = reverse ? bg : fg;
		g.setColor(back);
		g.fillRect(x, y, w, h);
		var rom = chargens();
		int base = (machine.rom * 2 + (lower ? 1 : 0)) * 1024 + (sc & 0x7f) * 8;
		g.setColor(front);
		for (int row = 0; row < 8; row++) {
			int bits = rom[base + row] & 0xff;
			if (bits == 0) {
				continue;
			}
			int y0 = y + row * h / 8;
			int y1 = y + (row + 1) * h / 8;
			for (int col = 0; col < 8; col++) {
				if ((bits & (0x80 >> col)) != 0) {
					int x0 = x + col * w / 8;
					int x1 = x + (col + 1) * w / 8;
					g.fillRect(x0, y0, x1 - x0, y1 - y0);
				}
			}
		}
	}

	/** Draw a PETSCII byte as it is shown in a directory listing, with the character ROM of a machine. */
	static void drawPetscii(Graphics2D g, int petscii, int x, int y, int w, int h, Color fg, Color bg, Machine machine, boolean lower) {
		drawScreenCode(g, petsciiToScreen(petscii), x, y, w, h, fg, bg, machine, lower);
	}

	private static final Map<Integer, Font> FONTS = new HashMap<>();

	private C64() {
	}

	/** Font for characters with a cell of this height. */
	static Font font(int cellHeight) {
		return FONTS.computeIfAbsent(cellHeight, h -> Setting.CBM_FONT.getFont().deriveFont(Font.PLAIN, h * 1.28f));
	}

	/**
	 * Convert a PETSCII byte, as it is shown in a quoted directory listing, to a screen code (0-255).
	 * Control codes are shown as reversed characters, as on a real C64 in quote mode.
	 */
	static int petsciiToScreen(int b) {
		b &= 0xff;
		if (b < 0x20) {
			return b + 0x80;
		} else if (b < 0x40) {
			return b;
		} else if (b < 0x60) {
			return b - 0x40;
		} else if (b < 0x80) {
			return b - 0x20;
		} else if (b < 0xa0) {
			return b + 0x40;
		} else if (b < 0xc0) {
			return b - 0x40;
		} else if (b < 0xff) {
			return b - 0x80;
		} else {
			return 0x5e;
		}
	}

	/** Convert a printable ASCII character typed on the keyboard to PETSCII (upper case / graphics mode). */
	static int asciiToPetscii(char c) {
		if (c >= 'a' && c <= 'z') {
			return c - 'a' + 0x41;
		} else if (c >= 'A' && c <= 'Z') {
			return c - 'A' + 0x41;
		} else if (c >= 0x20 && c < 0x40) {
			return c;
		} else if (c == '@') {
			return 0x40;
		} else if (c == '[') {
			return 0x5b;
		} else if (c == ']') {
			return 0x5d;
		}
		return -1;
	}

	/** Font character for a screen code 0-127 (the upper half is the same glyph, reversed). */
	private static char glyphForScreen(int sc) {
		int s = sc & 0x7f;
		int petscii;
		if (s < 0x20) {
			petscii = s + 0x40;
		} else if (s < 0x40) {
			petscii = s;
		} else if (s < 0x60) {
			petscii = s + 0x20;
		} else {
			petscii = s + 0x40;
		}
		return (char) Utility.petsciiGlyph(petscii);
	}

	/**
	 * Draw one character cell.
	 * @param sc screen code 0-255 (128-255 are reversed)
	 */
	static void drawScreenCode(Graphics2D g, int sc, int x, int y, int w, int h, Color fg, Color bg) {
		boolean reverse = (sc & 0x80) != 0;
		g.setColor(reverse ? fg : bg);
		g.fillRect(x, y, w, h);
		if ((sc & 0x7f) == 0x20) {
			return;
		}
		g.setColor(reverse ? bg : fg);
		var oldClip = g.getClip();
		g.clipRect(x, y, w, h);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		var oldFont = g.getFont();
		var f = font(h);
		g.setFont(f);
		var fm = g.getFontMetrics();
		var s = String.valueOf(glyphForScreen(sc));
		int tx = x + (w - fm.stringWidth(s)) / 2;
		int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(s, tx, ty);
		g.setFont(oldFont);
		g.setClip(oldClip);
	}

	/** Draw a PETSCII byte as it is shown in a directory listing. */
	static void drawPetscii(Graphics2D g, int petscii, int x, int y, int w, int h, Color fg, Color bg) {
		drawScreenCode(g, petsciiToScreen(petscii), x, y, w, h, fg, bg);
	}
}
