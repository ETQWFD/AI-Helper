package com.etc.aihelper;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

public class App extends Application {
    public static final String PREFS = "aihelper_prefs";
    public static final String KEY_FIRST_RUN = "first_run";
    public static final String KEY_AI_MODE = "ai_mode"; // "custom" or "applied"
    public static final String KEY_AI_URL = "ai_url";
    public static final String KEY_AI_KEY = "ai_key";
    public static final String KEY_AI_NAME = "ai_name";
    public static final String KEY_LOGGED_IN = "logged_in";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_AVATAR = "avatar_base64";
    public static final String KEY_ACCOUNT_STATUS = "account_status";
    public static final String KEY_BANNED = "banned";

    // Applied account preset AI config
    public static final String APPLIED_AI_URL = "https://api.hcnsec.cn/v1/chat/completions";
    public static final String APPLIED_AI_KEY = "sk-z2x3WfpsOMByzVsSHpfMP42SOBaBEaqBOrApMY1d30ZP2Ct4";
    public static final String APPLIED_AI_NAME = "longcat-2.0";

    // GitHub account repo
    public static final String GITHUB_OWNER = "ETQWFD";
    public static final String GITHUB_ACCOUNT_REPO = "AI-Helper-Accounts";
    public static final String GITHUB_BRANCH = "main";
    public static final String GITHUB_SOFTWARE_REPO = "AI-Helper";

    public static final String APPLY_EMAIL = "etqe1234@outlook.com";

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // VM detection on startup
        boolean isAllowed = VMDetector.isAllowedEnvironment(this);
        if (!isAllowed) {
            Log.e("AIHelper", "Unsupported VM environment detected. Self-destructing.");
            selfDestruct();
            // Crash immediately
            throw new RuntimeException("Unsupported environment. App self-destructed.");
        }
    }

    public static App getInstance() {
        return instance;
    }

    public SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /**
     * Self-destruct: corrupt own APK copy in app directory so it cannot be reinstalled
     * from local cache. This only affects the developer's own distributed package copy.
     */
    private void selfDestruct() {
        try {
            // Corrupt any local APK update copies
            File updatesDir = new File(getFilesDir(), "updates");
            if (updatesDir.exists()) {
                File[] apks = updatesDir.listFiles((dir, name) -> name.endsWith(".apk"));
                if (apks != null) {
                    for (File apk : apks) {
                        try {
                            byte[] garbage = new byte[(int) Math.min(apk.length(), 4096)];
                            new Random().nextBytes(garbage);
                            FileOutputStream fos = new FileOutputStream(apk);
                            fos.write(garbage);
                            fos.close();
                            Log.d("AIHelper", "Corrupted local APK: " + apk.getName());
                        } catch (Exception e) {
                            Log.e("AIHelper", "Failed to corrupt APK", e);
                        }
                    }
                }
            }
            // Mark as destroyed
            getPrefs().edit().putBoolean("self_destructed", true).apply();
        } catch (Exception e) {
            Log.e("AIHelper", "Self-destruct error", e);
        }
    }

    public static String getAccountRawBase() {
        return "https://raw.githubusercontent.com/" + GITHUB_OWNER + "/"
                + GITHUB_ACCOUNT_REPO + "/" + GITHUB_BRANCH + "/server/";
    }
}
