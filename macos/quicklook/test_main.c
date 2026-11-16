#include <stdio.h>
#include <stdlib.h>
#include "d64html.h"
int main(int c, char **v) {
	FILE *f = fopen(v[1], "rb"); static uint8_t d[1200000]; size_t n = fread(d, 1, sizeof d, f); fclose(f);
	static char buf[400000]; int w, h; int len = d64_html(d, n, buf, sizeof buf, &w, &h);
	if (len < 0) { fprintf(stderr, "not an image\n"); return 1; }
	fwrite(buf, 1, len, stdout); fprintf(stderr, "%d bytes, %dx%d\n", len, w, h); return 0;
}
