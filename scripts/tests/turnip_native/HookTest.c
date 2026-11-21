#include <android/dlext.h>
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

void app__pojav_linkerhook_pass_handles(void*, void*, void*);
void* android_load_sphal_library(const char*, int);
static int open_calls, namespace_calls;
static bool namespace_available;
static int marker;
static void* driver = (void*)0x1000;
static void* system_driver = (void*)0x2000;
static const char* last_name;
static bool last_info;
static android_dlextinfo saved_info;

static void* forward_open(const char* name, int flags, const android_dlextinfo* info) {
    (void)flags;
    open_calls++;
    last_name = name;
    last_info = info != NULL;
    if(info) saved_info = *info;
    return system_driver;
}
static struct android_namespace_t* find_namespace(const char* name) {
    namespace_calls++;
    if(namespace_available && !strcmp(name, "vendor")) return (struct android_namespace_t*)&marker;
    return NULL;
}
int main(int argc, char** argv) {
    assert(argc == 2);
    const char* scenario = argv[1];
    if(!strcmp(scenario, "uninitialized")) {
        assert(android_load_sphal_library("libvendor.so", 0) == NULL);
        assert(android_dlopen_ext("libvendor.so", 0, NULL) == NULL);
        assert(android_dlopen_ext(NULL, 0, NULL) == NULL);
    } else if(!strcmp(scenario, "null-filename")) {
        app__pojav_linkerhook_pass_handles(driver, forward_open, find_namespace);
        assert(android_dlopen_ext(NULL, 0, NULL) == system_driver);
        assert(last_name == NULL && !last_info && open_calls == 1);
        assert(android_load_sphal_library(NULL, 0) == NULL);
    } else if(!strcmp(scenario, "override-and-forward")) {
        app__pojav_linkerhook_pass_handles(driver, forward_open, find_namespace);
        assert(android_load_sphal_library("/vendor/lib64/hw/vulkan.vendor.so", 0) == driver);
        assert(android_dlopen_ext("vulkan.vendor.so", 0, NULL) == driver);
        assert(open_calls == 0 && namespace_calls == 0);
        assert(android_dlopen_ext("libvendor.so", 0, NULL) == system_driver);
        assert(open_calls == 1 && !strcmp(last_name, "libvendor.so"));
    } else if(!strcmp(scenario, "namespace-and-zero-init")) {
        namespace_available = true;
        app__pojav_linkerhook_pass_handles(driver, forward_open, find_namespace);
        assert(android_load_sphal_library("libvendor.so", 0) == system_driver);
        assert(namespace_calls == 2 && last_info);
        assert(saved_info.flags == ANDROID_DLEXT_USE_NAMESPACE);
        assert(saved_info.library_namespace == (struct android_namespace_t*)&marker);
        assert(saved_info.reserved_addr == NULL && saved_info.reserved_size == 0);
        assert(saved_info.relro_fd == 0 && saved_info.library_fd == 0 && saved_info.library_fd_offset == 0);
    } else if(!strcmp(scenario, "missing-namespace")) {
        app__pojav_linkerhook_pass_handles(driver, forward_open, find_namespace);
        assert(android_load_sphal_library("libvendor.so", 0) == NULL);
        assert(namespace_calls == 3 && open_calls == 0);
    } else if(!strcmp(scenario, "inactive-forwarding")) {
        namespace_available = true;
        app__pojav_linkerhook_pass_handles(driver, forward_open, find_namespace);
        app__pojav_linkerhook_pass_handles(NULL, forward_open, find_namespace);
        assert(android_dlopen_ext("vulkan.vendor.so", 0, NULL) == system_driver);
        assert(android_load_sphal_library("vulkan.vendor.so", 0) == system_driver);
        assert(open_calls == 2 && namespace_calls == 2);
    } else if(!strcmp(scenario, "partial-callbacks")) {
        app__pojav_linkerhook_pass_handles(NULL, NULL, find_namespace);
        assert(android_dlopen_ext("libvendor.so", 0, NULL) == NULL);
        assert(android_load_sphal_library("libvendor.so", 0) == NULL);
        app__pojav_linkerhook_pass_handles(NULL, forward_open, NULL);
        assert(android_load_sphal_library("libvendor.so", 0) == NULL);
        assert(namespace_calls == 0 && open_calls == 0);
    } else abort();
    printf("PASS hook %s\n", scenario);
}
