package com.windsing.common;

import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * Created by wangjha on 2017/2/20.
 */

public class AudioControl {
    private static final String LOG_TAG = "AudioControl";
    private static int mAlarmVolume;
    private static int mDTMFVolume;
    private static int mRingVolume;
    private static int mNotifVolume;
    private static int mMusicVolume;
    private static int mSystemVolume;
    private static int mVoiceVolume;


    public static void setMediaMute(Context context, boolean mute){
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        if(mute){
            mSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            mMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            mDTMFVolume = audioManager.getStreamVolume(AudioManager.STREAM_DTMF);
            mNotifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            mRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
            mVoiceVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);

            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_DTMF, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0);

            audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC,true);
            audioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
            audioManager.setStreamMute(AudioManager.STREAM_DTMF,true);
            audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
            audioManager.setStreamMute(AudioManager.STREAM_RING,true);
            audioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL,true);


        }else {
            audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC,false);
            audioManager.setStreamMute(AudioManager.STREAM_ALARM, false);
            audioManager.setStreamMute(AudioManager.STREAM_DTMF,false);
            audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
            audioManager.setStreamMute(AudioManager.STREAM_RING,false);
            audioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL,false);

            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, mSystemVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mMusicVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, mAlarmVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_DTMF, mDTMFVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, mNotifVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, mRingVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, mVoiceVolume, 0);
        }
    }
}
