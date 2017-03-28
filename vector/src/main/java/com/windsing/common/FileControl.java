package com.windsing.common;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by wangjha on 2017/2/23.
 */

public class FileControl {
    private static final String LOG_TAG = "FileControl";
    private static final String appHomePath = "/windsing";

    public static String getFileString(String path, String filePrefix){
        File sdCard = Environment.getExternalStorageDirectory();
        String dirString = sdCard.getAbsolutePath() + appHomePath + "/" + path;

        File dir = new File(dirString);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("_yyyyMMdd_HHmmss");
        String fileName = dir + "/" + filePrefix + dateFormat.format(date);
        Log.d(LOG_TAG, "getFileString:" + fileName);

        return fileName;
    }

    public static void mediaScanBc(Context context, String fileString){
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(new File(fileString)));
        context.sendBroadcast(mediaScanIntent);
    }

    public static void delFile(String path){
        File file = new File(path);
        if (!file.exists()) {
            return;
        }

        if (file.isFile()) {
            file.delete();
        }
    }
}
