package com.etc.aihelper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_login_username);
        etPassword = findViewById(R.id.et_login_password);
        btnLogin = findViewById(R.id.btn_login_submit);
        progress = findViewById(R.id.login_progress);

        btnLogin.setOnClickListener(v -> doLogin());

        findViewById(R.id.btn_login_back).setOnClickListener(v -> finish());

        findViewById(R.id.tv_no_account).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ApplyActivity.class)));

        // Check if already logged in
        SharedPreferences sp = getSharedPreferences(App.PREFS, MODE_PRIVATE);
        if (sp.getBoolean(App.KEY_LOGGED_IN, false)) {
            etUsername.setText(sp.getString(App.KEY_USERNAME, ""));
        }
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        new AccountManager(this).login(username, password, new AccountManager.LoginCallback() {
            @Override
            public void onSuccess(String user) {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "登录成功，欢迎 " + user, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("登录失败")
                        .setMessage(error)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
    }
}
