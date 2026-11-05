package droid64.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Minimal HTTP helper for the update functions. */
final class Http {

	/** Progress callback. */
	interface Progress {
		/** @param done bytes so far @param total bytes in total or -1 */
		void update(long done, long total);
	}

	private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(20)).build();
	private static final String AGENT = "curl/8.4.0 DroiD64";

	private Http() {
	}

	private static HttpRequest.Builder req(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", AGENT);
	}

	static String text(String url) throws IOException {
		try {
			var r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				throw new IOException("HTTP " + r.statusCode() + " for " + url);
			}
			return r.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	/** @return true if the URL answers 200 (HEAD, with GET fallback) */
	static boolean exists(String url) {
		try {
			var r = CLIENT.send(req(url).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
			return r.statusCode() == 200;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	static void download(String url, File target, Progress p, java.util.function.BooleanSupplier cancelled) throws IOException {
		try {
			var r = CLIENT.send(req(url).timeout(Duration.ofMinutes(30)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			if (r.statusCode() != 200) {
				throw new IOException("HTTP " + r.statusCode() + " for " + url);
			}
			long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
			var tmp = new File(target.getPath() + ".part");
			try (InputStream in = r.body(); var out = Files.newOutputStream(tmp.toPath())) {
				var buf = new byte[65536];
				long done = 0;
				int n;
				while ((n = in.read(buf)) > 0) {
					if (cancelled.getAsBoolean()) {
						throw new IOException("cancelled");
					}
					out.write(buf, 0, n);
					done += n;
					p.update(done, total);
				}
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}
}
