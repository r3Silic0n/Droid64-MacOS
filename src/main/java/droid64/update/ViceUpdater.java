package droid64.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Checks for a newer VICE macOS package (from the VICE SourceForge release folder) and installs it. */
public final class ViceUpdater {

	public static final String LISTING = "https://sourceforge.net/projects/vice-emu/files/releases/binaries/macos/";
	private static final String DOWNLOAD = "https://downloads.sourceforge.net/project/vice-emu/releases/binaries/macos/";
	private static final Pattern DMG = Pattern.compile("vice-(arm64|x86_64)-(gtk3|sdl2)-([0-9]+(?:\\.[0-9]+)*)\\.dmg");
	private static final String[] NAMES = { "x64sc", "x128", "xplus4", "xvic" };

	/** The newest package. */
	public static final class Latest {
		public final String version;
		public final String file;

		Latest(String version, String file) {
			this.version = version;
			this.file = file;
		}
	}

	private ViceUpdater() {
	}

	/** @return "arm64" or "x86_64" */
	private static String arch() {
		var a = System.getProperty("os.arch", "");
		return a.contains("aarch64") || a.contains("arm64") ? "arm64" : "x86_64";
	}

	/** @param flavor "gtk3" or "sdl2" @return the newest macOS package for this processor, or null */
	public static Latest latest(String flavor) throws IOException {
		var page = Http.text(LISTING);
		Latest best = null;
		var m = DMG.matcher(page);
		while (m.find()) {
			if (m.group(1).equals(arch()) && m.group(2).equals(flavor) && (best == null || compare(m.group(3), best.version) > 0)) {
				best = new Latest(m.group(3), m.group());
			}
		}
		return best;
	}

	/** Compare dotted version numbers. */
	public static int compare(String a, String b) {
		var x = a.split("\\.");
		var y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? parse(x[i]) : 0;
			int q = i < y.length ? parse(y[i]) : 0;
			if (p != q) {
				return Integer.compare(p, q);
			}
		}
		return 0;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D.*", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** @param path the emulator (executable or .app) @return the version from the Info.plist of its application, or null */
	public static String installedVersion(String path) {
		if (path == null || path.trim().isEmpty()) {
			return null;
		}
		var f = new File(path.trim());
		while (f != null && !f.getName().endsWith(".app")) {
			f = f.getParentFile();
		}
		if (f == null) {
			return null;
		}
		var plist = new File(f, "Contents/Info.plist");
		if (!plist.isFile()) {
			return null;
		}
		try {
			var p = new ProcessBuilder("/usr/bin/plutil", "-extract", "CFBundleShortVersionString", "raw", "-o", "-", plist.getPath()).redirectErrorStream(true).start();
			var out = new String(p.getInputStream().readAllBytes()).trim();
			return p.waitFor() == 0 && out.matches("[0-9][0-9.]*") ? out : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** @return "gtk3" or "sdl2", the flavor of the installed VICE (gtk3 if unknown) */
	public static String flavorOf(String path) {
		return path != null && path.toLowerCase().contains("sdl") ? "sdl2" : "gtk3";
	}

	/** Download the dmg and install it below targetParent/VICE-version. @return paths of the emulators (x64sc, x128, xplus4, xvic) */
	public static Map<String, String> install(Latest l, File targetParent, Log log) throws IOException {
		if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			throw new IOException("Installing the VICE macOS package works on macOS only.");
		}
		var tmp = Files.createTempDirectory("vice-update").toFile();
		var mnt = new File(tmp, "mnt");
		boolean mounted = false;
		try {
			log.log("Downloading " + l.file + " ...");
			var dmg = new File(tmp, l.file);
			Http.download(DOWNLOAD + l.file, dmg, log::progress, log::cancelled);
			mnt.mkdirs();
			log.log("Mounting ...");
			run("hdiutil", "attach", "-nobrowse", "-readonly", "-noverify", "-mountpoint", mnt.getPath(), dmg.getPath());
			mounted = true;
			var target = new File(targetParent, "VICE-" + l.version);
			target.mkdirs();
			log.log("Installing into " + target + " ...");
			run("ditto", mnt.getPath(), target.getPath());
			var result = new LinkedHashMap<String, String>();
			try (Stream<java.nio.file.Path> walk = Files.walk(target.toPath(), 5)) {
				for (var p : (Iterable<java.nio.file.Path>) walk::iterator) {
					var n = p.getFileName().toString();
					for (var e : NAMES) {
						if ((n.equals(e + ".app") || n.equals(e) && p.getParent().getFileName().toString().equals("MacOS")) && !result.containsKey(e)) {
							result.put(e, p.toString());
						}
					}
				}
			}
			if (result.isEmpty()) {
				throw new IOException("No emulator found in the package (installed into " + target + ").");
			}
			log.log("Installed: " + String.join(", ", result.keySet()));
			return result;
		} finally {
			if (mounted) {
				try {
					run("hdiutil", "detach", mnt.getPath(), "-force");
				} catch (IOException e) {	//NOSONAR
					// ignore
				}
			}
			HvscUpdater.deleteTree(tmp);
		}
	}

	private static void run(String... cmd) throws IOException {
		var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		var out = new String(p.getInputStream().readAllBytes());
		try {
			if (p.waitFor() != 0) {
				throw new IOException(cmd[0] + " failed: " + out.substring(Math.max(0, out.length() - 300)));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
