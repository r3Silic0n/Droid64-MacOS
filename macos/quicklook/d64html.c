/*
 * DroiD64 Quick Look preview: the directory of a Commodore disk image as an HTML page that looks
 * like the C64 screen after LOAD"$",8 and LIST: Commodore character set (the DroiD64 font, embedded),
 * 40 columns, 2x pixels, blue screen with the light blue border.
 * D64 (35/40 tracks, with or without error info), D71, D81, D80, D82.
 */
#include <stddef.h>
#include <stdint.h>
#include "d64html.h"
#include "font_b64.h"

#define SCALE 2
#define CHAR_PX (8 * SCALE)
#define COLUMNS 28	/* the longest directory line: 5 + 18 + 1 + 3 + "<" */
#define BORDER_X 16
#define BORDER_Y 16

typedef struct {
	char *p;
	size_t len, cap;
} out_t;

static void put(out_t *o, const char *s) {
	while (*s) {
		if (o->len < o->cap) o->p[o->len] = *s;
		o->len++;
		s++;
	}
}

/* PETSCII as shown in a quoted directory listing -> screen code (as DroiD64 does it) */
static unsigned screen_code(uint8_t b) {
	if (b < 0x20) return b + 0x80u;		/* control codes show reversed (quote mode) */
	if (b < 0x40) return b;
	if (b < 0x60) return b - 0x40u;
	if (b < 0x80) return b - 0x20u;
	if (b < 0xa0) return b + 0x40u;
	if (b < 0xc0) return b - 0x40u;
	if (b < 0xff) return b - 0x80u;
	return 0x5e;
}

/* one character cell: the glyph of screen code sc (0-255, 128+ reversed) at U+E000+sc in the embedded ROM font */
static void put_sc(out_t *o, unsigned sc) {
	unsigned c = 0xe000 + (sc & 0xff);
	char t[4] = { (char) (0xe0 | (c >> 12)), (char) (0x80 | ((c >> 6) & 0x3f)), (char) (0x80 | (c & 0x3f)), 0 };
	put(o, t);
}

static void put_pet(out_t *o, uint8_t petscii, int reverse) {
	put_sc(o, screen_code(petscii) ^ (reverse ? 0x80 : 0));
}

/* plain text (upper case letters, digits, punctuation) in the C64 font */
static void put_text(out_t *o, const char *s, int reverse) {
	for (; *s; s++) {
		if (*s == '\n') put(o, "\n");
		else put_pet(o, (uint8_t) *s, reverse);
	}
}

static void put_num(out_t *o, unsigned n) {
	char t[12];
	int i = 11;
	t[i] = 0;
	do { t[--i] = (char) ('0' + n % 10); n /= 10; } while (n);
	put_text(o, t + i, 0);
}

static int num_len(unsigned n) {
	int l = 1;
	while (n >= 10) { n /= 10; l++; }
	return l;
}

/* ---- image geometry ---- */

enum { K_NONE, K_D64, K_D71, K_D81, K_D80, K_D82 };

static int sectors(int kind, int t) {
	switch (kind) {
	case K_D64:
		return t <= 17 ? 21 : t <= 24 ? 19 : t <= 30 ? 18 : 17;
	case K_D71:
		return sectors(K_D64, t > 35 ? t - 35 : t);
	case K_D81:
		return 40;
	case K_D80:
	case K_D82:
		if (t > 77) t -= 77;
		return t <= 39 ? 29 : t <= 53 ? 27 : t <= 64 ? 25 : 23;
	}
	return 0;
}

static int tracks(int kind, size_t size) {
	switch (kind) {
	case K_D64: return size >= 196608 ? 40 : 35;
	case K_D71: return 70;
	case K_D81: return 80;
	case K_D80: return 77;
	case K_D82: return 154;
	}
	return 0;
}

static long offset(int kind, size_t size, int t, int s) {
	if (t < 1 || t > tracks(kind, size) || s < 0 || s >= sectors(kind, t)) return -1;
	long o = 0;
	for (int i = 1; i < t; i++) o += sectors(kind, i);
	o = (o + s) * 256;
	return o + 256 <= (long) size ? o : -1;
}

static int detect(size_t size) {
	switch (size) {
	case 174848: case 175531: case 196608: case 197376: return K_D64;
	case 349696: case 351062: return K_D71;
	case 819200: case 822400: return K_D81;
	case 533248: return K_D80;
	case 1066496: return K_D82;
	}
	return K_NONE;
}

/* blocks free as the DOS shows them */
static unsigned blocks_free(int kind, const uint8_t *d, size_t size) {
	unsigned f = 0;
	long b;
	switch (kind) {
	case K_D64:
	case K_D71:
		b = offset(kind, size, 18, 0);
		for (int t = 1; t <= 35; t++) if (t != 18) f += d[b + 4 * t];
		if (kind == K_D71) for (int t = 36; t <= 70; t++) if (t != 53) f += d[b + 0xdd + t - 36];
		break;
	case K_D81:
		for (int t = 1; t <= 80; t++) {
			if (t == 40) continue;
			b = offset(kind, size, 40, t <= 40 ? 1 : 2);
			f += d[b + 0x10 + 6 * ((t - 1) % 40)];
		}
		break;
	case K_D80:
	case K_D82: {
		int last = kind == K_D80 ? 77 : 154;
		static const int bam_sec[] = { 0, 3, 6, 9 };
		for (int t = 1; t <= last; t++) {
			if (t == 38 || t == 39) continue;
			b = offset(kind, size, 38, bam_sec[(t - 1) / 50]);
			if (b >= 0) f += d[b + 6 + 5 * ((t - 1) % 50)];
		}
		break;
	}
	}
	return f;
}

/* ---- the page ---- */

static void header_field(out_t *o, const uint8_t *p, int n) {
	for (int i = 0; i < n; i++) put_pet(o, p[i] == 0xa0 ? 0x20 : p[i], 1);
}

/* @return number of text lines written */
static int listing(out_t *o, const uint8_t *d, size_t size, int kind) {
	int lines = 0;
	long h;
	const uint8_t *name, *id;
	int dt, ds;
	if (kind == K_D81) {
		h = offset(kind, size, 40, 0);
		name = d + h + 0x04;
		id = d + h + 0x16;
		dt = 40;
		ds = 3;
	} else if (kind == K_D80 || kind == K_D82) {
		h = offset(kind, size, 39, 0);
		name = d + h + 0x06;
		id = d + h + 0x18;
		dt = 39;
		ds = 1;
	} else {
		h = offset(kind, size, 18, 0);
		name = d + h + 0x90;
		id = d + h + 0xa2;
		dt = 18;
		ds = 1;
	}
	/* 0 "DISK NAME       " ID 2A  (reversed from the quote on, as on the C64) */
	put_text(o, "0 ", 0);
	put_text(o, "\"", 1);
	header_field(o, name, 16);
	put_text(o, "\" ", 1);
	header_field(o, id, 5);
	put(o, "\n");
	lines++;

	static uint8_t seen[4200];	/* sector numbers already listed (a looping directory chain stops) */
	for (unsigned i = 0; i < sizeof seen; i++) seen[i] = 0;
	int guard = 0;
	while (dt != 0 && guard++ < 400) {
		long p = offset(kind, size, dt, ds);
		if (p < 0) break;
		long key = p / 256;
		if (key >= (long) sizeof seen || seen[key]) break;
		seen[key] = 1;
		for (int e = 0; e < 8; e++) {
			const uint8_t *en = d + p + e * 32;
			uint8_t type = en[2];
			if (type == 0) continue;	/* scratched */
			unsigned blocks = en[30] | en[31] << 8;
			put_num(o, blocks);
			for (int k = num_len(blocks); k < 5; k++) put_text(o, " ", 0);
			/* "NAME" then the rest of the 16 characters after the first shifted space */
			int col = 1;
			put_text(o, "\"", 0);
			int k = 0;
			while (k < 16 && en[5 + k] != 0xa0) { put_pet(o, en[5 + k], 0); k++; col++; }
			put_text(o, "\"", 0);
			col++;
			for (; k < 16; k++) { put_pet(o, en[5 + k] == 0xa0 ? 0x20 : en[5 + k], 0); col++; }
			for (; col < 18; col++) put_text(o, " ", 0);
			put_text(o, (type & 0x80) ? " " : "*", 0);
			static const char *names[] = { "DEL", "SEQ", "PRG", "USR", "REL", "CBM" };
			int t = type & 7;
			put_text(o, t <= 5 ? names[t] : "???", 0);
			if (type & 0x40) put_text(o, "<", 0);
			put(o, "\n");
			lines++;
		}
		dt = d[p];
		ds = d[p + 1];
	}
	put_num(o, blocks_free(kind, d, size));
	put_text(o, " BLOCKS FREE.\nREADY.\n", 0);
	put(o, "<span class=\"c\">");
	put_sc(o, 0xa0);			/* the cursor: a reversed space, blinking */
	put(o, "</span>\n");
	return lines + 3;
}

int d64_html(const uint8_t *d, size_t size, char *buf, size_t cap, int *width, int *height) {
	int kind = detect(size);
	if (kind == K_NONE) return -1;
	out_t o = { buf, 0, cap };
	put(&o, "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
		"@font-face{font-family:'DroiD64 C64';src:url(data:font/ttf;base64,");
	put(&o, FONT_B64);
	put(&o, ") format('truetype')}"
		"html,body{margin:0;background:#6C5EB5}"
		"body{display:flex;justify-content:center}"
		"#s{width:448px;margin:16px 0;background:#352879;color:#6C5EB5;"
		"font:16px/16px 'DroiD64 C64',monospace;white-space:pre;overflow:hidden;"
		"-webkit-font-smoothing:none;font-kerning:none;font-variant-ligatures:none}"
		"@keyframes k{0%,49%{opacity:1}50%,100%{opacity:0}}"
		".c{animation:k 1.06s steps(1) infinite}"
		"</style></head><body><div id=\"s\">");
	int lines = listing(&o, d, size, kind);
	put(&o, "</div></body></html>");
	if (o.len < cap) buf[o.len] = 0;
	*width = COLUMNS * CHAR_PX + 2 * BORDER_X;
	*height = lines * CHAR_PX + 2 * BORDER_Y;
	return (int) o.len;
}
