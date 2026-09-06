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
            String apiUrl = "https://api.github.com/repos/" + App.GITHUB_OWNER + "/"
                    + App.GITHUB_SOFTWARE_REPO + "/releases/latest";
            // 国内镜像备选
            String[] apiMirrors = new String[] {
                apiUrl,
                "https://ghproxy.com/" + apiUrl,
                "https://gh.api.99988866.xyz/" + apiUrl
            };

            String response = null;
            for (String mirror : apiMirrors) {
                response = httpGet(mirror, 3);
                if (response != null) break;
            }

            if (response == null) {
                postError(callback, "无法连接GitHub，请检查网络");
                return;
            }

            try {
                JSONObject release = new JSONObject(response);
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

                String currentVersion = "0.2";
                if (isNewerVersion(tagName, currentVersion)) {
                    postUpdateAvailable(callback, tagName, downloadUrl, body);
                } else {
                    postNoUpdate(callback);
                }
            } catch (Exception e) {
                postError(callback, "解析更新信息失败: " + e.getMessage());
            }
        });
    }

    private String httpGet(String urlStr, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "AIHelper/0.2");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(25000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    return sb.toString();
                }
            } catch (Exception e) {
                // retry
            } finally {
                if (conn != null) conn.disconnect();
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return null;
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
                    try {
                        new AlertDialog.Builder(activity)
                                .setTitle("下载更新")
                                .setMessage("正在下载 v" + version + "...")
                                .setCancelable(false)
                                .show();
                    } catch (Exception ignored) {}
                });

                // 构建下载镜像列表
                String[] downloadMirrors;
                if (downloadUrl.contains("github.com")) {
                    downloadMirrors = new String[] {
                        downloadUrl,
                        "https://ghproxy.com/" + downloadUrl,
                        "https://gh.api.99988866.xyz/" + downloadUrl
                    };
                } else {
                    downloadMirrors = new String[] { downloadUrl };
                }

                File apkFile = null;
                for (String mirror : downloadMirrors) {
                    apkFile = downloadApk(mirror, version);
                    if (apkFile != null && apkFile.length() > 100000) break;
                }

                if (apkFile == null || apkFile.length() < 100000) {
                    mainHandler.post(() -> {
                        try {
                            new AlertDialog.Builder(activity)
                                    .setTitle("下载失败")
                                    .setMessage("所有下载源均失败，请检查网络后重试")
                                    .setPositiveButton("确定", null)
                                    .show();
                        } catch (Exception ignored) {}
                    });
                    return;
                }

                final File finalApk = apkFile;
                mainHandler.post(() -> installApk(finalApk, activity));

            } catch (Exception e) {
                mainHandler.post(() -> {
                    try {
                        new AlertDialog.Builder(activity)
                                .setTitle("下载失败")
                                .setMessage(e.getMessage())
                                .setPositiveButton("确定", null)
                                .show();
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private File downloadApk(String urlStr, String version) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "AIHelper/0.2");
            conn.setInstanceFollowRedirects(true);
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
            return apkFile;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
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
