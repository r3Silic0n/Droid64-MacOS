package droid64.gui;

import java.awt.image.BufferedImage;

/**
 * Decoder for Amiga IFF pictures: ILBM (bitplanes, optionally ByteRun1 packed, with palette, EHB or HAM) and PBM (chunky).
 */
public final class IffImage {

	private static final int MAX_PIXELS = 16_000_000;
	private static final int CAMG_HAM = 0x800;
	private static final int CAMG_EHB = 0x80;

	/** A decoded IFF picture. */
	public static final class Result {
		public final BufferedImage image;
		public final String description;

		Result(BufferedImage image, String description) {
			this.image = image;
			this.description = description;
		}
	}

	private IffImage() {
	}

	private static int u8(byte[] d, int i) {
		return i >= 0 && i < d.length ? d[i] & 0xff : 0;
	}

	private static int be16(byte[] d, int i) {
		return u8(d, i) << 8 | u8(d, i + 1);
	}

	private static long be32(byte[] d, int i) {
		return (long) be16(d, i) << 16 | be16(d, i + 2);
	}

	private static boolean tag(byte[] d, int i, String t) {
		if (i < 0 || i + 4 > d.length) {
			return false;
		}
		for (int k = 0; k < 4; k++) {
			if ((d[i + k] & 0xff) != t.charAt(k)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param d file data
	 * @return true if the data is an IFF ILBM or PBM picture
	 */
	public static boolean isIff(byte[] d) {
		return d != null && d.length > 20 && tag(d, 0, "FORM") && (tag(d, 8, "ILBM") || tag(d, 8, "PBM "));
	}

	/**
	 * Decode a picture.
	 * @param d file data
	 * @return the picture, or null if it is not a supported IFF picture
	 */
	public static Result decode(byte[] d) {
		if (!isIff(d)) {
			return null;
		}
		boolean pbm = tag(d, 8, "PBM ");
		int width = 0;
		int height = 0;
		int planes = 0;
		int masking = 0;
		int compression = 0;
		int xAspect = 1;
		int yAspect = 1;
		long camg = 0;
		int[] palette = new int[0];
		int bodyStart = -1;
		int bodyLen = 0;
		int pos = 12;
		while (pos + 8 <= d.length) {
			long len = be32(d, pos + 4);
			int start = pos + 8;
			if (len < 0 || start + len > d.length) {
				len = d.length - start;
			}
			if (tag(d, pos, "BMHD") && len >= 20) {
				width = be16(d, start);
				height = be16(d, start + 2);
				planes = u8(d, start + 8);
				masking = u8(d, start + 9);
				compression = u8(d, start + 10);
				xAspect = u8(d, start + 14);
				yAspect = u8(d, start + 15);
			} else if (tag(d, pos, "CMAP")) {
				palette = readPalette(d, start, (int) len);
			} else if (tag(d, pos, "CAMG") && len >= 4) {
				camg = be32(d, start);
			} else if (tag(d, pos, "BODY")) {
				bodyStart = start;
				bodyLen = (int) len;
				break;
			}
			pos = (int) (start + len + (len & 1));
		}
		if (width <= 0 || height <= 0 || planes <= 0 || bodyStart < 0 || (long) width * height > MAX_PIXELS || compression > 1) {
			return null;
		}
		if (planes > 32 || (pbm && planes > 8)) {
			return null;
		}
		boolean ham = (camg & CAMG_HAM) != 0 && planes >= 5 && planes <= 8;
		boolean ehb = !ham && !pbm && ((camg & CAMG_EHB) != 0 || (camg == 0 && planes == 6 && palette.length == 32));
		int rowBytes = pbm ? width + (width & 1) : ((width + 15) / 16) * 2;
		int rowPlanes = pbm ? 1 : planes + (masking == 1 ? 1 : 0);
		var body = compression == 1 ? unpack(d, bodyStart, bodyLen, rowBytes * rowPlanes * height) : slice(d, bodyStart, bodyLen);
		var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		if (palette.length == 0 && planes <= 8) {
			palette = greyPalette(planes);
		}
		for (int y = 0; y < height; y++) {
			int rowStart = y * rowBytes * rowPlanes;
			if (rowStart >= body.length) {
				break;
			}
			if (pbm) {
				for (int x = 0; x < width; x++) {
					img.setRGB(x, y, color(palette, u8(body, rowStart + x)));
				}
			} else if (planes >= 24) {
				drawTrueColor(img, body, rowStart, rowBytes, planes, y);
			} else if (ham) {
				drawHam(img, body, rowStart, rowBytes, planes, palette, y);
			} else {
				drawPlanar(img, body, rowStart, rowBytes, planes, palette, ehb, y);
			}
		}
		var text = "Amiga IFF " + (pbm ? "PBM" : "ILBM") + ", " + width + " x " + height + ", " + planes + " bitplanes"
				+ (planes <= 8 ? " (" + (ham ? "HAM" : ehb ? "Extra Half-Brite, 64 colours" : (1 << planes) + " colours") + ")" : ", true colour")
				+ (compression == 1 ? ", packed" : "") + (xAspect > 0 && yAspect > 0 && xAspect != yAspect ? ", pixel aspect " + xAspect + ":" + yAspect : "");
		return new Result(img, text);
	}

	private static byte[] slice(byte[] d, int start, int len) {
		var out = new byte[Math.max(0, Math.min(len, d.length - start))];
		System.arraycopy(d, start, out, 0, out.length);
		return out;
	}

	/** ByteRun1: a control byte n: 0..127 copy n + 1 bytes, -1..-127 repeat the next byte 1 - n times, -128 nothing. */
	private static byte[] unpack(byte[] d, int start, int len, int expected) {
		var out = new byte[expected];
		int o = 0;
		int i = start;
		int end = Math.min(d.length, start + len);
		while (i < end && o < expected) {
			int n = d[i++];
			if (n >= 0) {
				int c = n + 1;
				for (int k = 0; k < c && i < end && o < expected; k++) {
					out[o++] = d[i++];
				}
			} else if (n != -128 && i < end) {
				int c = 1 - n;
				byte v = d[i++];
				for (int k = 0; k < c && o < expected; k++) {
					out[o++] = v;
				}
			}
		}
		return out;
	}

	private static int[] readPalette(byte[] d, int start, int len) {
		int n = len / 3;
		var pal = new int[n];
		boolean fourBit = true;
		for (int i = 0; i < n * 3; i++) {
			if ((u8(d, start + i) & 0x0f) != 0) {
				fourBit = false;
				break;
			}
		}
		for (int i = 0; i < n; i++) {
			int r = u8(d, start + i * 3);
			int g = u8(d, start + i * 3 + 1);
			int b = u8(d, start + i * 3 + 2);
			if (fourBit) {
				r |= r >> 4;
				g |= g >> 4;
				b |= b >> 4;
			}
			pal[i] = r << 16 | g << 8 | b;
		}
		return pal;
	}

	private static int[] greyPalette(int planes) {
		int n = 1 << planes;
		var pal = new int[n];
		for (int i = 0; i < n; i++) {
			int v = n > 1 ? i * 255 / (n - 1) : 0;
			pal[i] = v << 16 | v << 8 | v;
		}
		return pal;
	}

	private static int color(int[] pal, int index) {
		return index >= 0 && index < pal.length ? pal[index] : 0;
	}

	private static int pixelIndex(byte[] body, int rowStart, int rowBytes, int planes, int x) {
		int v = 0;
		int mask = 0x80 >> (x & 7);
		int off = x >> 3;
		for (int p = 0; p < planes; p++) {
			if ((u8(body, rowStart + p * rowBytes + off) & mask) != 0) {
				v |= 1 << p;
			}
		}
		return v;
	}

	private static void drawPlanar(BufferedImage img, byte[] body, int rowStart, int rowBytes, int planes, int[] pal, boolean ehb, int y) {
		for (int x = 0; x < img.getWidth(); x++) {
			int idx = pixelIndex(body, rowStart, rowBytes, planes, x);
			int rgb;
			if (ehb && idx >= 32) {
				int c = color(pal, idx - 32);
				rgb = (c >> 1) & 0x7f7f7f;
			} else {
				rgb = color(pal, idx);
			}
			img.setRGB(x, y, rgb);
		}
	}

	private static void drawHam(BufferedImage img, byte[] body, int rowStart, int rowBytes, int planes, int[] pal, int y) {
		int bits = planes - 2;
		int base = color(pal, 0);
		int r = base >> 16 & 255;
		int g = base >> 8 & 255;
		int b = base & 255;
		for (int x = 0; x < img.getWidth(); x++) {
			int idx = pixelIndex(body, rowStart, rowBytes, planes, x);
			int ctl = idx >> bits;
			int val = idx & ((1 << bits) - 1);
			int scaled = bits == 6 ? val << 2 | val >> 4 : val * 255 / ((1 << bits) - 1);
			switch (ctl) {
			case 0:
				int c = color(pal, val);
				r = c >> 16 & 255;
				g = c >> 8 & 255;
				b = c & 255;
				break;
			case 1:
				b = scaled;
				break;
			case 2:
				r = scaled;
				break;
			default:
				g = scaled;
				break;
			}
			img.setRGB(x, y, r << 16 | g << 8 | b);
		}
	}

	private static void drawTrueColor(BufferedImage img, byte[] body, int rowStart, int rowBytes, int planes, int y) {
		for (int x = 0; x < img.getWidth(); x++) {
			int r = 0;
			int g = 0;
			int b = 0;
			int mask = 0x80 >> (x & 7);
			int off = x >> 3;
			for (int bit = 0; bit < 8; bit++) {
				if ((u8(body, rowStart + bit * rowBytes + off) & mask) != 0) {
					r |= 1 << bit;
				}
				if ((u8(body, rowStart + (8 + bit) * rowBytes + off) & mask) != 0) {
					g |= 1 << bit;
				}
				if ((u8(body, rowStart + (16 + bit) * rowBytes + off) & mask) != 0) {
					b |= 1 << bit;
				}
			}
			img.setRGB(x, y, r << 16 | g << 8 | b);
		}
	}
}
