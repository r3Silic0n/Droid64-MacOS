package droid64.net;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A small FTP client (passive mode, binary transfers, no encryption). */
public final class FtpClient implements Closeable {

	/** One entry of a remote directory. */
	public static final class Entry {
		public final String name;
		public final boolean dir;
		public final long size;
		public final String date;

		Entry(String name, boolean dir, long size, String date) {
			this.name = name;
			this.dir = dir;
			this.size = size;
			this.date = date;
		}
	}

	/** Progress of a transfer. */
	public interface Progress {
		void bytes(long done);

		boolean cancelled();
	}

	private static final int TIMEOUT = 20000;
	private static final Pattern UNIX = Pattern.compile("^([\\-dlbcps])[rwxsStT\\-]{9}[+@.]?\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\w{3}\\s+\\d+\\s+[\\d:]+)\\s+(.+)$");
	private static final Pattern DOS = Pattern.compile("^(\\d\\d-\\d\\d-\\d\\d\\d?\\d?\\s+\\d\\d:\\d\\d[AP]M)\\s+(<DIR>|\\d+)\\s+(.+)$");

	private Socket control;
	private BufferedReader in;
	private OutputStream out;
	private String lastReply = "";
	private String encoding = "ISO-8859-1";

	/** Connect and read the greeting. */
	public void connect(String host, int port) throws IOException {
		control = new Socket();
		control.connect(new InetSocketAddress(host, port), TIMEOUT);
		control.setSoTimeout(TIMEOUT);
		in = new BufferedReader(new InputStreamReader(control.getInputStream(), encoding));
		out = control.getOutputStream();
		int c = reply();
		if (c / 100 != 2) {
			throw new IOException("Server refused: " + lastReply);
		}
	}

	public boolean isConnected() {
		return control != null && control.isConnected() && !control.isClosed();
	}

	/** @return the text of the last reply */
	public String getLastReply() {
		return lastReply;
	}

	public void login(String user, String password) throws IOException {
		int c = command("USER " + user);
		if (c == 331 || c == 332) {
			c = command("PASS " + password);
		}
		if (c / 100 != 2) {
			throw new IOException("Login failed: " + lastReply);
		}
		command("TYPE I");
	}

	public String pwd() throws IOException {
		if (command("PWD") != 257) {
			throw new IOException(lastReply);
		}
		int a = lastReply.indexOf('"');
		int b = lastReply.lastIndexOf('"');
		return a >= 0 && b > a ? lastReply.substring(a + 1, b) : "/";
	}

	public void cwd(String dir) throws IOException {
		if (command("CWD " + dir) / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	public void cdup() throws IOException {
		if (command("CDUP") / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	public void mkdir(String name) throws IOException {
		if (command("MKD " + name) / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	public void delete(String name, boolean dir) throws IOException {
		if (command((dir ? "RMD " : "DELE ") + name) / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	public void rename(String from, String to) throws IOException {
		if (command("RNFR " + from) != 350 || command("RNTO " + to) / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	/** List the current directory. */
	public List<Entry> list() throws IOException {
		var text = new ByteArrayOutputStream();
		boolean mlsd = true;
		try (Socket data = openData()) {
			int c = command("MLSD");
			if (c / 100 != 1) {
				mlsd = false;
			} else {
				copy(data.getInputStream(), text, null);
			}
		}
		if (mlsd) {
			expectDone();
			return parse(text.toString(encoding), true);
		}
		text.reset();
		try (Socket data = openData()) {
			int c = command("LIST");
			if (c / 100 != 1) {
				throw new IOException(lastReply);
			}
			copy(data.getInputStream(), text, null);
		}
		expectDone();
		return parse(text.toString(encoding), false);
	}

	/** Download a file. */
	public void retrieve(String name, OutputStream target, Progress progress) throws IOException {
		try (Socket data = openData()) {
			if (command("RETR " + name) / 100 != 1) {
				throw new IOException(lastReply);
			}
			copy(data.getInputStream(), target, progress);
		}
		expectDone();
	}

	/** Upload a file. */
	public void store(String name, InputStream source, Progress progress) throws IOException {
		try (Socket data = openData()) {
			if (command("STOR " + name) / 100 != 1) {
				throw new IOException(lastReply);
			}
			copy(source, data.getOutputStream(), progress);
		}
		expectDone();
	}

	/** @return size of a remote file or -1 */
	public long size(String name) {
		try {
			if (command("SIZE " + name) == 213) {
				return Long.parseLong(lastReply.substring(4).trim());
			}
		} catch (IOException | RuntimeException e) {	//NOSONAR
			// unknown
		}
		return -1;
	}

	@Override
	public void close() {
		try {
			if (isConnected()) {
				command("QUIT");
			}
		} catch (IOException e) {	//NOSONAR
			// closing anyway
		}
		try {
			if (control != null) {
				control.close();
			}
		} catch (IOException e) {	//NOSONAR
			// ignore
		}
	}

	// ----

	private void expectDone() throws IOException {
		int c = reply();
		if (c / 100 != 2) {
			throw new IOException(lastReply);
		}
	}

	private Socket openData() throws IOException {
		int c = command("EPSV");
		String host = control.getInetAddress().getHostAddress();
		int port;
		if (c == 229) {
			int a = lastReply.indexOf('(');
			var parts = lastReply.substring(a + 1, lastReply.indexOf(')', a)).split("\\|");
			port = Integer.parseInt(parts[3]);
		} else {
			c = command("PASV");
			if (c != 227) {
				throw new IOException("Passive mode failed: " + lastReply);
			}
			int a = lastReply.indexOf('(');
			var n = lastReply.substring(a + 1, lastReply.indexOf(')', a)).split(",");
			host = n[0] + "." + n[1] + "." + n[2] + "." + n[3];
			port = Integer.parseInt(n[4].trim()) * 256 + Integer.parseInt(n[5].trim());
		}
		var s = new Socket();
		s.connect(new InetSocketAddress(host, port), TIMEOUT);
		s.setSoTimeout(TIMEOUT);
		return s;
	}

	private int command(String cmd) throws IOException {
		out.write((cmd + "\r\n").getBytes(encoding));
		out.flush();
		return reply();
	}

	private int reply() throws IOException {
		String line = in.readLine();
		if (line == null) {
			throw new IOException("Connection closed by the server");
		}
		var all = new StringBuilder(line);
		if (line.length() >= 4 && line.charAt(3) == '-') {
			String code = line.substring(0, 3) + " ";
			while ((line = in.readLine()) != null) {
				all.append('\n').append(line);
				if (line.startsWith(code)) {
					break;
				}
			}
		}
		lastReply = all.toString();
		try {
			return Integer.parseInt(lastReply.substring(0, 3));
		} catch (RuntimeException e) {
			throw new IOException("Bad reply: " + lastReply);
		}
	}

	private static void copy(InputStream in, OutputStream out, Progress progress) throws IOException {
		var buf = new byte[16384];
		long done = 0;
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
			done += n;
			if (progress != null) {
				progress.bytes(done);
				if (progress.cancelled()) {
					throw new IOException("Cancelled");
				}
			}
		}
		out.flush();
	}

	static List<Entry> parse(String text, boolean mlsd) {
		var result = new ArrayList<Entry>();
		for (var line : text.split("\r?\n")) {
			if (line.trim().isEmpty()) {
				continue;
			}
			Entry e = mlsd ? parseMlsd(line) : parseList(line);
			if (e != null && !e.name.equals(".") && !e.name.equals("..")) {
				result.add(e);
			}
		}
		result.sort((a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
		return result;
	}

	private static Entry parseMlsd(String line) {
		int sp = line.indexOf(' ');
		if (sp < 0) {
			return null;
		}
		String name = line.substring(sp + 1);
		boolean dir = false;
		long size = 0;
		String date = "";
		for (var f : line.substring(0, sp).split(";")) {
			int eq = f.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String k = f.substring(0, eq).toLowerCase();
			String v = f.substring(eq + 1);
			if (k.equals("type")) {
				dir = v.equalsIgnoreCase("dir") || v.equalsIgnoreCase("cdir") || v.equalsIgnoreCase("pdir");
			} else if (k.equals("size")) {
				size = parseLong(v);
			} else if (k.equals("modify") && v.length() >= 12) {
				date = v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8) + " " + v.substring(8, 10) + ":" + v.substring(10, 12);
			}
		}
		return new Entry(name, dir, size, date);
	}

	private static Entry parseList(String line) {
		var m = UNIX.matcher(line);
		if (m.matches()) {
			String name = m.group(4);
			int arrow = name.indexOf(" -> ");
			if (arrow > 0) {
				name = name.substring(0, arrow);
			}
			char t = m.group(1).charAt(0);
			return new Entry(name, t == 'd' || t == 'l', parseLong(m.group(2)), m.group(3));
		}
		m = DOS.matcher(line);
		if (m.matches()) {
			boolean dir = m.group(2).equals("<DIR>");
			return new Entry(m.group(3), dir, dir ? 0 : parseLong(m.group(2)), m.group(1));
		}
		return null;
	}

	private static long parseLong(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** For tests: text encoding is ISO-8859-1, bytes are not changed. */
	static String latin(byte[] b) {
		return new String(b, StandardCharsets.ISO_8859_1);
	}
}
