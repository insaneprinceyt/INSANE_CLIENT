#include <android/api-level.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <jni.h>
#include <stdatomic.h>
#include <pthread.h>
#include <driver_helper/nsbypass.h>
#include <android/dlext.h>
#include <mojoexec.h>

static atomic_bool turnip_enabled = false;

#ifdef ENABLE_TURNIP_LOADER
static pthread_mutex_t driver_lock = PTHREAD_MUTEX_INITIALIZER;
static bool driver_loaded;
static void *hook_handle, *driver_handle, *dl_android_handle;
static void* namespace_lookup;
static void (*publish_handles)(void*, void*, void*);

static void select_driver(bool enabled) {
    if (publish_handles) {
        publish_handles(enabled ? driver_handle : NULL, android_dlopen_ext, namespace_lookup);
    }
}

// All cached handles are bounded (one per library) and retained for the process.
// DF_1_GLOBAL hooks may still be called after custom setup fails. Closing their
// callback libraries or driver would leave callable pointers to unmapped memory.
static bool load_turnip_locked(void) {
    if (!atomic_load(&turnip_enabled)) return false;
    if (driver_loaded) {
        select_driver(true);
        return true;
    }
    const char* cache_dir = getenv("TMPDIR");
    if (!cache_dir || !*cache_dir || !linker_ns_load(mojoexec_native_dir)) return false;
    if (!hook_handle) hook_handle = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (!hook_handle) return false;
    if (!driver_handle) driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW);
    if (!driver_handle) return false;
    if (!dl_android_handle) dl_android_handle = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (!dl_android_handle) return false;
    if (!namespace_lookup) namespace_lookup = dlsym(dl_android_handle, "android_get_exported_namespace");
    if (!publish_handles) publish_handles = dlsym(hook_handle, "app__pojav_linkerhook_pass_handles");
    if (!namespace_lookup || !publish_handles) return false;
    select_driver(true);
    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk.so", RTLD_LOCAL | RTLD_NOW);
    printf("MojoExec: Loaded mjlvlk, ptr=%p\n", libvulkan);
    if (libvulkan) {
        driver_loaded = true;
        return true;
    }
    select_driver(false);
    return false;
}

static bool load_turnip_vulkan(void) {
    pthread_mutex_lock(&driver_lock);
    bool loaded = load_turnip_locked();
    pthread_mutex_unlock(&driver_lock);
    return loaded;
}
#endif

void* mojoexec_acq_vulkan_handle(void) {
    mojoexec_make_bigcore_affine();
    int flags = RTLD_LOCAL | RTLD_NOW;
#ifdef ENABLE_TURNIP_LOADER
    if (android_get_device_api_level() >= 28 && atomic_load(&turnip_enabled)) {
        pthread_mutex_lock(&driver_lock);
        if (load_turnip_locked()) {
            // Give the caller its own reference, so its dlclose cannot unload ours.
            void* custom = linker_ns_dlopen("libmjlvlk.so", flags);
            if (custom) {
                pthread_mutex_unlock(&driver_lock);
                return custom;
            }
            select_driver(false);
        }
        pthread_mutex_unlock(&driver_lock);
    }
#endif
    void* system = dlopen("libvulkan.so", flags);
    printf("MojoExec: loaded system vulkan, ptr=%p\n", system);
    return system;
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojoexec_MojoExec_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    (void)env;
    (void)clazz;
#ifdef ENABLE_TURNIP_LOADER
    pthread_mutex_lock(&driver_lock);
#endif
    atomic_store(&turnip_enabled, enable != 0);
#ifdef ENABLE_TURNIP_LOADER
    if (!enable) select_driver(false);
    pthread_mutex_unlock(&driver_lock);
#endif
}

JNIEXPORT void JNICALL
Java_git_artdeell_mojoexec_MojoExec_preloadVulkan(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
#ifdef ENABLE_TURNIP_LOADER
    if (android_get_device_api_level() < 28 || !atomic_load(&turnip_enabled)) return;
    if (!load_turnip_vulkan()) printf("MojoExec: Failed to preload Turnip; using system Vulkan.\n");
#endif
}
