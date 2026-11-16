/*
 * DroiD64 Quick Look preview extension (macOS, arm64), plain C on the Objective-C runtime:
 * the class DroiD64PreviewProvider (a QLPreviewProvider) is created at load time and answers
 * providePreviewForFileRequest:completionHandler: with the HTML page from d64_html(), sized to
 * the listing (fixed width, the height follows the number of files).
 */
#include <stddef.h>
#include <stdint.h>
#include "d64html.h"

typedef void *id;
typedef void *SEL;
typedef void *Class;
typedef void *Protocol;
typedef void (*IMP)(void);
typedef struct { double width, height; } CGSize;

extern id objc_msgSend(void);	/* cast to the right type at each call */
extern SEL sel_registerName(const char *);
extern Class objc_getClass(const char *);
extern Protocol *objc_getProtocol(const char *);
extern Class objc_allocateClassPair(Class, const char *, size_t);
extern void objc_registerClassPair(Class);
extern int class_addMethod(Class, SEL, IMP, const char *);
extern int class_addProtocol(Class, Protocol *);
extern void *dlopen(const char *, int);
extern int open(const char *, int, ...);
extern long read(int, void *, unsigned long);
extern int close(int);
extern void *malloc(unsigned long);
extern void *realloc(void *, unsigned long);
extern void free(void *);
extern void *_Block_copy(const void *);
extern void *_NSConcreteStackBlock[32];
extern int NSExtensionMain(int, char **);

#define MSG(ret, ...) ((ret (*)(id, SEL, ##__VA_ARGS__)) objc_msgSend)

static id nsstr(const char *s) {
	return MSG(id, const char *)(objc_getClass("NSString"), sel_registerName("stringWithUTF8String:"), s);
}

static id make_error(const char *what) {
	return MSG(id, id, long, id)(objc_getClass("NSError"), sel_registerName("errorWithDomain:code:userInfo:"), nsstr(what), 1, 0);
}

/* the data block given to QLPreviewReply: returns the prepared NSData */
struct data_block {
	void *isa;
	int flags;
	int reserved;
	id (*invoke)(struct data_block *, id, id *);
	const void *descriptor;
	id data;
};

static id data_block_invoke(struct data_block *b, id reply, id *error) {
	(void) reply;
	(void) error;
	return b->data;
}

static const struct {
	unsigned long reserved, size;
	const char *signature;
} data_block_descriptor = { 0, sizeof(struct data_block), "@24@?0@8^@16" };

/* call a block of type void (^)(QLPreviewReply *, NSError *) */
struct handler_block {
	void *isa;
	int flags;
	int reserved;
	void (*invoke)(void *, id, id);
};

static unsigned char *read_file(const char *path, size_t *size) {
	int fd = open(path, 0);
	if (fd < 0) return 0;
	size_t cap = 1 << 20, n = 0;
	unsigned char *buf = malloc(cap);
	for (;;) {
		if (n == cap) {
			if (cap >= (8u << 20)) break;	/* no disk image is that big */
			cap *= 2;
			buf = realloc(buf, cap);
		}
		long r = read(fd, buf + n, cap - n);
		if (r <= 0) break;
		n += (size_t) r;
	}
	close(fd);
	*size = n;
	return buf;
}

static void provide(id self, SEL cmd, id request, struct handler_block *handler) {
	(void) self;
	(void) cmd;
	id url = MSG(id)(request, sel_registerName("fileURL"));
	const char *path = MSG(const char *)(url, sel_registerName("fileSystemRepresentation"));
	size_t size = 0;
	unsigned char *img = path ? read_file(path, &size) : 0;
	if (!img) {
		handler->invoke(handler, 0, make_error("DroiD64Preview: cannot read the file"));
		return;
	}
	int w = 0, h = 0;
	size_t cap = 256 * 1024;
	char *html = malloc(cap);
	int len = d64_html(img, size, html, cap, &w, &h);
	if (len >= 0 && (size_t) len >= cap) {
		cap = (size_t) len + 1;
		html = realloc(html, cap);
		len = d64_html(img, size, html, cap, &w, &h);
	}
	free(img);
	if (len < 0) {
		free(html);
		handler->invoke(handler, 0, make_error("DroiD64Preview: not a disk image"));
		return;
	}
	id data = MSG(id, const void *, unsigned long)(objc_getClass("NSData"), sel_registerName("dataWithBytes:length:"), html, (unsigned long) len);
	free(html);
	MSG(id)(data, sel_registerName("retain"));
	id type = MSG(id, id)(objc_getClass("UTType"), sel_registerName("typeWithIdentifier:"), nsstr("public.html"));
	struct data_block stack = { _NSConcreteStackBlock, 1 << 30, 0, data_block_invoke, &data_block_descriptor, data };
	void *blk = _Block_copy(&stack);
	CGSize sz = { (double) w, (double) h };
	id reply = MSG(id)(objc_getClass("QLPreviewReply"), sel_registerName("alloc"));
	reply = MSG(id, id, CGSize, void *)(reply, sel_registerName("initWithDataOfContentType:contentSize:dataCreationBlock:"), type, sz, blk);
	handler->invoke(handler, reply, 0);
}

__attribute__((constructor)) static void setup(void) {
	dlopen("/System/Library/Frameworks/UniformTypeIdentifiers.framework/UniformTypeIdentifiers", 1);
	dlopen("/System/Library/Frameworks/QuickLook.framework/QuickLook", 1);
	dlopen("/System/Library/Frameworks/QuickLookUI.framework/QuickLookUI", 1);
	Class base = objc_getClass("QLPreviewProvider");
	if (!base) base = objc_getClass("NSObject");
	Class c = objc_allocateClassPair(base, "DroiD64PreviewProvider", 0);
	if (!c) return;
	class_addMethod(c, sel_registerName("providePreviewForFileRequest:completionHandler:"), (IMP) provide, "v32@0:8@16@?24");
	Protocol *p = objc_getProtocol("QLPreviewingController");
	if (p) class_addProtocol(c, p);
	objc_registerClassPair(c);
}

int main(int argc, char **argv) {
	return NSExtensionMain(argc, argv);
}
