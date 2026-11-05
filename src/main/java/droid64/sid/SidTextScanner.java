package droid64.sid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Looks for the text a music player shows on screen (title, "music by ...") inside the program data.
 */
public final class SidTextScanner {

	/** Texts found, and the author / release guessed from them. */
	public static final class Result {
		public final List<String> texts = new ArrayList<>();
		public String author = "";
		public String released = "";
	}

	private static final Pattern DATE = Pattern.compile("(\\d{2}/\\d{1,2}|\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|(19|20)\\d{2})");

	private SidTextScanner() {
		super();
	}

	/**
	 * @param prg PRG with load address
	 * @return the texts
	 */
	public static Result scan(byte[] prg) {
		var r = new Result();
		var sb = new StringBuilder();
		for (int i = 2; i <= prg.length; i++) {
			int c = i < prg.length ? prg[i] & 0xff : 0;
			char ch = map(c);
			if (ch != 0) {
				sb.append(ch);
			} else {
				add(r, sb.toString());
				sb.setLength(0);
			}
		}
		guess(r);
		return r;
	}

	private static char map(int c) {
		if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ') {
			return (char) c;
		}
		if (c == 0x60 || c == 0xa0) {		// shifted space / screen space
			return ' ';
		}
		if (c >= 'a' && c <= 'z') {
			return (char) c;
		}
		if (c != 0 && ".,:;/-!'()&+*#?@=".indexOf(c) >= 0) {
			return (char) c;
		}
		return 0;
	}

	private static void add(Result r, String s) {
		var t = s.trim().replaceAll(" {2,}", "  ");
		if (t.length() < 8) {
			return;
		}
		int letters = 0;
		for (char ch : t.toCharArray()) {
			if (Character.isLetter(ch)) {
				letters++;
			}
		}
		if (letters < 5 || letters * 2 < t.length()) {
			return;
		}
		// a text has vowels; code and tables usually do not
		if (!t.toUpperCase().matches(".*[AEIOUY].*")) {
			return;
		}
		int run = 1;
		for (int i = 1; i < t.length(); i++) {
			run = t.charAt(i) == t.charAt(i - 1) + 1 ? run + 1 : 1;
			if (run >= 6) {
				return;		// character set table, not a message
			}
		}
		if (!r.texts.contains(t)) {
			r.texts.add(t);
		}
	}

	private static void guess(Result r) {
		for (var t : r.texts) {
			var u = t.toUpperCase();
			int k = -1;
			for (var key : new String[] { "MUSIC BY ", "MUSIC : ", "MUSIC:", "COMPOSED BY ", "TUNE BY ", "CODE BY ", "BY " }) {
				k = u.indexOf(key);
				if (k >= 0) {
					var rest = t.substring(k + key.length()).trim();
					var m = DATE.matcher(rest);
					String date = "";
					if (m.find() && m.start() > 0) {
						date = m.group();
						rest = rest.substring(0, m.start()).trim();
					} else if (m.find(0) && m.start() == 0) {
						rest = "";
					}
					rest = rest.replaceAll("[-:,. ]+$", "").trim();
					if (!rest.isEmpty()) {
						r.author = rest;
						r.released = date;
						return;
					}
				}
			}
		}
	}
}
