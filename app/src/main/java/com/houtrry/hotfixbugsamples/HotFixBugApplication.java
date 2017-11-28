package com.houtrry.hotfixbugsamples;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * @author houtrry
 * @version $Rev$
 * @time 2017/11/28 10:35
 * @desc ${TODO}
 */

public class HotFixBugApplication extends Application {

    private static final String TAG = HotFixBugApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        fixDexBug();
    }

    private void fixDexBug() {

        Log.d(TAG, "onClick: getRootDirectory: "+ Environment.getRootDirectory());
        Log.d(TAG, "onClick: getExternalStorageState: "+ Environment.getExternalStorageState());
        Log.d(TAG, "onClick: getDataDirectory: "+ Environment.getDataDirectory());
        Log.d(TAG, "onClick: getDownloadCacheDirectory: "+ Environment.getDownloadCacheDirectory());
        Log.d(TAG, "onClick: getExternalStorageDirectory: "+ Environment.getExternalStorageDirectory());

        //这个是存放已经修复问题的新的dex文件的路径
        File root  = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/dex");
        FixDexUtils.loadFixedDex(this, root);
    }
}
