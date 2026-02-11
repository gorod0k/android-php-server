package com.local;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    // Путь к корню сайта: /storage/emulated/0/HTDOCS
    private final File DOC_ROOT = new File(Environment.getExternalStorageDirectory(), "HTDOCS");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Создаем WebView программно (без XML)
        webView = new WebView(this);
        setContentView(webView);

        // Настройки WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        
        // Чтобы ссылки открывались внутри приложения, а не в Chrome
        webView.setWebViewClient(new WebViewClient());

        // 2. Проверяем права и запускаем
        if (hasStoragePermission()) {
            startServerAndLoadUi();
        } else {
            requestStoragePermission();
        }
    }

    private void startServerAndLoadUi() {
        // А. Создаем папку и дефолтный файл, если их нет
        setupFileSystem();

        // Б. Запускаем PHP сервер
        // Передаем путь к /sdcard/HTDOCS
        Server.start(this, DOC_ROOT.getAbsolutePath());

        // В. Загружаем страницу (с небольшой задержкой, чтобы сервер успел подняться)
        webView.postDelayed(() -> {
            webView.loadUrl("http://127.0.0.1:8080");
            Toast.makeText(this, "Server running at " + DOC_ROOT.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }, 500);
    }

    private void setupFileSystem() {
        try {
            if (!DOC_ROOT.exists()) {
                DOC_ROOT.mkdirs();
            }

            File indexFile = new File(DOC_ROOT, "index.php");
            if (!indexFile.exists()) {
                String content = "<html><body><h1>It Works!</h1><p>PHP is running on Android.</p><?php phpinfo(); ?></body></html>";
                FileOutputStream fos = new FileOutputStream(indexFile);
                fos.write(content.getBytes());
                fos.close();
            }
        } catch (Exception e) {
            Log.e("MAIN", "File setup failed", e);
        }
    }

    // --- Блок работы с правами (Permissions) ---

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
                Toast.makeText(this, "Please allow 'All files access' to use /HTDOCS", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasStoragePermission()) {
                startServerAndLoadUi();
            } else {
                Toast.makeText(this, "Permission denied. Server cannot start.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServerAndLoadUi();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Убиваем сервер при выходе
        Server.stop();
    }
    
    // Позволяет возвращаться назад в браузере кнопкой "Назад" на телефоне
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}