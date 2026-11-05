package droid64.update;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import droid64.sid.Hvsc;

/**
 * Checks for a newer HVSC (High Voltage SID Collection), downloads the update packages and applies them to the local
 * collection (incremental update with the HVSC update script), or downloads the complete collection.
 */
public final class HvscUpdater {

	/** Default mirrors; the folder URL must contain HVSC_Update_NN.7z and HVSC_NN-all-of-it.7z. */
	public static final String DEFAULT_MIRRORS = "https://hvsc.c64.org/download/;https://hvsc.brona.dk/HVSC/;https://www.prosonix.co.uk/hvsc/";

	private static final Set<String> HEADER = Set.of("AUTHOR", "TITLE", "RELEASED", "CREDITS", "SONGS", "SPEED", "INITPLAY", "FREEPAGES", "CLOCK", "SIDMODEL");
	private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList("COPY", "MOVE", "DELETE", "REPLACE", "FLAGS", "FIXLOAD"));
	private static final Pattern STIL_VERSION = Pattern.compile("STIL v(\\d+)");

	static {
		KEYWORDS.addAll(HEADER);
	}

	/** Result of a version check. */
	public static final class Check {
		public final int installed;
		public final int latest;
		public final String mirror;

		Check(int installed, int latest, String mirror) {
			this.installed = installed;
			this.latest = latest;
			this.mirror = mirror;
		}

		public boolean newer() {
			return latest > installed;
		}
	}

	private HvscUpdater() {
	}

	/** @return the version of the collection (from DOCUMENTS/STIL.txt), or -1 */
	public static int installedVersion(File musicRoot) {
		if (musicRoot == null) {
			return -1;
		}
		var stil = new File(musicRoot, "DOCUMENTS/STIL.txt");
		if (!stil.isFile()) {
			return -1;
		}
		try (var lines = Files.lines(stil.toPath(), StandardCharsets.ISO_8859_1)) {
			for (var l : (Iterable<String>) lines.limit(40)::iterator) {
				var m = STIL_VERSION.matcher(l);
				if (m.find()) {
					return Integer.parseInt(m.group(1));
				}
			}
		} catch (IOException e) {
			return -1;
		}
		return -1;
	}

	private static List<String> mirrors(String list) {
		var out = new ArrayList<String>();
		for (var m : (list == null || list.trim().isEmpty() ? DEFAULT_MIRRORS : list).split("[;\\s]+")) {
			if (!m.isEmpty()) {
				out.add(m.endsWith("/") ? m : m + "/");
			}
		}
		return out;
	}

	/** Look for newer update packages. Network errors give "no newer". */
	public static Check check(File musicRoot, String mirrorList) {
		int inst = installedVersion(musicRoot);
		if (inst < 0) {
			return new Check(-1, -1, null);
		}
		for (var m : mirrors(mirrorList)) {
			if (Http.exists(m + "HVSC_Update_" + (inst + 1) + ".7z")) {
				int v = inst + 1;
				while (Http.exists(m + "HVSC_Update_" + (v + 1) + ".7z")) {
					v++;
				}
				return new Check(inst, v, m);
			}
		}
		return new Check(inst, inst, null);
	}

	/** Download and apply all newer update packages. @return the new version */
	public static int update(File musicRoot, String mirrorList, Log log) throws IOException {
		int inst = installedVersion(musicRoot);
		if (inst < 0) {
			throw new IOException("Cannot find the version of the collection (DOCUMENTS/STIL.txt). Use the full download instead.");
		}
		int v = inst;
		while (!log.cancelled()) {
			String url = null;
			for (var m : mirrors(mirrorList)) {
				if (Http.exists(m + "HVSC_Update_" + (v + 1) + ".7z")) {
					url = m + "HVSC_Update_" + (v + 1) + ".7z";
					break;
				}
			}
			if (url == null) {
				break;
			}
			v++;
			applyPackage(url, v, musicRoot, log);
		}
		Hvsc.reload();
		return v;
	}

	private static void applyPackage(String url, int version, File root, Log log) throws IOException {
		var tmp = Files.createTempDirectory("hvsc-update").toFile();
		try {
			log.log("Downloading HVSC update " + version + " ...");
			var archive = new File(tmp, "update.7z");
			Http.download(url, archive, log::progress, log::cancelled);
			log.log("Extracting ...");
			log.progress(0, -1);
			var ex = new File(tmp, "ex");
			SevenZip.extract(archive, ex);
			var up = new File(ex, "update");
			File[] scripts = up.listFiles((d, n) -> n.toLowerCase().endsWith(".hvs"));
			if (scripts == null || scripts.length == 0) {
				throw new IOException("The update package has no update script (.hvs).");
			}
			log.log("Applying update " + version + " ...");
			var touched = new ArrayList<File>();
			applyScript(scripts[0], ex, root, touched, log);
			Hvsc.reload();
			Hvsc.setRoot(root.getPath());
			int bad = 0;
			for (var f : touched) {
				if (f.isFile() && f.getName().toLowerCase().endsWith(".sid") && Hvsc.lookup(Files.readAllBytes(f.toPath())).status != Hvsc.Status.FOUND) {
					bad++;
					if (bad <= 5) {
						log.log("  not matching the song length database: " + f.getPath().substring(root.getPath().length()));
					}
				}
			}
			log.log(bad == 0 ? "Update " + version + " done, all changed SID files verified." : "Update " + version + " done, but " + bad
					+ " changed SID file(s) differ from the database. Use the full download if you want an exact copy.");
		} finally {
			deleteTree(tmp);
		}
	}

	/** Download the complete collection into the folder; returns the music root (C64Music). */
	public static File installFull(File targetDir, String mirrorList, Log log) throws IOException {
		targetDir.mkdirs();
		String url = null;
		var given = mirrorList == null ? "" : mirrorList.trim();
		if (given.toLowerCase().endsWith(".7z")) {
			url = given;	// direct link to the archive
		} else {
			log.progress(0, -1);
			for (var m : mirrors(mirrorList)) {
				log.log("Looking for the full HVSC on " + m + " ...");
				int found = -1;
				for (int v = 85; v < 200 && !log.cancelled(); v++) {
					if (Http.exists(m + "HVSC_" + v + "-all-of-it.7z")) {
						found = v;
					} else if (found > 0) {
						break;
					}
				}
				if (found > 0) {
					url = m + "HVSC_" + found + "-all-of-it.7z";
					break;
				}
				log.log("  not found there");
			}
		}
		if (url == null) {
			throw new IOException("No HVSC download found on the mirrors. Download the full HVSC .7z in your browser from hvsc.c64.org and give its link or file in the dialog.");
		}
		var tmp = Files.createTempDirectory("hvsc-full").toFile();
		try {
			log.log("Downloading " + url + " ...");
			var archive = new File(tmp, "hvsc.7z");
			Http.download(url, archive, log::progress, log::cancelled);
			log.log("Extracting (this takes a while) ...");
			log.progress(0, -1);
			SevenZip.extract(archive, targetDir);
		} finally {
			deleteTree(tmp);
		}
		Hvsc.reload();
		var root = Hvsc.musicRoot(targetDir.getPath());
		if (root == null && new File(targetDir, "C64Music").isDirectory()) {
			root = new File(targetDir, "C64Music");
		}
		if (root == null) {
			throw new IOException("Extracted, but no C64Music folder found in " + targetDir);
		}
		log.log("HVSC installed in " + root);
		return root;
	}

	// ---- update script ----

	private static boolean skip(String l) {
		return l.isEmpty() || l.startsWith("#");
	}

	private static File map(String p, File pkg, File root) throws IOException {
		var f = p.startsWith("/update/") || p.equals("/update") ? new File(pkg, p.substring(1)) : new File(root, p);
		var base = (p.startsWith("/update") ? pkg : root).getCanonicalPath();
		if (!f.getCanonicalPath().startsWith(base)) {
			throw new IOException("Unsafe path in update script: " + p);
		}
		return f;
	}

	static void applyScript(File script, File pkg, File root, List<File> touched, Log log) throws IOException {
		var lines = new ArrayList<String>();
		for (var l : Files.readAllLines(script.toPath(), StandardCharsets.ISO_8859_1)) {
			lines.add(l.replace("\r", "").trim());
		}
		String mode = "COPY";
		for (int i = 0; i < lines.size() && !log.cancelled(); i++) {
			var l = lines.get(i);
			if (skip(l)) {
				continue;
			}
			if (KEYWORDS.contains(l)) {
				mode = l;
				continue;
			}
			if (!l.startsWith("/")) {
				continue;
			}
			switch (mode) {
			case "DELETE":
				if (!l.startsWith("/update")) {
					deleteTree(map(l, pkg, root));
				}
				break;
			case "COPY":
			case "REPLACE":
			case "MOVE":
				int j = i + 1;
				while (j < lines.size() && skip(lines.get(j))) {
					j++;
				}
				if (j >= lines.size()) {
					break;
				}
				var dst = lines.get(j);
				i = j;
				if ("MOVE".equals(mode)) {
					move(map(l, pkg, root), map(dst, pkg, root), dst.endsWith("/"), touched);
				} else {
					copy(map(l, pkg, root), map(dst, pkg, root), dst.endsWith("/"), touched);
				}
				break;
			default:
				if (HEADER.contains(mode)) {
					int need = "CREDITS".equals(mode) ? 3 : 1;
					var vals = new ArrayList<String>();
					int k = i + 1;
					while (k < lines.size() && vals.size() < need && !lines.get(k).isEmpty() && !lines.get(k).startsWith("#")) {
						vals.add(lines.get(k++));
					}
					i = k - 1;
					var f = map(l, pkg, root);
					if (f.isFile() && vals.size() == need) {
						patchHeader(f, mode, vals);
						touched.add(f);
					}
				}
				break;
			}
		}
	}

	private static void copy(File src, File dst, boolean dstIsDir, List<File> touched) throws IOException {
		if (!src.exists()) {
			return;
		}
		if (src.isDirectory()) {
			try (Stream<java.nio.file.Path> walk = Files.walk(src.toPath())) {
				for (var p : (Iterable<java.nio.file.Path>) walk::iterator) {
					var target = new File(dst, src.toPath().relativize(p).toString());
					if (Files.isDirectory(p)) {
						target.mkdirs();
					} else {
						target.getParentFile().mkdirs();
						Files.copy(p, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
						touched.add(target);
					}
				}
			}
			return;
		}
		var target = dstIsDir || dst.isDirectory() ? new File(dst, src.getName()) : dst;
		target.getParentFile().mkdirs();
		Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		touched.add(target);
	}

	private static void move(File src, File dst, boolean dstIsDir, List<File> touched) throws IOException {
		if (!src.exists()) {
			return;
		}
		if (src.isDirectory()) {
			if (dst.exists()) {
				copy(src, dst, true, touched);
				deleteTree(src);
			} else {
				dst.getParentFile().mkdirs();
				Files.move(src.toPath(), dst.toPath());
			}
			return;
		}
		var target = dstIsDir || dst.isDirectory() ? new File(dst, src.getName()) : dst;
		target.getParentFile().mkdirs();
		Files.move(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		touched.add(target);
	}

	static void deleteTree(File f) throws IOException {
		if (f == null || !f.exists()) {
			return;
		}
		try (Stream<java.nio.file.Path> walk = Files.walk(f.toPath())) {
			var all = new ArrayList<java.nio.file.Path>();
			walk.forEach(all::add);
			for (int i = all.size() - 1; i >= 0; i--) {
				Files.deleteIfExists(all.get(i));
			}
		}
	}

	// ---- SID header patching ----

	private static void put16(byte[] d, int o, int v) {
		d[o] = (byte) (v >> 8);
		d[o + 1] = (byte) v;
	}

	private static void putText(byte[] d, int o, String s) {
		var b = s.getBytes(StandardCharsets.ISO_8859_1);
		for (int i = 0; i < 32; i++) {
			d[o + i] = i < b.length ? b[i] : 0;
		}
	}

	private static int hex(String s) {
		return Integer.parseInt(s.trim().replace("$", ""), 16);
	}

	static void patchHeader(File f, String cmd, List<String> v) throws IOException {
		var d = Files.readAllBytes(f.toPath());
		if (d.length < 0x7c || !(d[0] == 'P' || d[0] == 'R') || d[1] != 'S' || d[2] != 'I' || d[3] != 'D') {
			return;
		}
		try {
			switch (cmd) {
			case "TITLE":
				putText(d, 0x16, v.get(0));
				break;
			case "AUTHOR":
				putText(d, 0x36, v.get(0));
				break;
			case "RELEASED":
				putText(d, 0x56, v.get(0));
				break;
			case "CREDITS":
				for (int k = 0; k < 3; k++) {
					if (!"*".equals(v.get(k))) {
						putText(d, 0x16 + 0x20 * k, v.get(k));
					}
				}
				break;
			case "SONGS": {
				var p = v.get(0).split(",");
				put16(d, 0x0e, Integer.parseInt(p[0].trim()));
				put16(d, 0x10, Integer.parseInt(p[1].trim()));
				break;
			}
			case "SPEED": {
				long s = Long.parseLong(v.get(0).trim().replace("$", ""), 16);
				for (int k = 0; k < 4; k++) {
					d[0x12 + k] = (byte) (s >> (24 - 8 * k));
				}
				break;
			}
			case "INITPLAY": {
				var p = v.get(0).split(",");
				put16(d, 0x0a, hex(p[0]));
				put16(d, 0x0c, hex(p[1]));
				break;
			}
			case "FREEPAGES": {
				var p = v.get(0).split(",");
				d[0x78] = (byte) hex(p[0]);
				d[0x79] = (byte) hex(p[1]);
				break;
			}
			case "CLOCK":
			case "SIDMODEL": {
				int shift = "CLOCK".equals(cmd) ? 2 : 4;
				int val;
				switch (v.get(0).trim().toUpperCase()) {
				case "PAL":
				case "6581":
					val = 1;
					break;
				case "NTSC":
				case "8580":
					val = 2;
					break;
				case "ANY":
					val = 3;
					break;
				default:
					val = 0;
				}
				int flags = ((d[0x76] & 255) << 8 | (d[0x77] & 255)) & ~(3 << shift) | val << shift;
				put16(d, 0x76, flags);
				break;
			}
			default:
				return;
			}
		} catch (RuntimeException e) {	//NOSONAR
			return;
		}
		Files.write(f.toPath(), d);
	}
}
