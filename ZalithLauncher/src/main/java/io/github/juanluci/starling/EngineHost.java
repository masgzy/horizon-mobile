package io.github.juanluci.starling;

/**
 * The bridge between the Horizon shell (ZalithLauncher's Horizon fork) and the ported Starling
 * platform classes. The shell's engine is not inside the APK: it is a downloaded engine bundle
 * (libhorizon_android.so plus its prepare kit), installed and versioned by the shell's engine
 * manager. The shell sets {@link #engineLibraryPath} before it starts the game, so loadGame()
 * binds the native methods against the downloaded library instead of an APK library, and the
 * manifest's stub library (horizon_stub) finds it already loaded, as NativeMods' builds do.
 */
public final class EngineHost {
    /** Absolute path of the active engine's libhorizon_android.so, null when none is installed. */
    public static volatile String engineLibraryPath;

    private EngineHost() {}
}
