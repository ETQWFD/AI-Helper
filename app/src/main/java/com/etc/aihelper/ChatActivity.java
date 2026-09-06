package com.etc.aihelper;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private LinearLayout chatContainer;
    private ScrollView scrollView;
    private EditText etInput;
    private Button btnSend;
    private TextView tvScreenInfo;
    private AIClient aiClient;
    private boolean isProcessing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable thinkingRunnable;
    private TextView thinkingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        aiClient = new AIClient(this);
        chatContainer = findViewById(R.id.chat_container);
        scrollView = findViewById(R.id.scroll_chat);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);
        tvScreenInfo = findViewById(R.id.tv_screen_info);

        addMessage("system", "AI帮助器 v0.2 已启动。无障碍服务已连接，可以读取屏幕并执行操作。请输入你的需求。");

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_refresh_screen).setOnClickListener(v -> refreshScreenInfo());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        refreshScreenInfo();
    }

    private void refreshScreenInfo() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null && MyAccessibilityService.isServiceEnabled()) {
            String desc = service.getScreenDescription();
            tvScreenInfo.setText(desc.length() > 400 ? desc.substring(0, 400) + "..." : desc);
        } else {
            tvScreenInfo.setText("无障碍服务未连接，请返回主界面开启");
        }
    }

    private void sendMessage() {
        if (isProcessing) {
            Toast.makeText(this, "正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        addMessage("user", text);
        etInput.setText("");
        isProcessing = true;
        btnSend.setEnabled(false);
        btnSend.setText("处理中");

        showThinking();

        String screenContext = "";
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) screenContext = service.getScreenDescription();

        aiClient.sendMessage(text, screenContext, new AIClient.Callback() {
            @Override
            public void onSuccess(String response, String actionsJson) {
                hideThinking();
                isProcessing = false;
                btnSend.setEnabled(true);
                btnSend.setText("发送");
                typeMessage(response, () -> {
                    if (actionsJson != null && !actionsJson.isEmpty()) {
                        addMessage("system", "正在执行操作...");
                        executeActionsAsync(actionsJson);
                    }
                });
            }

            @Override
            public void onError(String error) {
                hideThinking();
                isProcessing = false;
                btnSend.setEnabled(true);
                btnSend.setText("发送");
                addMessage("system", "错误: " + error);
            }
        });
    }

    private void showThinking() {
        thinkingView = new TextView(this);
        thinkingView.setText("正在思考.");
        thinkingView.setTextSize(15);
        thinkingView.setTextColor(0xFF8899AA);
        thinkingView.setPadding(24, 12, 24, 12);
        chatContainer.addView(thinkingView);
        scrollToBottom();

        final int[] dotCount = {1};
        thinkingRunnable = new Runnable() {
            @Override
            public void run() {
                if (thinkingView != null) {
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < dotCount[0]; i++) dots.append(".");
                    thinkingView.setText("正在思考" + dots.toString());
                    dotCount[0] = dotCount[0] % 3 + 1;
                    handler.postDelayed(this, 400);
                }
            }
        };
        handler.postDelayed(thinkingRunnable, 400);
    }

    private void hideThinking() {
        if (thinkingRunnable != null) {
            handler.removeCallbacks(thinkingRunnable);
            thinkingRunnable = null;
        }
        if (thinkingView != null) {
            chatContainer.removeView(thinkingView);
            thinkingView = null;
        }
    }

    private void typeMessage(String text, Runnable onComplete) {
        TextView tv = new TextView(this);
        tv.setTextSize(15);
        tv.setTextColor(0xFFB0C4DE);
        tv.setPadding(24, 12, 24, 12);
        chatContainer.addView(tv);
        scrollToBottom();

        final int[] index = {0};
        final char[] chars = text.toCharArray();
        Runnable typeRunnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] < chars.length) {
                    int batch = Math.min(3, chars.length - index[0]);
                    tv.append(new String(chars, index[0], batch));
                    index[0] += batch;
                    scrollToBottom();
                    handler.postDelayed(this, 25);
                } else {
                    if (onComplete != null) onComplete.run();
                }
            }
        };
        handler.post(typeRunnable);
    }

    private void executeActionsAsync(String actionsJson) {
        new Thread(() -> {
            MyAccessibilityService service = MyAccessibilityService.getInstance();
            if (service == null) {
                runOnUiThread(() -> addMessage("system", "无障碍服务未连接，无法执行操作"));
                return;
            }
            List<String> results = service.executeActions(actionsJson);
            StringBuilder sb = new StringBuilder("操作结果:\n");
            for (String r : results) sb.append("- ").append(r).append("\n");
            runOnUiThread(() -> {
                addMessage("system", sb.toString());
                refreshScreenInfo();
            });
        }).start();
    }

    private void addMessage(String role, String content) {
        TextView tv = new TextView(this);
        tv.setTextSize(15);
        tv.setPadding(24, 12, 24, 12);
        int color;
        switch (role) {
            case "user":
                color = 0xFF90EE90;
                tv.setGravity(Gravity.END);
                tv.setText("你: " + content);
                break;
            case "ai":
                color = 0xFFB0C4DE;
                tv.setText(content);
                break;
            default:
                color = 0xFFFFA500;
                tv.setText(content);
                break;
        }
        tv.setTextColor(color);
        chatContainer.addView(tv);
        scrollToBottom();
    }

    private void scrollToBottom() {
        handler.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 50);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (thinkingRunnable != null) handler.removeCallbacks(thinkingRunnable);
    }
}
