package app.patchlab.extension.offlinegames;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public final class NativeLibraries {
    private NativeLibraries() {}

    /** Called by Unity before System.load(libmain) and NativeLoader.load(directory). */
    public static String directory(Context context) {
        try {
            // Read sourceDir directly: a root bind mount changes these bytes, whereas
            // PackageManager's nativeLibraryDir still contains the original installation.
            File apk = new File(context.getApplicationInfo().sourceDir);
            File root = context.getDir("patchlab-offlinegames-native", Context.MODE_PRIVATE);
            File directory = NativeLibraryStore.prepare(apk, root);
            Log.i("PatchLabOfflineGames", "Using verified Unity libraries from mounted APK: " + directory);
            return directory.getAbsolutePath();
        } catch (IOException error) {
            // Falling back silently would recreate the original 'patch applied, no effect' bug.
            throw new IllegalStateException("Cannot load patched Offline Games native libraries", error);
        }
    }
}
