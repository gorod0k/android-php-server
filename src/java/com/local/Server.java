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

        try {
            // 1. Ищем бинарник
            File phpBin = new File(context.getApplicationInfo().nativeLibraryDir, "libphp.so");
            
            Log.d(TAG, "Looking for PHP binary at: " + phpBin.getAbsolutePath());

            if (!phpBin.exists()) {
                String error = "FATAL: PHP binary NOT found at " + phpBin.getAbsolutePath();
                Log.e(TAG, error);
                // ПОКАЗЫВАЕМ ОШИБКУ НА ЭКРАНЕ
                uiHandler.post(() -> Toast.makeText(context, error, Toast.LENGTH_LONG).show());
                return;
            }

            // Пытаемся дать права (на всякий случай)
            try {
                phpBin.setExecutable(true, false);
            } catch (Exception e) {
                Log.w(TAG, "Could not set executable permission");
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
                    }
                } catch (Exception e) { 
                    // Игнорируем закрытие потока
                }
            }).start();

        } catch (Exception e) {
            String errorMsg = "Start failed: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            // ПОКАЗЫВАЕМ ИСКЛЮЧЕНИЕ ПОЛЬЗОВАТЕЛЮ
            uiHandler.post(() -> Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show());
        }
    }

    public static void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}