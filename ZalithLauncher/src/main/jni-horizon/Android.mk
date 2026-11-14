# Zalith Launcher 2 (Horizon fork) — native components of the Horizon shell.
#
# The Java-launching machinery of the old src/main/jni is gone with Java Edition support; what
# remains serves the Horizon engine:
# - horizon_stub: the NativeActivity entry that hands over to the downloaded engine
#   (game/horizon/EngineRepository, io.github.juanluci.starling.EngineHost).
# - starling_java / starling_javac: the java/javac launchers horizon-prepare runs on the phone
#   (packaging/android/native/java_launcher.c upstream). Android runs programs only from the
#   app's library folder, so they ship in the APK as libraries; they dlopen the OpenJDK 25
#   runtime the shell pre-seeds (ResourcePrefill.prefillJre) or the engine fetches itself.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := horizon_stub
LOCAL_CFLAGS := -O2 -Wall -Werror
LOCAL_LDLIBS := -ldl -llog
LOCAL_SRC_FILES := horizon_stub.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := starling_java
LOCAL_CFLAGS := -O2 -Wall -Werror
LOCAL_LDLIBS := -ldl
LOCAL_SRC_FILES := java_launcher.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := starling_javac
LOCAL_CFLAGS := -O2 -Wall -Werror -DJAVAC
LOCAL_LDLIBS := -ldl
LOCAL_SRC_FILES := java_launcher.c
include $(BUILD_SHARED_LIBRARY)
