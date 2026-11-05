package droid64.update;

/** Receives messages and progress of an update. */
public interface Log {
	void log(String message);

	/** @param done bytes done @param total bytes in total, or -1 */
	void progress(long done, long total);

	boolean cancelled();
}
