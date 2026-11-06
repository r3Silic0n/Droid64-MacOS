package droid64.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;

import javax.swing.JTextArea;

import droid64.d64.DiskImage;

/**
 * Catalog of disk and tape images: a local index of the images found in folders (and inside zip files), with the
 * files they contain. No database server is needed; the catalog is kept in one compressed text file.
 */
final class Catalog {

	/** A file inside an image. */
	static final class Item {
		/** Name as stored on the image (PETSCII characters). */
		final String name;
		final String type;
		final int blocks;

		Item(String name, String type, int blocks) {
			this.name = name;
			this.type = type;
			this.blocks = blocks;
		}
	}

	/** One disk or tape image. */
	static final class Entry {
		/** The file (the zip file when the image is inside a zip). */
		String location;
		/** Name of the image inside the zip file, or null. */
		String inner;
		String format = "";
		boolean tape;
		String diskName = "";
		String diskId = "";
		long size;
		long modified;
		String md5 = "";
		int free = -1;
		String machine = "";
		final List<Item> files = new ArrayList<>();

		String key() {
			return inner == null ? location : location + "!" + inner;
		}

		/** @return the file name of the image */
		String imageName() {
			return new File(inner != null ? inner : location).getName();
		}

		/** @return the folder of the image */
		String folder() {
			var f = new File(location);
			var p = f.getParent();
			return (p != null ? p : "") + (inner != null ? File.separator + f.getName() : "");
		}
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<String> roots = new ArrayList<>();

	List<Entry> entries() {
		return new ArrayList<>(entries.values());
	}

	List<String> roots() {
		return roots;
	}

	int size() {
		return entries.size();
	}

	void clear() {
		entries.clear();
		roots.clear();
	}

	/** @return file where the catalog is stored */
	static File file() {
		return Setting.getCatalogFile();
	}

	// ---- storage ----

	private static String esc(String s) {
		return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	void save() throws IOException {
		var tmp = new File(file().getPath() + ".tmp");
		try (var w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp.toPath())), StandardCharsets.UTF_8))) {
			w.write("DroiD64 catalog 1\n");
			for (var r : roots) {
				w.write("R\t" + esc(r) + "\n");
			}
			for (var e : entries.values()) {
				w.write(String.join("\t", "E", esc(e.location), esc(e.inner), esc(e.format), e.tape ? "1" : "0", esc(e.diskName), esc(e.diskId),
						Long.toString(e.size), Long.toString(e.modified), e.md5, Integer.toString(e.free), esc(e.machine)) + "\n");
				for (var i : e.files) {
					w.write("F\t" + esc(i.name) + "\t" + esc(i.type) + "\t" + i.blocks + "\n");
				}
			}
		}
		Files.move(tmp.toPath(), file().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	void load() {
		clear();
		var f = file();
		if (!f.isFile()) {
			return;
		}
		try (var r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(f.toPath())), StandardCharsets.UTF_8))) {
			var line = r.readLine();
			if (line == null || !line.startsWith("DroiD64 catalog")) {
				return;
			}
			Entry cur = null;
			while ((line = r.readLine()) != null) {
				var p = line.split("\t", -1);
				if ("R".equals(p[0]) && p.length > 1) {
					roots.add(p[1]);
				} else if ("E".equals(p[0]) && p.length >= 12) {
					cur = new Entry();
					cur.location = p[1];
					cur.inner = p[2].isEmpty() ? null : p[2];
					cur.format = p[3];
					cur.tape = "1".equals(p[4]);
					cur.diskName = p[5];
					cur.diskId = p[6];
					cur.size = Long.parseLong(p[7]);
					cur.modified = Long.parseLong(p[8]);
					cur.md5 = p[9];
					cur.free = Integer.parseInt(p[10]);
					cur.machine = p[11];
					entries.put(cur.key(), cur);
				} else if ("F".equals(p[0]) && cur != null && p.length >= 4) {
					cur.files.add(new Item(p[1], p[2], Integer.parseInt(p[3])));
				}
			}
		} catch (IOException | RuntimeException e) {
			entries.clear();
			roots.clear();
		}
	}

	// ---- scanning ----

	/** Result counters of a scan. */
	static final class ScanResult {
		int found;
		int added;
		int unchanged;
		int removed;
		int failed;
	}

	/**
	 * Scan a folder (recursively, zip files included) and update the catalog. Images whose size and time did not change are kept.
	 * @param root the folder
	 * @param progress gets the path being read
	 * @param cancelled returns true to stop
	 * @return counters
	 */
	ScanResult scan(File root, Consumer<String> progress, java.util.function.BooleanSupplier cancelled) {
		var result = new ScanResult();
		var seen = new HashSet<String>();
		if (!roots.contains(root.getPath())) {
			roots.add(root.getPath());
		}
		walk(root, result, seen, progress, cancelled);
		if (!cancelled.getAsBoolean()) {
			var prefix = root.getPath() + File.separator;
			var it = entries.values().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.location.startsWith(prefix) && !seen.contains(e.key())) {
					it.remove();
					result.removed++;
				}
			}
		}
		return result;
	}

	private void walk(File dir, ScanResult result, Set<String> seen, Consumer<String> progress, java.util.function.BooleanSupplier cancelled) {
		var list = dir.listFiles();
		if (list == null) {
			return;
		}
		java.util.Arrays.sort(list, Comparator.comparing(f -> f.getName().toLowerCase()));
		for (var f : list) {
			if (cancelled.getAsBoolean()) {
				return;
			}
			var name = f.getName();
			if (name.startsWith(".")) {
				continue;
			}
			if (f.isDirectory()) {
				walk(f, result, seen, progress, cancelled);
			} else if (name.toLowerCase().endsWith(".zip")) {
				progress.accept(f.getPath());
				scanZip(f, result, seen, cancelled);
			} else if (Setting.isImageFileName(f)) {
				progress.accept(f.getPath());
				result.found++;
				var old = entries.get(f.getPath());
				seen.add(f.getPath());
				if (old != null && old.size == f.length() && old.modified == f.lastModified()) {
					result.unchanged++;
					continue;
				}
				try {
					var data = Files.readAllBytes(f.toPath());
					var e = read(f, null, f.getName(), data);
					e.size = f.length();
					e.modified = f.lastModified();
					entries.put(e.key(), e);
					result.added++;
				} catch (Exception | OutOfMemoryError ex) {	//NOSONAR
					result.failed++;
				}
			}
		}
	}

	private void scanZip(File zip, ScanResult result, Set<String> seen, java.util.function.BooleanSupplier cancelled) {
		try (var zf = new ZipFile(zip)) {
			var en = zf.entries();
			while (en.hasMoreElements() && !cancelled.getAsBoolean()) {
				var ze = en.nextElement();
				var name = ze.getName();
				var base = new File(name).getName();
				if (ze.isDirectory() || base.startsWith(".") || name.startsWith("__MACOSX") || !Setting.isImageFileName(new File(base))) {
					continue;
				}
				result.found++;
				var key = zip.getPath() + "!" + name;
				seen.add(key);
				var old = entries.get(key);
				if (old != null && old.size == ze.getSize() && old.modified == zip.lastModified()) {
					result.unchanged++;
					continue;
				}
				try (var in = zf.getInputStream(ze)) {
					var data = in.readAllBytes();
					var e = read(zip, name, base, data);
					e.size = ze.getSize();
					e.modified = zip.lastModified();
					entries.put(e.key(), e);
					result.added++;
				} catch (Exception | OutOfMemoryError ex) {	//NOSONAR
					result.failed++;
				}
			}
		} catch (IOException | RuntimeException ex) {	//NOSONAR
			result.failed++;
		}
	}

	private static Entry read(File location, String inner, String imageName, byte[] data) throws Exception {
		var e = new Entry();
		e.location = location.getPath();
		e.inner = inner;
		var img = DiskImage.getDiskImage(new File(imageName), data, new ConsoleStream(new JTextArea()));
		img.readBAM();
		img.readDirectory();
		var format = img.getImageFormat();
		e.format = DiskImage.getImageTypeName(format);
		var id = format != null ? format.id : "";
		e.tape = "T64".equals(id) || "TAP".equals(id);
		e.md5 = md5(data);
		if (!img.isCpmImage()) {
			var bam = img.getBam();
			e.diskName = bam != null && bam.getDiskName() != null ? bam.getDiskName() : "";
			e.diskId = bam != null && bam.getDiskId() != null ? bam.getDiskId() : "";
			try {
				e.machine = String.valueOf(MachineDetector.detect(img));
			} catch (RuntimeException ex) {	//NOSONAR
				e.machine = "";
			}
		}
		try {
			if (!e.tape && !(img instanceof droid64.d64.LNX)) {
				e.free = img.getBlocksFree();
			}
		} catch (RuntimeException ex) {	//NOSONAR
			e.free = -1;
		}
		for (int i = 0; i < img.getCbmFileSize(); i++) {
			var f = img.getCbmFile(i);
			if (f == null) {
				continue;
			}
			if (!img.isCpmImage() && f.isFileScratched()) {
				continue;
			}
			var type = f.getFileType() != null ? f.getFileType().name() : "";
			e.files.add(new Item(f.getName(), type, f.getSizeInBlocks()));
		}
		return e;
	}

	private static String md5(byte[] data) {
		try {
			var d = MessageDigest.getInstance("MD5").digest(data);
			var sb = new StringBuilder();
			for (var b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException ex) {
			return "";
		}
	}

	/** @param folder remove the images of this scanned folder from the catalog */
	void removeRoot(String folder) {
		roots.remove(folder);
		var prefix = folder + File.separator;
		entries.values().removeIf(e -> e.location.startsWith(prefix));
	}
}
