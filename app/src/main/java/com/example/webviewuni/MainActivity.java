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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String WIFI_SSID_PREFIX = "AlertBox-";
    private static final String WIFI_PASSWORD = "boxmaster";
    private static final int WIFI_PERMISSION_REQUEST = 1;
    private TextView logTextView;
    private WifiManager wifiManager;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        logTextView = findViewById(R.id.logTextView);
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            if (checkWifiCapabilities()) {
                connectToWifiAndOpenWebView();
            }
        });
    }

    private void logMessage(String message, int color) {
        SpannableString spannableString = new SpannableString(message + "\n");
        spannableString.setSpan(new ForegroundColorSpan(color), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logTextView.append(spannableString);
    }


    private void openInfoWifi() {
        Intent intent = new Intent(this, InfoWifiActivity.class);
        startActivity(intent);
    }

    private boolean checkWifiCapabilities() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            logMessage("Диспетчер Wi-Fi недоступен", Color.RED);
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            logMessage("Wi-Fi выключен. Включение...", Color.YELLOW);
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

        logMessage("Попытка подключения...", Color.YELLOW);
        String currentSSID = wifiManager.getConnectionInfo().getSSID();

        if (currentSSID != null && currentSSID.startsWith("\"" + WIFI_SSID_PREFIX)) {
            logMessage("Уже подключен к нужной сети", Color.GREEN);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openWebView();
                }
            }, 500);
            return;
        }

        handler.postDelayed(() -> {
            wifiManager.startScan();
            List<ScanResult> results = wifiManager.getScanResults();
            String targetSSID = null;
            for (ScanResult result : results) {
                if (result.SSID.startsWith(WIFI_SSID_PREFIX)) {
                    targetSSID = result.SSID;
                    logMessage("Найден нужный SSID: " + targetSSID, Color.GREEN);
                    break;
                }
            }
            if (targetSSID == null) {
                logMessage("Нужный Wi-Fi не найден", Color.RED);
                return;
            }

            WifiConfiguration wifiConfig = null;
            for (WifiConfiguration existingConfig : wifiManager.getConfiguredNetworks()) {
                if (existingConfig.SSID.equals("\"" + targetSSID + "\"")) {
                    wifiConfig = existingConfig;
                    logMessage("Найдена существующая конфигурация: " + wifiConfig.SSID, Color.GREEN);
                    break;
                }
            }

            int netId;
            if (wifiConfig == null) {
                wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = targetSSID;
                wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
                logMessage("Создаем новую конфигурацию: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.GREEN);
                netId = wifiManager.addNetwork(wifiConfig);
                logMessage("Добавляем сеть, netId: " + netId, Color.GREEN);
                if (netId == -1) {
                    logMessage("Не удалось добавить сеть", Color.RED);
                    showManualConnectionPrompt();
                    return;
                }
            } else {
                netId = wifiManager.updateNetwork(wifiConfig);
                logMessage("Обновляем конфигурацию, netId: " + netId, Color.GREEN);
                if (netId == -1) {
                    logMessage("Не удалось обновить конфигурацию сети", Color.RED);
                    wifiConfig = new WifiConfiguration();
                    wifiConfig.SSID = targetSSID;
                    wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
                    logMessage("Создаем новую конфигурацию: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.GREEN);
                    netId = wifiManager.addNetwork(wifiConfig);
                    logMessage("Добавляем сеть, netId: " + netId, Color.GREEN);
                    if (netId == -1) {
                        logMessage("Не удалось добавить сеть", Color.RED);
                        showManualConnectionPrompt();
                        return;
                    }
                }
            }

            boolean enabled = wifiManager.enableNetwork(netId, true);
            logMessage("Включаем сеть, enabled: " + enabled, Color.GREEN);
            if (enabled) {
                wifiManager.reconnect();
                logMessage("Подключение к сети " + targetSSID + "...", Color.GREEN);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openWebView();
                    }
                }, 500);
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
            }
        }
    }
}