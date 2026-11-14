# All four ABIs like upstream's engine bundles: the shell runs the downloaded engine of the
# matching ABI, and the java/javac launchers must exist for whichever device installs the APK.
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
# The JRE's own libraries need libc++_shared.so beside them; let ndkBuild package it.
APP_STL := c++_shared
# 16 KB page alignment (Android 15 devices) comes with the pinned NDK's next upgrade; the
# upstream engine bundles already carry it (build_apk.py page flags).
