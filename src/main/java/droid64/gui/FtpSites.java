package droid64.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** The saved FTP sites (site manager). Every site is a String[]: name, host, port, user, remote folder, local folder, password. */
public final class FtpSites {

	public static final int NAME = 0;
	public static final int HOST = 1;
	public static final int PORT = 2;
	public static final int USER = 3;
	public static final int REMOTE = 4;
	public static final int LOCAL = 5;
	public static final int PASSWORD = 6;
	private static final int FIELDS = 7;
	private static final String PW_PREFIX = "b64:";

	private FtpSites() {
	}

	/** Remove the characters used as separators in the stored text. */
	public static String clean(String v) {
		return v == null ? "" : v.replace("|", "_").replace("~", "_").trim();
	}

	public static List<String[]> load() {
		var sites = new ArrayList<String[]>();
		var all = Setting.FTP_SITES.getString();
		if (all != null && !all.isEmpty()) {
			for (var rec : all.split("~~")) {
				var f = rec.split("\\|", -1);
				if (f.length >= 5 && !f[0].isEmpty()) {
					var s = new String[FIELDS];
					for (int i = 0; i < FIELDS; i++) {
						s[i] = i < f.length ? f[i] : "";
					}
					sites.add(s);
				}
			}
		}
		return sites;
	}

	public static void store(List<String[]> sites) {
		var sb = new StringBuilder();
		for (var s : sites) {
			if (sb.length() > 0) {
				sb.append("~~");
			}
			sb.append(String.join("|", s));
		}
		Setting.FTP_SITES.set(sb.toString());
	}

	public static String[] newSite(String name, String host, String port, String user, String password, String remote, String local) {
		return new String[] { clean(name), clean(host), clean(port.isEmpty() ? "21" : port), clean(user), clean(remote), clean(local), encode(password) };
	}

	/** The password is only hidden from a casual look at the settings file: it is not encrypted. */
	public static String encode(String password) {
		if (password == null || password.isEmpty()) {
			return "";
		}
		return PW_PREFIX + Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
	}

	public static String password(String[] site) {
		var v = site.length > PASSWORD ? site[PASSWORD] : "";
		if (v.startsWith(PW_PREFIX)) {
			try {
				return new String(Base64.getDecoder().decode(v.substring(PW_PREFIX.length())), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {	//NOSONAR
				return "";
			}
		}
		return "";
	}

	/** Where the FTP connection opens. */
	public static String openIn() {
		var v = Setting.FTP_OPEN_IN.getString();
		return "left".equals(v) || "right".equals(v) || "active".equals(v) ? v : "window";
	}
}
