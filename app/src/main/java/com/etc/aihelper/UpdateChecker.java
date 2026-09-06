package com.etc.aihelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {
    private static final String TAG = "AIHelper_Update";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CheckCallback {
        void onUpdateAvailable(String version, String downloadUrl, String releaseNotes);
        void onNoUpdate();
        void onError(String error);
    }

    public UpdateChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    public void checkForUpdate(CheckCallback callback) {
        executor.execute(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + App.GITHUB_OWNER + "/"
                        + App.GITHUB_SOFTWARE_REPO + "/releases/latest";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    postError(callback, "检查更新失败(" + code + ")");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.getString("tag_name");
                String body = release.optString("body", "");
                String downloadUrl = null;

                org.json.JSONArray assets = release.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.getString("name");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        break;
                    }
                }

                if (downloadUrl == null) {
                    postNoUpdate(callback);
                    return;
                }

                // Compare versions
                String currentVersion = "0.1";
                if (isNewerVersion(tagName, currentVersion)) {
                    postUpdateAvailable(callback, tagName, downloadUrl, body);
                } else {
                    postNoUpdate(callback);
                }

            } catch (Exception e) {
                postError(callback, "检查更新错误: " + e.getMessage());
            }
        });
    }

    private boolean isNewerVersion(String remote, String current) {
        try {
            String r = remote.replace("v", "").trim();
            String[] rParts = r.split("\\.");
            String[] cParts = current.split("\\.");
            int len = Math.max(rParts.length, cParts.length);
            for (int i = 0; i < len; i++) {
                int rv = i < rParts.length ? Integer.parseInt(rParts[i]) : 0;
                int cv = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
                if (rv > cv) return true;
                if (rv < cv) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void downloadAndInstall(String downloadUrl, String version, Activity activity) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> {
                    AlertDialog progress = new AlertDialog.Builder(activity)
                            .setTitle("下载更新")
                            .setMessage("正在下载 v" + version + "...")
                            .setCancelable(false)
                            .show();
                });

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);
                InputStream is = conn.getInputStream();

                File updatesDir = new File(context.getFilesDir(), "updates");
                if (!updatesDir.exists()) updatesDir.mkdirs();
                File apkFile = new File(updatesDir, "AIHelper-v" + version + ".apk");

                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();

                mainHandler.post(() -> {
                    // Dismiss progress and show install prompt
                    installApk(apkFile, activity);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("下载失败")
                            .setMessage(e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        });
    }

    private void installApk(File apkFile, Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(activity)
                    .setTitle("安装失败")
                    .setMessage(e.getMessage())
                    .setPositiveButton("确定", null)
                    .show();
        }
    }

    private void postUpdateAvailable(CheckCallback cb, String v, String url, String notes) {
        mainHandler.post(() -> cb.onUpdateAvailable(v, url, notes));
    }

    private void postNoUpdate(CheckCallback cb) {
        mainHandler.post(cb::onNoUpdate);
    }

    private void postError(CheckCallback cb, String err) {
        mainHandler.post(() -> cb.onError(err));
    }
}
