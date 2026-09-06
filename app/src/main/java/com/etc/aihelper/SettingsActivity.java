package com.etc.aihelper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private RadioGroup rgAiMode;
    private RadioButton rbCustom, rbApplied;
    private View layoutCustom;
    private EditText etAiUrl, etAiKey, etAiName;
    private Button btnSave;
    private TextView tvUpdateInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rgAiMode = findViewById(R.id.rg_ai_mode);
        rbCustom = findViewById(R.id.rb_custom);
        rbApplied = findViewById(R.id.rb_applied);
        layoutCustom = findViewById(R.id.layout_custom_ai);
        etAiUrl = findViewById(R.id.et_ai_url);
        etAiKey = findViewById(R.id.et_ai_key);
        etAiName = findViewById(R.id.et_ai_name);
        btnSave = findViewById(R.id.btn_save_ai);
        tvUpdateInfo = findViewById(R.id.tv_update_info);

        // Load saved config
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        String mode = sp.getString(App.KEY_AI_MODE, "custom");
        if ("applied".equals(mode)) {
            rbApplied.setChecked(true);
            layoutCustom.setVisibility(View.GONE);
        } else {
            rbCustom.setChecked(true);
            layoutCustom.setVisibility(View.VISIBLE);
        }
        etAiUrl.setText(sp.getString(App.KEY_AI_URL, ""));
        etAiKey.setText(sp.getString(App.KEY_AI_KEY, ""));
        etAiName.setText(sp.getString(App.KEY_AI_NAME, ""));

        rgAiMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_applied) {
                layoutCustom.setVisibility(View.GONE);
            } else {
                layoutCustom.setVisibility(View.VISIBLE);
            }
        });

        btnSave.setOnClickListener(v -> saveConfig());

        findViewById(R.id.btn_apply).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ApplyActivity.class)));

        findViewById(R.id.btn_check_update).setOnClickListener(v -> checkUpdate());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void saveConfig() {
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        if (rbApplied.isChecked()) {
            // Check if logged in
            if (!sp.getBoolean(App.KEY_LOGGED_IN, false)) {
                new AlertDialog.Builder(this)
                        .setTitle("需要登录")
                        .setMessage("使用申请的AI配置需要先登录账号。\n\n如果你还没有账号，请点击\"申请账号\"发送邮件申请。\n\n申请邮箱: " + App.APPLY_EMAIL)
                        .setPositiveButton("去登录", (d, w) ->
                                startActivity(new android.content.Intent(this, LoginActivity.class)))
                        .setNegativeButton("取消", null)
                        .show();
                // Revert to custom
                rbCustom.setChecked(true);
                layoutCustom.setVisibility(View.VISIBLE);
                return;
            }
            editor.putString(App.KEY_AI_MODE, "applied");
            Toast.makeText(this, "已切换为申请的AI配置", Toast.LENGTH_SHORT).show();
        } else {
            String url = etAiUrl.getText().toString().trim();
            String key = etAiKey.getText().toString().trim();
            String name = etAiName.getText().toString().trim();
            if (url.isEmpty() || key.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "请填写完整的AI地址、Key和模型名称", Toast.LENGTH_SHORT).show();
                return;
            }
            editor.putString(App.KEY_AI_MODE, "custom");
            editor.putString(App.KEY_AI_URL, url);
            editor.putString(App.KEY_AI_KEY, key);
            editor.putString(App.KEY_AI_NAME, name);
            Toast.makeText(this, "AI配置已保存", Toast.LENGTH_SHORT).show();
        }
        editor.apply();
        finish();
    }

    private void checkUpdate() {
        tvUpdateInfo.setText("正在检查更新...");
        new UpdateChecker(this).checkForUpdate(new UpdateChecker.CheckCallback() {
            @Override
            public void onUpdateAvailable(String version, String downloadUrl, String releaseNotes) {
                tvUpdateInfo.setText("发现新版本: v" + version);
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("发现新版本 v" + version)
                        .setMessage("更新内容:\n" + (releaseNotes.isEmpty() ? "无" : releaseNotes)
                                + "\n\n是否立即下载更新？")
                        .setPositiveButton("立即更新", (d, w) ->
                                new UpdateChecker(SettingsActivity.this)
                                        .downloadAndInstall(downloadUrl, version, SettingsActivity.this))
                        .setNegativeButton("暂不更新", (d, w) ->
                                tvUpdateInfo.setText("当前版本: v0.1（已有新版本 v" + version + "）"))
                        .show();
            }

            @Override
            public void onNoUpdate() {
                tvUpdateInfo.setText("当前已是最新版本 v0.1");
            }

            @Override
            public void onError(String error) {
                tvUpdateInfo.setText("检查更新失败: " + error);
            }
        });
    }
}
