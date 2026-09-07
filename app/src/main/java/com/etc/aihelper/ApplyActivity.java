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

        String content = "【使用方法】\n\n" +
                "1. 在主界面点击「获取无障碍模式」，在系统设置中开启本应用的无障碍服务（每次启动需重新确认）。\n\n" +
                "2. 进入「设置」配置AI：\n" +
                "   • 自定义AI：输入完整的API地址、Key和模型名称，保存即可使用\n" +
                "   • 申请的AI：需先登录账号，登录后自动使用开发者预配置的AI\n\n" +
                "3. 返回主界面点击「开始」，确认无障碍和AI配置后进入聊天界面。\n\n" +
                "4. 输入你的需求（如\"帮我打游戏\"），AI会分析当前屏幕并自动执行点击、滑动、输入等操作。\n\n" +
                "5. 在「设置」中可检测更新，有新版本时应用内自动下载安装。\n\n" +
                "═══════════════════════\n\n" +
                "【账号申请说明】\n\n" +
                "申请通过后可免配置AI直接使用，并获得专属头像。\n\n" +
                "申请邮箱：" + App.APPLY_EMAIL + "\n\n" +
                "邮件中请包含：\n" +
                "1. 您的真实名称\n" +
                "2. 需要注册的用户名\n" +
                "3. QQ号或其他联系邮箱\n" +
                "4. 将要设置的密码\n\n" +
                "开发者目前是初中生，学业比较忙，可能无暇顾及，一般会在放假时查看邮件。\n" +
                "我们将会在一年之内回复您，请见谅。\n" +
                "若一年之内未收到回复，请重新发送申请。";

        tvContent.setText(content);

        btnCopyEmail.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("email", App.APPLY_EMAIL));
            Toast.makeText(this, "邮箱已复制: " + App.APPLY_EMAIL, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> finish());
    }
}
