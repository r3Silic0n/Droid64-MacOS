package droid64.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Decoders for the well known Commodore 64 and Plus/4 bitmap picture formats.
 * Every picture is returned as a 320 x 200 image (multicolor pixels are doubled).
 */
public final class CommodoreGraphics {

	/** Picture formats. */
	public enum Format {
		AUTO("Detect automatically"),
		KOALA("Koala Painter (multicolor, $6000)"),
		KOALA_PACKED("Koala Painter, packed (GG)"),
		AMICA("Amica Paint (multicolor, packed, $4000)"),
		ECI("ECI (interlaced FLI multicolor, packed, $4000)"),
		DRAZLACE("Drazlace / Drazpaint (interlaced multicolor, $5800)"),
		ART_STUDIO("Art Studio (hires, $2000)"),
		DOODLE("Doodle (hires, $5C00)"),
		HIRES("Hires bitmap + screen RAM"),
		PLUS4_MULTI("Plus/4 multicolor (Multi Botticelli)"),
		PLUS4_HIRES("Plus/4 hires (Botticelli)"),
		IFF("Amiga IFF (ILBM / PBM)"),
		HIRES_PLAIN("Hires bitmap, no colour data"),
		MULTI_PLAIN("Multicolor bitmap, no colour data");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** A decoded picture. */
	public static final class Result {
		public final BufferedImage image;
		public final Format format;
		public final String description;

		/** Native C64 data (bitmap, screen, colour RAM) for saving a runnable program, null for Plus/4 pictures */
		public Layers layers;
		/** Native Plus/4 data for saving a runnable program, null for C64 pictures */
		public Plus4Layers plus4;
		/** Unpacked data of an ECI picture (two frames of 16 KB: bitmap, colour RAM, 8 screens), otherwise null */
		public byte[] eci;

		Result(BufferedImage image, Format format, String description) {
			this.image = image;
			this.format = format;
			this.description = description;
		}

		Result with(Plus4Layers l) {
			this.plus4 = l;
			return this;
		}

		Result with(Layers l) {
			this.layers = l;
			return this;
		}
	}

	/** C64 picture data as it sits in memory. */
	public static final class Layers {
		public final boolean multicolor;
		public final byte[] bitmap;
		/** second bitmap of an interlaced picture, otherwise null */
		public final byte[] bitmap2;
		public final byte[] screen;
		/** colour RAM, null for hires */
		public final byte[] color;
		public final int background;

		Layers(boolean multicolor, byte[] bitmap, byte[] bitmap2, byte[] screen, byte[] color, int background) {
			this.multicolor = multicolor;
			this.bitmap = bitmap;
			this.bitmap2 = bitmap2;
			this.screen = screen;
			this.color = color;
			this.background = background;
		}
	}

	/** Plus/4 picture data: attribute (luminance) matrix, video (colour) matrix and bitmap. */
	public static final class Plus4Layers {
		public final boolean multicolor;
		public final byte[] luminance;
		public final byte[] color;
		public final byte[] bitmap;
		public final int background0;
		public final int background1;

		Plus4Layers(boolean multicolor, byte[] luminance, byte[] color, byte[] bitmap, int background0, int background1) {
			this.multicolor = multicolor;
			this.luminance = luminance;
			this.color = color;
			this.bitmap = bitmap;
			this.background0 = background0;
			this.background1 = background1;
		}
	}

	private static byte[] part(byte[] d, int from, int len) {
		var out = new byte[len];
		for (int i = 0; i < len; i++) {
			out[i] = (byte) u(d, from + i);
		}
		return out;
	}

	private static Layers koalaLayers(byte[] b) {
		return new Layers(true, part(b, 0, 8000), null, part(b, KOALA_SCREEN, 1000), part(b, KOALA_COLOR, 1000), u(b, KOALA_BG) & 15);
	}

	/** Plus/4 (TED) colours, index = luminance * 16 + colour (VICE "Colodore" palette). */
	private static final int[] TED_PALETTE = {
		0x000000, 0x171717, 0x46070a, 0x002a26, 0x3e0246, 0x003300, 0x0f0d70, 0x1f2100,
		0x3e0e00, 0x301700, 0x0f2b00, 0x460326, 0x00310a, 0x031761, 0x1f0770, 0x033100,
		0x000000, 0x262626, 0x591417, 0x013b37, 0x510c59, 0x054501, 0x1e1c85, 0x303200,
		0x511c01, 0x422700, 0x1e3c00, 0x590e37, 0x014217, 0x0f2675, 0x301385, 0x0f4300,
		0x000000, 0x373737, 0x6d2327, 0x0c4e49, 0x641b6d, 0x12580c, 0x2e2c9b, 0x414400,
		0x642c0c, 0x553800, 0x2e4e00, 0x6d1d49, 0x0c5527, 0x1d378a, 0x41229b, 0x1d5600,
		0x000000, 0x4a4a4a, 0x813338, 0x1a615d, 0x792a82, 0x206c1a, 0x3f3db1, 0x545700,
		0x793d1a, 0x684a07, 0x3f6200, 0x812d5d, 0x1a6938, 0x2d49a0, 0x5433b1, 0x2d6907,
		0x000000, 0x7b7b7b, 0xb86267, 0x449690, 0xaf58b9, 0x4ca144, 0x706deb, 0x878a1f,
		0xaf6e44, 0x9d7c2b, 0x70961f, 0xb85a90, 0x449e67, 0x5b7bd9, 0x8762eb, 0x5b9e2b,
		0x000000, 0x9b9b9b, 0xdb8186, 0x61b7b1, 0xd176dc, 0x69c360, 0x8f8cff, 0xa8ab38,
		0xd18d60, 0xbf9c45, 0x8fb738, 0xdb79b1, 0x61c086, 0x799bfd, 0xa880ff, 0x79c045,
		0x000000, 0xe0e0e0, 0xffc3c9, 0xa0fef8, 0xffb7ff, 0xa9ff9f, 0xd3d0ff, 0xedf171,
		0xffd19f, 0xffe081, 0xd3fe71, 0xffbaf8, 0xa0ffc9, 0xbbe0ff, 0xedc3ff, 0xbbff81,
		0x000000, 0xffffff, 0xffffff, 0xfdffff, 0xffffff, 0xfffffd, 0xffffff, 0xffffc9,
		0xfffffd, 0xffffdb, 0xffffc9, 0xffffff, 0xfdffff, 0xffffff, 0xffffff, 0xffffdb,
	};

	private static final int KOALA_BITMAP = 0;
	private static final int KOALA_SCREEN = 8000;
	private static final int KOALA_COLOR = 9000;
	private static final int KOALA_BG = 10000;

	private CommodoreGraphics() {
	}

	private static int le(byte[] d) {
		return d.length >= 2 ? (d[0] & 0xff) | (d[1] & 0xff) << 8 : -1;
	}

	private static int u(byte[] d, int i) {
		return i >= 0 && i < d.length ? d[i] & 0xff : 0;
	}

	/**
	 * Guess the format.
	 * @param data file with 2 byte load address
	 * @return format or null if it is not a known picture
	 */
	public static Format detect(byte[] data) {
		if (IffImage.isIff(data)) {
			return Format.IFF;
		}
		if (data == null || data.length < 1000) {
			return null;
		}
		int load = le(data);
		int len = data.length;
		switch (load) {
		case 0x6000:
			if (len >= 10003) {
				return Format.KOALA;
			}
			return unpack(data, 0xfe, 2, true).length >= 10001 ? Format.KOALA_PACKED : null;
		case 0x4000:
			if (isEci(data)) {
				return Format.ECI;
			}
			return unpack(data, 0xc2, 2, true).length >= 10001 ? Format.AMICA : null;
		case 0x5800:
			if (len >= 18242 || unpack(data, 0xc2, 2, true).length >= 18240) {
				return Format.DRAZLACE;
			}
			return null;
		case 0x2000:
			if (len >= 9002 && len <= 9100) {
				return Format.ART_STUDIO;
			}
			if (len >= 10003 && len <= 10100) {
				// Koala layout (bitmap, screen RAM, colour RAM, background) followed by a few extra bytes
				return Format.KOALA;
			}
			return isPlainMulticolor(data) ? Format.MULTI_PLAIN : plainBitmap(data);
		case 0x5c00:
			return len >= 9218 && len <= 9300 ? Format.DOODLE : null;
		case 0x7800:
			return len >= 2 + 0x2000 + 0x800 - 192 ? plus4Format(data) : null;
		default:
			break;
		}
		if (len >= 10003 && len <= 10010) {
			return Format.KOALA;
		}
		if (len >= 9002 && len <= 9100) {
			return Format.HIRES;
		}
		return plainBitmap(data);
	}

	/** A bare 8000 byte bitmap with a 2 byte load address, saved by programs that keep the colours elsewhere. */
	private static Format plainBitmap(byte[] data) {
		int load = le(data);
		boolean bitmapAddress = load == 0x2000 || load == 0x4000 || load == 0x6000 || load == 0x8000 || load == 0xa000 || load == 0xe000;
		return data.length == 8002 && bitmapAddress ? Format.HIRES_PLAIN : null;
	}

	/** A multicolor bitmap without colour data: mostly the pixel patterns %01010101 and %10101010 and shorter than 8000 bytes. */
	private static boolean isPlainMulticolor(byte[] data) {
		if (data.length < 6000 || data.length > 8001) {
			return false;
		}
		int pattern = 0;
		for (int i = 2; i < data.length; i++) {
			int v = data[i] & 0xff;
			if (v == 0x55 || v == 0xaa) {
				pattern++;
			}
		}
		return pattern * 4 >= data.length;
	}

	/** ECI files are packed with the escape byte in the first data byte and unpack to 32 KB plus 3 bytes. */
	private static boolean isEci(byte[] data) {
		if (data.length < 100) {
			return false;
		}
		int n = unpack(data, u(data, 2), 3, true).length;
		return n >= 0x8000 && n <= 0x8010;
	}

	private static Format plus4Format(byte[] data) {
		return isMulti(data) ? Format.PLUS4_MULTI : Format.PLUS4_HIRES;
	}

	/** Multi Botticelli marks its files with "MULT" at $7BFA. */
	private static boolean isMulti(byte[] d) {
		int o = 2 + 0x3fa;
		return u(d, o) == 'M' && u(d, o + 1) == 'U' && u(d, o + 2) == 'L' && u(d, o + 3) == 'T';
	}

	/**
	 * Run length decoder: escape, count, value (a count of 0 means 256).
	 * @param data data
	 * @param escape escape byte
	 * @param start first byte
	 * @param countFirst count comes before the value
	 * @return unpacked bytes
	 */
	static byte[] unpack(byte[] data, int escape, int start, boolean countFirst) {
		var out = new java.io.ByteArrayOutputStream(20000);
		int i = start;
		while (i < data.length && out.size() < 40000) {
			int b = data[i++] & 0xff;
			if (b == escape && i + 1 < data.length) {
				int x = data[i] & 0xff;
				int y = data[i + 1] & 0xff;
				i += 2;
				int count = countFirst ? x : y;
				int value = countFirst ? y : x;
				if (count == 0) {
					count = 256;
				}
				for (int k = 0; k < count; k++) {
					out.write(value);
				}
			} else {
				out.write(b);
			}
		}
		return out.toByteArray();
	}

	/**
	 * Decode a picture.
	 * @param data file with 2 byte load address
	 * @param wanted format, or AUTO
	 * @return picture or null when the data can not be shown as that format
	 */
	public static Result decode(byte[] data, Format wanted) {
		Format f = wanted == null || wanted == Format.AUTO ? detect(data) : wanted;
		if (f == null || data == null || data.length < 4) {
			return null;
		}
		switch (f) {
		case KOALA:
			var kb = Arrays.copyOfRange(data, 2, data.length);
			return new Result(multicolor(kb), f, "Koala Painter, 320 x 200, multicolor").with(koalaLayers(kb));
		case KOALA_PACKED:
			var gb = unpack(data, 0xfe, 2, true);
			return new Result(multicolor(gb), f, "Koala Painter (packed), 320 x 200, multicolor").with(koalaLayers(gb));
		case AMICA:
			var ab = unpack(data, 0xc2, 2, true);
			return new Result(multicolor(ab), f, "Amica Paint, 320 x 200, multicolor").with(koalaLayers(ab));
		case ECI:
			return eci(data);
		case DRAZLACE:
			return drazlace(data);
		case ART_STUDIO:
		case HIRES:
			return new Result(hires(data, 2, 2 + 8000), f, (f == Format.ART_STUDIO ? "Art Studio" : "Hires bitmap") + ", 320 x 200, hires")
					.with(new Layers(false, part(data, 2, 8000), null, part(data, 2 + 8000, 1000), null, 0));
		case DOODLE:
			return new Result(hires(data, 2 + 1024, 2), f, "Doodle, 320 x 200, hires")
					.with(new Layers(false, part(data, 2 + 1024, 8000), null, part(data, 2, 1000), null, 0));
		case PLUS4_MULTI:
		case PLUS4_HIRES:
			return plus4(data, f);
		case IFF:
			var iff = IffImage.decode(data);
			return iff == null ? null : new Result(iff.image, f, iff.description);
		case HIRES_PLAIN:
			return new Result(plainBitmap(data, false), f, "Hires bitmap without colour data, 320 x 200");
		case MULTI_PLAIN:
			return new Result(plainBitmap(data, true), f, "Multicolor bitmap without colour data, 320 x 200 (shown in grey)");
		default:
			return null;
		}
	}

	/** Bitmap only: hires in white on black, multicolor in four grey levels. The bitmap may be shorter than 8000 bytes. */
	private static BufferedImage plainBitmap(byte[] d, boolean multi) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		int[] grey = { 0x000000, 0x808080, 0xc0c0c0, 0xffffff };
		for (int cell = 0; cell < 1000 && 2 + cell * 8 < d.length; cell++) {
			int ox = (cell % 40) * 8;
			int oy = (cell / 40) * 8;
			for (int line = 0; line < 8; line++) {
				int v = u(d, 2 + cell * 8 + line);
				if (multi) {
					for (int p = 0; p < 4; p++) {
						int rgb = grey[(v >> (6 - 2 * p)) & 3];
						img.setRGB(ox + p * 2, oy + line, rgb);
						img.setRGB(ox + p * 2 + 1, oy + line, rgb);
					}
				} else {
					for (int p = 0; p < 8; p++) {
						img.setRGB(ox + p, oy + line, ((v >> (7 - p)) & 1) == 1 ? 0xffffff : 0x000000);
					}
				}
			}
		}
		return img;
	}

	private static Color c64(int i) {
		return C64.PALETTE[i & 15];
	}

	/** Koala layout: bitmap, screen RAM, colour RAM, background. */
	private static BufferedImage multicolor(byte[] b) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		int bg = u(b, KOALA_BG) & 15;
		for (int cell = 0; cell < 1000; cell++) {
			int scr = u(b, KOALA_SCREEN + cell);
			int col = u(b, KOALA_COLOR + cell) & 15;
			int[] c = { c64(bg).getRGB(), c64(scr >> 4).getRGB(), c64(scr).getRGB(), c64(col).getRGB() };
			mcCell(img, b, cell, KOALA_BITMAP + cell * 8, c);
		}
		return img;
	}

	private static void mcCell(BufferedImage img, byte[] b, int cell, int bitmapPos, int[] c) {
		int ox = (cell % 40) * 8;
		int oy = (cell / 40) * 8;
		for (int line = 0; line < 8; line++) {
			int v = u(b, bitmapPos + line);
			for (int p = 0; p < 4; p++) {
				int rgb = c[(v >> (6 - 2 * p)) & 3];
				img.setRGB(ox + p * 2, oy + line, rgb);
				img.setRGB(ox + p * 2 + 1, oy + line, rgb);
			}
		}
	}

	/** Hires: screen RAM nibbles give foreground (high) and background (low). */
	private static BufferedImage hires(byte[] d, int bm, int sc) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		for (int cell = 0; cell < 1000; cell++) {
			int s = u(d, sc + cell);
			int fg = c64(s >> 4).getRGB();
			int bg = c64(s).getRGB();
			int ox = (cell % 40) * 8;
			int oy = (cell / 40) * 8;
			for (int line = 0; line < 8; line++) {
				int v = u(d, bm + cell * 8 + line);
				for (int p = 0; p < 8; p++) {
					img.setRGB(ox + p, oy + line, ((v >> (7 - p)) & 1) == 1 ? fg : bg);
				}
			}
		}
		return img;
	}

	/**
	 * ECI: two FLI frames (one per 16 KB VIC bank) shown alternately; here blended.
	 * Each frame is a multicolor bitmap at +0, colour RAM at +$1C00 and eight screen RAMs at +$2000.
	 */
	private static Result eci(byte[] data) {
		byte[] b = unpack(data, u(data, 2), 3, true);
		if (b.length < 0x8000) {
			b = Arrays.copyOf(b, 0x8000);
		}
		int bg = b.length > 0x8000 ? b[0x8000] & 15 : 0;
		var frames = new BufferedImage[2];
		for (int f = 0; f < 2; f++) {
			int base = f * 0x4000;
			var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
			for (int cell = 0; cell < 1000; cell++) {
				int col = u(b, base + 0x1c00 + cell) & 15;
				int ox = (cell % 40) * 8;
				int oy = (cell / 40) * 8;
				for (int line = 0; line < 8; line++) {
					int scr = u(b, base + 0x2000 + line * 0x400 + cell);
					int[] c = { c64(bg).getRGB(), c64(scr >> 4).getRGB(), c64(scr).getRGB(), c64(col).getRGB() };
					int v = u(b, base + cell * 8 + line);
					for (int p = 0; p < 4; p++) {
						int rgb = c[(v >> (6 - 2 * p)) & 3];
						img.setRGB(ox + p * 2, oy + line, rgb);
						img.setRGB(ox + p * 2 + 1, oy + line, rgb);
					}
				}
			}
			frames[f] = img;
		}
		var out = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 200; y++) {
			for (int x = 0; x < 320; x++) {
				int p = frames[0].getRGB(x, y);
				int q = frames[1].getRGB(x, y);
				int rgb = 0;
				for (int sh = 0; sh <= 16; sh += 8) {
					rgb |= ((((p >> sh) & 255) + ((q >> sh) & 255)) / 2) << sh;
				}
				out.setRGB(x, y, rgb);
			}
		}
		var r = new Result(out, Format.ECI, "ECI, 320 x 200, interlaced FLI multicolor (both frames blended)");
		r.eci = b;
		return r;
	}

	/** Drazlace: two multicolor bitmaps sharing screen and colour RAM, shown as one blended picture. */
	private static Result drazlace(byte[] data) {
		byte[] b = data.length >= 18242 ? Arrays.copyOfRange(data, 2, data.length) : unpack(data, 0xc2, 2, true);
		var a = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		var c2 = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		int bg = u(b, 0x2740) & 15;
		for (int cell = 0; cell < 1000; cell++) {
			int col = u(b, cell) & 15;
			int scr = u(b, 0x400 + cell);
			int[] c = { c64(bg).getRGB(), c64(scr >> 4).getRGB(), c64(scr).getRGB(), c64(col).getRGB() };
			mcCell(a, b, cell, 0x800 + cell * 8, c);
			mcCell(c2, b, cell, 0x2800 + cell * 8, c);
		}
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 200; y++) {
			for (int x = 0; x < 320; x++) {
				int p = a.getRGB(x, y);
				int q = c2.getRGB(x, y);
				int r = (((p >> 16) & 255) + ((q >> 16) & 255)) / 2;
				int g = (((p >> 8) & 255) + ((q >> 8) & 255)) / 2;
				int bl = ((p & 255) + (q & 255)) / 2;
				img.setRGB(x, y, r << 16 | g << 8 | bl);
			}
		}
		return new Result(img, Format.DRAZLACE, "Drazlace, 320 x 200, interlaced multicolor (both frames blended)")
				.with(new Layers(true, part(b, 0x800, 8000), part(b, 0x2800, 8000), part(b, 0x400, 1000), part(b, 0, 1000), bg));
	}

	private static int swapNibbles(int v) {
		return ((v & 15) << 4 | (v >> 4) & 15) & 0x7f;
	}

	/**
	 * Plus/4 picture as saved by Botticelli: luminance matrix ($7800), colour matrix ($7C00), bitmap ($8000).
	 */
	private static Result plus4(byte[] d, Format f) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		int lumBase = 2;
		int colBase = 2 + 0x400;
		int bmBase = 2 + 0x800;
		boolean multi = f == Format.PLUS4_MULTI;
		int bg0 = 0;
		int bg1 = 0;
		if (multi) {
			bg0 = swapNibbles(u(d, 2 + 0x3ff));
			bg1 = swapNibbles(u(d, 2 + 0x3fe));
		}
		for (int cell = 0; cell < 1000; cell++) {
			int cbuf = u(d, lumBase + cell);
			int vbuf = u(d, colBase + cell);
			int ox = (cell % 40) * 8;
			int oy = (cell / 40) * 8;
			int c1 = (vbuf >> 4) + ((cbuf & 7) << 4);
			int c2 = (vbuf & 15) + (cbuf & 0x70);
			for (int line = 0; line < 8; line++) {
				int v = u(d, bmBase + cell * 8 + line);
				if (multi) {
					int[] c = { bg0, c1, c2, bg1 };
					for (int p = 0; p < 4; p++) {
						int rgb = TED_PALETTE[c[(v >> (6 - 2 * p)) & 3] & 0x7f];
						img.setRGB(ox + p * 2, oy + line, rgb);
						img.setRGB(ox + p * 2 + 1, oy + line, rgb);
					}
				} else {
					for (int p = 0; p < 8; p++) {
						int idx = ((v >> (7 - p)) & 1) == 1 ? c1 : c2;
						img.setRGB(ox + p, oy + line, TED_PALETTE[idx & 0x7f]);
					}
				}
			}
		}
		return new Result(img, f, multi ? "Plus/4 multicolor (Multi Botticelli), 320 x 200" : "Plus/4 hires (Botticelli), 320 x 200")
				.with(new Plus4Layers(multi, part(d, lumBase, 1000), part(d, colBase, 1000), part(d, bmBase, 8000), bg0, bg1));
	}

	/**
	 * @param text message
	 * @return a picture telling the data is not a known picture
	 */
	public static BufferedImage message(String text) {
		var img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		var g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, 320, 200);
		g.setColor(Color.LIGHT_GRAY);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(text, 12, 100);
		g.dispose();
		return img;
	}
}
