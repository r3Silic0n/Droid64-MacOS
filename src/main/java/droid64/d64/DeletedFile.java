package droid64.d64;

/** A scratched file found by {@link DiskImage#scanDeleted()}. */
public class DeletedFile {
	public static final int COMPLETE = 0;
	public static final int PARTIAL = 1;
	public static final int BROKEN = 2;

	private final CbmFile entry;
	private final int blocks;
	private final int status;
	private final byte[] data;

	public DeletedFile(CbmFile entry, int blocks, int status, byte[] data) {
		this.entry = entry;
		this.blocks = blocks;
		this.status = status;
		this.data = data;
	}

	public CbmFile getEntry() { return entry; }
	public int getBlocks() { return blocks; }
	public int getStatus() { return status; }
	public byte[] getData() { return data; }

	/** @return OK, PT (only the first blocks survive) or BD (start block already reused) */
	public String getStatusText() {
		return status == COMPLETE ? "OK" : status == PARTIAL ? "PT" : "BD";
	}
}
