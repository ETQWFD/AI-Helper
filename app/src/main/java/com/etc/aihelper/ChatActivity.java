package com.etc.aihelper;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView rvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvScreenInfo;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private AIClient aiClient;
    private boolean isProcessing = false;

    public static class ChatMessage {
        public String role; // "user" or "ai" or "system"
        public String content;
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        aiClient = new AIClient(this);
        rvChat = findViewById(R.id.rv_chat);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);
        tvScreenInfo = findViewById(R.id.tv_screen_info);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        addMessage("system", "AI帮助器已启动。无障碍服务已连接，AI可以读取屏幕并执行操作。请输入你的需求。");

        btnSend.setOnClickListener(v -> sendMessage());

        findViewById(R.id.btn_refresh_screen).setOnClickListener(v -> refreshScreenInfo());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        refreshScreenInfo();
    }

    private void refreshScreenInfo() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            String desc = service.getScreenDescription();
            tvScreenInfo.setText(desc.length() > 500 ? desc.substring(0, 500) + "..." : desc);
        } else {
            tvScreenInfo.setText("无障碍服务未连接");
        }
    }

    private void sendMessage() {
        if (isProcessing) {
            Toast.makeText(this, "AI正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        addMessage("user", text);
        etInput.setText("");
        isProcessing = true;
        btnSend.setEnabled(false);

        // Get screen context
        String screenContext = "";
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            screenContext = service.getScreenDescription();
        }

        aiClient.sendMessage(text, screenContext, new AIClient.Callback() {
            @Override
            public void onSuccess(String response, String actionsJson) {
                isProcessing = false;
                btnSend.setEnabled(true);
                addMessage("ai", response);

                // Execute actions if present
                if (actionsJson != null && !actionsJson.isEmpty()) {
                    addMessage("system", "AI正在执行操作...");
                    executeActionsAsync(actionsJson);
                }
            }

            @Override
            public void onError(String error) {
                isProcessing = false;
                btnSend.setEnabled(true);
                addMessage("system", "错误: " + error);
            }
        });
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
            for (String r : results) sb.append("• ").append(r).append("\n");
            runOnUiThread(() -> {
                addMessage("system", sb.toString());
                refreshScreenInfo();
            });
        }).start();
    }

    private void addMessage(String role, String content) {
        messages.add(new ChatMessage(role, content));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
    }

    // Simple adapter
    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<ChatMessage> list;
        ChatAdapter(List<ChatMessage> list) { this.list = list; }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 16, 24, 16);
            tv.setTextSize(15);
            tv.setMaxLines(Integer.MAX_VALUE);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            ChatMessage msg = list.get(position);
            TextView tv = (TextView) holder.itemView;
            String prefix;
            int color;
            switch (msg.role) {
                case "user": prefix = "🧑 你: "; color = 0xFF4CAF50; break;
                case "ai": prefix = "🤖 AI: "; color = 0xFF2196F3; break;
                default: prefix = "ℹ️ "; color = 0xFFFF9800; break;
            }
            tv.setText(prefix + msg.content);
            tv.setTextColor(color);
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(android.view.View v) { super(v); }
        }
    }
}
