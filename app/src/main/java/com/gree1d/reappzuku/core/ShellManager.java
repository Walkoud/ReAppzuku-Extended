package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;


public class ShellManager {

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;

    private volatile Boolean hasRoot = null;

    @SuppressWarnings("deprecation")
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;

    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.executor = executor;
    }

    public void setShizukuPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        this.shizukuPermissionListener = listener;
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
    }

    public void removeShizukuPermissionListener() {
        if (shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        }
    }

    public boolean hasRootOnlyMode() {
        return hasRootAccess() && !hasShizukuPermission();
    }

    public boolean hasRootAccess() {
        if (hasRoot == null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                hasRoot = checkRootAccessBlocking();
            } else {
                AppDebugManager.w(Category.CORE, "ShellManager: hasRootAccess: called on main thread before root check completed, returning false");
                return false;
            }
        }
        return hasRoot;
    }

    public boolean hasShizukuPermission() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: Error checking Shizuku permission", e);
            return false;
        }
    }

    public void checkShellPermissions() {
        if (hasRoot != null && hasRoot) {
            AppDebugManager.d(Category.CORE, "ShellManager: Root access available, skipping Shizuku permission request");
            return;
        }
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0);
                }
            }
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: Error checking shell permissions", e);
        }
    }

    public boolean hasAnyShellPermission() {
        if (hasShizukuPermission()) {
            AppDebugManager.d(Category.CORE, "ShellManager: hasAnyShellPermission: true (Shizuku)");
            return true;
        }
        boolean result = hasRoot != null && hasRoot;
        AppDebugManager.d(Category.CORE, "ShellManager: hasAnyShellPermission: " + result + " (root)");
        return result;
    }

    public boolean resolveAnyShellPermission() {
        if (hasShizukuPermission()) {
            AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermission: true (Shizuku)");
            return true;
        }
        boolean result = hasRootAccess();
        AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermission: " + result + " (root)");
        return result;
    }

    public boolean resolveAnyShellPermissionBlocking() {
        if (hasRoot == null) {
            hasRoot = checkRootAccessBlocking();
        }
        if (hasRoot) {
            AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermissionBlocking: true (root)");
            return true;
        }
        boolean shizuku = hasShizukuPermission();
        AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermissionBlocking: " + shizuku + " (Shizuku)");
        return shizuku;
    }

    public void runShellCommand(String command, Runnable onSuccess) {
        runShellCommand(command, onSuccess, null);
    }

    public void runShellCommand(String command, Runnable onSuccess, Runnable onFailure) {
        executor.execute(() -> {
            boolean succeeded = runShellCommandBlocking(command);

            if (succeeded) {
                if (onSuccess != null) {
                    handler.post(onSuccess);
                }
            } else if (onFailure != null) {
                handler.post(onFailure);
            }
        });
    }

    public boolean runShellCommandBlocking(String command) {
        return runShellCommandForResult(command).succeeded();
    }

    public ShellResult runShellCommandForResult(String command) {
        ShellResult rootResult = null;
        if (hasRootAccess()) {
            rootResult = executeRootCommandForResult(command);
            if (rootResult.succeeded()) {
                return rootResult;
            }
        }
        if (hasShizukuPermission()) {
            ShellResult shizukuResult = executeShizukuCommandForResult(command);
            if (shizukuResult.succeeded() || rootResult == null) {
                return shizukuResult;
            }
        }
        if (rootResult != null) {
            return rootResult;
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runShellCommandForResult: no Root or Shizuku permission available, command=" + command);
        return new ShellResult(false, -1, "No Root or Shizuku permission available");
    }

    public void runShellCommandWithOutput(String command, Consumer<String> outputProcessor) {
        executor.execute(() -> {
            boolean executed = false;
            if (hasRootAccess()) {
                executed = executeRootCommand(command, outputProcessor);
            }
            if (!executed && hasShizukuPermission()) {
                executed = executeShizukuCommandWithOutput(command, outputProcessor);
            }
        });
    }

    public String runShellCommandAndGetFullOutput(String command) {
        if (hasRootAccess()) {
            return executeRootCommandAndGetFullOutput(command);
        } else if (hasShizukuPermission()) {
            return executeShizukuCommandAndGetFullOutput(command);
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runShellCommandAndGetFullOutput: no Root or Shizuku permission available, command=" + command);
        return null;
    }

    @androidx.annotation.WorkerThread
    @androidx.annotation.Nullable
    public String runCommandAndGetOutput(String command) {
        if (hasRootAccess()) {
            return executeRootCommandAndGetFullOutput(command);
        } else if (hasShizukuPermission()) {
            return executeShizukuCommandAndGetFullOutput(command);
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runCommandAndGetOutput: no Root or Shizuku permission available, command=" + command);
        return null;
    }

    private boolean checkRootAccessBlocking() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id -u\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();

            return "0".equals(output != null ? output.trim() : "");
        } catch (IOException | InterruptedException e) {
            AppDebugManager.d(Category.CORE, "ShellManager: Root not available: " + e.getMessage());
            return false;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean executeRootCommand(String command, Consumer<String> outputProcessor) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            if (outputProcessor != null) {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(
                                new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        final String finalLine = line;
                        handler.post(() -> outputProcessor.accept(finalLine));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        final String finalLine = line;
                        handler.post(() -> outputProcessor.accept("ERROR: " + finalLine));
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Root command exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command failed", e);
            return false;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean executeShizukuCommand(String command) {
        ShizukuRemoteProcess remote = null;
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(remote.getInputStream()))) {
                while (reader.readLine() != null) {
                }
            }

            int exitCode = remote.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return false;
        } finally {
            if (remote != null) {
                remote.destroy();
            }
        }
    }

    private ShellResult executeRootCommandForResult(String command) {
        Process process = null;
        DataOutputStream os = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Root command exited with code " + exitCode + ": " + command);
            }
            return new ShellResult(exitCode == 0, exitCode, output.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command failed", e);
            return new ShellResult(false, -1, e.getMessage());
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean executeShizukuCommandWithOutput(String command, Consumer<String> outputProcessor) {
        ShizukuRemoteProcess remote = null;
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(remote.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(remote.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    final String finalLine = line;
                    handler.post(() -> outputProcessor.accept(finalLine));
                }
                while ((line = errorReader.readLine()) != null) {
                    final String finalLine = line;
                    handler.post(() -> outputProcessor.accept("ERROR: " + finalLine));
                }
            }
            int exitCode = remote.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command with output exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command with output failed", e);
            return false;
        } finally {
            if (remote != null) {
                remote.destroy();
            }
        }
    }

    private String executeRootCommandAndGetFullOutput(String command) {
        Process process = null;
        DataOutputStream os = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command get output failed", e);
            return null;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private String executeShizukuCommandAndGetFullOutput(String command) {
        ShizukuRemoteProcess remote = null;
        StringBuilder output = new StringBuilder();
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(remote.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(remote.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }
            remote.waitFor();
            return output.toString();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command get output failed", e);
            return null;
        } finally {
            if (remote != null) {
                remote.destroy();
            }
        }
    }

    private ShellResult executeShizukuCommandForResult(String command) {
        ShizukuRemoteProcess remote = null;
        StringBuilder output = new StringBuilder();
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(remote.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(remote.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }
            int exitCode = remote.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + exitCode + ": " + command);
            }
            return new ShellResult(exitCode == 0, exitCode, output.toString());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return new ShellResult(false, -1, e.getMessage());
        } finally {
            if (remote != null) {
                remote.destroy();
            }
        }
    }

    public static final class ShellResult {
        private final boolean succeeded;
        private final int exitCode;
        private final String output;

        private ShellResult(boolean succeeded, int exitCode, String output) {
            this.succeeded = succeeded;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
        }

        public boolean succeeded() {
            return succeeded;
        }

        public int exitCode() {
            return exitCode;
        }

        public String output() {
            return output;
        }
    }
}
