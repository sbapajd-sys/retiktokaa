package com.retiktok.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.content.SharedPreferences;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Switch toggleSwitch;
    private TextView statusText;
    private EditText telegramInput;
    private Button saveBtn;
    private LinearLayout setupBar;
    private LinearLayout controlBar;
    private boolean extensionEnabled = false;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String SERVER_URL = "https://api.editingnews.com";

    private static final String BASE_SCRIPT =
        "(function() {" +
        "  if (window.__ReTikTokInjected) return;" +
        "  window.__ReTikTokInjected = true;" +
        "  const WM = '\\u2605 upload method \\u2605 TG: @editing_news';" +
        "  function fixBody(body) {" +
        "    if (!body || typeof body !== 'string') return body;" +
        "    body = body.split(WM).join('').trimEnd();" +
        "    try {" +
        "      const obj = JSON.parse(body);" +
        "      function walk(o) {" +
        "        if (!o || typeof o !== 'object') return;" +
        "        for (const k of Object.keys(o)) {" +
        "          if (typeof o[k] === 'string') {" +
        "            if (o[k]==='SELF_ONLY'||o[k]==='MUTUAL_FOLLOW_FRIENDS'||o[k]==='FOLLOWER_OF_CREATOR') o[k]='PUBLIC_TO_EVERYONE';" +
        "          } else if (typeof o[k] === 'object') { walk(o[k]); }" +
        "        }" +
        "      }" +
        "      walk(obj); return JSON.stringify(obj);" +
        "    } catch(e) { return body; }" +
        "  }" +
        "  function fixFormData(body) {" +
        "    if (body instanceof FormData) {" +
        "      for (const [k,v] of body.entries()) {" +
        "        if (typeof v==='string') {" +
        "          let f=v.split(WM).join('').trimEnd();" +
        "          if (f==='SELF_ONLY'||f==='MUTUAL_FOLLOW_FRIENDS'||f==='FOLLOWER_OF_CREATOR') f='PUBLIC_TO_EVERYONE';" +
        "          if (f!==v) body.set(k,f);" +
        "        }" +
        "      }" +
        "    }" +
        "    if (body instanceof URLSearchParams) {" +
        "      for (const [k,v] of body.entries()) {" +
        "        let f=v.split(WM).join('').trimEnd();" +
        "        if (f==='SELF_ONLY'||f==='MUTUAL_FOLLOW_FRIENDS'||f==='FOLLOWER_OF_CREATOR') f='PUBLIC_TO_EVERYONE';" +
        "        if (f!==v) body.set(k,f);" +
        "      }" +
        "    }" +
        "    return body;" +
        "  }" +
        "  const _fetch=window.fetch.bind(window);" +
        "  const _xhrSend=XMLHttpRequest.prototype.send;" +
        "  function safeFetch(input,init){init=init||{};if(init.body){if(typeof init.body==='string')init.body=fixBody(init.body);else init.body=fixFormData(init.body);}return _fetch(input,init);}" +
        "  function safeXHRSend(body){if(typeof body==='string')body=fixBody(body);else body=fixFormData(body);_xhrSend.call(this,body);}" +
        "  Object.defineProperty(window,'fetch',{get:()=>safeFetch,set:()=>{},configurable:false});" +
        "  Object.defineProperty(XMLHttpRequest.prototype,'send',{get:()=>safeXHRSend,set:()=>{},configurable:false});" +
        "  function cleanNode(node){if(!node)return;if((node.tagName==='INPUT'||node.tagName==='TEXTAREA')&&typeof node.value==='string'&&node.value.includes(WM)){node.value=node.value.split(WM).join('').trimEnd();node.dispatchEvent(new Event('input',{bubbles:true}));}if(node.isContentEditable&&node.innerText&&node.innerText.includes(WM)){node.childNodes.forEach(c=>{if(c.nodeType===Node.TEXT_NODE&&c.textContent.includes(WM))c.textContent=c.textContent.split(WM).join('').trimEnd();});node.dispatchEvent(new Event('input',{bubbles:true}));}}" +
        "  setInterval(()=>document.querySelectorAll('input,textarea,[contenteditable]').forEach(cleanNode),100);" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "UseSwitchCompatOrMaterialCode", "HardwareIds"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs        = getSharedPreferences("retiktok", MODE_PRIVATE);
        webView      = findViewById(R.id.webview);
        toggleSwitch = findViewById(R.id.toggleSwitch);
        statusText   = findViewById(R.id.statusText);
        telegramInput= findViewById(R.id.telegramInput);
        saveBtn      = findViewById(R.id.saveBtn);
        setupBar     = findViewById(R.id.setupBar);
        controlBar   = findViewById(R.id.controlBar);
        String saved = prefs.getString("telegramUsername", "");
        if (!saved.isEmpty()) {
            setupBar.setVisibility(View.GONE);
            controlBar.setVisibility(View.VISIBLE);
        } else {
            setupBar.setVisibility(View.VISIBLE);
            controlBar.setVisibility(View.GONE);
        }
        saveBtn.setOnClickListener(v -> {
            String username = telegramInput.getText().toString().trim().replace("@", "");
            if (!username.isEmpty()) {
                prefs.edit().putString("telegramUsername", username).apply();
                setupBar.setVisibility(View.GONE);
                controlBar.setVisibility(View.VISIBLE);
            }
        });
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (extensionEnabled && url != null && url.contains("tiktok.com")) {
                    webView.evaluateJavascript(BASE_SCRIPT, null);
                }
            }
        });
        webView.loadUrl("https://www.tiktok.com/tiktokstudio/upload");
        toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            extensionEnabled = isChecked;
            if (isChecked) {
                updateStatus("⏳ Loading...", 0xFFFFAA00);
                webView.evaluateJavascript(BASE_SCRIPT, null);
                fetchAndInjectChunks();
            } else {
                webView.reload();
                updateStatus("🔴 Extension OFF", 0xFFFF4444);
            }
        });
    }

    private String getFingerprint() {
        String cached = prefs.getString("fingerprint", "");
        if (!cached.isEmpty()) return cached;
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String fp = "android_" + androidId;
        prefs.edit().putString("fingerprint", fp).apply();
        return fp;
    }

    private void fetchAndInjectChunks() {
        String username = prefs.getString("telegramUsername", "");
        String fingerprint = getFingerprint();
        if (username.isEmpty()) return;
        executor.execute(() -> {
            try {
                JSONObject body1 = new JSONObject();
                body1.put("userId", username);
                body1.put("fingerprint", fingerprint);
                String resp1 = post(SERVER_URL + "/api/secure/chunks", body1.toString());
                if (resp1 == null) { mainHandler.post(() -> updateStatus("❌ Server error", 0xFFFF4444)); return; }
                JSONObject json1 = new JSONObject(resp1);
                if (!json1.optBoolean("success", false)) { mainHandler.post(() -> updateStatus("❌ Not subscribed", 0xFFFF4444)); return; }
                JSONArray chunkNames = json1.getJSONArray("chunks");
                List<String> codes = new ArrayList<>();
                for (int i = 0; i < chunkNames.length(); i++) {
                    String name = chunkNames.getString(i);
                    JSONObject body2 = new JSONObject();
                    body2.put("userId", username);
                    body2.put("fingerprint", fingerprint);
                    String resp2 = post(SERVER_URL + "/api/secure/chunk/" + name, body2.toString());
                    if (resp2 != null) {
                        JSONObject json2 = new JSONObject(resp2);
                        if (json2.optBoolean("success", false)) {
                            String code = json2.optString("encryptedCode", "");
                            if (!code.isEmpty()) codes.add(code);
                        }
                    }
                }
                mainHandler.post(() -> {
                    for (String code : codes) {
                        String safe = code.replace("\\", "\\\\").replace("`", "\\`");
                        webView.evaluateJavascript("(function(){try{new Function('window','document',`" + safe + "`)(window,document);}catch(e){}})();", null);
                    }
                    updateStatus(codes.isEmpty() ? "🟡 Base only" : "🟢 ON — " + codes.size() + " modules", codes.isEmpty() ? 0xFFFFAA00 : 0xFF1DB954);
                });
            } catch (Exception e) {
                mainHandler.post(() -> updateStatus("❌ Error", 0xFFFF4444));
            }
        });
    }

    private String post(String urlStr, String jsonBody) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) { os.write(jsonBody.getBytes(StandardCharsets.UTF_8)); }
            if (conn.getResponseCode() != 200) return null;
            Scanner sc = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name());
            StringBuilder sb = new StringBuilder();
            while (sc.hasNextLine()) sb.append(sc.nextLine());
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private void updateStatus(String text, int color) {
        statusText.setText(text);
        statusText.setTextColor(color);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
```

---

**File 3 — filename:** `gradle/wrapper/gradle-wrapper.properties`
```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
