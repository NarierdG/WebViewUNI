package com.example.webviewuni;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import androidx.cardview.widget.CardView;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    private static final String WIFI_SSID_PREFIX = "AlertBox-";
    private static final String WIFI_PASSWORD = "boxmaster";
    private static final int WIFI_PERMISSION_REQUEST = 1;
    private static final String LOG_FILE_NAME = "log.txt";
    private TextView logTextView;
    private WifiManager wifiManager;
    private Handler handler = new Handler();
    private WebView WebInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        logTextView = findViewById(R.id.logTextView);
        Button startButton = findViewById(R.id.startButton);
        Button openLogButton = findViewById(R.id.openLogButton);
        Button closeLogButton = findViewById(R.id.closeLogButton);
        Button openInfoButton = findViewById(R.id.openInfoButton);
        Button closeInfoButton = findViewById(R.id.closeInfoButton);
        CardView logCardView = findViewById(R.id.logCardView);
        CardView infoCardView = findViewById(R.id.infoCardView);
        Button cleanLogButton = findViewById(R.id.cleanLogButton);

        WebInfo = findViewById(R.id.infoWebView);
        WebInfo.loadUrl("file:///android_asset/instrukciia-po-rabote-s-unimon-loragate.html");
        WebSettings webSettings = WebInfo.getSettings();
        webSettings.setJavaScriptEnabled(true);

        loadLogsFromFile();

        startButton.setOnClickListener(v -> {
            if (checkWifiCapabilities()) {
                connectToWifiAndOpenWebView();
            }
        });

        Intent intent = getIntent();
        if (intent != null) {
            int errorCode = intent.getIntExtra("error_code", 0);
            if (errorCode != 0) {
                logMessage("Ошибка WebView: " + errorCode, Color.RED);
            }
        }

        openLogButton.setOnClickListener(v -> {
            logCardView.setVisibility(View.VISIBLE);
            infoCardView.setVisibility(View.GONE);
        });

        closeLogButton.setOnClickListener(v -> logCardView.setVisibility(View.GONE));
        cleanLogButton.setOnClickListener(v -> clearLogs());

        openInfoButton.setOnClickListener(v -> {
            infoCardView.setVisibility(View.VISIBLE);
            logCardView.setVisibility(View.GONE);
        });

        closeInfoButton.setOnClickListener(v -> infoCardView.setVisibility(View.GONE));
    }

    private void logMessage(String message, int color) {
        SpannableString spannableString = new SpannableString(message + "\n");
        spannableString.setSpan(new ForegroundColorSpan(color), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logTextView.append(spannableString);
        writeLogToFile(message); // Запись сообщения в файл
    }

    private void writeLogToFile(String message) {
        try (FileOutputStream fos = openFileOutput(LOG_FILE_NAME, Context.MODE_APPEND)) {
            fos.write((message + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLogsFromFile() {
        try (FileInputStream fis = openFileInput(LOG_FILE_NAME)) {
            byte[] buffer = new byte[1024];
            int length;
            StringBuilder logBuilder = new StringBuilder();
            while ((length = fis.read(buffer)) != -1) {
                logBuilder.append(new String(buffer, 0, length));
            }
            logTextView.setText(logBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clearLogs() {
        logTextView.setText("");
        try (FileOutputStream fos = openFileOutput(LOG_FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write("".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkWifiCapabilities() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            logMessage("Диспетчер Wi-Fi недоступен", Color.RED);
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            logMessage("Wi-Fi выключен. Включение...", Color.BLACK);
            wifiManager.setWifiEnabled(true);
        }
        return true;
    }

    private void connectToWifiAndOpenWebView() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_PERMISSION_REQUEST);
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, WIFI_PERMISSION_REQUEST);
            return;
        }

        logMessage("Попытка подключения...", Color.BLACK);
        String currentSSID = wifiManager.getConnectionInfo().getSSID();

        if (currentSSID != null && currentSSID.startsWith("\"" + WIFI_SSID_PREFIX)) {
            logMessage("Уже подключен к нужной сети", Color.BLACK);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openWebView();
                }
            }, 1000);
            return;
        }

        handler.postDelayed(() -> {
            wifiManager.startScan();
            List<ScanResult> results = wifiManager.getScanResults();
            String targetSSID = null;
            for (ScanResult result : results) {
                if (result.SSID.startsWith(WIFI_SSID_PREFIX)) {
                    targetSSID = result.SSID;
                    logMessage("Найден нужный SSID: " + targetSSID, Color.BLACK);
                    break;
                }
            }
            if (targetSSID == null) {
                logMessage("Нужный SSID не найден", Color.RED);
                return;
            }

            WifiConfiguration wifiConfig = null;
            for (WifiConfiguration existingConfig : wifiManager.getConfiguredNetworks()) {
                if (existingConfig.SSID.equals("\"" + targetSSID + "\"")) {
                    wifiConfig = existingConfig;
                    logMessage("Найдена существующая конфигурация: " + wifiConfig.SSID, Color.BLACK);
                    break;
                }
            }

            int netId;

            if (wifiConfig == null) {
                wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = "\"" + targetSSID + "\"";
                wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
                logMessage("Создаем новую конфигурацию: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.BLACK);
                netId = wifiManager.addNetwork(wifiConfig);
                logMessage("Добавляем сеть, netId: " + netId, Color.BLACK);
                if (netId == -1) {
                    logMessage("Не удалось добавить сеть", Color.RED);
                    showManualConnectionPrompt();
                    return;
                }
            } else {
                netId = wifiManager.updateNetwork(wifiConfig);
                logMessage("Обновляем конфигурацию, netId: " + netId, Color.BLACK);
                if (netId == -1) {
                    logMessage("Не удалось обновить конфигурацию сети", Color.RED);
                    wifiConfig = new WifiConfiguration();
                    wifiConfig.SSID = "\"" + targetSSID + "\"";
                    wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
                    logMessage("Создаем новую конфигурацию: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.BLACK);
                    netId = wifiManager.addNetwork(wifiConfig);
                    logMessage("Добавляем сеть, netId: " + netId, Color.BLACK);
                    if (netId == -1) {
                        logMessage("Не удалось добавить сеть", Color.RED);
                        showManualConnectionPrompt();
                        return;
                    }
                }
            }

            boolean enabled = wifiManager.enableNetwork(netId, true);
            logMessage("Включаем сеть, enabled: " + enabled, Color.BLACK);
            if (enabled) {
                wifiManager.reconnect();
                logMessage("Подключение к сети " + targetSSID + "...", Color.BLACK);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openWebView();
                    }
                }, 1000);
            } else {
                logMessage("Не удалось подключиться к сети", Color.RED);
                showManualConnectionPrompt();
            }
        }, 1000);
    }

    private void openWebView() {
        Intent intent = new Intent(this, WebViewActivity.class);
        startActivity(intent);
    }

    private void showManualConnectionPrompt() {
        logMessage("Не удалось подключиться к Wi-Fi. Пожалуйста, подключитесь вручную.", Color.RED);
        Toast.makeText(this, "Не удалось подключиться к Wi-Fi. Пожалуйста, подключитесь вручную.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToWifiAndOpenWebView();
            } else {
                logMessage("Разрешение на доступ к Wi-Fi не предоставлено", Color.RED);
                Toast.makeText(this, "Разнерешние на доступ к Wi-FI не предоставлено", Toast.LENGTH_LONG).show();
            }
        }
    }
}