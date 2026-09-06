package com.etc.aihelper;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Detects whether the app is running on a real device or an allowed VM.
 * Allowed: real devices, UU VM (uu虚拟机), 光速虚拟机 (GuangSu VM).
 * Unsupported VMs cause the app to self-destruct.
 */
public class VMDetector {

    // Known allowed VM fingerprints
    private static final List<String> ALLOWED_VM_MODELS = Arrays.asList(
            "uu", "uuvm", "uu虚拟机",
            "guangsu", "gsvm", "光速虚拟机", "guangsuvm"
    );

    // Known disallowed emulator/VM fingerprints
    private static final List<String> EMULATOR_HINTS = Arrays.asList(
            "goldfish", "ranchu", "emulator", "sdk_gphone", "android_x86",
            "android_x64", "generic", "vbox", "vmware", "virtualbox",
            "nox", "ldplayer", "ld", "memu", "memuplay", "bluestacks",
            "bluestack", "andy", "droid4x", "windroye", "genymotion",
            "tencent", "tencentvm", "mumu", "nemu", "netease",
            "phoenix", "phoenixos", "primeos", "remix", "remixos",
            "chromium", "arc++", "android_sdk"
    );

    private static final List<String> EMULATOR_FILES = Arrays.asList(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props",
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd"
    );

    public static boolean isAllowedEnvironment(Context context) {
        // If self-destructed previously, always disallow
        if (context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE)
                .getBoolean("self_destructed", false)) {
            return false;
        }

        boolean isEmulator = detectEmulator();
        if (!isEmulator) {
            // Real device - allowed
            return true;
        }

        // It's a VM/emulator - check if it's an allowed one
        return isAllowedVM();
    }

    private static boolean detectEmulator() {
        // Check Build properties
        String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase() : "";
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.toLowerCase() : "";
        String hardware = Build.HARDWARE != null ? Build.HARDWARE.toLowerCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase() : "";

        // Check for emulator-specific files
        for (String path : EMULATOR_FILES) {
            if (new File(path).exists()) {
                return true;
            }
        }

        // Check known emulator hints in build props
        String combined = fingerprint + " " + model + " " + manufacturer + " "
                + brand + " " + product + " " + hardware + " " + device;
        for (String hint : EMULATOR_HINTS) {
            if (combined.contains(hint)) {
                return true;
            }
        }

        // Generic emulator checks
        if (fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")) return true;
        if (model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for x86")) return true;
        if (hardware.equals("goldfish") || hardware.equals("ranchu") || hardware.equals("vbox86")) return true;
        if (product.equals("sdk_gphone_x86") || product.equals("sdk_gphone_arm64")
                || product.equals("sdk_phone_x86") || product.equals("sdk_phone_arm64")) return true;

        // Check /proc/cpuinfo for virtualization
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains("virtualbox")
                        || line.toLowerCase().contains("vmware")
                        || line.toLowerCase().contains("qemu")) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception ignored) {}

        return false;
    }

    private static boolean isAllowedVM() {
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.toLowerCase() : "";
        String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase() : "";
        String combined = model + " " + manufacturer + " " + brand + " " + product + " " + fingerprint;

        for (String allowed : ALLOWED_VM_MODELS) {
            if (combined.contains(allowed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
