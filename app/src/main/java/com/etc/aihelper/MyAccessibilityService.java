package com.etc.aihelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "AIHelper_A11y";
    private static MyAccessibilityService instance;
    private boolean serviceEnabled = false;

    public static boolean isServiceEnabled() {
        return instance != null && instance.serviceEnabled;
    }

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        serviceEnabled = true;
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not actively processing events; we query window content on demand
    }

    @Override
    public void onInterrupt() {
        serviceEnabled = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceEnabled = false;
        if (instance == this) instance = null;
    }

    /**
     * Get a text description of the current screen for AI analysis.
     */
    public String getScreenDescription() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "无法获取屏幕内容";
            StringBuilder sb = new StringBuilder();
            sb.append("当前包名: ").append(root.getPackageName()).append("\n");
            sb.append("屏幕元素:\n");
            dumpNode(root, sb, 0, new int[]{0});
            root.recycle();
            return sb.toString();
        } catch (Exception e) {
            return "屏幕分析错误: " + e.getMessage();
        }
    }

    private void dumpNode(AccessibilityNodeInfo node, StringBuilder sb, int depth, int[] count) {
        if (node == null || count[0] > 100) return;
        count[0]++;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < depth; i++) line.append("  ");
        line.append("[");
        String cls = node.getClassName() != null ? node.getClassName().toString() : "?";
        // Shorten class name
        int dot = cls.lastIndexOf('.');
        if (dot >= 0) cls = cls.substring(dot + 1);
        line.append(cls);

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            line.append(" text=\"").append(text.toString().replace("\n", " ")).append("\"");
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) {
            line.append(" desc=\"").append(desc.toString().replace("\n", " ")).append("\"");
        }
        if (node.isClickable()) line.append(" clickable");
        if (node.isScrollable()) line.append(" scrollable");
        if (node.isEditable()) line.append(" editable");
        if (node.isCheckable()) line.append(" checkable");
        line.append(" bounds=").append(bounds.left).append(",").append(bounds.top)
                .append("-").append(bounds.right).append(",").append(bounds.bottom);
        line.append("]");

        // Only include meaningful nodes
        boolean meaningful = (text != null && text.length() > 0)
                || (desc != null && desc.length() > 0)
                || node.isClickable() || node.isEditable() || node.isScrollable();
        if (meaningful) {
            sb.append(line).append("\n");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNode(child, sb, depth + 1, count);
                child.recycle();
            }
        }
    }

    /**
     * Execute a tap at given coordinates.
     */
    public boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    /**
     * Execute a swipe gesture.
     */
    public boolean swipe(int x1, int y1, int x2, int y2, int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    /**
     * Input text into the currently focused field.
     */
    public boolean inputText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            // Find focused or first editable field
            AccessibilityNodeInfo target = findEditable(root);
            if (target == null) {
                root.recycle();
                return false;
            }
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean result = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            target.recycle();
            root.recycle();
            return result;
        } catch (Exception e) {
            Log.e(TAG, "inputText error", e);
            return false;
        }
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isFocused()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findEditable(child);
            if (found != null) return found;
            if (child != null) child.recycle();
        }
        // If no focused editable, return first editable
        if (node.isEditable()) return node;
        return null;
    }

    /**
     * Perform back button.
     */
    public boolean goBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /**
     * Perform home button.
     */
    public boolean goHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /**
     * Execute a list of action commands from AI response.
     */
    public List<String> executeActions(String actionsJson) {
        List<String> results = new ArrayList<>();
        if (actionsJson == null || actionsJson.isEmpty()) return results;
        try {
            org.json.JSONObject json = new org.json.JSONObject(actionsJson);
            org.json.JSONArray actions = json.getJSONArray("actions");
            for (int i = 0; i < actions.length(); i++) {
                org.json.JSONObject action = actions.getJSONObject(i);
                String type = action.getString("type");
                switch (type) {
                    case "tap":
                        int tx = action.getInt("x");
                        int ty = action.getInt("y");
                        boolean t = tap(tx, ty);
                        results.add("点击(" + tx + "," + ty + "): " + (t ? "成功" : "失败"));
                        Thread.sleep(400);
                        break;
                    case "swipe":
                        int sx1 = action.getInt("x1");
                        int sy1 = action.getInt("y1");
                        int sx2 = action.getInt("x2");
                        int sy2 = action.getInt("y2");
                        int dur = action.optInt("duration", 300);
                        boolean s = swipe(sx1, sy1, sx2, sy2, dur);
                        results.add("滑动: " + (s ? "成功" : "失败"));
                        Thread.sleep(500);
                        break;
                    case "text":
                        String txt = action.getString("text");
                        boolean it = inputText(txt);
                        results.add("输入文字: " + (it ? "成功" : "失败"));
                        Thread.sleep(300);
                        break;
                    case "back":
                        goBack();
                        results.add("返回");
                        Thread.sleep(400);
                        break;
                    case "home":
                        goHome();
                        results.add("主页");
                        Thread.sleep(400);
                        break;
                    default:
                        results.add("未知操作: " + type);
                }
            }
        } catch (Exception e) {
            results.add("操作解析错误: " + e.getMessage());
        }
        return results;
    }
}
