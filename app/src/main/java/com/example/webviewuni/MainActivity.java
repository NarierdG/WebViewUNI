package com.example.webviewuni;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import androidx.cardview.widget.CardView;
import android.view.View;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String WIFI_SSID_PREFIX = "AlertBox-";
    private static final String WIFI_PASSWORD = "boxmaster";
    private static final int WIFI_PERMISSION_REQUEST = 1;
    private static final String LOG_FILE_NAME = "log.txt";
    private TextView logTextView;
    private WifiManager wifiManager;
    private Handler handler = new Handler();
    private WebView WebInfo;
    private int ssidFoundCount = 0;
    private Button openInfoButton;
    private Button startButton;
    private CardView logCardView;
    private CardView infoCardView;
    private TextView statusTextView;
    private Runnable connectionRunnable;
    private String targetSSID = null;

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
        startButton = findViewById(R.id.startButton);
        Button openLogButton = findViewById(R.id.openLogButton);
        Button closeLogButton = findViewById(R.id.closeLogButton);
        openInfoButton = findViewById(R.id.openInfoButton);
        Button closeInfoButton = findViewById(R.id.closeInfoButton);
        logCardView = findViewById(R.id.logCardView);
        infoCardView = findViewById(R.id.infoCardView);
        Button cleanLogButton = findViewById(R.id.cleanLogButton);
        statusTextView = findViewById(R.id.statusTextView);

        WebInfo = findViewById(R.id.infoWebView);
        WebInfo.loadUrl("file:///android_asset/instrukciia-po-rabote-s-unimon-loragate.html");
        WebSettings webSettings = WebInfo.getSettings();
        webSettings.setJavaScriptEnabled(true);

        loadLogsFromFile();

        startButton.setOnClickListener(v -> {
            startButton.setEnabled(false);
            statusTextView.setText("Connection: 1..");

            connectionRunnable = new Runnable() {
                @Override
                public void run() {
                    if (checkWifiCapabilities()) {
                        targetSSID = null;
                        connectToWifiAndOpenWebView();
                    } else {
                        resetApp();
                    }
                }
            };

            handler.postDelayed(connectionRunnable, 1000);
        });

        Intent intent = getIntent();
        if (intent != null) {
            String description = intent.getStringExtra("error_code");
            if (description != null && !description.isEmpty()) {
                logMessage("[" + getCurrentDateTime() + "] " + "Error WebView: " + description, Color.RED);
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

    private void resetApp(){
        statusTextView.setText("Connection: ERROR");
        resetStatusAndButton();
    }

    private void resetStatusAndButton() {
        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                startButton.setEnabled(true);
                statusTextView.setText("");
            }
        };
        handler.postDelayed(connectionRunnable, 1000);
    }

    private void logMessage(String message, int color) {
        SpannableString spannableString = new SpannableString(message + "\n");
        spannableString.setSpan(new ForegroundColorSpan(color), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logTextView.append(spannableString);
        writeLogToFile(message);

        if (ssidFoundCount >= 3) {
            openInfoButton.performClick();
            ssidFoundCount = 0;
        }
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
            logMessage("[" + getCurrentDateTime() + "] " + "The Wi-Fi Manager is unavailable", Color.RED);
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            logMessage("[" + getCurrentDateTime() + "] " + "Wi-Fi is disabled. An attempt to enable it..", Color.BLACK);
            wifiManager.setWifiEnabled(true);
        }
        return true;
    }

    private String getCurrentDateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(calendar.getTime());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToWifiAndOpenWebView();
            } else {
                logMessage("[" + getCurrentDateTime() + "] " + "Permission to access Wi-Fi has not been granted", Color.RED);
                Toast.makeText(this, "Permission to access Wi-Fi has not been granted", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showWifiSelectionDialog(List<String> ssidList) {
        final boolean[] selectionMade = {false};
        final long timeout = 10000;
        String[] ssidArray = ssidList.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Wi-Fi:")
                .setItems(ssidArray, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        targetSSID = ssidArray[which];
                        logMessage("[" + getCurrentDateTime() + "]" + "Selected Wi-Fi: " + targetSSID, Color.BLACK);
                        selectionMade[0] = true;
                        connectToWifiAndOpenWebView2(true);
                        dialog.dismiss();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            new Thread(() -> {
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!selectionMade[0]) {
                    runOnUiThread(() -> {
                        logMessage("[" + getCurrentDateTime() + "]" + "No Wi-Fi selected, operation timed out.", Color.RED);
                        connectToWifiAndOpenWebView2(false);
                        dialog.dismiss();
                    });
                }
            }).start();
        });

        infoCardView.setVisibility(View.GONE);
        logCardView.setVisibility(View.GONE);

        dialog.show();
    }

    private void connectToWifiAndOpenWebView() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_PERMISSION_REQUEST);
            resetApp();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, WIFI_PERMISSION_REQUEST);
            resetApp();
            return;
        }

        logMessage("[" + getCurrentDateTime() + "] " + "Connection attempt...", Color.BLACK);
        String currentSSID = wifiManager.getConnectionInfo().getSSID();

        if (currentSSID != null && currentSSID.startsWith("\"" + WIFI_SSID_PREFIX)) {
            logMessage("[" + getCurrentDateTime() + "] " + "Already connected to the desired Wi-Fi", Color.BLACK);

            openWebViewIfConnected();

            return;
        }

        wifiManager.startScan();

        List<ScanResult> results = wifiManager.getScanResults();
        List<String> matchingSSIDs = new ArrayList<>();

        for (ScanResult result : results) {
            if (result.SSID.startsWith(WIFI_SSID_PREFIX)) {
                matchingSSIDs.add(result.SSID);
            }
        }

        if (matchingSSIDs.isEmpty()) {
            logMessage("[" + getCurrentDateTime() + "]" + "LoraGate Wi-Fi not found", Color.RED);
            resetApp();
        } else if (matchingSSIDs.size() == 1) {
            targetSSID = matchingSSIDs.get(0);
            logMessage("[" + getCurrentDateTime() + "]" + "LoraGate Wi-Fi has been found: " + targetSSID, Color.BLACK);
            connectToWifiAndOpenWebView2(true);
        } else {
            showWifiSelectionDialog(matchingSSIDs);
        }
    }

    private void connectToWifiAndOpenWebView2(boolean check_arr_wifi) {
        if (!check_arr_wifi) {
            resetApp();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_PERMISSION_REQUEST);
            resetApp();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, WIFI_PERMISSION_REQUEST);
            resetApp();
            return;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (!wifiManager.isWifiEnabled()) {
                Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
                startActivity(intent);
            } else {
                WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                        .setSsid(targetSSID)
                        .setWpa2Passphrase(WIFI_PASSWORD)
                        .build();

                List<WifiNetworkSuggestion> suggestionsList = new ArrayList<>();
                suggestionsList.add(suggestion);

                int status = wifiManager.addNetworkSuggestions(suggestionsList);
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    logMessage("[" + getCurrentDateTime() + "]" + "Wi-Fi suggestion added successfully.", Color.BLACK);

                    openWebViewIfConnected();
                }
            }
        }

        WifiConfiguration wifiConfig = null;
        for (WifiConfiguration existingConfig : wifiManager.getConfiguredNetworks()) {
            if (existingConfig.SSID.equals("\"" + targetSSID + "\"")) {
                wifiConfig = existingConfig;
                logMessage("[" + getCurrentDateTime() + "] " + "An existing configuration was found: " + wifiConfig.SSID, Color.BLACK);
                break;
            }
        }

        int netId;

        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                statusTextView.setText("Connection: 1..2..");
            }
        };
        handler.postDelayed(connectionRunnable, 500);

        if (wifiConfig == null) {
            wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = "\"" + targetSSID + "\"";
            wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
            logMessage("[" + getCurrentDateTime() + "] " + "Creating a new configuration: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.BLACK);
            netId = wifiManager.addNetwork(wifiConfig);
            logMessage("[" + getCurrentDateTime() + "] " + "Adding a network, netId: " + netId, Color.BLACK);
            if (netId == -1) {
                logMessage("[" + getCurrentDateTime() + "] " + "Failed to add network", Color.RED);
                showManualConnectionPrompt();
                resetApp();
                return;
            }
        } else {
            netId = wifiManager.updateNetwork(wifiConfig);
            logMessage("[" + getCurrentDateTime() + "] " + "Updating the configuration, netId: " + netId, Color.BLACK);
            if (netId == -1) {
                logMessage("[" + getCurrentDateTime() + "] " + "The network configuration could not be updated.", Color.RED);
                wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = "\"" + targetSSID + "\"";
                wifiConfig.preSharedKey = "\"" + WIFI_PASSWORD + "\"";
                logMessage("[" + getCurrentDateTime() + "] " + "Creating a new configuration: " + wifiConfig.SSID + ", " + wifiConfig.preSharedKey, Color.BLACK);
                netId = wifiManager.addNetwork(wifiConfig);
                logMessage("[" + getCurrentDateTime() + "] " + "Adding a network, netId: " + netId, Color.BLACK);
                if (netId == -1) {
                    logMessage("[" + getCurrentDateTime() + "] " + "Failed to add network.", Color.RED);
                    showManualConnectionPrompt();
                    resetApp();
                    return;
                }
            }
        }

        boolean enabled = wifiManager.enableNetwork(netId, true);
        logMessage("[" + getCurrentDateTime() + "] " + "Turning on the network, enabled: " + enabled, Color.BLACK);
        if (enabled) {
            String currentSSID = wifiManager.getConnectionInfo().getSSID();

            wifiManager.reconnect();

            logMessage("[" + getCurrentDateTime() + "] " + "Connecting to the network " + targetSSID + "...", Color.BLACK);
            connectionRunnable = new Runnable() {
                @Override
                public void run() {
                    statusTextView.setText("Connection: 1..2..3");
                }
            };
            handler.postDelayed(connectionRunnable, 500);

            openWebViewIfConnected();
        } else {
            logMessage("[" + getCurrentDateTime() + "] " + "Couldn't connect to the network", Color.RED);
            showManualConnectionPrompt();
            resetApp();
        }
    }

    private void openWebViewIfConnected() {
        String currentSSID = wifiManager.getConnectionInfo().getSSID();
        if (currentSSID != null && currentSSID.startsWith("\"" + WIFI_SSID_PREFIX)) {
            connectionRunnable = new Runnable() {
                @Override
                public void run() {
                    statusTextView.setText("Connection: RUN");
                    openWebView();
                }
            };
            handler.postDelayed(connectionRunnable, 1500);
        } else {
            logMessage("[" + getCurrentDateTime() + "]" + "Not connected to the desired Wi-Fi, cannot open WebView", Color.RED);
            resetApp();
        }
    }

    private void openWebView() {
        Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
        startActivity(intent);
        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                resetApp();
            }
        };
        handler.postDelayed(connectionRunnable, 300);
    }

    private void showManualConnectionPrompt() {
        logMessage("[" + getCurrentDateTime() + "] " + "Couldn't connect to Wi-Fi. Please connect by hand", Color.RED);
        Toast.makeText(this, "Couldn't connect to Wi-Fi. Please connect by hand", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        startActivity(intent);
    }
}