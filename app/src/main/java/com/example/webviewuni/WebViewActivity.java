package com.example.webviewuni;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

public class WebViewActivity extends AppCompatActivity {
    private WebView myWebView;
    private static final int REQUEST_CAMERA_PERMISSION = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        myWebView = findViewById(R.id.webViewQ);
        myWebView.setWebViewClient(new MyWebClient());

        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        myWebView.loadUrl("http://wifi.unimon.ru/");
        myWebView.addJavascriptInterface(new WebAppInterface(), "Android");
    }

    private class MyWebClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            checkUrl(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Toast.makeText(WebViewActivity.this, "Error: " + description, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(WebViewActivity.this, MainActivity.class);
            intent.putExtra("error_code", errorCode);
            startActivity(intent);
            finish();
        }
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void scanQR() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scanQRL();
                }
            });
        }
    }

    private void checkUrl(WebView view, String url) {
        if (url.startsWith("http://wifi.unimon.ru/node/new?")) {
            if (view.getSettings().getJavaScriptEnabled()) {
                view.evaluateJavascript(
                        "(function() {" +
                                "  if (typeof window.Android !== 'undefined' && typeof window.Android.scanQR === 'function') {" +
                                "    var button = document.createElement('button');" +
                                "    button.className = 'btn';" +
                                "    button.textContent = 'Сканировать QR';" +
                                "    button.addEventListener('click', function() {" +
                                "      window.Android.scanQR();" +
                                "    });" +
                                "    var container = document.createElement('div');" +
                                "    container.style.display = 'flex';" +
                                "    container.style.justifyContent = 'center';" +
                                "    container.style.marginTop = '20px';" +
                                "    container.appendChild(button);" +
                                "    document.body.appendChild(container);" +
                                "  }" +
                                "})();",
                        null
                );
            }
        } else if (url.equals("http://wifi.unimon.ru/exit")) {
            Intent intent = new Intent(WebViewActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void scanQRL() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            IntentIntegrator intentIntegrator = new IntentIntegrator(this);
            intentIntegrator.setPrompt(""); // Тут надпись слева на QR
            intentIntegrator.setOrientationLocked(true);
            intentIntegrator.initiateScan();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            if (intentResult.getContents() == null) {
                Toast.makeText(getBaseContext(), "Cancelled", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    JSONObject qrCodeData = new JSONObject(intentResult.getContents());
                    String deveui = qrCodeData.getString("DevEUI");
                    String appeui = qrCodeData.getString("AppEUI");
                    String appkey = qrCodeData.getString("AppKey");

                    myWebView.evaluateJavascript("(function() {" +
                            "document.querySelector('input[name=\"deveui\"]').value='" + deveui + "';" +
                            "document.querySelector('input[name=\"appeui\"]').value='" + appeui + "';" +
                            "document.querySelector('input[name=\"appkey\"]').value='" + appkey + "';" +
                            "})()", null);
                } catch (JSONException e) {
                    Toast.makeText(getBaseContext(), "Invalid QR code format", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
