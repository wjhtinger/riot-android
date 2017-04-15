package com.windsing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
//import android.media.FaceDetector;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import com.jjoe64.motiondetection.motiondetection.MotionDetector;
import com.jjoe64.motiondetection.motiondetection.MotionDetectorCallback;
import com.windsing.common.FileControl;
import com.windsing.facedetection.WsFacedetectorCallback;
import com.windsing.sounddetection.SoundDetector;
import com.windsing.sounddetection.SoundDetectorCallback;
import com.windsing.timerdetection.TimerDetector;
import com.windsing.timerdetection.TimerDetectorCallback;
import com.windsing.facedetection.WsFacedetector;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorRoomActivity;

/**
 * Created by New on 2017/2/12.
 */

public class DetectManager {
    private static final String LOG_TAG = "DetectManager";
    Context mContext = null;

    private static DetectManager mDetectManager = null;
    private MotionDetector mMotionDetector = null;
    private SoundDetector mSoundDetector = null;
    private TimerDetector mTimerDetector = null;
    private WsFacedetector mFaceDetector = null;
    private DetectEnv mDetectEnvMotion;
    private DetectEnv mDetectEnvAudiio;
    private DetectEnv mDetectEnvTimer;
    private DetectEnv mDetectEnvFace;

    private PowerManager pm;
    private detectType mRunningDetect = detectType.INVALID;
    private boolean mDetectMotionRunFlg, mDetectSoundRunFlg, mDetectTimerRunFlg, mDetectFaceRunFlg;

    public enum detectType {
        MOTION,
        AUDIO,
        TIMER,
        FACE,
        ALL,
        INVALID
    }
    public enum detectContent_type{
        PICTURE,
        VIDEO,
        AUDIO,
        VIDEO_CALL
    }

    private class DetectEnv{
        public static final int envNum = 1;
        detectType mDetectType;
        private MXSession mSession;
        private List<Room> mRoom;

        public DetectEnv(detectType detectType){
            mDetectType = detectType;
            mRoom = new ArrayList<Room>();
        }

        public Room add(MXSession session, Room room){
            Room removeRoom = null;

            mSession = session;

            for(Room inRoom : mRoom){
                if(room == inRoom){
                    return null;
                }
            }

            if(mRoom.size() >= envNum){
                removeRoom = mRoom.get(0);
                mRoom.remove(0);
            }
            mRoom.add(room);

            return removeRoom;
        }

        public int remove(MXSession session, Room room){
            mRoom.remove(room);
            return mRoom.size();
        }

        public boolean checkRoom(MXSession session, Room room){
            for(Room inRoom : mRoom){
                if(room == inRoom){
                    return true;
                }
            }
            return false;
        }

        public Room getRoom(int location){
            return mRoom.get(location);
        }

        public int getRoomNum(){
            return mRoom.size();
        }

        public void envHandle(detectContent_type content_type, String file){
            for(Room room: mRoom){
                switch (content_type){
                    case PICTURE:
                        //MediaUploader.pictureUploaderwithThumb(mContext, mSession, room, null, null, "file://" + file, file.substring(file.lastIndexOf("/") + 1), "image/jpeg");
                        MediaUploader.pictureUploader(mContext, mSession, room, file);
                        break;

                    case VIDEO:
                        MediaUploader.videoUploader(mContext, mSession, room, null, null, "image/jpeg", "file://" + file, file.substring(file.lastIndexOf("/") + 1), "video/mp4");
                        break;

                    case AUDIO:
                        MediaUploader.audioUploader(mContext, mSession, room, file);
                        break;

                    case VIDEO_CALL:
                        lunchCall(mSession, room, 0);
                        break;

                    default:
                        Log.e(LOG_TAG, "envHandle content_type error:" + content_type);
                        break;
                }
            }
        }
    }


    public DetectManager(Context context){
        mContext = context;
    }

    private void mysleep(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean  lunchCall(final MXSession session, Room room, int type){
        if ((null != room) && room.isEncrypted() && (room.getActiveMembers().size() > 2))  {
            Log.e(LOG_TAG, "lunchCall error!");
            return false;
        }

        session.mCallsManager.createCallInRoom(room.getRoomId(), new ApiCallback<IMXCall>() {
                @Override
                public void onSuccess(final IMXCall call) {
                    Log.d(LOG_TAG,"createCallInRoom: onSuccess");
                    call.setIsVideo(true);
                    call.setIsIncoming(false);

                    Context context = VectorApp.getInstance();
                    Intent intent2 = new Intent(context, VectorCallViewActivity.class);
                    intent2.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, session.getCredentials().userId);
                    intent2.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());
                    //intent2.putExtra(VectorCallViewActivity.EXTRA_AUTO_ACCEPT, true);
                    intent2.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent2);
                }

                private void onError() {
                }

                @Override
                public void onNetworkError(Exception e) {
                }

                @Override
                public void onMatrixError(MatrixError e) {
                }

                @Override
                public void onUnexpectedError(Exception e) {
                }
            });

        return true;
    }

    private boolean getDetectEnable(detectType type){
        boolean en = false;
        String str = "";

        switch (type){
            case MOTION:
                str = mContext.getString(R.string.settings_enable_motion_detecting);
                break;

            case AUDIO:
                str = mContext.getString(R.string.settings_enable_audio_detecting);
                break;

            case TIMER:
                str = mContext.getString(R.string.settings_enable_timer_detecting);
                break;

            case FACE:
                str = mContext.getString(R.string.settings_enable_face_detecting);
                break;

            default:
                en =  false;
                break;
        }

        en = Matrix.getInstance(mContext).getSharedGCMRegistrationManager().isFunctionEnable(str);
        return en;
    }

    private void sendMsg(MXSession session, Room room, String bodyString){
        Log.d(LOG_TAG, "sendMsg:" + bodyString);

        Message message = new Message();
        message.msgtype = Message.MSGTYPE_TEXT;
        message.body = bodyString;

        final Event event = new Event(message, session.getCredentials().userId, room.getRoomId());
        room.storeOutgoingEvent(event);
        room.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "Send message : onSuccess ");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "Send message : onNetworkError " + e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "Send message : onMatrixError " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.getLocalizedMessage());
            }
        });
    }

    private void detectMotionStart(final MXSession session, final Room room, detectContent_type content, int cameraId){
        Log.d(LOG_TAG, "detectMotionStart:" + content + cameraId);
        if(!getDetectEnable(detectType.MOTION)){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_notenable, mContext.getResources().getString(R.string.detect_motion)));
            return;
        }

        if(checkStatusforStart(session, room, detectType.MOTION)){
            return;
        }

        if(mMotionDetector == null){
            mMotionDetector = new MotionDetector(mContext, null);
            mMotionDetector.setMotionDetectorCallback(new MotionDetectorCallback() {
                @Override
                public void onMotionDetected() {
                }

                @Override
                public void onTooDark() {
                }

                @Override
                public void onContent(String file, int type) {
                    if(type == 0){
                        mDetectEnvMotion.envHandle(detectContent_type.PICTURE, file);
                    }else if(type == 1){
                        mDetectEnvMotion.envHandle(detectContent_type.VIDEO, file);
                    }
                }
            });
            mDetectEnvMotion = new DetectEnv(detectType.MOTION);
        }

        Room removeRoom = mDetectEnvMotion.add(session, room);
        if(removeRoom != null){
            sendMsg(session, removeRoom, mContext.getResources().getString(R.string.detect_echo_removed, mContext.getResources().getString(R.string.detect_motion)));
        }

        if(content == detectContent_type.PICTURE){
            mMotionDetector.setContentType(0);
        }else if(content == detectContent_type.VIDEO ){
            mMotionDetector.setContentType(1);
        }
        mMotionDetector.setCamera(cameraId);
        if(mMotionDetector.start() == 0){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_start, mContext.getResources().getString(R.string.detect_motion)));
        }else{
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restart, mContext.getResources().getString(R.string.detect_motion)));
        }
    }

    private void detectMotionStop(final MXSession session, final Room room){
        Log.d(LOG_TAG, "detectMotionStop");
        if(mMotionDetector != null) {
            int size = mDetectEnvMotion.getRoomNum();
            if(size > 0){
                int size2 = mDetectEnvMotion.remove(session, room);
                if(size2 == 0) {
                    if (mMotionDetector.stop() == 0) {
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_motion)));
                        return;
                    }
                }
                if(size2 < size){  //这个节点只有在允许（DetectEnv.envNum > 1）多房间监控时才能进入
                    sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_exit, mContext.getResources().getString(R.string.detect_motion)));
                    return;
                }
                if(DetectEnv.envNum == 1){  //但只允许一个房间进行监控时，允许其他房间关闭监控
                    if (mMotionDetector.stop() == 0) {
                        sendMsg(session, mDetectEnvMotion.getRoom(0), mContext.getResources().getString(R.string.detect_echo_stop_byother, mContext.getResources().getString(R.string.detect_motion)));
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_motion)));
                        return;
                    }
                }
            }
        }
        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restop, mContext.getResources().getString(R.string.detect_motion)));
    }

    private void detectAudioStart(final MXSession session, final Room room, detectContent_type content, int cameraId){
        Log.d(LOG_TAG, "detectAudioStart:" + content);
        if(!getDetectEnable(detectType.AUDIO)){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_notenable, mContext.getResources().getString(R.string.detect_audio)));
            return;
        }

        if(checkStatusforStart(session, room, detectType.AUDIO)){
            return;
        }

        if(mSoundDetector == null) {
            mSoundDetector = new SoundDetector(mContext, new SoundDetectorCallback() {
                @Override
                public void onContent(String file, int type) {
                    if(type == 0){
                        mDetectEnvAudiio.envHandle(detectContent_type.AUDIO, file);
                    }else if(type == 1){
                        mDetectEnvAudiio.envHandle(detectContent_type.VIDEO, file);
                    }
                }
            });
            mDetectEnvAudiio = new DetectEnv(detectType.AUDIO);
        }

        Room removeRoom = mDetectEnvAudiio.add(session, room);
        if(removeRoom != null){
            sendMsg(session, removeRoom, mContext.getResources().getString(R.string.detect_echo_removed, mContext.getResources().getString(R.string.detect_audio)));
        }

        if(content == detectContent_type.AUDIO){
            mSoundDetector.setContentType(0);
        }else if(content == detectContent_type.VIDEO ){
            mSoundDetector.setContentType(1);
        }
        mSoundDetector.setCamera(cameraId);
        if(mSoundDetector.start() == 0) {
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_start, mContext.getResources().getString(R.string.detect_audio)));
        }else{
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restart, mContext.getResources().getString(R.string.detect_audio)));
        }
    }

    private void detectAudioStop(final MXSession session, final Room room) {
        Log.d(LOG_TAG, "detectAudioStop");
        if(mSoundDetector != null) {
            int size = mDetectEnvAudiio.getRoomNum();
            if(size > 0){
                int size2 = mDetectEnvAudiio.remove(session, room);
                if(size2 == 0) {
                    if (mSoundDetector.stop() == 0) {
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_audio)));
                        return;
                    }
                }
                if(size2 < size){  //这个节点只有在允许（DetectEnv.envNum > 1）多房间监控时才能进入
                    sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_exit, mContext.getResources().getString(R.string.detect_audio)));
                    return;
                }
                if(DetectEnv.envNum == 1){  //但只允许一个房间进行监控时，允许其他房间关闭监控
                    if (mSoundDetector.stop() == 0) {
                        sendMsg(session, mDetectEnvAudiio.getRoom(0), mContext.getResources().getString(R.string.detect_echo_stop_byother, mContext.getResources().getString(R.string.detect_audio)));
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_audio)));
                        return;
                    }
                }
            }
        }
        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restop, mContext.getResources().getString(R.string.detect_audio)));
    }

    private void detectTimerStart(final MXSession session, final Room room, detectContent_type content, int cameraID, int interval){
        Log.d(LOG_TAG, "detectTimerStart:" + content);
        if(!getDetectEnable(detectType.TIMER)){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_notenable, mContext.getResources().getString(R.string.detect_timer)));
            return;
        }

        if(checkStatusforStart(session, room, detectType.TIMER)){
            return;
        }

        if(mTimerDetector == null){
            mTimerDetector = new TimerDetector(mContext, new TimerDetectorCallback(){
                @Override
                public void onContent(String file, int type){
                    if(type == 0){
                        mDetectEnvTimer.envHandle(detectContent_type.PICTURE, file);
                    }else if(type == 1){
                        mDetectEnvTimer.envHandle(detectContent_type.VIDEO, file);
                    }
                }
                @Override
                public void oneshot(){
                    mDetectEnvTimer.remove(session, room);
                    mTimerDetector.stop();
                }
            });
            mDetectEnvTimer = new DetectEnv(detectType.TIMER);
        }

        Room removeRoom = mDetectEnvTimer.add(session, room);
        if(removeRoom != null){
            sendMsg(session, removeRoom, mContext.getResources().getString(R.string.detect_echo_removed, mContext.getResources().getString(R.string.detect_timer)));
        }

        if(content == detectContent_type.PICTURE){
            mTimerDetector.setContentType(0);
        }else if(content == detectContent_type.VIDEO ){
            mTimerDetector.setContentType(1);
        }
        mTimerDetector.setCamera(cameraID);
        mTimerDetector.setInterval(interval);
        if(mTimerDetector.start() == 0){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_start, mContext.getResources().getString(R.string.detect_timer)));
        }else{
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restart, mContext.getResources().getString(R.string.detect_timer)));
        }
    }

    private void detectTimerStop(final MXSession session, final Room room) {
        Log.d(LOG_TAG, "detectTimerStop");
        if(mTimerDetector != null) {
            int size = mDetectEnvTimer.getRoomNum();
            if(size > 0){
                int size2 = mDetectEnvTimer.remove(session, room);
                if(size2 == 0) {
                    if (mTimerDetector.stop() == 0) {
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_timer)));
                        return;
                    }
                }
                if(size2 < size){  //这个节点只有在允许（DetectEnv.envNum > 1）多房间监控时才能进入
                    sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_exit, mContext.getResources().getString(R.string.detect_timer)));
                    return;
                }
                if(DetectEnv.envNum == 1){  //但只允许一个房间进行监控时，允许其他房间关闭监控
                    if (mTimerDetector.stop() == 0) {
                        sendMsg(session, mDetectEnvTimer.getRoom(0), mContext.getResources().getString(R.string.detect_echo_stop_byother, mContext.getResources().getString(R.string.detect_timer)));
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_timer)));
                        return;
                    }
                }
            }
        }
        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restop, mContext.getResources().getString(R.string.detect_timer)));
    }

    private void detectFaceStart(final MXSession session, final Room room, detectContent_type content, int cameraID) {
        Log.d(LOG_TAG, "detectFaceStart:" + content);
        if(!getDetectEnable(detectType.FACE)){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_notenable, mContext.getResources().getString(R.string.detect_face)));
            return;
        }

        if(checkStatusforStart(session, room, detectType.FACE)){
            return;
        }

        if(content == detectContent_type.VIDEO_CALL){
            List members = (List) room.getJoinedMembers();
            if(members.size() > 2){
                sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_face_videocall_member_limit));
                return;
            }
        }

        if (mFaceDetector == null) {
            mFaceDetector = new WsFacedetector(mContext, new WsFacedetectorCallback() {
                @Override
                public void onDetected(){
                    KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
                    KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unLock");
                    kl.disableKeyguard();
                    //mContext.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

                    pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
                    if(!pm.isScreenOn()){
                        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "turnon");
                        wl.acquire();
                        wl.release();
                    }
                }

                @Override
                public void onDetectLost() {
//                    PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
//                    if(pm.isScreenOn()){
//                        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "turnon");
//                        wl.acquire();
//                        wl.release();
//                        //Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 100);
//                    }
                    //blackScreen();
                }

                @Override
                public void onContent(String file, int type) {
                    if(type == 1){
                        mDetectEnvFace.envHandle(detectContent_type.VIDEO, file);
                    }

                    KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
                    KeyguardManager.KeyguardLock kl = km.newKeyguardLock("Lock");
                    kl.reenableKeyguard();
                }

                @Override
                public void onVideoCall() {
                    suspendDetect();
                    mDetectEnvFace.envHandle(detectContent_type.VIDEO_CALL, null);
                }
            });
            mDetectEnvFace = new DetectEnv(detectType.FACE);
        }

        //VIDEO_CALL只能允许一个房间
        if(content == detectContent_type.VIDEO_CALL){
            if(mDetectEnvFace.getRoomNum() > 0){
                Room currentRoom = mDetectEnvFace.getRoom(0);
                if(currentRoom != room){
                    sendMsg(session, mDetectEnvFace.getRoom(0), mContext.getResources().getString(R.string.detect_echo_removed, mContext.getResources().getString(R.string.detect_face)));
                }
                mDetectEnvFace.remove(session, mDetectEnvFace.getRoom(0));
            }
        }

        Room removeRoom = mDetectEnvFace.add(session, room);
        if(removeRoom != null){
            sendMsg(session, removeRoom, mContext.getResources().getString(R.string.detect_echo_removed, mContext.getResources().getString(R.string.detect_face)));
        }

        if(content == detectContent_type.VIDEO_CALL){
            mFaceDetector.setContentType(0);
        }else if(content == detectContent_type.VIDEO ){
            mFaceDetector.setContentType(1);
        }
        mFaceDetector.setCamera(cameraID);

        if(mFaceDetector.start() == 0){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_start, mContext.getResources().getString(R.string.detect_face)));
        }else{
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restart, mContext.getResources().getString(R.string.detect_face)));
        }
    }

    private void detectFaceStop(final MXSession session, final Room room) {
        Log.d(LOG_TAG, "detectFaceStop");
        if(mFaceDetector != null) {
            int size = mDetectEnvFace.getRoomNum();
            if(size > 0){
                int size2 = mDetectEnvFace.remove(session, room);
                if(size2 == 0) {
                    if (mFaceDetector.stop() == 0) {
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_face)));
                        return;
                    }
                }
                if(size2 < size){  //这个节点只有在允许（DetectEnv.envNum > 1）多房间监控时才能进入
                    sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_exit, mContext.getResources().getString(R.string.detect_face)));
                    return;
                }
                if(DetectEnv.envNum == 1){  //但只允许一个房间进行监控时，允许其他房间关闭监控
                    if (mFaceDetector.stop() == 0) {
                        sendMsg(session, mDetectEnvFace.getRoom(0), mContext.getResources().getString(R.string.detect_echo_stop_byother, mContext.getResources().getString(R.string.detect_face)));
                        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_stop, mContext.getResources().getString(R.string.detect_face)));
                        return;
                    }
                }
            }
        }
        sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_restop, mContext.getResources().getString(R.string.detect_face)));
    };

    private boolean checkStatusforStart(MXSession session, Room room, detectType type){
        boolean runRlg = false;
        int detectStatus = R.string.detect_echo_status_running_forStart;

        if(type != detectType.MOTION && mMotionDetector != null){
            if(mMotionDetector.getStatus()){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_motion)));
                runRlg = true;
            }
        }
        if(type != detectType.AUDIO && mSoundDetector != null){
            if(mSoundDetector.getStatus()){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_audio)));
                runRlg = true;
            }
        }
        if(type != detectType.TIMER && mTimerDetector != null){
            if(mTimerDetector.getStatus()){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_timer)));
                runRlg = true;
            }
        }
        if(type != detectType.FACE && mFaceDetector != null){
            if(mFaceDetector.getStatus()){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_face)));
                runRlg = true;
            }
        }

        return runRlg;
    }

    private void detectStatus(MXSession session, Room room){
        boolean runRlg = false;
        int detectStatus = R.string.detect_echo_status_running;

        if(mDetectEnvMotion != null){
            if(mDetectEnvMotion.checkRoom(session, room)){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_motion)));
                runRlg = true;
            }
        }
        if(mDetectEnvAudiio != null){
            if(mDetectEnvAudiio.checkRoom(session, room)){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_audio)));
                runRlg = true;
            }
        }
        if(mDetectEnvTimer != null){
            if(mDetectEnvTimer.checkRoom(session, room)){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_timer)));
                runRlg = true;
            }
        }
        if(mDetectEnvFace != null){
            if(mDetectEnvFace.checkRoom(session, room)){
                sendMsg(session, room, mContext.getResources().getString(detectStatus, mContext.getResources().getString(R.string.detect_face)));
                runRlg = true;
            }
        }

        if(!runRlg){
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_status_all_notrunning));
        }
    }


    public String sendStartMotionDetect(MXSession session, Room room, detectContent_type content, int cameraID){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_motion);

        switch (content){
            case PICTURE:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_picture);
                break;
            case VIDEO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_video);
                break;
            default:
                Log.d(LOG_TAG, "sendStartMotionDetect content error：" + content);
                break;        }

        cmdString += "[-" + String.valueOf(cameraID)  + "-]";

        return cmdString;
    }

    public String sendStartAudioDetect(MXSession session, Room room, detectContent_type content, int cameraID){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_audio);

        switch (content){
            case VIDEO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_video);
                break;
            case AUDIO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_audio);
                break;
            default:
                Log.d(LOG_TAG, "sendStartAudioDetect content error：" + content);
                break;
        }

        cmdString += "[-" + String.valueOf(cameraID)  + "-]";

        return cmdString;
    }

    public String sendStartTimerDetect(MXSession session, Room room, detectContent_type content, int cameraID, int interval){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_timer);

        switch (content){
            case PICTURE:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_picture);
                break;
            case VIDEO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_video);
                break;
            default:
                Log.d(LOG_TAG, "sendStartTimerDetect content error：" + content);
                break;
        }

        cmdString += "[-" + String.valueOf(cameraID)  + "-]";
        cmdString += "[-" + String.valueOf(interval)  + "-]";

        return cmdString;
    }

    public String sendStartFaceDetect(MXSession session, Room room, detectContent_type content, int cameraID){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_face);

        switch (content){
            case VIDEO_CALL:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_videocall);
                break;
            case VIDEO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_content_video);
                break;
            default:
                Log.d(LOG_TAG, "sendStartTimerDetect content error：" + content);
                break;
        }

        cmdString += "[-" + String.valueOf(cameraID)  + "-]";

        return cmdString;
    }

    public String sendStopDetectAll(MXSession session, Room room){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_stop);

        return cmdString;
    }

    public String sendStopDetect(MXSession session, Room room, detectType type, int cameraID){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        switch (type) {
            case MOTION:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_motion);
                break;
            case AUDIO:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_audio);
                break;
            case TIMER:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_timer);
                break;
            case FACE:
                cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_face);
                break;
            default:
                Log.e(LOG_TAG, "sendStopDetect type error!");
                break;
        }

        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_stop);
        cmdString += "[-" + String.valueOf(cameraID)  + "-]";

        return cmdString;
    }

    public String sendDetectStatus(MXSession session, Room room){
        String cmdString = mContext.getResources().getString(R.string.tag_message_command);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect);
        cmdString += mContext.getResources().getString(R.string.tag_message_command_detect_status);

        return cmdString;
    }

    public void detectHandle(MXSession session, Room room, String cmdString){
        Log.d(LOG_TAG, "detectHandle:" + cmdString);
        String[] cmdSplit = cmdString.split("-]");
        if(cmdSplit.length < 2 || !(cmdSplit[1] + "-]").equals(mContext.getResources().getString(R.string.tag_message_command_detect))){
            return;
        }

        if(cmdSplit.length < 3){
            return;
        }

        IMXCall call = VectorCallViewActivity.getActiveCall();  
        if(call != null) {
            sendMsg(session, room, mContext.getResources().getString(R.string.detect_echo_call_ison));
            return;
        }

        String detectType = cmdSplit[2] + "-]";
        Log.d(LOG_TAG, "detectHandle.detectType：" + detectType);
        if(cmdSplit.length == 5 && detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_motion))){
            String contentType = cmdSplit[3] + "-]";
            String cameraId = cmdSplit[4].replace("[-", "");

            Log.d(LOG_TAG, "detectHandle.contentType：" + contentType);
            if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_picture))){
                detectMotionStart(session, room, detectContent_type.PICTURE,  Integer.parseInt(cameraId));
            }else if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_video))){
                detectMotionStart(session, room, detectContent_type.VIDEO,  Integer.parseInt(cameraId));
            }else if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_stop))){
                detectMotionStop(session, room);
            }
        }else if(cmdSplit.length == 5 && detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_audio))) {
            String contentType = cmdSplit[3] + "-]";
            String cameraId = cmdSplit[4].replace("[-", "");

            Log.d(LOG_TAG, "detectHandle.contentType：" + contentType);
            if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_audio))) {
                detectAudioStart(session, room, detectContent_type.AUDIO, Integer.parseInt(cameraId));
            } else if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_video))) {
                detectAudioStart(session, room, detectContent_type.VIDEO, Integer.parseInt(cameraId));
            }else if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_stop))){
                detectAudioStop(session, room);
            }
        }else if (cmdSplit.length == 6 && detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_timer))) {
            String contentType = cmdSplit[3] + "-]";
            String cameraId = cmdSplit[4].replace("[-", "");

            if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_picture))) {
                String Inteval = cmdSplit[5].replace("[-", "");
                detectTimerStart(session, room, detectContent_type.PICTURE, Integer.parseInt(cameraId), Integer.parseInt(Inteval));
            } else if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_video))) {
                String Inteval = cmdSplit[5].replace("[-", "");
                detectTimerStart(session, room, detectContent_type.VIDEO, Integer.parseInt(cameraId), Integer.parseInt(Inteval));
            }else if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_stop))){
                detectTimerStop(session, room);
            }
        }else if(cmdSplit.length == 5 && detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_face))){
            String contentType = cmdSplit[3] + "-]";
            String cameraId = cmdSplit[4].replace("[-", "");

            if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_videocall))) {
                detectFaceStart(session, room, detectContent_type.VIDEO_CALL, Integer.parseInt(cameraId));
            } else if (contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_content_video))) {
                detectFaceStart(session, room, detectContent_type.VIDEO, Integer.parseInt(cameraId));
            }else if(contentType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_stop))){
                detectFaceStop(session, room);
            }
        }else if(detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_stop))){
            detectMotionStop(session, room);
            detectAudioStop(session, room);
            detectTimerStop(session, room);
            detectFaceStop(session, room);
        }else if(detectType.equals(mContext.getResources().getString(R.string.tag_message_command_detect_status))) {
            detectStatus(session, room);
        }
    }

    public static DetectManager instance(Context context){
        if(mDetectManager == null && context != null) {
            mDetectManager = new DetectManager(context);
        }

        return mDetectManager;
    }

    public void suspendDetect(){
        if(mMotionDetector != null){
            if(mMotionDetector.getStatus()){
                mMotionDetector.stop();
                mDetectMotionRunFlg = true;
            }
        }
        if(mSoundDetector != null){
            if(mSoundDetector.getStatus()){
                mSoundDetector.stop();
                mDetectSoundRunFlg = true;
            }
        }
        if(mTimerDetector != null){
            if(mTimerDetector.getStatus()){
                mTimerDetector.stop();
                mDetectTimerRunFlg = true;
            }
        }
        if(mFaceDetector != null){
            if(mFaceDetector.getStatus()){
                mFaceDetector.stop();
                mDetectFaceRunFlg = true;
            }
        }
    }

    public void restoreDetect(){
        if(mDetectMotionRunFlg){
            mysleep(500);
            mMotionDetector.start();
            mDetectMotionRunFlg = false;
        }
        if(mDetectSoundRunFlg){
            mysleep(500);
            mSoundDetector.start();
            mDetectSoundRunFlg = false;
        }
        if(mDetectTimerRunFlg){
            mysleep(500);
            mTimerDetector.start();
            mDetectTimerRunFlg = false;
        }
        if(mDetectFaceRunFlg){
            mysleep(500);
            mFaceDetector.start();
            mDetectFaceRunFlg = false;
        }
    }

    public void stop(detectType type){
        switch (type){
            case MOTION:
                if(mMotionDetector != null) {
                    if(mMotionDetector.getStatus()) {
                        mMotionDetector.stop();
                        mMotionDetector= null;
                    }
                }
                break;

            case AUDIO:
                if(mSoundDetector != null) {
                    if(mSoundDetector.getStatus()) {
                        mSoundDetector.stop();
                        mSoundDetector = null;
                    }
                }
                break;

            case TIMER:
                if(mTimerDetector != null){
                    if(mTimerDetector.getStatus()){
                        mTimerDetector.stop();
                        mTimerDetector = null;
                    }
                }
                break;

            case FACE:
                if(mFaceDetector != null){
                    if(mFaceDetector.getStatus()){
                        mFaceDetector.stop();
                        mFaceDetector = null;
                    }
                }
                break;

            case ALL:
                if(mMotionDetector != null) {
                    if(mMotionDetector.getStatus()) {
                        mMotionDetector.stop();
                        mMotionDetector = null;
                    }
                }
                if(mSoundDetector != null) {
                    if(mSoundDetector.getStatus()) {
                        mSoundDetector.stop();
                        mSoundDetector = null;
                    }
                }
                if(mTimerDetector != null){
                    if(mTimerDetector.getStatus()){
                        mTimerDetector.stop();
                        mTimerDetector = null;
                    }
                }
                if(mFaceDetector != null){
                    if(mFaceDetector.getStatus()){
                        mFaceDetector.stop();
                        mFaceDetector = null;
                    }
                }

                mDetectManager= null;
                break;

            default:
                break;
        }
    }
}
