package droid64.sid;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** A SID tune: PSID/RSID file, or a plain PRG with a music routine. */
public final class SidTune {

	/** Kind of source. */
	public enum Source {
		PSID, RSID, PRG
	}

	public Source source = Source.PSID;
	public int version;
	public int loadAddress;
	public int initAddress;
	public int playAddress;
	public int songs = 1;
	public int startSong = 1;
	public long speed;
	public String title = "";
	public String author = "";
	public String released = "";
	/** header flags (version 2 and later) */
	public int flags;
	public int secondSid;
	public int thirdSid;
	/** program data without the load address */
	public byte[] data = new byte[0];
	/** the original file, null for PRG music */
	public byte[] file;

	public static boolean isSidFile(byte[] d) {
		return d != null && d.length > 0x76 && d[1] == 'S' && d[2] == 'I' && d[3] == 'D'
				&& (d[0] == 'P' || d[0] == 'R');
	}

	private static int be16(byte[] d, int p) {
		return (d[p] & 0xff) << 8 | (d[p + 1] & 0xff);
	}

	private static String str(byte[] d, int p) {
		int end = p;
		while (end < p + 32 && end < d.length && d[end] != 0) {
			end++;
		}
		return new String(d, p, end - p, StandardCharsets.ISO_8859_1);
	}

	/**
	 * @param d file contents
	 * @return tune
	 * @throws IllegalArgumentException when it is not a valid SID file
	 */
	public static SidTune parse(byte[] d) {
		if (!isSidFile(d)) {
			throw new IllegalArgumentException("Not a PSID/RSID file");
		}
		var t = new SidTune();
		t.file = d;
		t.source = d[0] == 'R' ? Source.RSID : Source.PSID;
		t.version = be16(d, 4);
		int dataOffset = be16(d, 6);
		t.loadAddress = be16(d, 8);
		t.initAddress = be16(d, 10);
		t.playAddress = be16(d, 12);
		t.songs = Math.max(1, be16(d, 14));
		t.startSong = Math.max(1, Math.min(t.songs, be16(d, 16)));
		t.speed = (be16(d, 18) & 0xffffL) << 16 | be16(d, 20);
		t.title = str(d, 0x16);
		t.author = str(d, 0x36);
		t.released = str(d, 0x56);
		if (t.version >= 2 && d.length >= 0x7c) {
			t.flags = be16(d, 0x76);
			int s2 = d[0x7a] & 0xff;
			int s3 = d[0x7b] & 0xff;
			t.secondSid = isSidAddress(s2) ? 0xd000 | s2 << 4 : 0;
			t.thirdSid = isSidAddress(s3) ? 0xd000 | s3 << 4 : 0;
		}
		int start = Math.min(dataOffset, d.length);
		if (t.loadAddress == 0 && d.length >= start + 2) {
			t.loadAddress = (d[start] & 0xff) | (d[start + 1] & 0xff) << 8;
			start += 2;
		}
		t.data = java.util.Arrays.copyOfRange(d, start, d.length);
		return t;
	}

	private static boolean isSidAddress(int b) {
		return (b >= 0x42 && b <= 0xfe && (b & 1) == 0) && !(b >= 0x80 && b < 0xe0);
	}

	/**
	 * @param prg PRG file with load address
	 * @param init init routine
	 * @param play play routine
	 * @param name name for the title
	 * @return tune
	 */
	public static SidTune fromPrg(byte[] prg, int init, int play, String name) {
		var t = new SidTune();
		t.source = Source.PRG;
		t.loadAddress = (prg[0] & 0xff) | (prg[1] & 0xff) << 8;
		t.initAddress = init;
		t.playAddress = play;
		t.title = name == null ? "" : name;
		t.data = java.util.Arrays.copyOfRange(prg, 2, prg.length);
		return t;
	}

	public int endAddress() {
		return loadAddress + data.length - 1;
	}

	public boolean isMus() {
		return (flags & 1) != 0;
	}

	/** @return 0 unknown, 1 PAL, 2 NTSC, 3 both */
	public int clockFlag() {
		return (flags >> 2) & 3;
	}

	/** @return 0 unknown, 1 6581, 2 8580, 3 both */
	public int modelFlag() {
		return (flags >> 4) & 3;
	}

	/** @return true if the song uses the 60 Hz CIA timer instead of the vertical blank */
	public boolean ciaSpeed(int song) {
		int bit = Math.min(song - 1, 31);
		return ((speed >> bit) & 1) != 0;
	}

	/**
	 * @return a PSID (version 2, or 3 with extra SID chips) file
	 */
	public byte[] toPsid() {
		var o = new ByteArrayOutputStream();
		int version2 = secondSid != 0 || thirdSid != 0 ? 3 : 2;
		o.write('P');
		o.write('S');
		o.write('I');
		o.write('D');
		w16(o, version2);
		w16(o, 0x7c);
		w16(o, 0);					// load address is the first two bytes of the data
		w16(o, initAddress);
		w16(o, playAddress);
		w16(o, songs);
		w16(o, startSong);
		w16(o, (int) (speed >> 16));
		w16(o, (int) (speed & 0xffff));
		wstr(o, title);
		wstr(o, author);
		wstr(o, released);
		w16(o, flags & 0xfffe);
		o.write(0);
		o.write(0);
		o.write(secondSid != 0 ? (secondSid >> 4) & 0xff : 0);
		o.write(thirdSid != 0 ? (thirdSid >> 4) & 0xff : 0);
		o.write(loadAddress & 0xff);
		o.write(loadAddress >> 8);
		o.write(data, 0, data.length);
		return o.toByteArray();
	}

	private static void w16(ByteArrayOutputStream o, int v) {
		o.write((v >> 8) & 0xff);
		o.write(v & 0xff);
	}

	private static void wstr(ByteArrayOutputStream o, String s) {
		byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
		for (int i = 0; i < 32; i++) {
			o.write(i < b.length && i < 31 ? b[i] : 0);
		}
	}
}
