package com.etc.aihelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIClient {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public interface Callback {
        void onSuccess(String text, String actionsJson);
        void onError(String error);
    }

    public AIClient(Context context) {
        this.context = context.getApplicationContext();
    }

    private String getAiUrl() {
        SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) return App.APPLIED_AI_URL;
        return sp.getString(App.KEY_AI_URL, "");
    }

    private String getAiKey() {
        SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) return App.APPLIED_AI_KEY;
        return sp.getString(App.KEY_AI_KEY, "");
    }

    private String getAiName() {
        SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) return App.APPLIED_AI_NAME;
        return sp.getString(App.KEY_AI_NAME, "");
    }

    public boolean isConfigured() {
        SharedPreferences sp = context.getSharedPreferences(App.PREFS, Context.MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) {
            return sp.getBoolean(App.KEY_LOGGED_IN, false)
                    && !"banned".equals(sp.getString(App.KEY_ACCOUNT_STATUS, "enabled"));
        }
        String url = sp.getString(App.KEY_AI_URL, "");
        String key = sp.getString(App.KEY_AI_KEY, "");
        String name = sp.getString(App.KEY_AI_NAME, "");
        return !url.isEmpty() && !key.isEmpty() && !name.isEmpty();
    }

    public void sendMessage(String userMessage, String screenContext, Callback callback) {
        executor.execute(() -> {
            try {
                String urlStr = getAiUrl();
                String apiKey = getAiKey();
                String model = getAiName();

                if (urlStr.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
                    postError(callback, "AI未配置，请先在设置中配置AI");
                    return;
                }

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);

                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", buildSystemPrompt());
                messages.put(sysMsg);

                if (screenContext != null && !screenContext.isEmpty()) {
                    JSONObject ctxMsg = new JSONObject();
                    ctxMsg.put("role", "system");
                    ctxMsg.put("content", "当前屏幕:\n" + screenContext);
                    messages.put(ctxMsg);
                }

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userMessage);
                messages.put(userMsg);

                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("messages", messages);
                body.put("temperature", 0.7);
                body.put("max_tokens", 1024);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                BufferedReader reader;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                }
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String err = response.length() > 200 ? response.substring(0, 200) : response.toString();
                    postError(callback, "API错误(" + responseCode + "): " + err);
                    return;
                }

                JSONObject jsonResponse = new JSONObject(response.toString());
                String content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content");

                String actionsJson = extractActions(content);
                String cleanText = stripActions(content);

                postSuccess(callback, cleanText, actionsJson);

            } catch (Exception e) {
                postError(callback, "网络错误: " + e.getMessage());
            }
        });
    }

    private String buildSystemPrompt() {
        return "你是运行在安卓手机上的操作助手。你拥有无障碍权限，可以读取屏幕并执行操作。\n\n" +
                "规则:\n" +
                "1. 只帮助用户，不损坏任何文件和数据。\n" +
                "2. 打游戏时以正常玩家水平操作，不作弊不改数据。\n" +
                "3. 先看屏幕信息，再决定操作。\n" +
                "4. 回复要简短直接，不要长篇大论。\n" +
                "5. 用中文回复。\n\n" +
                "操作指令格式(回复末尾，不要代码块):\n" +
                "ACTIONS:{\"actions\":[{\"type\":\"tap\",\"x\":100,\"y\":200}," +
                "{\"type\":\"tap_text\",\"text\":\"按钮文字\"}," +
                "{\"type\":\"swipe\",\"x1\":100,\"y1\":500,\"x2\":100,\"y2\":200,\"duration\":300}," +
                "{\"type\":\"text\",\"text\":\"输入内容\"}," +
                "{\"type\":\"long_press\",\"x\":100,\"y\":200}," +
                "{\"type\":\"back\"},{\"type\":\"home\"},{\"type\":\"wait\",\"ms\":1000}]}\n" +
                "不需要操作就只回复文字。";
    }

    private String extractActions(String content) {
        int idx = content.indexOf("ACTIONS:");
        if (idx >= 0) return content.substring(idx + 8).trim();
        int jsonStart = content.indexOf("{\"actions\"");
        if (jsonStart >= 0) {
            int depth = 0;
            for (int i = jsonStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                if (content.charAt(i) == '}') {
                    depth--;
                    if (depth == 0) return content.substring(jsonStart, i + 1);
                }
            }
        }
        return null;
    }

    private String stripActions(String content) {
        int idx = content.indexOf("ACTIONS:");
        if (idx >= 0) return content.substring(0, idx).trim();
        int jsonStart = content.indexOf("{\"actions\"");
        if (jsonStart >= 0) return content.substring(0, jsonStart).trim();
        return content.trim();
    }

    private void postSuccess(Callback cb, String text, String actions) {
        mainHandler.post(() -> cb.onSuccess(text, actions));
    }

    private void postError(Callback cb, String err) {
        mainHandler.post(() -> cb.onError(err));
    }
}
