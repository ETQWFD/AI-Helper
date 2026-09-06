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
    private volatile boolean serviceEnabled = false;

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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() { serviceEnabled = false; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceEnabled = false;
        if (instance == this) instance = null;
    }

    public String getScreenDescription() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "无法获取屏幕内容";
            StringBuilder sb = new StringBuilder();
            sb.append("包名:").append(root.getPackageName()).append("\n元素:\n");
            int[] count = {0};
            dumpNode(root, sb, count);
            root.recycle();
            if (count[0] == 0) sb.append("(无可交互元素)\n");
            return sb.toString();
        } catch (Exception e) {
            return "屏幕分析错误: " + e.getMessage();
        }
    }

    private void dumpNode(AccessibilityNodeInfo node, StringBuilder sb, int[] count) {
        if (node == null || count[0] >= 60) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean hasText = text != null && text.length() > 0;
        boolean hasDesc = desc != null && desc.length() > 0;
        boolean interactive = node.isClickable() || node.isEditable() || node.isScrollable() || node.isCheckable();
        if ((hasText || hasDesc || interactive) && bounds.width() > 0 && bounds.height() > 0) {
            count[0]++;
            String cls = node.getClassName() != null ? node.getClassName().toString() : "?";
            int dot = cls.lastIndexOf('.');
            if (dot >= 0) cls = cls.substring(dot + 1);
            sb.append(count[0]).append(".[").append(cls);
            if (hasText) {
                String t = text.toString().replace("\n", " ").trim();
                if (t.length() > 30) t = t.substring(0, 30) + "..";
                sb.append(" t=\"").append(t).append("\"");
            }
            if (hasDesc) {
                String d = desc.toString().replace("\n", " ").trim();
                if (d.length() > 20) d = d.substring(0, 20) + "..";
                sb.append(" d=\"").append(d).append("\"");
            }
            if (node.isClickable()) sb.append(" C");
            if (node.isEditable()) sb.append(" E");
            if (node.isScrollable()) sb.append(" S");
            sb.append(" @").append(bounds.centerX()).append(",").append(bounds.centerY()).append("]\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { dumpNode(child, sb, count); child.recycle(); }
        }
    }

    public boolean tapByText(String targetText) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo found = findNodeByText(root, targetText);
            if (found != null) {
                Rect bounds = new Rect();
                found.getBoundsInScreen(bounds);
                boolean result = found.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                found.recycle(); root.recycle();
                if (result) return true;
                return tap(bounds.centerX(), bounds.centerY());
            }
            root.recycle();
        } catch (Exception e) { Log.e(TAG, "tapByText", e); }
        return false;
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo node, String target) {
        if (node == null) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if ((text != null && text.toString().contains(target)) || (desc != null && desc.toString().contains(target))) {
            AccessibilityNodeInfo cur = node;
            while (cur != null && !cur.isClickable()) cur = cur.getParent();
            return cur != null ? cur : node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findNodeByText(child, target);
                if (found != null) return found;
                child.recycle();
            }
        }
        return null;
    }

    public boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 100, false);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) { return false; }
    }

    public boolean longPress(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 600, false);
            return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Exception e) { return false; }
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration, false);
            return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Exception e) { return false; }
    }

    public boolean inputText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = findEditable(root);
            if (target == null) { root.recycle(); return false; }
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean result = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            target.recycle(); root.recycle();
            return result;
        } catch (Exception e) { return false; }
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
        if (node.isEditable()) return node;
        return null;
    }

    public boolean goBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean goHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }

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
                        int tx = action.getInt("x"), ty = action.getInt("y");
                        results.add("点击(" + tx + "," + ty + "): " + (tap(tx, ty) ? "成功" : "失败"));
                        Thread.sleep(500); break;
                    case "tap_text":
                        String tt = action.getString("text");
                        results.add("点击文字\"" + tt + "\": " + (tapByText(tt) ? "成功" : "失败"));
                        Thread.sleep(500); break;
                    case "long_press":
                        int lx = action.getInt("x"), ly = action.getInt("y");
                        results.add("长按: " + (longPress(lx, ly) ? "成功" : "失败"));
                        Thread.sleep(700); break;
                    case "swipe":
                        boolean s = swipe(action.getInt("x1"), action.getInt("y1"), action.getInt("x2"), action.getInt("y2"), action.optInt("duration", 300));
                        results.add("滑动: " + (s ? "成功" : "失败"));
                        Thread.sleep(600); break;
                    case "text":
                        results.add("输入文字: " + (inputText(action.getString("text")) ? "成功" : "失败"));
                        Thread.sleep(400); break;
                    case "back": goBack(); results.add("返回"); Thread.sleep(500); break;
                    case "home": goHome(); results.add("主页"); Thread.sleep(500); break;
                    case "wait": Thread.sleep(action.optInt("ms", 1000)); results.add("等待"); break;
                    default: results.add("未知操作: " + type);
                }
            }
        } catch (Exception e) { results.add("操作解析错误: " + e.getMessage()); }
        return results;
    }
}
