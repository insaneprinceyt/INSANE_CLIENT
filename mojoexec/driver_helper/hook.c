#include <android/dlext.h>
#include <stdint.h>
#include <stdatomic.h>
#include <string.h>

// These are the public Android APIs, including the three-argument dlopen ABI.
typedef void* (*open_library_fn)(const char*, int, const android_dlextinfo*);
typedef struct android_namespace_t* (*get_namespace_fn)(const char*);
static _Atomic(open_library_fn) open_library;
static _Atomic(get_namespace_fn) get_namespace;
static _Atomic(void*) ready_handle;
static const char* sphal_namespaces[] = {"sphal", "vendor", "default"};

// The owner retains every published handle for the lifetime of this process.
__attribute__((visibility("default"), used))
void app__pojav_linkerhook_pass_handles(void* driver, void* open, void* namespaces) {
    atomic_store(&open_library, (open_library_fn)open);
    atomic_store(&get_namespace, (get_namespace_fn)namespaces);
    atomic_store(&ready_handle, driver);
}

__attribute__((visibility("default"), used))
void* android_dlopen_ext(const char* filename, int flags, const android_dlextinfo* info) {
    void* driver = atomic_load(&ready_handle);
    if (driver && filename && strstr(filename, "vulkan.")) return driver;
    open_library_fn open = atomic_load(&open_library);
    return open ? open(filename, flags, info) : NULL;
}

__attribute__((visibility("default"), used))
void* android_load_sphal_library(const char* filename, int flags) {
    if (!filename) return NULL;
    void* driver = atomic_load(&ready_handle);
    if (driver && strstr(filename, "vulkan.")) return driver;
    open_library_fn open = atomic_load(&open_library);
    get_namespace_fn namespaces = atomic_load(&get_namespace);
    if (!open || !namespaces) return NULL;
    struct android_namespace_t* ns = NULL;
    for (unsigned i = 0; i < sizeof(sphal_namespaces) / sizeof(sphal_namespaces[0]); i++) {
        ns = namespaces(sphal_namespaces[i]);
        if (ns) break;
    }
    if (!ns) return NULL;
    android_dlextinfo info = {0};
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    info.library_namespace = ns;
    return open(filename, flags, &info);
}

// Older Android versions do not export this optional tracing API.
__attribute__((visibility("default"), used)) uint64_t atrace_get_enabled_tags(void) {
    return 0;
}
