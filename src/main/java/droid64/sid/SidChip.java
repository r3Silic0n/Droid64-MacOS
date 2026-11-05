package droid64.sid;

/**
 * MOS 6581 / 8580 SID emulation: three oscillators, envelopes and the multimode filter.
 * The chip is clocked in system cycles; takeSample() returns the average output since the last call.
 */
public final class SidChip {

	/** Chip revision, changes the filter and the digi (volume register) level. */
	public enum Model {
		MOS6581("MOS 6581"), MOS8580("MOS 8580");

		private final String label;

		Model(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final int[] RATE_PERIOD = { 9, 32, 63, 95, 149, 220, 267, 313, 392, 977, 1954, 3126, 3907, 11720, 19532, 31251 };

	private static final int ATTACK = 0;
	private static final int DECAY_SUSTAIN = 1;
	private static final int RELEASE = 2;

	private static final class Voice {
		int acc;
		int freq;
		int pw;
		int ctrl;
		int ad;
		int sr;
		int shift = 0x7ffff8;
		boolean msbRising;
		int env;
		int rateCounter;
		int ratePeriod = RATE_PERIOD[0];
		int expCounter;
		int expPeriod = 1;
		int state = RELEASE;
		boolean holdZero = true;
		boolean gate;
		int wave;
	}

	private final Voice[] v = { new Voice(), new Voice(), new Voice() };
	private final int[] regs = new int[32];
	private Model model = Model.MOS6581;
	private final double clockHz;
	private int fc;
	private int res;
	private int filterRoute;
	private int mode;
	private int volume;
	private boolean voice3Off;
	private double lp;
	private double bp;
	private double hp;
	private double fCoef;
	private double damping = 1.4;
	private int cycleCounter;
	private double sum;
	private int count;
	private double last;
	private int muteMask;
	private boolean filterEnabled = true;
	private int lastBusValue;

	public SidChip(Model model, double clockHz) {
		this.clockHz = clockHz;
		this.model = model;
		updateFilter();
	}

	public void setModel(Model model) {
		this.model = model;
		updateFilter();
	}

	public Model getModel() {
		return model;
	}

	/** @param mask bit n set = voice n silent */
	public void setMuteMask(int mask) {
		this.muteMask = mask;
	}

	public void setFilterEnabled(boolean enabled) {
		this.filterEnabled = enabled;
	}

	public void reset() {
		for (var vo : v) {
			vo.acc = 0;
			vo.freq = 0;
			vo.pw = 0;
			vo.ctrl = 0;
			vo.ad = 0;
			vo.sr = 0;
			vo.shift = 0x7ffff8;
			vo.env = 0;
			vo.state = RELEASE;
			vo.holdZero = true;
			vo.gate = false;
			vo.ratePeriod = RATE_PERIOD[0];
		}
		java.util.Arrays.fill(regs, 0);
		fc = 0;
		res = 0;
		filterRoute = 0;
		mode = 0;
		volume = 0;
		voice3Off = false;
		lp = 0;
		bp = 0;
		hp = 0;
		updateFilter();
	}

	/** Level 0..1 of the envelope of a voice, for level meters. */
	public double getVoiceLevel(int voice) {
		return v[voice].env / 255.0;
	}

	public void write(int reg, int value) {
		reg &= 0x1f;
		value &= 0xff;
		regs[reg] = value;
		lastBusValue = value;
		if (reg < 21) {
			var vo = v[reg / 7];
			switch (reg % 7) {
			case 0:
				vo.freq = (vo.freq & 0xff00) | value;
				break;
			case 1:
				vo.freq = (vo.freq & 0x00ff) | value << 8;
				break;
			case 2:
				vo.pw = (vo.pw & 0xf00) | value;
				break;
			case 3:
				vo.pw = (vo.pw & 0x0ff) | (value & 0x0f) << 8;
				break;
			case 4:
				writeControl(vo, value);
				break;
			case 5:
				vo.ad = value;
				if (vo.state == ATTACK) {
					vo.ratePeriod = RATE_PERIOD[value >> 4];
				} else if (vo.state == DECAY_SUSTAIN) {
					vo.ratePeriod = RATE_PERIOD[value & 15];
				}
				break;
			default:
				vo.sr = value;
				if (vo.state == RELEASE) {
					vo.ratePeriod = RATE_PERIOD[value & 15];
				}
				break;
			}
			return;
		}
		switch (reg) {
		case 21:
			fc = (fc & 0x7f8) | (value & 7);
			updateFilter();
			break;
		case 22:
			fc = (fc & 0x007) | value << 3;
			updateFilter();
			break;
		case 23:
			filterRoute = value & 0x0f;
			res = value >> 4;
			updateFilter();
			break;
		case 24:
			volume = value & 15;
			mode = (value >> 4) & 7;
			voice3Off = (value & 0x80) != 0;
			break;
		default:
			break;
		}
	}

	private void writeControl(Voice vo, int value) {
		boolean gate = (value & 1) != 0;
		if (gate && !vo.gate) {
			vo.state = ATTACK;
			vo.ratePeriod = RATE_PERIOD[vo.ad >> 4];
			vo.holdZero = false;
		} else if (!gate && vo.gate) {
			vo.state = RELEASE;
			vo.ratePeriod = RATE_PERIOD[vo.sr & 15];
		}
		vo.gate = gate;
		if ((value & 8) != 0) {
			vo.acc = 0;
			vo.shift = 0x7ffff8;
		}
		vo.ctrl = value;
	}

	public int read(int reg) {
		reg &= 0x1f;
		switch (reg) {
		case 0x19:
		case 0x1a:
			return 0xff;
		case 0x1b:
			return v[2].wave >> 4;
		case 0x1c:
			return v[2].env;
		default:
			return lastBusValue;
		}
	}

	private void updateFilter() {
		double hz;
		if (model == Model.MOS6581) {
			hz = 30 + 9500 * Math.pow(fc / 2047.0, 1.7);
		} else {
			hz = 5 + 12500 * (fc / 2047.0);
		}
		double fs = clockHz / 4;
		fCoef = 2 * Math.sin(Math.PI * Math.min(hz, fs / 6) / fs);
		damping = 1.41421 - res / 15.0 * 1.2;
	}

	private void clockEnvelope(Voice vo) {
		vo.rateCounter = (vo.rateCounter + 1) & 0x7fff;
		if (vo.rateCounter != vo.ratePeriod) {
			return;
		}
		vo.rateCounter = 0;
		if (vo.state == ATTACK || ++vo.expCounter == vo.expPeriod) {
			vo.expCounter = 0;
			if (vo.holdZero) {
				return;
			}
			switch (vo.state) {
			case ATTACK:
				vo.env = (vo.env + 1) & 0xff;
				if (vo.env == 0xff) {
					vo.state = DECAY_SUSTAIN;
					vo.ratePeriod = RATE_PERIOD[vo.ad & 15];
				}
				break;
			case DECAY_SUSTAIN:
				if (vo.env != (vo.sr >> 4) * 17) {
					vo.env = (vo.env - 1) & 0xff;
				}
				break;
			default:
				vo.env = (vo.env - 1) & 0xff;
				break;
			}
			switch (vo.env) {
			case 0xff:
				vo.expPeriod = 1;
				break;
			case 0x5d:
				vo.expPeriod = 2;
				break;
			case 0x36:
				vo.expPeriod = 4;
				break;
			case 0x1a:
				vo.expPeriod = 8;
				break;
			case 0x0e:
				vo.expPeriod = 16;
				break;
			case 0x06:
				vo.expPeriod = 30;
				break;
			case 0x00:
				vo.expPeriod = 1;
				vo.holdZero = true;
				break;
			default:
				break;
			}
		}
	}

	private void clockOscillator(Voice vo) {
		if ((vo.ctrl & 8) != 0) {
			vo.msbRising = false;
			return;
		}
		int prev = vo.acc;
		vo.acc = (prev + vo.freq) & 0xffffff;
		vo.msbRising = (~prev & vo.acc & 0x800000) != 0;
		if ((~prev & vo.acc & 0x080000) != 0) {
			int bit = ((vo.shift >> 22) ^ (vo.shift >> 17)) & 1;
			vo.shift = ((vo.shift << 1) & 0x7fffff) | bit;
		}
	}

	private static int noise(int s) {
		return ((s & 0x400000) >> 11) | ((s & 0x100000) >> 10) | ((s & 0x010000) >> 7) | ((s & 0x002000) >> 5)
				| ((s & 0x000800) >> 4) | ((s & 0x000080) >> 1) | ((s & 0x000010) << 1) | ((s & 0x000004) << 2);
	}

	private int waveform(Voice vo, Voice source) {
		int ctrl = vo.ctrl;
		int sel = ctrl & 0xf0;
		if (sel == 0) {
			return 0;
		}
		int w = 0xfff;
		if ((sel & 0x10) != 0) {
			int msb = vo.acc & 0x800000;
			if ((ctrl & 4) != 0) {
				msb ^= source.acc & 0x800000;
			}
			int t = (msb != 0 ? ~vo.acc : vo.acc) >> 11;
			w &= t & 0xfff;
		}
		if ((sel & 0x20) != 0) {
			w &= vo.acc >> 12;
		}
		if ((sel & 0x40) != 0) {
			w &= ((vo.acc >> 12) >= vo.pw || (ctrl & 8) != 0) ? 0xfff : 0;
		}
		if ((sel & 0x80) != 0) {
			w &= noise(vo.shift);
		}
		return w;
	}

	/**
	 * Run the chip.
	 * @param cycles number of system clock cycles
	 */
	public void clock(int cycles) {
		for (int n = 0; n < cycles; n++) {
			for (var vo : v) {
				clockOscillator(vo);
				clockEnvelope(vo);
			}
			for (int i = 0; i < 3; i++) {
				if ((v[i].ctrl & 2) != 0 && v[(i + 2) % 3].msbRising) {
					v[i].acc = 0;
				}
			}
			if ((++cycleCounter & 3) == 0) {
				mixStep();
			}
		}
	}

	private void mixStep() {
		double direct = 0;
		double filtered = 0;
		for (int i = 0; i < 3; i++) {
			var vo = v[i];
			vo.wave = waveform(vo, v[(i + 2) % 3]);
			if ((muteMask & (1 << i)) != 0) {
				continue;
			}
			double out = (vo.wave - 0x800) * vo.env;
			if ((filterRoute & (1 << i)) != 0 && filterEnabled) {
				filtered += out;
			} else if (!(i == 2 && voice3Off)) {
				direct += out;
			}
		}
		double fo = 0;
		if (filterEnabled) {
			double in = filtered / 522240.0;
			lp += fCoef * bp;
			hp = in - lp - damping * bp;
			bp += fCoef * hp;
			if ((mode & 1) != 0) {
				fo += lp;
			}
			if ((mode & 2) != 0) {
				fo += bp;
			}
			if ((mode & 4) != 0) {
				fo += hp;
			}
		} else {
			direct += filtered;
		}
		double dc = model == Model.MOS6581 ? 0.5 : 0.12;
		double total = (direct / 522240.0 + fo + dc) * (volume / 15.0) / 3.0;
		sum += total;
		count++;
	}

	/** @return average output since the previous call, roughly -1..1 */
	public double takeSample() {
		if (count > 0) {
			last = sum / count;
			sum = 0;
			count = 0;
		}
		return last;
	}
}
