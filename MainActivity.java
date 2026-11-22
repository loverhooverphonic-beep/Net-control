package com.example.netcontrol;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 23, 42);
    private static final int PANEL = Color.rgb(30, 41, 59);
    private static final int TEXT = Color.rgb(241, 245, 249);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int GREEN = Color.rgb(74, 222, 128);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText routerField, userField, passField;
    private TextView statusView, countView, deviceListView, updatedView;
    private WebView routerWeb;
    private LinearLayout root;
    private String username = "";
    private String password = "";
    private boolean connected = false;
    private boolean autoRefresh = true;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (autoRefresh) {
                scanLan();
                handler.postDelayed(this, 10 * 60 * 1000L);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        handler.postDelayed(refreshTask, 10 * 60 * 1000L);
        scanLan();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void addGap(LinearLayout l, int h) {
        View v = new View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private void styleButton(Button b) {
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235)));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = column();
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("NET CONTROL", 23, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text("V1 • LOCAL", 11, GREEN, true);
        header.addView(tag);
        root.addView(header);
        addGap(root, 8);
        root.addView(text("TP-Link TD-W8901N V3", 14, MUTED, false));
        addGap(root, 18);

        LinearLayout routerCard = card();
        routerCard.addView(text("ROUTER CONNECTION", 12, MUTED, true));
        addGap(routerCard, 8);
        routerField = new EditText(this);
        routerField.setSingleLine(true);
        routerField.setText("http://192.168.1.1");
        routerField.setTextColor(TEXT);
        routerField.setHintTextColor(MUTED);
        routerField.setHint("Router address");
        routerCard.addView(routerField, new LinearLayout.LayoutParams(-1, dp(48)));

        userField = new EditText(this);
        userField.setSingleLine(true);
        userField.setTextColor(TEXT);
        userField.setHintTextColor(MUTED);
        userField.setHint("Router username");
        routerCard.addView(userField, new LinearLayout.LayoutParams(-1, dp(48)));

        passField = new EditText(this);
        passField.setSingleLine(true);
        passField.setInputType(129);
        passField.setTextColor(TEXT);
        passField.setHintTextColor(MUTED);
        passField.setHint("Router password");
        routerCard.addView(passField, new LinearLayout.LayoutParams(-1, dp(48)));

        Button connect = new Button(this);
        connect.setText("Open Router Admin");
        styleButton(connect);
        routerCard.addView(connect, new LinearLayout.LayoutParams(-1, dp(48)));
        connect.setOnClickListener(v -> openRouter());
        root.addView(routerCard);

        addGap(root, 16);
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout countCard = card();
        countCard.addView(text("LAN DEVICES FOUND", 11, MUTED, true));
        countView = text("—", 30, TEXT, true);
        countCard.addView(countView);
        LinearLayout intervalCard = card();
        intervalCard.addView(text("AUTO REFRESH", 11, MUTED, true));
        intervalCard.addView(text("10 minutes", 22, TEXT, true));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(0, 0, dp(7), 0);
        summary.addView(countCard, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1);
        half2.setMargins(dp(7), 0, 0, 0);
        summary.addView(intervalCard, half2);
        root.addView(summary);

        addGap(root, 16);
        LinearLayout devicesCard = card();
        LinearLayout devHeader = new LinearLayout(this);
        devHeader.setGravity(Gravity.CENTER_VERTICAL);
        devHeader.addView(text("DISCOVERED DEVICES", 12, MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button scan = new Button(this);
        scan.setText("Scan now");
        styleButton(scan);
        devHeader.addView(scan);
        scan.setOnClickListener(v -> scanLan());
        devicesCard.addView(devHeader);
        addGap(devicesCard, 10);
        deviceListView = text("Scanning the local network…", 13, TEXT, false);
        devicesCard.addView(deviceListView);
        updatedView = text("Last scan: not yet completed", 11, MUTED, false);
        devicesCard.addView(updatedView);
        root.addView(devicesCard);

        addGap(root, 14);
        statusView = text("Ready. Connect your phone to the TP-Link Wi-Fi network.", 12, MUTED, false);
        root.addView(statusView);

        addGap(root, 18);
        root.addView(text("ROUTER ADMIN PAGE", 12, MUTED, true));
        addGap(root, 6);
        routerWeb = new WebView(this);
        routerWeb.getSettings().setJavaScriptEnabled(true);
        routerWeb.getSettings().setDomStorageEnabled(true);
        routerWeb.getSettings().setLoadsImagesAutomatically(true);
        routerWeb.setBackgroundColor(Color.WHITE);
        routerWeb.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                              String host, String realm) {
                handler.proceed(username, password);
            }
            @Override public void onPageFinished(WebView view, String url) {
                connected = true;
                statusView.setText("Router page loaded. Use the router's own menu to view DHCP clients or wireless clients.");
            }
        });
        routerWeb.setWebChromeClient(new WebChromeClient());
        root.addView(routerWeb, new LinearLayout.LayoutParams(-1, dp(360)));
    }

    private LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(16));
        l.setBackground(bg);
        return l;
    }

    private void openRouter() {
        String url = routerField.getText().toString().trim();
        if (url.isEmpty()) url = "http://192.168.1.1";
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        username = userField.getText().toString();
        password = passField.getText().toString();
        statusView.setText("Opening router administration page…");
        routerWeb.loadUrl(url);
    }

    private void scanLan() {
        statusView.setText("Scanning local network. This may take a little while…");
        deviceListView.setText("Scanning…");
        worker.execute(() -> {
            List<String> found = new ArrayList<>();
            try {
                // The default subnet for this router is 192.168.1.x.
                // Reachability checks only inspect the local LAN.
                for (int i = 1; i <= 254; i++) {
                    String ip = "192.168.1." + i;
                    try {
                        InetAddress address = InetAddress.getByName(ip);
                        if (address.isReachable(90)) {
                            found.add(ip);
                        }
                    } catch (Exception ignored) { }
                }
                List<String> arp = readArpTable();
                final StringBuilder out = new StringBuilder();
                if (found.isEmpty() && arp.isEmpty()) {
                    out.append("No responding devices found yet. Android may limit LAN discovery.\\n")
                       .append("Open Router Admin and check Interface Setup > LAN > DHCP Table.");
                } else {
                    out.append("IP ADDRESS                         MAC ADDRESS\\n");
                    for (String ip : found) {
                        String mac = findMac(arp, ip);
                        out.append(ip).append("        ").append(mac == null ? "Not available" : mac).append("\n");
                    }
                    if (!arp.isEmpty()) {
                        out.append("\nARP cache entries:\n");
                        for (String line : arp) out.append(line).append("\n");
                    }
                }
                final int n = found.size();
                runOnUiThread(() -> {
                    countView.setText(String.valueOf(n));
                    deviceListView.setText(out.toString());
                    updatedView.setText("Last scan: just now • automatic interval: 10 minutes");
                    statusView.setText("LAN scan finished. Results depend on Android network visibility.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusView.setText("Scan error: " + e.getMessage()));
            }
        });
    }

    private List<String> readArpTable() {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4 && !p[3].equals("00:00:00:00:00:00")) {
                    lines.add(p[0] + "    " + p[3]);
                }
            }
        } catch (Exception ignored) { }
        return lines;
    }

    private String findMac(List<String> arp, String ip) {
        for (String line : arp) {
            if (line.startsWith(ip + " ")) return line.substring(ip.length()).trim();
        }
        return null;
    }

    @Override protected void onDestroy() {
        autoRefresh = false;
        handler.removeCallbacks(refreshTask);
        if (routerWeb != null) routerWeb.destroy();
        worker.shutdownNow();
        super.onDestroy();
    }
}
