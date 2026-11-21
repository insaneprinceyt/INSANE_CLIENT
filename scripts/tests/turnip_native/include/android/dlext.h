#ifndef TEST_ANDROID_DLEXT_H
#define TEST_ANDROID_DLEXT_H
#include <stddef.h>
#include <stdint.h>
struct android_namespace_t;
typedef struct {
    uint64_t flags;
    void* reserved_addr;
    size_t reserved_size;
    int relro_fd;
    int library_fd;
    int64_t library_fd_offset;
    struct android_namespace_t* library_namespace;
} android_dlextinfo;
#define ANDROID_DLEXT_USE_NAMESPACE 0x200
void* android_dlopen_ext(const char*, int, const android_dlextinfo*);
#endif
