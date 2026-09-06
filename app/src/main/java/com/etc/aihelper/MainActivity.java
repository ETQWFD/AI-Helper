package com.etc.aihelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    private TextView tvUserInfo;
    private ImageView ivAvatar;
    private Button btnStart;
    private AIClient aiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aiClient = new AIClient(this);
        tvStatus = findViewById(R.id.tv_status);
        tvUserInfo = findViewById(R.id.tv_user_info);
        ivAvatar = findViewById(R.id.iv_avatar);
        btnStart = findViewById(R.id.btn_start);

        // First run check
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        if (sp.getBoolean(App.KEY_FIRST_RUN, true)) {
            sp.edit().putBoolean(App.KEY_FIRST_RUN, false).apply();
            startActivity(new Intent(this, ApplyActivity.class));
        }

        findViewById(R.id.btn_get_accessibility).setOnClickListener(v -> requestAccessibility());
        findViewById(R.id.btn_login).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_apply).setOnClickListener(v ->
                startActivity(new Intent(this, ApplyActivity.class)));
        btnStart.setOnClickListener(v -> startChat());

        // 点击用户信息区域可登出
        findViewById(R.id.tv_user_info).setOnClickListener(v -> {
            if (sp.getBoolean(App.KEY_LOGGED_IN, false)) {
                new AlertDialog.Builder(this)
                        .setTitle("登出")
                        .setMessage("确定要退出当前账号吗？")
                        .setPositiveButton("登出", (d, w) -> {
                            new AccountManager(this).logout();
                            updateUI();
                            Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        // Check account status if logged in
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        if (sp.getBoolean(App.KEY_LOGGED_IN, false)) {
            String username = sp.getString(App.KEY_USERNAME, "");
            new AccountManager(this).checkStatus(username, new AccountManager.StatusCallback() {
                @Override public void onResult(String status) {
                    if ("banned".equalsIgnoreCase(status)) {
                        Toast.makeText(MainActivity.this, "账号已被封禁", Toast.LENGTH_LONG).show();
                        new AccountManager(MainActivity.this).logout();
                        updateUI();
                    }
                }
                @Override public void onError(String error) {}
            });
        }
    }

    private void updateUI() {
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        boolean a11y = MyAccessibilityService.isServiceEnabled();
        boolean aiOk = aiClient.isConfigured();
        boolean loggedIn = sp.getBoolean(App.KEY_LOGGED_IN, false);

        StringBuilder status = new StringBuilder();
        status.append("无障碍服务: ").append(a11y ? "已开启 ✓" : "未开启 ✗").append("\n");
        status.append("AI配置: ").append(aiOk ? "已就绪 ✓" : "未配置 ✗").append("\n");
        status.append("登录状态: ").append(loggedIn ? "已登录 ✓" : "未登录");
        tvStatus.setText(status.toString());

        if (loggedIn) {
            String username = sp.getString(App.KEY_USERNAME, "");
            tvUserInfo.setText("用户: " + username);
            String avatarB64 = sp.getString(App.KEY_AVATAR, "");
            Bitmap avatar = AccountManager.decodeAvatar(avatarB64);
            if (avatar != null) {
                ivAvatar.setImageBitmap(avatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_launcher_foreground);
            }
        } else {
            tvUserInfo.setText("未登录");
            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }

    private void requestAccessibility() {
        Toast.makeText(this, "请在系统设置中开启 AI帮助器 的无障碍服务", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void startChat() {
        // Check accessibility
        if (!MyAccessibilityService.isServiceEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要无障碍权限")
                    .setMessage("请先点击\"获取无障碍\"按钮，在系统设置中开启本应用的无障碍服务。每次启动都需要重新确认开启。")
                    .setPositiveButton("去开启", (d, w) -> requestAccessibility())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        // Check AI config
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) {
            if (!sp.getBoolean(App.KEY_LOGGED_IN, false)) {
                new AlertDialog.Builder(this)
                        .setTitle("需要登录")
                        .setMessage("使用申请的AI需要先登录账号。没有账号？请先申请。")
                        .setPositiveButton("去登录", (d, w) ->
                                startActivity(new Intent(this, LoginActivity.class)))
                        .setNegativeButton("返回配置", (d, w) ->
                                startActivity(new Intent(this, SettingsActivity.class)))
                        .show();
                return;
            }
            if ("banned".equals(sp.getString(App.KEY_ACCOUNT_STATUS, "enabled"))) {
                Toast.makeText(this, "账号已被封禁，无法使用", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            if (!aiClient.isConfigured()) {
                new AlertDialog.Builder(this)
                        .setTitle("AI未配置")
                        .setMessage("请先在设置中配置AI的地址、API Key和模型名称。")
                        .setPositiveButton("去配置", (d, w) ->
                                startActivity(new Intent(this, SettingsActivity.class)))
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
        }

        startActivity(new Intent(this, ChatActivity.class));
    }
}
