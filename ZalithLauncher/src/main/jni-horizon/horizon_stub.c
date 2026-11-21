/* Zalith Launcher 2 (Horizon fork)
 *
 * horizon_stub.c — the shell's NativeActivity entry.
 *
 * The shell APK carries no engine: the Horizon engine (libhorizon_android.so) is a downloaded
 * engine bundle (see game/horizon/EngineRepository). StarlingActivity.loadGame() loads the active
 * engine with System.load before super.onCreate, so its Java native methods are bound; this stub
 * is what the manifest's android.app.lib_name points to, and it hands NativeActivity's call over
 * to the engine's starling_on_create — dlopen finds the engine already loaded, exactly like the
 * engine's own native-mod handover (STARLING_GAME_LIBRARY, android_lib.rs).
 *
 * HORIZON_ENGINE_LIBRARY (set by the shell before starting the activity) names the engine path;
 * as a fallback the stub searches the process for the engine's entry directly.
 */
#include <dlfcn.h>
#include <stddef.h>
#include <stdlib.h>
#include <android/log.h>
#include <android/native_activity.h>

#define TAG "horizon-stub"

typedef void (*OnCreate)(ANativeActivity *activity, void *saved_state, size_t saved_state_size);

/* Defined below; the comparison below needs the declaration in scope. */
void starling_on_create(ANativeActivity *activity, void *saved_state, size_t saved_state_size);

static void *find_engine_entry(void) {
    const char *path = getenv("HORIZON_ENGINE_LIBRARY");
    if (path && *path) {
        void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (handle) {
            void *entry = dlsym(handle, "starling_on_create");
            if (entry) return entry;
            __android_log_print(ANDROID_LOG_ERROR, TAG, "%s has no starling_on_create", path);
        } else {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen(%s) failed: %s", path, dlerror());
        }
    }
    /* The engine may already sit in this process (System.load): search it, skipping this stub. */
    void *entry = dlsym(RTLD_DEFAULT, "starling_on_create");
    if (entry == (void *)&starling_on_create) return NULL;
    return entry;
}

__attribute__((visibility("default")))
void starling_on_create(ANativeActivity *activity, void *saved_state, size_t saved_state_size) {
    void *entry = find_engine_entry();
    if (!entry) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "no Horizon engine library: install one in the launcher's engine manager");
        return;
    }
    ((OnCreate)entry)(activity, saved_state, saved_state_size);
}

/* NativeActivity's default entry (no android.app.func_name) lands here too. */
__attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity *activity, void *saved_state, size_t saved_state_size) {
    starling_on_create(activity, saved_state, saved_state_size);
}
