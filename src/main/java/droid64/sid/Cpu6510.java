package droid64.sid;

/**
 * MOS 6510 CPU emulation (documented and the commonly used undocumented opcodes), used to run SID music routines.
 */
public final class Cpu6510 {

	/** Memory access. */
	public interface Bus {
		int read(int address);

		void write(int address, int value);
	}

	private static final int C = 1;
	private static final int Z = 2;
	private static final int I = 4;
	private static final int D = 8;
	private static final int B = 16;
	private static final int U = 32;
	private static final int V = 64;
	private static final int N = 128;

	public int a;
	public int x;
	public int y;
	public int sp = 0xff;
	public int pc;
	private int p = U | I;
	/** Set when a JAM opcode was executed. */
	public boolean jammed;

	private final Bus bus;
	private int cycles;

	public Cpu6510(Bus bus) {
		this.bus = bus;
	}

	public void setFlags(int flags) {
		p = (flags | U) & 0xff;
	}

	public int getFlags() {
		return p;
	}

	private int rd(int addr) {
		return bus.read(addr & 0xffff) & 0xff;
	}

	private void wr(int addr, int v) {
		bus.write(addr & 0xffff, v & 0xff);
	}

	private int fetch() {
		int v = rd(pc);
		pc = (pc + 1) & 0xffff;
		return v;
	}

	private int fetch16() {
		int lo = fetch();
		return lo | fetch() << 8;
	}

	private void push(int v) {
		wr(0x100 | sp, v);
		sp = (sp - 1) & 0xff;
	}

	private int pull() {
		sp = (sp + 1) & 0xff;
		return rd(0x100 | sp);
	}

	private void setZN(int v) {
		p = (p & ~(Z | N)) | (v == 0 ? Z : 0) | (v & N);
	}

	/** Pushes PC and flags like an interrupt and jumps to the given address. */
	public void interrupt(int vector) {
		push(pc >> 8);
		push(pc & 0xff);
		push(p & ~B);
		p |= I;
		pc = vector;
	}

	// effective address helpers; pageCross is set for indexed modes
	private boolean pageCross;

	private int zp() {
		return fetch();
	}

	private int zpX() {
		return (fetch() + x) & 0xff;
	}

	private int zpY() {
		return (fetch() + y) & 0xff;
	}

	private int abs() {
		return fetch16();
	}

	private int absX() {
		int base = fetch16();
		int ea = (base + x) & 0xffff;
		pageCross = (base & 0xff00) != (ea & 0xff00);
		return ea;
	}

	private int absY() {
		int base = fetch16();
		int ea = (base + y) & 0xffff;
		pageCross = (base & 0xff00) != (ea & 0xff00);
		return ea;
	}

	private int indX() {
		int zpa = (fetch() + x) & 0xff;
		return rd(zpa) | rd((zpa + 1) & 0xff) << 8;
	}

	private int indY() {
		int zpa = fetch();
		int base = rd(zpa) | rd((zpa + 1) & 0xff) << 8;
		int ea = (base + y) & 0xffff;
		pageCross = (base & 0xff00) != (ea & 0xff00);
		return ea;
	}

	/** Address for addressing mode bbb of the group "01" instructions; returns -1 for immediate. */
	private int addr01(int bbb) {
		switch (bbb) {
		case 0: return indX();
		case 1: return zp();
		case 2: return -1;
		case 3: return abs();
		case 4: return indY();
		case 5: return zpX();
		case 6: return absY();
		default: return absX();
		}
	}

	private void adc(int m) {
		if ((p & D) != 0) {
			int lo = (a & 0x0f) + (m & 0x0f) + (p & C);
			if (lo > 9) {
				lo = ((lo + 6) & 0x0f) + 0x10;
			}
			int r = (a & 0xf0) + (m & 0xf0) + lo;
			int bin = a + m + (p & C);
			p = (p & ~(Z | N | V | C)) | ((bin & 0xff) == 0 ? Z : 0) | (r & N)
					| ((~(a ^ m) & (a ^ r) & 0x80) != 0 ? V : 0);
			if (r >= 0xa0) {
				r += 0x60;
			}
			if (r > 0xff) {
				p |= C;
			}
			a = r & 0xff;
		} else {
			int r = a + m + (p & C);
			p = (p & ~(C | V)) | (r > 0xff ? C : 0) | ((~(a ^ m) & (a ^ r) & 0x80) != 0 ? V : 0);
			a = r & 0xff;
			setZN(a);
		}
	}

	private void sbc(int m) {
		if ((p & D) != 0) {
			int bin = a - m - (1 - (p & C));
			int lo = (a & 0x0f) - (m & 0x0f) - (1 - (p & C));
			int hi = (a >> 4) - (m >> 4);
			if (lo < 0) {
				lo -= 6;
				hi--;
			}
			if (hi < 0) {
				hi -= 6;
			}
			int flagsV = ((a ^ m) & (a ^ bin) & 0x80) != 0 ? V : 0;
			p = (p & ~(C | V | Z | N)) | (bin >= 0 ? C : 0) | flagsV | ((bin & 0xff) == 0 ? Z : 0) | (bin & N);
			a = ((hi << 4) | (lo & 0x0f)) & 0xff;
		} else {
			int r = a - m - (1 - (p & C));
			p = (p & ~(C | V)) | (r >= 0 ? C : 0) | (((a ^ m) & (a ^ r) & 0x80) != 0 ? V : 0);
			a = r & 0xff;
			setZN(a);
		}
	}

	private void cmp(int reg, int m) {
		int r = reg - m;
		p = (p & ~C) | (r >= 0 ? C : 0);
		setZN(r & 0xff);
	}

	private int asl(int v) {
		p = (p & ~C) | (v >> 7);
		v = (v << 1) & 0xff;
		setZN(v);
		return v;
	}

	private int lsr(int v) {
		p = (p & ~C) | (v & 1);
		v >>= 1;
		setZN(v);
		return v;
	}

	private int rol(int v) {
		int c = p & C;
		p = (p & ~C) | (v >> 7);
		v = ((v << 1) | c) & 0xff;
		setZN(v);
		return v;
	}

	private int ror(int v) {
		int c = p & C;
		p = (p & ~C) | (v & 1);
		v = (v >> 1) | (c << 7);
		setZN(v);
		return v;
	}

	private void branch(boolean cond) {
		int off = (byte) fetch();
		cycles += 2;
		if (cond) {
			int np = (pc + off) & 0xffff;
			cycles += 1 + ((np & 0xff00) != (pc & 0xff00) ? 1 : 0);
			pc = np;
		}
	}

	private static final int[] BASE_CYCLES_01 = { 6, 3, 2, 4, 5, 4, 4, 4 };

	/**
	 * Execute one instruction.
	 * @return number of cycles used
	 */
	public int step() {
		cycles = 0;
		pageCross = false;
		int op = fetch();
		int aaa = op >> 5;
		int bbb = (op >> 2) & 7;
		int cc = op & 3;
		switch (cc) {
		case 1:
			group01(op, aaa, bbb);
			break;
		case 2:
			group10(op, aaa, bbb);
			break;
		case 0:
			group00(op, aaa, bbb);
			break;
		default:
			group11(op, aaa, bbb);
			break;
		}
		return cycles;
	}

	private void group01(int op, int aaa, int bbb) {
		int ea = addr01(bbb);
		cycles = BASE_CYCLES_01[bbb] + 0;
		if (bbb == 0) {
			cycles = 6;
		} else if (bbb == 4) {
			cycles = aaa == 4 ? 6 : 5;
		} else if (bbb == 6 || bbb == 7) {
			cycles = aaa == 4 ? 5 : 4;
		} else if (bbb == 5) {
			cycles = 4;
		}
		if (pageCross && aaa != 4 && (bbb == 4 || bbb == 6 || bbb == 7)) {
			cycles++;
		}
		if (aaa == 4) {
			if (ea < 0) {
				return;		// 0x89 NOP #imm
			}
			wr(ea, a);
			return;
		}
		int m;
		if (ea < 0) {
			m = fetch();
			cycles = 2;
		} else {
			m = rd(ea);
		}
		switch (aaa) {
		case 0:
			a |= m;
			setZN(a);
			break;
		case 1:
			a &= m;
			setZN(a);
			break;
		case 2:
			a ^= m;
			setZN(a);
			break;
		case 3:
			adc(m);
			break;
		case 5:
			a = m;
			setZN(a);
			break;
		case 6:
			cmp(a, m);
			break;
		default:
			sbc(m);
			break;
		}
	}

	private void group10(int op, int aaa, int bbb) {
		switch (op) {
		case 0x8a:
			a = x;
			setZN(a);
			cycles = 2;
			return;
		case 0x9a:
			sp = x;
			cycles = 2;
			return;
		case 0xaa:
			x = a;
			setZN(x);
			cycles = 2;
			return;
		case 0xba:
			x = sp;
			setZN(x);
			cycles = 2;
			return;
		case 0xca:
			x = (x - 1) & 0xff;
			setZN(x);
			cycles = 2;
			return;
		case 0xea:
		case 0x1a:
		case 0x3a:
		case 0x5a:
		case 0x7a:
		case 0xda:
		case 0xfa:
			cycles = 2;
			return;
		default:
			break;
		}
		if (bbb == 0) {
			if (op == 0xa2) {
				x = fetch();
				setZN(x);
			} else if (op == 0x82 || op == 0xc2 || op == 0xe2) {
				fetch();
			} else {
				jammed = true;
			}
			cycles = 2;
			return;
		}
		if (bbb == 2) {
			// accumulator shifts
			switch (aaa) {
			case 0:
				a = asl(a);
				break;
			case 1:
				a = rol(a);
				break;
			case 2:
				a = lsr(a);
				break;
			default:
				a = ror(a);
				break;
			}
			cycles = 2;
			return;
		}
		boolean useY = aaa == 4 || aaa == 5;
		int ea;
		if (aaa == 4 && bbb == 7) {
			fetch16();		// SHX abs,Y (unstable)
			cycles = 5;
			return;
		}
		switch (bbb) {
		case 1:
			ea = zp();
			cycles = aaa >= 6 || aaa < 4 ? 5 : 3;
			break;
		case 3:
			ea = abs();
			cycles = aaa >= 6 || aaa < 4 ? 6 : 4;
			break;
		case 5:
			ea = useY ? zpY() : zpX();
			cycles = aaa >= 6 || aaa < 4 ? 6 : 4;
			break;
		default:
			ea = useY ? absY() : absX();
			cycles = aaa >= 6 || aaa < 4 ? 7 : 4;
			if (pageCross && aaa == 5) {
				cycles++;
			}
			break;
		}
		switch (aaa) {
		case 0:
			wr(ea, asl(rd(ea)));
			break;
		case 1:
			wr(ea, rol(rd(ea)));
			break;
		case 2:
			wr(ea, lsr(rd(ea)));
			break;
		case 3:
			wr(ea, ror(rd(ea)));
			break;
		case 4:
			wr(ea, x);
			break;
		case 5:
			x = rd(ea);
			setZN(x);
			break;
		case 6: {
			int v = (rd(ea) - 1) & 0xff;
			wr(ea, v);
			setZN(v);
			break;
		}
		default: {
			int v = (rd(ea) + 1) & 0xff;
			wr(ea, v);
			setZN(v);
			break;
		}
		}
	}

	private void group00(int op, int aaa, int bbb) {
		switch (bbb) {
		case 2:
			implied00(aaa);
			return;
		case 4:
			switch (aaa) {
			case 0:
				branch((p & N) == 0);
				break;
			case 1:
				branch((p & N) != 0);
				break;
			case 2:
				branch((p & V) == 0);
				break;
			case 3:
				branch((p & V) != 0);
				break;
			case 4:
				branch((p & C) == 0);
				break;
			case 5:
				branch((p & C) != 0);
				break;
			case 6:
				branch((p & Z) == 0);
				break;
			default:
				branch((p & Z) != 0);
				break;
			}
			return;
		case 6:
			switch (aaa) {
			case 0:
				p &= ~C;
				break;
			case 1:
				p |= C;
				break;
			case 2:
				p &= ~I;
				break;
			case 3:
				p |= I;
				break;
			case 4:
				a = y;
				setZN(a);
				break;
			case 5:
				p &= ~V;
				break;
			case 6:
				p &= ~D;
				break;
			default:
				p |= D;
				break;
			}
			cycles = 2;
			return;
		default:
			break;
		}
		if (bbb == 0 && aaa < 4) {
			switch (aaa) {
			case 0:		// BRK
				fetch();
				push(pc >> 8);
				push(pc & 0xff);
				push(p | B);
				p |= I;
				pc = rd(0xfffe) | rd(0xffff) << 8;
				cycles = 7;
				return;
			case 1: {	// JSR
				int target = fetch16();
				int ret = (pc - 1) & 0xffff;
				push(ret >> 8);
				push(ret & 0xff);
				pc = target;
				cycles = 6;
				return;
			}
			case 2:		// RTI
				p = (pull() & ~B) | U;
				pc = pull();
				pc |= pull() << 8;
				cycles = 6;
				return;
			default:	// RTS
				pc = pull();
				pc |= pull() << 8;
				pc = (pc + 1) & 0xffff;
				cycles = 6;
				return;
			}
		}
		if (bbb == 0) {
			// LDY/CPY/CPX #imm, 0x80 NOP #imm
			int m = fetch();
			cycles = 2;
			switch (aaa) {
			case 5:
				y = m;
				setZN(y);
				break;
			case 6:
				cmp(y, m);
				break;
			case 7:
				cmp(x, m);
				break;
			default:
				break;
			}
			return;
		}
		int ea;
		switch (bbb) {
		case 1:
			ea = zp();
			cycles = 3;
			break;
		case 3:
			ea = abs();
			cycles = 4;
			if (aaa == 2) {		// JMP abs
				pc = ea;
				cycles = 3;
				return;
			}
			if (aaa == 3) {		// JMP (ind) with the 6502 page wrap bug
				int lo = rd(ea);
				int hi = rd((ea & 0xff00) | ((ea + 1) & 0xff));
				pc = lo | hi << 8;
				cycles = 5;
				return;
			}
			break;
		case 5:
			ea = zpX();
			cycles = 4;
			break;
		default:
			ea = absX();
			cycles = 4 + (pageCross ? 1 : 0);
			break;
		}
		if (aaa == 1 && (bbb == 5 || bbb == 7)) {
			rd(ea);
			return;
		}
		switch (aaa) {
		case 1: {		// BIT
			int m = rd(ea);
			p = (p & ~(Z | N | V)) | ((a & m) == 0 ? Z : 0) | (m & (N | V));
			break;
		}
		case 4:
			wr(ea, y);
			break;
		case 5:
			y = rd(ea);
			setZN(y);
			break;
		case 6:
			cmp(y, rd(ea));
			break;
		case 7:
			cmp(x, rd(ea));
			break;
		default:
			rd(ea);		// NOP
			break;
		}
	}

	private void implied00(int aaa) {
		cycles = 2;
		switch (aaa) {
		case 0:
			push(p | B | U);
			cycles = 3;
			break;
		case 1:
			p = (pull() & ~B) | U;
			cycles = 4;
			break;
		case 2:
			push(a);
			cycles = 3;
			break;
		case 3:
			a = pull();
			setZN(a);
			cycles = 4;
			break;
		case 4:
			y = (y - 1) & 0xff;
			setZN(y);
			break;
		case 5:
			y = a;
			setZN(y);
			break;
		case 6:
			y = (y + 1) & 0xff;
			setZN(y);
			break;
		default:
			x = (x + 1) & 0xff;
			setZN(x);
			break;
		}
	}

	private void group11(int op, int aaa, int bbb) {
		int ea;
		switch (op) {
		case 0x0b:
		case 0x2b: {
			a &= fetch();
			setZN(a);
			p = (p & ~C) | ((a & N) != 0 ? C : 0);
			cycles = 2;
			return;
		}
		case 0x4b: {
			a &= fetch();
			a = lsr(a);
			cycles = 2;
			return;
		}
		case 0x6b: {
			a &= fetch();
			int c = p & C;
			a = (a >> 1) | (c << 7);
			setZN(a);
			p = (p & ~(C | V)) | ((a & 0x40) != 0 ? C : 0) | ((((a >> 6) ^ (a >> 5)) & 1) != 0 ? V : 0);
			cycles = 2;
			return;
		}
		case 0x8b: {
			a = x & fetch();
			setZN(a);
			cycles = 2;
			return;
		}
		case 0xab: {
			a = x = fetch();
			setZN(a);
			cycles = 2;
			return;
		}
		case 0xcb: {
			int m = fetch();
			int r = (a & x) - m;
			p = (p & ~C) | (r >= 0 ? C : 0);
			x = r & 0xff;
			setZN(x);
			cycles = 2;
			return;
		}
		case 0xeb:
			sbc(fetch());
			cycles = 2;
			return;
		case 0x9b: case 0x9c: case 0x9e: case 0x9f: case 0xbb:
			// rarely used unstable instructions: consume operand only
			if (op == 0x9c || op == 0x9e || op == 0x9f || op == 0x9b || op == 0xbb) {
				fetch16();
			}
			cycles = 5;
			return;
		default:
			break;
		}
		switch (bbb) {
		case 0:
			ea = indX();
			cycles = 8;
			break;
		case 1:
			ea = zp();
			cycles = 5;
			break;
		case 3:
			ea = abs();
			cycles = 6;
			break;
		case 4:
			ea = indY();
			cycles = 8;
			break;
		case 5:
			ea = (aaa == 4 || aaa == 5) ? zpY() : zpX();
			cycles = 6;
			break;
		case 6:
			ea = absY();
			cycles = 7;
			break;
		default:
			ea = (aaa == 5) ? absY() : absX();
			cycles = 7;
			break;
		}
		switch (aaa) {
		case 0: {	// SLO
			int v = asl(rd(ea));
			wr(ea, v);
			a |= v;
			setZN(a);
			break;
		}
		case 1: {	// RLA
			int v = rol(rd(ea));
			wr(ea, v);
			a &= v;
			setZN(a);
			break;
		}
		case 2: {	// SRE
			int v = lsr(rd(ea));
			wr(ea, v);
			a ^= v;
			setZN(a);
			break;
		}
		case 3: {	// RRA
			int v = ror(rd(ea));
			wr(ea, v);
			adc(v);
			break;
		}
		case 4:		// SAX
			wr(ea, a & x);
			break;
		case 5: {	// LAX
			a = x = rd(ea);
			setZN(a);
			break;
		}
		case 6: {	// DCP
			int v = (rd(ea) - 1) & 0xff;
			wr(ea, v);
			cmp(a, v);
			break;
		}
		default: {	// ISC
			int v = (rd(ea) + 1) & 0xff;
			wr(ea, v);
			sbc(v);
			break;
		}
		}
	}
}
