package com.etc.aihelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountManager {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public interface LoginCallback {
        void onSuccess(String username);
        void onError(String error);
    }

    public interface StatusCallback {
        void onResult(String status);
        void onError(String error);
    }

    public AccountManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void login(String username, String password, LoginCallback callback) {
        executor.execute(() -> {
            try {
                String hashedInput = sha256(password);
                String base = App.getAccountRawBase() + username + "/";

                // Fetch password hash
                String storedHash = fetchUrl(base + "password.hash");
                if (storedHash == null || storedHash.isEmpty()) {
                    postError(callback, "账号不存在，请先申请");
                    return;
                }
                storedHash = storedHash.trim();

                if (!storedHash.equalsIgnoreCase(hashedInput)) {
                    postError(callback, "密码错误");
                    return;
                }

                // Fetch status
                String status = fetchUrl(base + "status");
                if (status != null && status.trim().equalsIgnoreCase("banned")) {
                    postError(callback, "该账号已被封禁，请联系开发者");
                    return;
                }

                // Fetch avatar
                String avatarB64 = fetchUrl(base + "avatar.b64");

                // Save to prefs
                SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean(App.KEY_LOGGED_IN, true);
                editor.putString(App.KEY_USERNAME, username);
                editor.putString(App.KEY_ACCOUNT_STATUS, status != null ? status.trim() : "enabled");
                if (avatarB64 != null && !avatarB64.trim().isEmpty()) {
                    editor.putString(App.KEY_AVATAR, avatarB64.trim());
                }
                editor.apply();

                postSuccess(callback, username);

            } catch (Exception e) {
                postError(callback, "登录失败: " + e.getMessage());
            }
        });
    }

    public void checkStatus(String username, StatusCallback callback) {
        executor.execute(() -> {
            try {
                String status = fetchUrl(App.getAccountRawBase() + username + "/status");
                if (status == null) {
                    postStatusError(callback, "无法获取账号状态");
                    return;
                }
                String s = status.trim();
                SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
                sp.edit().putString(App.KEY_ACCOUNT_STATUS, s).apply();
                postStatusResult(callback, s);
            } catch (Exception e) {
                postStatusError(callback, e.getMessage());
            }
        });
    }

    private String fetchUrl(String urlStr) {
        // Build mirror list for GitHub raw content (国内网络优化)
        String[] mirrors = buildMirrors(urlStr);
        for (String mirror : mirrors) {
            String result = fetchSingle(mirror);
            if (result != null) return result;
        }
        return null;
    }

    private String[] buildMirrors(String originalUrl) {
        if (originalUrl.contains("raw.githubusercontent.com")) {
            String path = originalUrl.replace("https://raw.githubusercontent.com/", "");
            return new String[] {
                originalUrl,
                "https://raw.gitmirror.com/" + path,
                "https://ghproxy.com/https://raw.githubusercontent.com/" + path,
                "https://gh.api.99988866.xyz/https://raw.githubusercontent.com/" + path
            };
        }
        return new String[] { originalUrl };
    }

    private String fetchSingle(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AIHelper/0.2");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static Bitmap decodeAvatar(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            // Remove data URI prefix if present
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void postSuccess(LoginCallback cb, String username) {
        mainHandler.post(() -> cb.onSuccess(username));
    }

    private void postError(LoginCallback cb, String err) {
        mainHandler.post(() -> cb.onError(err));
    }

    private void postStatusResult(StatusCallback cb, String s) {
        mainHandler.post(() -> cb.onResult(s));
    }

    private void postStatusError(StatusCallback cb, String err) {
        mainHandler.post(() -> cb.onError(err));
    }
}
