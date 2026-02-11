package com.local;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

public class Server {
    private static Process process;
    public static final String TAG = "PHP_SERVER";
    public static final int PORT = 8080;

    /**
     * Запускает PHP сервер
     * @param context контекст приложения
     * @param documentRoot путь к папке с сайтом (например, /sdcard/www)
     */
    public static void start(Context context, String documentRoot) {
        stop(); // Сначала убиваем старый процесс, если есть

        try {
            // 1. Ищем бинарник.
            // Благодаря android:extractNativeLibs="true" он лежит здесь:
            File phpBin = new File(context.getApplicationInfo().nativeLibraryDir, "libphp.so");

            if (!phpBin.exists()) {
                Log.e(TAG, "FATAL: PHP binary not found at " + phpBin.getAbsolutePath());
                return;
            }

            phpBin.setExecutable(true, false);
            phpBin.setReadable(true, false);

            context.getCacheDir().mkdirs();

            // 2. Формируем команду запуска
            // php -S 127.0.0.1:8080 -t /sdcard/HTDOCS
            ProcessBuilder pb = new ProcessBuilder(
                    phpBin.getAbsolutePath(),
                    "-S", "127.0.0.1:" + PORT,
                    "-t", documentRoot
            );

            // 3. Настраиваем переменные окружения
            // PHP нужно знать, где хранить временные файлы сессий и загрузок
            Map<String, String> env = pb.environment();
            env.put("TMPDIR", context.getCacheDir().getAbsolutePath());

            // 4. Объединяем поток ошибок и стандартный вывод
            pb.redirectErrorStream(true);

            // 5. Запускаем процесс
            process = pb.start();
            Log.i(TAG, "PHP PID=" + process.pid());
            Log.i(TAG, "Server started! Root: " + documentRoot);

            // 6. Читаем логи сервера в отдельном потоке (чтобы видеть ошибки PHP в Logcat)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "[PHP] " + line);
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки чтения при закрытии
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error starting server", e);
        }
    }

    /**
     * Останавливает сервер
     */
    public static void stop() {
        if (process != null) {
            process.destroy();
            process = null;
            Log.i(TAG, "Server stopped.");
        }
    }
}