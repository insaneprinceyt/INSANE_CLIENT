#define _POSIX_C_SOURCE 200809L
#include <android/dlext.h>
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <dlfcn.h>

void app__pojav_linkerhook_pass_handles(void*, void*, void*);
void* android_load_sphal_library(const char*, int);
static void* platform_open(const char*, int, const android_dlextinfo*);
static void* system_open(const char*, int);
static void* lookup_symbol(void*, const char*);
static int forbid_close(void*);
#define android_dlopen_ext platform_open
#define dlopen system_open
#define dlsym lookup_symbol
#define dlclose forbid_close
#include "vulkan_loader.c"
#undef android_dlopen_ext
#undef dlopen
#undef dlsym
#undef dlclose

const char* mojoexec_native_dir = "/native";
static int api = 34, failure;
static int ns_calls, hook_calls, driver_calls, dl_calls, unique_calls, closes;
static int hook_marker, driver_marker, dl_marker, custom_marker, system_marker, namespace_marker;
#define HOOK ((void*)&hook_marker)
#define DRIVER ((void*)&driver_marker)
#define DL ((void*)&dl_marker)
#define CUSTOM ((void*)&custom_marker)
#define SYSTEM ((void*)&system_marker)
int android_get_device_api_level(void) { return api; }
void mojoexec_make_bigcore_affine(void) { }
bool linker_ns_load(const char* path) {
    assert(!strcmp(path, "/native")); ns_calls++;
    return failure != 1;
}
void* linker_ns_dlopen(const char* name, int flags) {
    (void)flags;
    if (!strcmp(name, "liblinkerhook.so")) { hook_calls++; return failure == 2 ? NULL : HOOK; }
    if (!strcmp(name, "libvulkan_freedreno.so")) { driver_calls++; return failure == 3 ? NULL : DRIVER; }
    if (!strcmp(name, "libdl_android.so")) { dl_calls++; return failure == 4 ? NULL : DL; }
    assert(!strcmp(name, "libmjlvlk.so"));
    return failure == 7 ? NULL : CUSTOM;
}
void* linker_ns_dlopen_unique(const char* cache, const char* name, const char* patch, int flags) {
    (void)flags;
    assert(!strcmp(cache, "/cache") && !strcmp(name, "libvulkan.so") && !strcmp(patch, "libmjlvlk.so"));
    unique_calls++;
    return failure == 6 ? NULL : CUSTOM;
}
static struct android_namespace_t* platform_namespace(const char* name) {
    // A callback can be used after failed setup. Its owner must still be mapped.
    assert(closes == 0 && dl_calls > 0);
    return !strcmp(name, "sphal") ? (struct android_namespace_t*)&namespace_marker : NULL;
}
static void* platform_open(const char* name, int flags, const android_dlextinfo* info) {
    (void)name; (void)flags; (void)info;
    assert(closes == 0);
    return SYSTEM;
}
static void* system_open(const char* name, int flags) {
    (void)flags; assert(!strcmp(name, "libvulkan.so")); return SYSTEM;
}
static void* lookup_symbol(void* handle, const char* name) {
    if (failure == 5) return NULL;
    if (!strcmp(name, "android_get_exported_namespace")) { assert(handle == DL); return platform_namespace; }
    assert(handle == HOOK && !strcmp(name, "app__pojav_linkerhook_pass_handles"));
    return app__pojav_linkerhook_pass_handles;
}
static int forbid_close(void* handle) { (void)handle; closes++; return 0; }
static void enable(bool value) { Java_git_artdeell_mojoexec_MojoExec_setUseTurnip(NULL, NULL, value); }
static void preload(void) { Java_git_artdeell_mojoexec_MojoExec_preloadVulkan(NULL, NULL); }
static void* acquire_thread(void* unused) { (void)unused; assert(mojoexec_acq_vulkan_handle() == CUSTOM); return NULL; }
int main(int argc, char** argv) {
    assert(argc == 2 && setenv("TMPDIR", "/cache", 1) == 0);
    // Keep the failure sentinel linked even when the fixed code correctly has no dlclose.
    int (*close_sentinel)(void*) = forbid_close; assert(close_sentinel != NULL);
    const char* scenario = argv[1];
    if (!strcmp(scenario, "disabled")) {
        preload(); assert(mojoexec_acq_vulkan_handle() == SYSTEM && ns_calls == 0);
    } else if (!strcmp(scenario, "api27")) {
        api = 27; enable(true); preload();
        assert(mojoexec_acq_vulkan_handle() == SYSTEM && ns_calls == 0);
    } else if (!strcmp(scenario, "missing-cache")) {
        enable(true); assert(unsetenv("TMPDIR") == 0); preload();
        assert(mojoexec_acq_vulkan_handle() == SYSTEM && ns_calls == 0);
    } else if (!strcmp(scenario, "state-transitions")) {
        enable(true); preload(); assert(mojoexec_acq_vulkan_handle() == CUSTOM);
        assert(android_load_sphal_library("vulkan.vendor.so", 0) == DRIVER);
        enable(false);
        assert(android_load_sphal_library("vulkan.vendor.so", 0) == SYSTEM);
        assert(mojoexec_acq_vulkan_handle() == SYSTEM);
        enable(true); preload(); assert(mojoexec_acq_vulkan_handle() == CUSTOM);
        assert(android_load_sphal_library("vulkan.vendor.so", 0) == DRIVER && unique_calls == 1);
    } else if (!strcmp(scenario, "concurrent")) {
        enable(true); pthread_t threads[8];
        for (int i=0;i<8;i++) assert(!pthread_create(&threads[i], NULL, acquire_thread, NULL));
        for (int i=0;i<8;i++) assert(!pthread_join(threads[i], NULL));
        assert(unique_calls == 1 && hook_calls == 1 && driver_calls == 1 && dl_calls == 1);
    } else {
        failure = atoi(scenario); assert(failure >= 1 && failure <= 7);
        enable(true); assert(mojoexec_acq_vulkan_handle() == SYSTEM);
        assert(closes == 0);
        if (failure >= 6) {
            assert(android_load_sphal_library("libvendor.so", 0) == SYSTEM);
            assert(android_load_sphal_library("vulkan.vendor.so", 0) == SYSTEM);
            assert(hook_calls == 1 && driver_calls == 1 && dl_calls == 1);
        }
        failure = 0; assert(mojoexec_acq_vulkan_handle() == CUSTOM);
        assert(android_load_sphal_library("vulkan.vendor.so", 0) == DRIVER);
        if (!strcmp(scenario, "6")) assert(hook_calls == 1 && driver_calls == 1 && dl_calls == 1 && unique_calls == 2);
        if (!strcmp(scenario, "7")) assert(unique_calls == 1);
    }
    assert(closes == 0);
    printf("PASS loader %s\n", scenario);
}
