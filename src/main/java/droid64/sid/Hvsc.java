package droid64.sid;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to a local HVSC (High Voltage SID Collection): identifies a SID file by the MD5 of its complete
 * content in DOCUMENTS/Songlengths.md5 and delivers the song lengths and the STIL information.
 * The collection has to exist on the machine; its folder is set in the settings.
 */
public final class Hvsc {

	/** Result of a lookup. */
	public enum Status {
		/** no HVSC folder set or the database files were not found */
		UNAVAILABLE,
		/** the SID is not in the collection */
		NOT_FOUND,
		/** the SID is in the collection */
		FOUND
	}

	/** Information about one SID. */
	public static final class Result {
		public final Status status;
		/** path inside the collection, e.g. /MUSICIANS/H/Hubbard_Rob/Commando.sid */
		public final String path;
		/** song lengths in milliseconds, one for each tune (may be shorter than the number of tunes) */
		public final long[] lengthsMs;
		/** raw STIL text of the file (empty if none) */
		public final String stil;

		Result(Status status, String path, long[] lengthsMs, String stil) {
			this.status = status;
			this.path = path;
			this.lengthsMs = lengthsMs;
			this.stil = stil;
		}

		/** @return length of the tune (1-based) in milliseconds, or -1 if unknown */
		public long lengthMs(int song) {
			return song >= 1 && song <= lengthsMs.length ? lengthsMs[song - 1] : -1;
		}

		/** @return the STIL text for the tune (1-based): the general part and the section of that tune */
		public String stilFor(int song) {
			if (stil.isEmpty()) {
				return "";
			}
			var general = new StringBuilder();
			var mine = new StringBuilder();
			StringBuilder cur = general;
			for (var line : stil.split("\n")) {
				var m = SUBTUNE.matcher(line.trim());
				if (m.matches()) {
					cur = Integer.parseInt(m.group(1)) == song ? mine : null;
					continue;
				}
				if (cur != null) {
					cur.append(line).append('\n');
				}
			}
			return (general.toString() + mine).trim();
		}
	}

	private static final java.util.regex.Pattern SUBTUNE = java.util.regex.Pattern.compile("\\(#(\\d+)\\)");
	private static final Result UNAVAILABLE = new Result(Status.UNAVAILABLE, "", new long[0], "");
	private static final Result NOT_FOUND = new Result(Status.NOT_FOUND, "", new long[0], "");

	private static String rootSetting = "";
	private static Map<String, String[]> lengths;	// md5 -> {path, values}
	private static Map<String, String> stil;		// path -> text
	private static File docs;

	private Hvsc() {
	}

	/** Set the folder of the collection (the one containing DOCUMENTS, or C64Music). Loads again when it changed. */
	public static synchronized void setRoot(String dir) {
		var d = dir == null ? "" : dir.trim();
		if (!d.equals(rootSetting)) {
			rootSetting = d;
			lengths = null;
			stil = null;
			docs = null;
		}
	}

	/** Forget the loaded databases (after an update). */
	public static synchronized void reload() {
		lengths = null;
		stil = null;
		docs = null;
	}

	/** @return the DOCUMENTS folder found below the set folder, or null */
	public static synchronized File findDocuments(String dir) {
		if (dir == null || dir.trim().isEmpty()) {
			return null;
		}
		var base = new File(dir.trim());
		File[] candidates = { base, new File(base, "DOCUMENTS"), new File(base, "C64Music/DOCUMENTS"),
				new File(base, "C64Music"), base.getParentFile() == null ? base : new File(base.getParentFile(), "DOCUMENTS") };
		for (var c : candidates) {
			if (new File(c, "Songlengths.md5").isFile()) {
				return c;
			}
		}
		return null;
	}

	/** @return the folder which contains the music (MUSICIANS, DEMOS, GAMES), or null */
	public static File musicRoot(String dir) {
		if (dir == null || dir.trim().isEmpty()) {
			return null;
		}
		var base = new File(dir.trim());
		File[] c = { base, new File(base, "C64Music"), base.getParentFile() == null ? base : base.getParentFile() };
		for (var f : c) {
			if (new File(f, "MUSICIANS").isDirectory()) {
				return f;
			}
		}
		return null;
	}

	/** @return true if the database was found */
	public static synchronized boolean isAvailable() {
		load();
		return lengths != null;
	}

	/** @return number of known SID files, 0 if not available */
	public static synchronized int size() {
		load();
		return lengths == null ? 0 : lengths.size();
	}

	/** Identify a SID file (complete file content). */
	public static synchronized Result lookup(byte[] sidFile) {
		load();
		if (lengths == null) {
			return UNAVAILABLE;
		}
		if (sidFile == null) {
			return NOT_FOUND;
		}
		var e = lengths.get(md5(sidFile));
		if (e == null) {
			return NOT_FOUND;
		}
		return new Result(Status.FOUND, e[0], parseLengths(e[1]), stil == null ? "" : stil.getOrDefault(e[0], ""));
	}

	private static void load() {
		if (lengths != null || rootSetting.isEmpty()) {
			return;
		}
		docs = findDocuments(rootSetting);
		if (docs == null) {
			return;
		}
		var map = new HashMap<String, String[]>(131072);
		try (BufferedReader r = Files.newBufferedReader(new File(docs, "Songlengths.md5").toPath(), StandardCharsets.ISO_8859_1)) {
			String path = "";
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith(";")) {
					path = line.substring(1).trim();
				} else {
					int eq = line.indexOf('=');
					if (eq == 32) {
						map.put(line.substring(0, 32).toLowerCase(), new String[] { path, line.substring(33).trim() });
					}
				}
			}
		} catch (IOException e) {
			return;
		}
		lengths = map;
		stil = loadStil(new File(docs, "STIL.txt"));
	}

	private static Map<String, String> loadStil(File f) {
		var map = new HashMap<String, String>(32768);
		if (!f.isFile()) {
			return map;
		}
		try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.ISO_8859_1)) {
			String path = null;
			var text = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				boolean isPath = line.startsWith("/") && !line.endsWith("/");
				if (isPath || line.startsWith("#")) {
					if (path != null) {
						map.put(path, text.toString().trim());
					}
					path = isPath ? line.trim() : null;
					text.setLength(0);
				} else if (path != null) {
					text.append(line).append('\n');
				}
			}
			if (path != null) {
				map.put(path, text.toString().trim());
			}
		} catch (IOException e) {
			// keep what was read
		}
		return map;
	}

	/** Parse "3:57 1:02.5 0:06" into milliseconds. */
	static long[] parseLengths(String v) {
		var out = new ArrayList<Long>();
		for (var s : v.trim().split("\\s+")) {
			out.add(parseTime(s));
		}
		return out.stream().mapToLong(Long::longValue).toArray();
	}

	static long parseTime(String s) {
		try {
			int c = s.indexOf(':');
			long min = Long.parseLong(s.substring(0, c));
			double sec = Double.parseDouble(s.substring(c + 1));
			return Math.round((min * 60 + sec) * 1000);
		} catch (RuntimeException e) {
			return -1;
		}
	}

	static String md5(byte[] d) {
		try {
			var md = MessageDigest.getInstance("MD5");
			var sb = new StringBuilder();
			for (byte b : md.digest(d)) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Used by tests. */
	static List<String> paths() {
		return new ArrayList<>(stil.keySet());
	}
}
