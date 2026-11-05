package droid64.sid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Finds music in files: PSID/RSID files and plain PRG files with a SID music routine. */
public final class SidDetector {

	/** Result of the PRG probe. */
	public static final class Found {
		public final int init;
		public final int play;
		public final int sidWrites;

		Found(int init, int play, int sidWrites) {
			this.init = init;
			this.play = play;
			this.sidWrites = sidWrites;
		}
	}

	private SidDetector() {
	}

	/**
	 * Quick check on the bytes: number of stores to the SID registers.
	 * @param prg PRG file with load address
	 * @return number of instructions which store to $D400-$D41C
	 */
	public static int countSidStores(byte[] prg) {
		int n = 0;
		for (int i = 2; i + 2 < prg.length; i++) {
			int op = prg[i] & 0xff;
			if (op == 0x8d || op == 0x9d || op == 0x99 || op == 0x8e || op == 0x8c) {
				int lo = prg[i + 1] & 0xff;
				int hi = prg[i + 2] & 0xff;
				if (hi == 0xd4 && lo <= 0x1c) {
					n++;
				}
			}
		}
		return n;
	}

	private static int target(byte[] prg, int offset) {
		// JMP abs at file offset (after the 2 byte load address)
		if (offset + 2 < prg.length && (prg[offset] & 0xff) == 0x4c) {
			return (prg[offset + 1] & 0xff) | (prg[offset + 2] & 0xff) << 8;
		}
		return -1;
	}

	/**
	 * Try to find init and play routines by running the code and watching the SID registers.
	 * @param prg PRG file with load address
	 * @return the routines, or null when it does not look like music
	 */
	public static Found probe(byte[] prg) {
		if (prg == null || prg.length < 40 || prg.length > 40000 || countSidStores(prg) < 3) {
			return null;
		}
		int load = (prg[0] & 0xff) | (prg[1] & 0xff) << 8;
		if (load < 0x0200 || load > 0xcfff) {
			return null;
		}
		var entries = new LinkedHashSet<Integer>();
		for (int i = 0; i < 4; i++) {
			entries.add(load + i * 3);
		}
		for (int i = 0; i < 4; i++) {
			int t = target(prg, 2 + i * 3);
			if (t > 0) {
				entries.add(t);
			}
		}
		List<Integer> list = new ArrayList<>(entries);
		// the usual order first: init, play
		var pairs = new ArrayList<int[]>();
		pairs.add(new int[] { load, load + 3 });
		for (int i : list) {
			for (int p : list) {
				if (i != p && !(i == load && p == load + 3)) {
					pairs.add(new int[] { i, p });
				}
			}
		}
		for (var pair : pairs) {
			if (pair[0] > load + 12 && pair[1] > load + 12 && pair.length == 2 && !isLikelyTable(prg)) {
				continue;
			}
			var found = tryPair(prg, pair[0], pair[1]);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static boolean isLikelyTable(byte[] prg) {
		return target(prg, 2) > 0 && target(prg, 5) > 0;
	}

	private static Found tryPair(byte[] prg, int init, int play) {
		try {
			var tune = SidTune.fromPrg(prg, init, play, "");
			var engine = new SidEngine(tune, 1, true, SidChip.Model.MOS6581, 8000);
			engine.start();
			if (engine.hasFailed()) {
				return null;
			}
			short[] buf = new short[160];
			for (int i = 0; i < 30; i++) {
				engine.read(buf, 0, buf.length);
				if (engine.hasFailed()) {
					return null;
				}
			}
			if (engine.getSidWrites() >= 20 && engine.getVolumeWrites() > 0 && engine.isAlive()) {
				return new Found(init, play, (int) engine.getSidWrites());
			}
		} catch (RuntimeException e) {	//NOSONAR
			return null;
		}
		return null;
	}
}
