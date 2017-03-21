package com.windsing.mediarecorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by wangjha on 2017/2/9.
 */

public class ImMediaRecorder {
    private Context mContext;
    private MediaRecorder mRecorder = null;
    private ImMediaRecorderCallback mContentCallback = null;
    private Camera mCamera = null;
    private SurfaceView mSurfaceView = null;
    private SurfaceTexture mSurfaceTexture = null;
    private int recordType = 0;
    private int mVideoWidth = 1280, mVideoHeight = 720;
    private int mVideoFrameRate = 15;
    private String dirString = null;
    private String fileString = null;


    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    };

    private String getFileString(){
        if(dirString == null){
            File sdCard = Environment.getExternalStorageDirectory();
            dirString = sdCard.getAbsolutePath() + "/ImMediaRecorder";
        }
        File dir = new File(dirString);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("'ImMedia'_yyyyMMdd_HHmmss");
        String fileName = dir + "/" + dateFormat.format(date);
        Log.d("ImMediaRecorder", "###############:" + fileName);

        return fileName;
    }

    private void mediaScanBc(String fileString){
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(new File(fileString)));
        mContext.sendBroadcast(mediaScanIntent);
    }

    private void initCamera(int cameraId){
        mCamera = Camera.open(cameraId);
        if(mSurfaceView == null)
        {
            mSurfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            try {
                mCamera.setPreviewTexture(mSurfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            try {
                mCamera.setPreviewDisplay(mSurfaceView.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Camera.Parameters parameters = mCamera.getParameters();
        if(parameters.isVideoStabilizationSupported())
        {
            parameters.setVideoStabilization(true);
        }
        parameters.setPreviewSize(mVideoWidth, mVideoHeight);
        parameters.setPreviewFrameRate(mVideoFrameRate);
        mCamera.setParameters(parameters);
    }

    public ImMediaRecorder(Context context, ImMediaRecorderCallback callback){
        mContentCallback = callback;
        mContext = context;
    }

    public void setRecordType(int type){
        recordType = type;
    }

    public void setVideoSize(int width, int height){
        mVideoWidth = width;
        mVideoHeight = height;
    }

    public void setVideoFrameRate(int rate){
        mVideoFrameRate = rate;
    }

    public void setSurfaceView(SurfaceView previewSurface){
        mSurfaceView = previewSurface;
    }

    public void prepare(){
        //SurfaceHolder holder = mSurfaceView.getHolder();
        //holder.addCallback(surfaceCallback);
        mRecorder = new MediaRecorder();
        initCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    public void start(){
        if(recordType == 0){
            mCamera.unlock();
            mRecorder.setCamera(mCamera);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setVideoFrameRate(mVideoFrameRate);
            mRecorder.setVideoSize(mVideoWidth, mVideoHeight);
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mRecorder.setVideoEncodingBitRate(1024 * 800);
            mRecorder.setOrientationHint(270);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            fileString = getFileString() + ".3gp";
            mRecorder.setOutputFile(fileString);
        }else if (recordType == 1){
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setAudioSamplingRate(16000);
            mRecorder.setAudioChannels(1);
            //mRecorder.setAudioEncodingBitRate(6 * 1024);
            fileString = getFileString() + ".3gp";
            mRecorder.setOutputFile(fileString);
        }

        //mRecorder.setMaxFileSize(1024 * 8);  //8MB大小

        try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();
    }

    public void stop(){
        mRecorder.stop();

        if(mContentCallback != null){
            mContentCallback.onContent(fileString);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mediaScanBc(fileString);
            }
        }).start();
    }

    public void release(){
        if(mRecorder != null){
            mRecorder.release();
        }
        if(mCamera != null){
            mCamera.release();
        }
    }
}
