package droid64.update;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** Extracts .7z and .dmg-less archives with the 7-Zip command line tool (bundled in the macOS application, or found in the PATH). */
final class SevenZip {

	private SevenZip() {
	}

	/** @return the 7-Zip executable or null */
	static String find() {
		try {
			var jar = new File(SevenZip.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			var bundled = new File(jar.getParentFile().getParentFile(), "Resources/7zz");
			if (bundled.canExecute()) {
				return bundled.getPath();
			}
		} catch (Exception e) {	//NOSONAR
			// not in an application bundle
		}
		var path = System.getenv("PATH");
		for (var name : new String[] { "7zz", "7z", "7zr", "7za" }) {
			for (var dir : (path == null ? "" : path).split(File.pathSeparator)) {
				var f = new File(dir, name);
				if (f.canExecute()) {
					return f.getPath();
				}
			}
		}
		return null;
	}

	static void extract(File archive, File target) throws IOException {
		var exe = find();
		if (exe == null) {
			throw new IOException("7-Zip not found (it is bundled in the DroiD64 application; otherwise install 7zz / p7zip).");
		}
		target.mkdirs();
		var cmd = new ArrayList<String>();
		cmd.add(exe);
		cmd.add("x");
		cmd.add("-y");
		cmd.add("-bd");
		cmd.add("-o" + target.getPath());
		cmd.add(archive.getPath());
		var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
		var p = pb.start();
		var out = new String(p.getInputStream().readAllBytes());
		try {
			if (p.waitFor() != 0) {
				throw new IOException("7-Zip failed: " + out.substring(Math.max(0, out.length() - 400)));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
