package com.windsing.timerdetection;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.opengl.GLES11Ext;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.jjoe64.motiondetection.motiondetection.MotionDetector;
import com.windsing.common.AudioControl;
import com.windsing.common.CameraControl;
import com.windsing.common.FileControl;
import com.windsing.mediarecorder.ImMediaRecorderCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wangjha on 2017/2/13.
 */

public class TimerDetector {
    private static final String LOG_TAG = "TimerDetector";
    private final Context mContext;
    private TimerDetectorCallback mContentCallback = null;
    private TimerDetector.TimerDetectThread mWorker;
    private MediaRecorder mRecorder = null;
    private SurfaceTexture mSurfaceTexture = null;
    private Camera mCamera = null;
    private int mContentType = 0;
    private int mVideoWidth = 1280, mVideoHeight = 720;
    private int mVideoFrameRate = 15;
    private int mInterval = 10;  //s
    private String dirString = null;
    private String fileString = null;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private volatile boolean runing_Flg = false;
    private volatile boolean safeToTakePicture = true;


    public TimerDetector(Context context, TimerDetectorCallback callback){
        mContentCallback = callback;
        mContext = context;
    }

    class TimerDetectThread extends Thread{
        private AtomicBoolean runingFlg = new AtomicBoolean(true);

        public void stopThread(){
            runingFlg.set(false);
        }

        public void run(){
            while(runingFlg.get()){
                switch (mContentType){
                    case 0:
                        takePicture();
                        break;
                    case 1:
                        takeVideo();
                        break;
                }

                if (mInterval == 0){
                    return;
                }
                sleepMin(mInterval);
            }
        }
    }

    private class PhotoHandler implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
            try {
                File outFile = new File(fileString);
                FileOutputStream outStream = new FileOutputStream(outFile);
                outStream.write(data);
                outStream.flush();
                outStream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }

            releaseCamera();

            if(mContentCallback != null){
                mContentCallback.onContent(fileString, mContentType);
            }
            safeToTakePicture = true;

            if(mInterval == 0 && runing_Flg){
                if(mContentCallback != null) {
                    mContentCallback.oneshot();
                }
            }
        }
    }

    private void sleepMin(int min){
        for(int i = 0; i < min; i++) {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkCameraHardware() {
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    private Camera getCameraInstance(){
        Camera c = null;

        if (Camera.getNumberOfCameras() >= 2) {
            c = Camera.open(cameraId);
        } else {
            c = Camera.open();
        }

        return c;
    }

    private void initCamera(){
        if(checkCameraHardware()){
            mCamera = getCameraInstance();
            mSurfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            try {
                mCamera.setPreviewTexture(mSurfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Camera.Parameters parameters = mCamera.getParameters();
            if(parameters.isVideoStabilizationSupported()){
                parameters.setVideoStabilization(true);
            }
            //parameters.setPreviewSize(mVideoWidth, mVideoHeight);
            parameters.setPictureSize(mVideoWidth, mVideoHeight);
            parameters.setPictureFormat(PixelFormat.JPEG);
            parameters.setPreviewFrameRate(mVideoFrameRate);
            parameters.setRotation(CameraControl.getPhotoRotation(mContext, cameraId));
            if(mContentType == 0) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }else if(mContentType == 1){
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mCamera.setParameters(parameters);
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private int getPhotoRotation(Context context, int cameraId){
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        WindowManager wmManager=(WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wmManager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break; // portrait
            case Surface.ROTATION_90: degrees = 90; break; // landscape
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break; // landscape
        }

        int previewRotation;
        int imageRotation;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            imageRotation = previewRotation = (info.orientation + degrees) % 360;
            previewRotation = (360 - previewRotation) % 360;  // compensate the mirror
        } else {  // back-facing
            imageRotation = previewRotation = (info.orientation - degrees + 360) % 360;
        }

        return imageRotation;
    }

    private void takePicture(){
        safeToTakePicture = false;
        initCamera();
        fileString = FileControl.getFileString("Timerdetector", "Timer") + ".jpg";
        mCamera.startPreview();
        //mCamera.takePicture(null, null, new TimerDetector.PhotoHandler());

        try {
            Thread.sleep(500);    //此处必须加延时，不然某些手机照出来会严重发黑
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            List<String> supportedFocusModes = null;
            if (null != mCamera.getParameters()) {
                supportedFocusModes = mCamera.getParameters().getSupportedFocusModes();
            }
            Log.d(LOG_TAG, "onClickTakeImage : supported focus modes " + supportedFocusModes);

            if ((null != supportedFocusModes) && (supportedFocusModes.indexOf(Camera.Parameters.FOCUS_MODE_AUTO) >= 0)) {
                Log.d(LOG_TAG, "onClickTakeImage : autofocus starts");
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (!success) {
                            Log.e(LOG_TAG, "## autoFocus(): fails");
                        } else {
                            Log.d(LOG_TAG, "## autoFocus(): succeeds");
                        }
                        mCamera.takePicture(null, null, new TimerDetector.PhotoHandler());
                    }
                });
            } else {
                Log.d(LOG_TAG, "onClickTakeImage : no autofocus : take photo");
                mCamera.takePicture(null, null, new TimerDetector.PhotoHandler());
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## autoFocus(): EXCEPTION Msg=" + e.getMessage());
            mCamera.takePicture(null, null, new TimerDetector.PhotoHandler());
        }
    }

    private int getVideoRotation(Context context, int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        WindowManager wmManager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wmManager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        } else {  // back-facing
            return (info.orientation - degrees + 360) % 360;
        }
    }

    private void takeVideo(){
        safeToTakePicture = false;

        initCamera();
        mCamera.unlock();
        mRecorder.setCamera(mCamera);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setVideoFrameRate(mVideoFrameRate);
        mRecorder.setVideoSize(mVideoWidth, mVideoHeight);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mRecorder.setVideoEncodingBitRate(1024 * 1024 * 2);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        //mRecorder.setMaxDuration(1000 * 5);  //8MB大小
        //mRecorder.setProfile(CameraControl.getCamcorderProfile(cameraId));
        mRecorder.setOrientationHint(CameraControl.getVideoRotation(mContext, cameraId));
        fileString = FileControl.getFileString("Timerdetector", "Timer") + ".mp4";
        mRecorder.setOutputFile(fileString);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder.start();

        try {
            int count = 100;
            while(runing_Flg && count > 0){
                Thread.sleep(50);
                count--;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mRecorder.stop();
        releaseCamera();

        if(mContentCallback != null){
            mContentCallback.onContent(fileString, mContentType);
        }

        safeToTakePicture = true;

        if(mInterval == 0 && runing_Flg){
            if(mContentCallback != null) {
                mContentCallback.oneshot();
            }
        }
    }

    public void setCamera(int c){
        if( c == 0){
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        else if(c == 1){
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
    }

    public void setContentType(int type){
        mContentType = type;
    }

    public  void setInterval(int s){
        mInterval = s;
    }

    public synchronized int start(){
        if(runing_Flg){
            return -1;
        }

        AudioControl.setMediaMute(mContext, true);
        if(mContentType == 1){
            mRecorder = new MediaRecorder();
        }

        mWorker = new TimerDetector.TimerDetectThread();
        mWorker.start();
        runing_Flg = true;
        return 0;
    }

    public synchronized int stop(){
        if(!runing_Flg){
            return -1;
        }

        runing_Flg = false;
        try {
            while(!safeToTakePicture){
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        AudioControl.setMediaMute(mContext, false);
        mWorker.stopThread();
        if(mContentType == 1) {
            mRecorder.release();
        }

        return 0;
    }

    public boolean getStatus(){
        return runing_Flg;
    }

    public void release(){
    }
}
