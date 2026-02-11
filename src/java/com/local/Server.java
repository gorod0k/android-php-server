package com.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

public class Server {
    private static Process process;
    public static final String TAG = "PHP_SERVER";
    public static final int PORT = 8080;

    public static void start(Context context, String documentRoot) {
        stop();

        // Используем Handler, чтобы точно показывать Toasts в UI-потоке
        Handler uiHandler = new Handler(Looper.getMainLooper());
        File logFile = new File(context.getCacheDir(), "php_server_log.txt");

        try {
            // 1. Ищем бинарник
            File phpBin = new File(context.getApplicationInfo().nativeLibraryDir, "libphp.so");
            
            Log.d(TAG, "Looking for PHP binary at: " + phpBin.getAbsolutePath());

            if (!phpBin.exists()) {
                String error = "FATAL: PHP binary NOT found at " + phpBin.getAbsolutePath();
                Log.e(TAG, error);
                uiHandler.post(() -> Toast.makeText(context, error, Toast.LENGTH_LONG).show());
                writeLog(logFile, error);
                return;
            }

            // Пытаемся дать права (на всякий случай)
            try {
                phpBin.setExecutable(true, false);
            } catch (Exception e) {
                Log.w(TAG, "Could not set executable permission");
                writeLog(logFile, "Could not set executable permission: " + e.toString());
            }

            // 2. Создаем кэш и папку root, если их нет
            context.getCacheDir().mkdirs();
            new File(documentRoot).mkdirs(); 

            // 3. Формируем команду
            ProcessBuilder pb = new ProcessBuilder(
                    phpBin.getAbsolutePath(),
                    "-S", "127.0.0.1:" + PORT,
                    "-t", documentRoot
            );

            Map<String, String> env = pb.environment();
            env.put("TMPDIR", context.getCacheDir().getAbsolutePath());
            
            // ВАЖНО: Для отладки PHP ошибок
            pb.redirectErrorStream(true);

            // 4. Запускаем
            Log.d(TAG, "Starting process...");
            process = pb.start();
            
            Log.i(TAG, "Server started successfully!");
            uiHandler.post(() -> Toast.makeText(context, "PHP Server Started!", Toast.LENGTH_SHORT).show());

            // 5. Читаем логи сервера
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "[PHP OUTPUT] " + line);
                        writeLog(logFile, "[PHP OUTPUT] " + line);
                    }
                } catch (Exception e) { 
                    writeLog(logFile, "Log reading error: " + e.toString());
                }
            }).start();

        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Start failed: ").append(e.getMessage()).append("\n");
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append(el.toString()).append("\n");
            }
            String errorMsg = sb.toString();
            Log.e(TAG, errorMsg);
            uiHandler.post(() -> Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show());
            writeLog(logFile, errorMsg);
        }
    }

    // Запись логов в файл
    private static void writeLog(File logFile, String msg) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            fw.write(System.currentTimeMillis() + ": " + msg + "\n");
            fw.close();
        } catch (Exception ignore) {}
    }
    }

    public static void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}