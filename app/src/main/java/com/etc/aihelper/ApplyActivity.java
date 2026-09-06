package com.etc.aihelper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ApplyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apply);

        TextView tvContent = findViewById(R.id.tv_apply_content);
        Button btnCopyEmail = findViewById(R.id.btn_copy_email);
        Button btnBack = findViewById(R.id.btn_apply_back);

        String content = "【账号申请说明】\n\n" +
                "感谢您使用 AI帮助器！由于开发者比较忙于上学，请见谅。\n\n" +
                "申请方式：请通过邮箱发送申请信息给我们。\n\n" +
                "申请邮箱：" + App.APPLY_EMAIL + "\n\n" +
                "邮件中请务必包含以下信息：\n" +
                "1. 您的真实名称\n" +
                "2. 您需要注册的用户名\n" +
                "3. 您的QQ号或其他联系邮箱\n" +
                "4. 您将要设置的密码\n\n" +
                "我们将会重视每一份申请。\n\n" +
                "若一年之内没有收到回复，请重新发送申请。\n\n" +
                "申请通过后，您将获得：\n" +
                "• 专属账号，可直接登录使用\n" +
                "• 免配置AI（开发者已预配置好AI地址、Key和模型）\n" +
                "• 专属头像\n" +
                "• 实时账号状态同步";

        tvContent.setText(content);

        btnCopyEmail.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("email", App.APPLY_EMAIL));
            Toast.makeText(this, "邮箱已复制: " + App.APPLY_EMAIL, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> finish());
    }
}
