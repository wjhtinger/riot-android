/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;

import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.receiver.HeadsetConnectionReceiver;

/**
 * This class manages the sound for vector.
 * It is in charge of playing ringtones and managing the audio focus.
 */
public class VectorCallSoundManager {

    private static final String LOG_TAG = VectorCallSoundManager.class.getSimpleName();

    /** Observer pattern class to notify sound events.
     *  Clients listen to events by calling {@link IVectorCallSoundListener}**/
    public interface IVectorCallSoundListener {
        /**
         * Call back indicating new focus events (ex: {@link AudioManager#AUDIOFOCUS_GAIN},
         * {@link AudioManager#AUDIOFOCUS_LOSS}..).
         * @param aFocusEvent the focus event (see {@link AudioManager.OnAudioFocusChangeListener})
         */
        void onFocusChanged(int aFocusEvent);
    }

    // audio focus
    private static boolean mIsFocusGranted = false;
    // true when microphone is muted
    private static boolean sIsMute;
    // true when speaker is on
    private static boolean sIsSpeakerOn;

    // the media players are played on loudspeaker / earpiece according to setSpeakerOn
    private static MediaPlayer mRingtonePlayer;
    private static MediaPlayer mRingBackPlayer;
    private static MediaPlayer mCallEndPlayer;
    private static MediaPlayer mBusyPlayer;

    private static final int VIBRATE_DURATION = 500; // milliseconds
    private static final int VIBRATE_SLEEP = 1000;  // milliseconds
    private static final long[] VIBRATE_PATTERN = {0, VIBRATE_DURATION, VIBRATE_SLEEP};

    // audio focus management
    private final static ArrayList<IVectorCallSoundListener> mCallSoundListenersList = new ArrayList<>();

    private final static AudioManager.OnAudioFocusChangeListener mFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int aFocusEvent) {
            switch (aFocusEvent) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN");
                    // TODO resume voip call (ex: ending GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN_TRANSIENT");
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // TODO : continue playing at an attenuated level
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_REQUEST_FAILED");
                    break;

                default:
                    break;
            }

            // notify listeners
            for (IVectorCallSoundListener listener : mCallSoundListenersList) {
                listener.onFocusChanged(aFocusEvent);
            }
        }
    };

    // the audio manager
    private static AudioManager mAudioManager = null;

    /**
     * @return the audio manager
     */
    private static AudioManager getAudioManager() {
        if (null == mAudioManager) {
            mAudioManager =  (AudioManager)VectorApp.getInstance().getSystemService(Context.AUDIO_SERVICE);
        }

        return mAudioManager;
    }

    /**
     * Tells that the device is ringing.
     * @return true if the device is ringing
     */
    public static boolean isRinging() {
        return (null != mRingtonePlayer);
    }

    public static boolean isMute(){
        return sIsMute;
    }

    public static boolean isSpeakerOn(){
        return sIsSpeakerOn;
    }
    /**
     * Stop any playing ring tones.
     */
    private static void stopRingTones() {
        if (null != mRingtonePlayer) {
            if (mRingtonePlayer.isPlaying()) {
                mRingtonePlayer.stop();
            }
            mRingtonePlayer.release();
            mRingtonePlayer = null;
        }

        if (null != mRingBackPlayer) {
            if (mRingBackPlayer.isPlaying()) {
                mRingBackPlayer.stop();
            }
            mRingBackPlayer.release();
            mRingBackPlayer = null;
        }
    }

    /**
     * Stop the ringing sound
     */
    public static void stopRinging() {
        Log.d(LOG_TAG, "stopRinging");
        stopRingTones();

        // stop vibrate
        enableVibrating(false);
    }

    /**
     * Getter method.
     * @return true is focus is granted, false otherwise.
     */
    public static boolean isFocusGranted(){
        return mIsFocusGranted;
    }

    /**
     * Request a permanent audio focus if the focus was not yet granted.
     */
    public static void requestAudioFocus() {
        if(! mIsFocusGranted) {
            int focusResult;
            AudioManager audioMgr;

            if ((null != (audioMgr = getAudioManager()))) {
                // Request permanent audio focus for voice call
                focusResult = audioMgr.requestAudioFocus(mFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

                if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == focusResult) {
                    mIsFocusGranted = true;
                    Log.d(LOG_TAG, "## getAudioFocus(): granted");
                } else {
                    mIsFocusGranted = false;
                    Log.w(LOG_TAG, "## getAudioFocus(): refused - focusResult=" + focusResult);
                }
            }
        } else {
            Log.d(LOG_TAG, "## getAudioFocus(): already granted");
        }
    }

    /**
     * Release the audio focus if it was granted.
     */
    public static void releaseAudioFocus() {
        if(mIsFocusGranted) {
            Handler handler = new Handler(Looper.getMainLooper());

            // the audio focus is abandoned with delay
            // to let the call to finish properly
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AudioManager audioMgr;

                    if ((null != (audioMgr = getAudioManager()))) {
                        // release focus
                        int abandonResult = audioMgr.abandonAudioFocus(mFocusListener);

                        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == abandonResult) {
                            mIsFocusGranted = false;
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_GRANTED");
                        }

                        if (AudioManager.AUDIOFOCUS_REQUEST_FAILED == abandonResult) {
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_FAILED");
                        }
                    } else {
                        Log.d(LOG_TAG, "## releaseAudioFocus(): failure - invalid AudioManager");
                    }
                }
            }, 300);
        }
    }
    /**
     * Enable the vibrate mode.
     * @param aIsVibrateEnabled true to force vibrate, false to stop vibrate.
     */
    private static void enableVibrating(boolean aIsVibrateEnabled) {
        Vibrator vibrator = (Vibrator)VectorApp.getInstance().getSystemService(Context.VIBRATOR_SERVICE);

        if((null != vibrator) && vibrator.hasVibrator()) {
            if(aIsVibrateEnabled) {
                vibrator.vibrate(VIBRATE_PATTERN, 0 /*repeat till stop*/);
                Log.d(LOG_TAG, "## startVibrating(): Vibrate started");
            } else {
                vibrator.cancel();
                Log.d(LOG_TAG, "## startVibrating(): Vibrate canceled");
            }
        } else {
            Log.w(LOG_TAG, "## startVibrating(): vibrator access failed");
        }
    }

    /**
     * Start the ring back sound
     */
    public static void startRingtoneSound() {
        Log.d(LOG_TAG, "Ringtone isHeadsetPlugged:" + HeadsetConnectionReceiver.isHeadsetPlugged());

        if (null != mRingtonePlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRinging();

        try {
            AssetFileDescriptor afd = VectorApp.getInstance().getResources().openRawResourceFd(R.raw.ring);
            if (afd == null) return;
            mRingtonePlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mRingtonePlayer.setAudioSessionId(getAudioManager().generateAudioSessionId());
            }
            mRingtonePlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mRingtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
            mRingtonePlayer.prepare();
            mRingtonePlayer.setLooping(true);
        } catch (Exception ex) {
            Log.d(LOG_TAG, "create failed:", ex);
            // fall through
        }

        if (null != mRingtonePlayer) {
            setSpeakerphoneOn(false, !HeadsetConnectionReceiver.isHeadsetPlugged());
            mRingtonePlayer.start();
        } else {
            Log.e(LOG_TAG, "Failed to start ringtone");
        }

        // start vibrate
        enableVibrating(true);
    }

    /**
     * Start the ring back sound
     */
    public static void startRingBackSound(boolean isVideo) {
        Log.d(LOG_TAG, "startRingBackSound isHeadsetPlugged:" + HeadsetConnectionReceiver.isHeadsetPlugged());

        if (null != mRingBackPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRinging();

        mRingBackPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.ringback);
        mRingBackPlayer.setLooping(true);

        if (null != mRingBackPlayer) {
            setSpeakerphoneOn(true, isVideo && !HeadsetConnectionReceiver.isHeadsetPlugged());
            mRingBackPlayer.start();
        } else {
            Log.e(LOG_TAG, "startRingBackSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the end call sound
     */
    public static void startEndCallSound() {
        Log.d(LOG_TAG, "startEndCallSound");

        if (null != mCallEndPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        mCallEndPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.callend);
        mCallEndPlayer.setLooping(false);

        if (null != mCallEndPlayer) {
            // do not update the audio path

            mCallEndPlayer.start();

            mCallEndPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    restoreAudioConfig();
                    mp.release();
                    mCallEndPlayer = null;
                }
            });
        } else {
            Log.e(LOG_TAG, "startEndCallSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the busy call sound
     */
    public static void startBusyCallSound() {
        Log.d(LOG_TAG, "startBusyCallSound");

        if (null != mBusyPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mBusyPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.busy);

        if (null != mBusyPlayer) {
            // do not update the audio path

            mBusyPlayer.start();

            mBusyPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    restoreAudioConfig();
                    mp.release();
                    mBusyPlayer = null;
                }
            });
        }
    }

    //==============================================================================================================
    // speakers management
    //==============================================================================================================

    // save the audio statuses
    private static Integer mAudioMode = null;
    private static Boolean mIsSpeakerOn = null;

    /**
     * Back up the current audio config.
     */
    private static void backupAudioConfig() {
        if (null == mAudioMode) { // not yet backuped
            AudioManager audioManager = getAudioManager();

            mAudioMode = audioManager.getMode();
            mIsSpeakerOn = audioManager.isSpeakerphoneOn();
        }
    }

    /**
     * Restore the audio config.
     */
    public static void restoreAudioConfig() {
        // ensure that something has been saved
        if ((null != mAudioMode) && (null != mIsSpeakerOn)) {
            AudioManager audioManager = getAudioManager();

            audioManager.setMode(AudioManager.MODE_NORMAL);

            if (mIsSpeakerOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(mIsSpeakerOn);
            }

            mAudioMode = null;
            mIsSpeakerOn = null;
        }
    }

    /**
     * Set the speakerphone ON or OFF.
     * @param isOn true to enable the speaker (ON), false to disable it (OFF)
     */
    public static void setCallSpeakerphoneOn(boolean isOn) {
        setSpeakerphoneOn(true, isOn);
    }

    /**
     * Save the current speaker status and the audio mode, before updating those
     * values.
     * The audio mode depends on if there is a call in progress.
     * If audio mode set to {@link AudioManager#MODE_IN_COMMUNICATION} and
     * a media player is in ON, the media player will reduce its audio level.
     *
     * @param isInCall true when the speaker is updated during call.
     * @param isSpeakerOn true to turn on the speaker (false to turn it off)
     */
    private static void setSpeakerphoneOn(boolean isInCall, boolean isSpeakerOn) {
        Log.d(LOG_TAG, "setCallSpeakerphoneOn " + isSpeakerOn);

        backupAudioConfig();

        AudioManager audioManager = getAudioManager();

        int audioMode = isInCall ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_RINGTONE;

        if (audioManager.getMode() != audioMode) {
            audioManager.setMode(audioMode);
        }

        if (isSpeakerOn != audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
            sIsSpeakerOn = isSpeakerOn;
        }
    }

    /**
     * Toggle the speaker
     */
    public static void toggleSpeaker() {
        AudioManager audioManager = getAudioManager();
        sIsSpeakerOn = !audioManager.isSpeakerphoneOn();
        audioManager.setSpeakerphoneOn(sIsSpeakerOn);
    }

    /**
     * Toggle the microphone mute
     */
    public static void toggleMute() {
        final boolean isMuted = getAudioManager().isMicrophoneMute();
        Log.d(LOG_TAG,"## toggleMicMute(): current mute val="+isMuted+" new mute val="+!isMuted);
        setMute(!isMuted);
    }

    public static void setMute(final boolean mustBeMute){
        getAudioManager().setMicrophoneMute(mustBeMute);
        sIsMute = mustBeMute;
    }
}
