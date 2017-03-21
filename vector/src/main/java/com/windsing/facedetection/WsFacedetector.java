package com.windsing.facedetection;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
//import android.hardware.camera2.params.Face;
import android.media.MediaRecorder;
import android.opengl.GLES11Ext;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.faceplusplus.api.FaceDetecter;
import com.faceplusplus.api.FaceDetecter.Face;
import com.windsing.DetectManager;
import com.windsing.common.FileControl;

import im.vector.R;
import im.vector.VectorApp;

import static android.content.Context.WINDOW_SERVICE;
import static com.google.android.gms.internal.zzid.runOnUiThread;


/**
 * Created by wangjha on 2017/2/13.
 */

public class WsFacedetector implements SurfaceHolder.Callback {
    private static final String LOG_TAG = "WsFacedetector";
    private final Context mContext;
    private WsFacedetectorCallback mContentCallback = null;
    private MediaRecorder mRecorder = null;
    private SurfaceTexture mSurfaceTexture = null;
    private Camera mCamera = null;
    private int mContentType = 0 ;
    private int mVideoWidth = 640, mVideoHeight = 480;
    private int mVideoFrameRate = 15;
    private String fileString = null;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private byte[] mBuffer;
    private FaceDetecter facedetecter = null;
    private SurfaceView mFacePreview;
    private FaceMask mFmMask;
    private TextView mTextViewCount;
    private TextView mTextRecordTime;
    private AlertDialog mPreviewDialog = null;
    double mFaceSizeLimit = 0.8;
    int frameCountAll = 0, frameCountNull = 0, frameCountValid = 0, frameCountScreen = 0;
    int mTextNum = 4;
    long mFirstCheck = 0, mLitghCheck;
    int mRecordTimeInit = 5000, mRecordTime;
    int mRecordTimeMax = 30000;
    int mRecordTimeCount = 0;
    private boolean detectedFlg = false;
    private volatile boolean runingFlg = false;
    private volatile boolean safeToTakePicture = true;
    private float mScreenBright = -1;


    public WsFacedetector(Context context, WsFacedetectorCallback callback) {
        mContext = context;
        mContentCallback = callback;
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) {
                if(runingFlg){
                    mCamera.addCallbackBuffer(mBuffer);
                }
            };
            //Camera.Size size = cam.getParameters().getPreviewSize();
            //if (size == null) return;
            Log.d(LOG_TAG, "PreviewCallback...");

            int is = 0;
            byte[] ori = new byte[mVideoWidth * mVideoHeight];
            for (int x = mVideoWidth - 1; x >= 0; x--) {
                for (int y = mVideoHeight - 1; y >= 0; y--) {
                    ori[is++] = data[y * mVideoWidth + x];
                }
            }
            final Face[] faceinfo = facedetecter.findFaces(ori, mVideoHeight, mVideoWidth);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFmMask.setFaceInfo(faceinfo);
                }
            });
            if(faceinfo == null){
                frameCountNull++;
                if(safeToTakePicture)
                    frameCountScreen++;
            }else{
                Face f = faceinfo[0];
                float size = (f.right - f.left) * (f.bottom - f.top);
                Log.d(LOG_TAG, "##################Size:" + size);
                if(size < mFaceSizeLimit){
                    frameCountNull++;
                    if(safeToTakePicture)
                        frameCountScreen++;
                }else{
                    frameCountValid++;
                    frameCountScreen = 0;
                }
            }

            if(frameCountScreen > 80 && safeToTakePicture){
                blackScreen();
            }

            if(safeToTakePicture){
                if(frameCountNull >= 1){
                    frameCountNull = 0;
                    frameCountAll = 0;
                    mTextNum = 4;
                    mTextViewCount.setVisibility(View.INVISIBLE);

                    if(mContentCallback != null && detectedFlg){
                        detectedFlg = false;
                        mContentCallback.onDetectLost();
                    }
                }

                if(frameCountAll++ >= 2){
                    frameCountNull = 0;
                    frameCountAll = 0;
                    mTextNum--;
                    detectedFlg = true;

                    restoreScreen();
                    mTextViewCount.setVisibility(View.VISIBLE);
                    showHintText(String.valueOf(mTextNum), Color.argb(255, 255, 255, 255));

                    if(mContentCallback != null){
                        mContentCallback.onDetected();

                        if(mTextNum == 0){
                            if(mContentType == 0)
                            {
                                //stop();
                                showHintText("Call", Color.argb(255, 50, 50, 255));
                                if(mContentCallback != null && runingFlg){
                                    mContentCallback.onVideoCall();
                                }
                                return;
                            }else if(mContentType == 1){
                                showHintText(String.valueOf("开始录制"), Color.argb(255, 255, 10, 10));
                                startRecord();
                            }
                        }
                    }
                }
            }else{
                long now = System.currentTimeMillis();
                if(mFirstCheck == 0){
                    mFirstCheck = now;
                    mLitghCheck = now;
                    frameCountValid = 0;
                    mRecordTimeCount = 0;
                    mRecordTime = mRecordTimeInit;
                    mFaceSizeLimit = 0.01;
                }

                long diff = now - mLitghCheck;
                if(diff > 500){
                    mLitghCheck = now;
                    mRecordTimeCount += 500;
                    if(mRecordTimeCount % 1000 == 0){
                        mTextRecordTime.setText(String.valueOf(mRecordTimeCount / 1000) + " s");
                        showHintText(String.valueOf("●"), Color.argb(255, 255, 10, 10));
                    }
                    if(mTextViewCount.getVisibility() == View.INVISIBLE){
                        mTextViewCount.setVisibility(View.VISIBLE);
                    }else{
                        mTextViewCount.setVisibility(View.INVISIBLE);
                    }
                }

                diff = now - mFirstCheck;
                if (diff > mRecordTime || diff > mRecordTimeMax) {
                    if(frameCountValid > 2 && diff < mRecordTimeMax){
                        frameCountValid = 0;
                        mRecordTime += mRecordTimeInit;
                    }else{
                        mTextViewCount.setVisibility(View.INVISIBLE);
                        stopRecord();
                        if(mContentCallback != null && runingFlg){
                            mContentCallback.onContent(fileString, mContentType);
                        }
                    }
                }
            }

            if(runingFlg){
                mCamera.addCallbackBuffer(mBuffer);
            }
        }
    };

    private void blackScreen(){
        if(mScreenBright != 0.01f){
            Activity activity = VectorApp.getCurrentActivity();
            if(activity != null){
                WindowManager.LayoutParams params = activity.getWindow().getAttributes();
                mScreenBright = 0.01f;
                params.screenBrightness = 0.01f;
                activity.getWindow().setAttributes(params);
            }
        }
    }

    private void restoreScreen(){
        if(mScreenBright != 0.9f){
            Activity activity = VectorApp.getCurrentActivity();
            if(activity != null){
                WindowManager.LayoutParams params = activity.getWindow().getAttributes();
                mScreenBright = 0.9f;
                params.screenBrightness = 0.9f;
                activity.getWindow().setAttributes(params);
            }
        }
    }

    private void showHintText(final String text, final int color){
                mTextViewCount.setTextColor(color);
                mTextViewCount.setText(text);
    }

    private void showPreviewDialog() {
        View v = View.inflate(mContext, R.layout.dialog_face_detect, null);
        mFacePreview = (SurfaceView)v.findViewById(R.id.sv_preview);
        mFacePreview.getHolder().addCallback(this);
        mFacePreview.setKeepScreenOn(true);
        mTextViewCount = (TextView)v.findViewById(R.id.dialog_count) ;
        mFmMask = (FaceMask)v.findViewById(R.id.fm_mask);
        mTextRecordTime = (TextView)v.findViewById(R.id.record_time);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setView(v);
        mPreviewDialog = builder.create();
        mPreviewDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        //mPreviewDialog.setCancelable(false);
        mPreviewDialog.show();

        /* set size & pos */
        /*
        WindowManager.LayoutParams lp = d.getWindow().getAttributes();
        WindowManager wm = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        if (display.getHeight() > display.getWidth()) {
            lp.width = (int) (display.getWidth() * 1.0);
        } else {
            lp.width = (int) (display.getWidth() * 0.5);
        }
        d.getWindow().setAttributes(lp);
        */
    }

    private void startRecord(){
        safeToTakePicture = false;
        mPreviewDialog.setCancelable(false);  //防止在开启Recorder时，用户关闭preview界面导致关闭mRecorder的冲突

        mCamera.unlock();
        mRecorder = new MediaRecorder();
        mRecorder.setCamera(mCamera);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setVideoFrameRate(mVideoFrameRate);
        mRecorder.setVideoSize(mVideoWidth, mVideoHeight);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mRecorder.setVideoEncodingBitRate(1024 * 400);
        mRecorder.setOrientationHint(270);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        fileString = FileControl.getFileString("Facedetector", "Face") + ".mp4";
        mRecorder.setOutputFile(fileString);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder.start();

        mPreviewDialog.setCancelable(true);
   }

    private synchronized void stopRecord(){
        mFirstCheck = 0;
        mFaceSizeLimit = 0.10;

        if(mRecorder != null){
            try {
                Thread.sleep(500); //防止刚start,又stop导致的死机。
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }

        safeToTakePicture = true;
    }

    private void hidePreviewDialog(){
        if(mPreviewDialog != null){
            mPreviewDialog.dismiss();
            mPreviewDialog = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open(cameraId);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        if(parameters.isVideoStabilizationSupported()){
            parameters.setVideoStabilization(true);
        }
        parameters.setPreviewSize(mVideoWidth, mVideoHeight);
        parameters.setPreviewFrameRate(mVideoFrameRate);
        mCamera.setParameters(parameters);
        mBuffer = new byte[mVideoWidth * mVideoHeight * 3]; // class variable
        mCamera.addCallbackBuffer(mBuffer);
        mCamera.setPreviewCallbackWithBuffer(previewCallback);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.setDisplayOrientation(90);
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopRecord();

        if(mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        mFacePreview = null;

        runingFlg = false;
    }

    public void setpreviewSurface(SurfaceView previewSurface){
        mFacePreview = previewSurface;
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

    public synchronized int start(){
        if(runingFlg){
            return -1;
        }
        runingFlg = true;

        facedetecter = new FaceDetecter();
        facedetecter.init(mContext, Global.FACEPP_KEY);
        facedetecter.setTrackingMode(true);
		frameCountNull = 0;
        frameCountAll = 0;
		mTextNum = 4;
		
        if(mFacePreview == null){
            showPreviewDialog();
        }else{
            //mFacePreview.getHolder().addCallback(this);
        }

        return 0;
    }

    public synchronized int stop(){
        if(!runingFlg){
            return -1;
        }

        hidePreviewDialog();

        if(facedetecter != null){
            facedetecter.release(mContext);
            facedetecter = null;
        }

        return 0;
    }

    public boolean getStatus(){
        return runingFlg;
    }

    public void release(){
    }
}
