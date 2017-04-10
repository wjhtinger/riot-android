package com.windsing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;

/**
 * Created by New on 2017/2/23.
 */

public class UserPreBroadcastReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "UserPreBroadcastReceiv";
    static final String ACTION = "android.intent.action.USER_PRESENT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION)) {
            Log.d(LOG_TAG, "Load windsing!");

            if(!Matrix.getInstance(context).getSharedGCMRegistrationManager().isFunctionEnable(context.getString(R.string.settings_enable_monitoring)) ||
                    !VectorApp.isAppInBackground()){
                return;
            }

            Intent mainActivityIntent = new Intent(context, im.vector.activity.VectorHomeActivity.class);
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mainActivityIntent);
        }
    }

}
