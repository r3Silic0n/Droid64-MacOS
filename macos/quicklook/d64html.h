#include <stddef.h>
#include <stdint.h>
/* @return length of the page (may exceed cap: call again with a bigger buffer), -1 if not a disk image */
int d64_html(const uint8_t *d, size_t size, char *buf, size_t cap, int *width, int *height);
