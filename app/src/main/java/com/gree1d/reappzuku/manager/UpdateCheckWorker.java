package com.gree1d.reappzuku.manager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

public class UpdateCheckWorker extends Worker {

    private static final String FILE_NAME = "UpdateCheckWorker";

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        AppDebugManager.d(Category.UTILS, FILE_NAME + ": Starting periodic update check");

        UpdateChecker.ReleaseInfo info = UpdateChecker.fetchLatestRelease();

        if (info == null) {
            AppDebugManager.w(Category.UTILS, FILE_NAME + ": fetchLatestRelease returned null, scheduling retry");
            return Result.retry();
        }

        String currentVersion = UpdateChecker.getAppVersion(context);

        if (UpdateChecker.isNewer(info.tagName, currentVersion)) {
            AppDebugManager.i(Category.UTILS, FILE_NAME + ": New version found: " + info.tagName);
            UpdateChecker.postUpdateNotification(context, info);
        } else {
            AppDebugManager.d(Category.UTILS, FILE_NAME + ": Already up to date (" + currentVersion + ")");
        }

        return Result.success();
    }
}
