package droid64.sid;

/**
 * A minimal C64 around the 6510 and the SID chips. It runs the init and play routines of a tune
 * and produces PCM samples.
 */
public final class SidEngine implements Cpu6510.Bus {

	private static final int SENTINEL = 0xfff0;
	private static final int PAL_CLOCK = 985248;
	private static final int NTSC_CLOCK = 1022727;

	private final byte[] ram = new byte[65536];
	private final Cpu6510 cpu = new Cpu6510(this);
	private final SidTune tune;
	private final int song;
	private final boolean pal;
	private final int sampleRate;
	private final SidChip[] chips;
	private final int[] chipBase;
	private final double clockHz;
	private final double cyclesPerSample;

	private long cycle;
	private long sidCycle;
	private long sampleCount;
	private long frameStart;
	private final int cyclesPerLine;
	private final int lines;
	private int framePeriod;
	private int ciaLatch;
	private boolean ciaMode;
	private long sidWrites;
	private int volumeWrites;

	private short[] pcm = new short[1 << 16];
	private int pcmRead;
	private int pcmWrite;
	private int pcmCount;
	private double gain = 1.0;
	private double dcPrev;
	private double dcOut;
	private final short[] scope = new short[1024];
	private volatile int scopePos;
	private boolean failed;
	private boolean alive = true;
	private String problem;

	public SidEngine(SidTune tune, int song, boolean pal, SidChip.Model model, int sampleRate) {
		this.tune = tune;
		this.song = song;
		this.pal = pal;
		this.sampleRate = sampleRate;
		this.clockHz = pal ? PAL_CLOCK : NTSC_CLOCK;
		this.cyclesPerLine = pal ? 63 : 65;
		this.lines = pal ? 312 : 263;
		this.cyclesPerSample = clockHz / sampleRate;
		int n = 1 + (tune.secondSid != 0 ? 1 : 0) + (tune.thirdSid != 0 ? 1 : 0);
		chips = new SidChip[n];
		chipBase = new int[n];
		for (int i = 0; i < n; i++) {
			chips[i] = new SidChip(model, clockHz);
		}
		chipBase[0] = 0xd400;
		if (n > 1) {
			chipBase[1] = tune.secondSid;
		}
		if (n > 2) {
			chipBase[2] = tune.thirdSid;
		}
	}

	public int getChipCount() {
		return chips.length;
	}

	public SidChip getChip(int i) {
		return chips[i];
	}

	public void setGain(double g) {
		this.gain = g;
	}

	public boolean hasFailed() {
		return failed;
	}

	public String getProblem() {
		return problem;
	}

	public long getSidWrites() {
		return sidWrites;
	}

	/** @return true when the play routine returned each time (it is not a demo that never gives control back) */
	public boolean isAlive() {
		return alive;
	}

	public int getVolumeWrites() {
		return volumeWrites;
	}

	public short[] getScope() {
		return scope;
	}

	public int getScopePos() {
		return scopePos;
	}

	public double getFrameRate() {
		return clockHz / framePeriod;
	}

	// ---- bus ----

	private boolean ioVisible() {
		int p = ram[1] & 7;
		return (p & 4) != 0 && (p & 3) != 0;
	}

	private int sidIndex(int addr) {
		for (int i = chips.length - 1; i > 0; i--) {
			if (addr >= chipBase[i] && addr < chipBase[i] + 0x20) {
				return i;
			}
		}
		return addr >= 0xd400 && addr < 0xd800 ? 0 : -1;
	}

	@Override
	public int read(int addr) {
		if (addr >= 0xd000 && addr < 0xe000 && ioVisible()) {
			int i = sidIndex(addr);
			if (i >= 0) {
				clockChipsTo(cycle);
				return chips[i].read(addr & 0x1f);
			}
			if (addr == 0xd012) {
				return rasterLine() & 0xff;
			}
			if (addr == 0xd011) {
				return (ram[0xd011] & 0x7f) | (rasterLine() > 255 ? 0x80 : 0);
			}
			if (addr == 0xdc0d || addr == 0xdd0d) {
				return 0;
			}
			return ram[addr] & 0xff;
		}
		return ram[addr] & 0xff;
	}

	@Override
	public void write(int addr, int value) {
		if (addr >= 0xd000 && addr < 0xe000 && ioVisible()) {
			int i = sidIndex(addr);
			if (i >= 0) {
				clockChipsTo(cycle);
				chips[i].write(addr & 0x1f, value);
				sidWrites++;
				if ((addr & 0x1f) == 0x18 && (value & 15) != 0) {
					volumeWrites++;
				}
				return;
			}
			if (addr == 0xdc04) {
				ciaLatch = (ciaLatch & 0xff00) | value;
			} else if (addr == 0xdc05) {
				ciaLatch = (ciaLatch & 0x00ff) | value << 8;
			}
			ram[addr] = (byte) value;
			return;
		}
		ram[addr] = (byte) value;
	}

	private int rasterLine() {
		return (int) (((cycle - frameStart) / cyclesPerLine) % lines);
	}

	// ---- clocking and sample output ----

	private void clockChipsTo(long target) {
		while (sidCycle < target) {
			long boundary = (long) Math.ceil((sampleCount + 1) * cyclesPerSample);
			long end = Math.min(target, boundary);
			int n = (int) (end - sidCycle);
			if (n > 0) {
				for (var c : chips) {
					c.clock(n);
				}
				sidCycle = end;
			}
			if (sidCycle >= boundary) {
				emitSample();
			}
		}
	}

	private void emitSample() {
		double s = 0;
		for (var c : chips) {
			s += c.takeSample();
		}
		// remove the DC level (digi playback through the volume register keeps its steps)
		dcOut = s - dcPrev + 0.9995 * dcOut;
		dcPrev = s;
		double v = dcOut * gain * 1.6;
		if (v > 1) {
			v = 1;
		} else if (v < -1) {
			v = -1;
		}
		short sv = (short) (v * 32000);
		if (pcmCount == pcm.length) {
			pcmRead = (pcmRead + 1) % pcm.length;		// drop the oldest sample
			pcmCount--;
		}
		pcm[pcmWrite] = sv;
		pcmWrite = (pcmWrite + 1) % pcm.length;
		pcmCount++;
		sampleCount++;
		scope[scopePos & 1023] = sv;
		scopePos++;
	}

	// ---- running the code ----

	private void load() {
		java.util.Arrays.fill(ram, (byte) 0);
		int addr = tune.loadAddress;
		for (int i = 0; i < tune.data.length && addr + i < 0x10000; i++) {
			ram[addr + i] = tune.data[i];
		}
		// Planted RTI for tunes that finish their IRQ handler with a jump into the KERNAL
		ram[0xea31] = 0x40;
		ram[0xea81] = 0x40;
		ram[0xea7e] = 0x40;
		ram[0xfffa] = (byte) 0xf0;
		ram[0xfffb] = (byte) 0xff;
		ram[0xfffe] = 0x31;
		ram[0xffff] = (byte) 0xea;
		ram[0x314] = 0x31;
		ram[0x315] = (byte) 0xea;
		int p = 0x37;
		int end = tune.endAddress();
		if (tune.loadAddress >= 0xd000 || end >= 0xd000) {
			p = 0x35;
		} else if (tune.loadAddress >= 0xa000 || end >= 0xa000) {
			p = 0x36;
		}
		ram[0] = 0x2f;
		ram[1] = (byte) p;
	}

	private int callCycles;

	private void run(int limit) {
		callCycles = 0;
		while (cpu.pc != SENTINEL && callCycles < limit) {
			int c = cpu.step();
			cycle += c;
			callCycles += c;
			if (cpu.jammed) {
				break;
			}
		}
	}

	private boolean callSubroutine(int addr, int a, int limit) {
		cpu.a = a;
		cpu.x = 0;
		cpu.y = 0;
		cpu.sp = 0xfd;
		cpu.setFlags(0x24);
		cpu.jammed = false;
		int ret = (SENTINEL - 1) & 0xffff;
		ram[0x1ff] = (byte) (ret >> 8);
		ram[0x1fe] = (byte) (ret & 0xff);
		cpu.pc = addr;
		run(limit);
		return cpu.pc == SENTINEL;
	}

	private void callInterrupt(int limit) {
		cpu.sp = 0xff;
		cpu.pc = SENTINEL;
		int vec;
		if ((ram[1] & 3) == 0 || (ram[1] & 7) == 4) {
			vec = (ram[0xfffe] & 0xff) | (ram[0xffff] & 0xff) << 8;
		} else {
			vec = (ram[0x314] & 0xff) | (ram[0x315] & 0xff) << 8;
		}
		cpu.setFlags(0x20);
		cpu.jammed = false;
		cpu.interrupt(vec);
		run(limit);
	}

	/** Load the tune and run its init routine. */
	public void start() {
		load();
		ciaMode = tune.ciaSpeed(song);
		framePeriod = cyclesPerLine * lines;
		if (tune.initAddress == 0) {
			tune.initAddress = tune.loadAddress;
		}
		cycle = 0;
		sidCycle = 0;
		frameStart = 0;
		if (!callSubroutine(tune.initAddress, song - 1, 3_000_000) && cpu.jammed) {
			failed = true;
			problem = "The init routine crashed (illegal instruction).";
		}
		if (ciaMode && ciaLatch > 100) {
			framePeriod = ciaLatch + 1;
		} else if (ciaMode) {
			framePeriod = (int) (clockHz / 60);
		}
		frameStart = cycle;
	}

	/** Run one frame: the play routine, then let the chips run to the end of the frame. */
	private void runFrame() {
		long begin = cycle;
		frameStart = begin;
		if (tune.playAddress != 0) {
			if (!callSubroutine(tune.playAddress, 0, 400_000)) {
				alive = false;
			}
		} else {
			callInterrupt(400_000);
		}
		if (cpu.jammed) {
			failed = true;
			problem = "The play routine crashed (illegal instruction).";
		}
		long end = begin + framePeriod;
		if (cycle < end) {
			cycle = end;
		}
		clockChipsTo(cycle);
	}

	/**
	 * Fill mono 16 bit samples.
	 * @param dst destination
	 * @param off offset
	 * @param len number of samples
	 */
	public void read(short[] dst, int off, int len) {
		int guard = 0;
		while (pcmCount < len && guard++ < 1000) {
			runFrame();
			if (failed) {
				break;
			}
		}
		int n = Math.min(len, pcmCount);
		for (int i = 0; i < n; i++) {
			dst[off + i] = pcm[pcmRead];
			pcmRead = (pcmRead + 1) % pcm.length;
		}
		pcmCount -= n;
		for (int i = n; i < len; i++) {
			dst[off + i] = 0;
		}
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public boolean isPal() {
		return pal;
	}
}
