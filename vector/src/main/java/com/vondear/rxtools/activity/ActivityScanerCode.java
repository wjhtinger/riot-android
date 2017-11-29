package com.vondear.rxtools.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.activity.VectorRoomCreationActivity;
import im.vector.activity.VectorRoomInviteMembersActivity;
import im.vector.adapters.ParticipantAdapterItem;

import com.vondear.rxtools.RxAnimationTool;
import com.vondear.rxtools.RxBarTool;
import com.vondear.rxtools.RxBeepTool;
import com.vondear.rxtools.RxConstants;
import com.vondear.rxtools.RxDataTool;
import com.vondear.rxtools.RxPhotoTool;
import com.vondear.rxtools.RxQrBarTool;
import com.vondear.rxtools.RxSPTool;
import com.vondear.rxtools.RxTool;
import com.vondear.rxtools.interfaces.OnRxScanerListener;
import com.vondear.rxtools.module.scaner.CameraManager;
import com.vondear.rxtools.module.scaner.CaptureActivityHandler;
import com.vondear.rxtools.module.scaner.decoding.InactivityTimer;
import com.vondear.rxtools.view.RxToast;
//import com.vondear.rxtools.view.dialog.RxDialogSure;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static im.vector.activity.MXCActionBarActivity.getSession;

public class ActivityScanerCode extends ActivityBase {
    private MXSession mSession;
    private ArrayList<ParticipantAdapterItem> mParticipants = new ArrayList<>();

    private static OnRxScanerListener mScanerListener;//扫描结果监听
    private InactivityTimer inactivityTimer;
    private CaptureActivityHandler handler;//扫描处理
    private RelativeLayout mContainer = null;//整体根布局
    private RelativeLayout mCropLayout = null;//扫描框根布局
    private int mCropWidth = 0;//扫描边界的宽度
    private int mCropHeight = 0;//扫描边界的高度
    private boolean hasSurface;//是否有预览
    private boolean vibrate = true;//扫描成功后是否震动
    private boolean mFlashing = true;//闪光灯开启状态
    private LinearLayout mLlScanHelp;//生成二维码 & 条形码 布局
    private ImageView mIvLight;//闪光灯 按钮
    //private RxDialogSure rxDialogSure;//扫描结果显示框

    public static void setScanerListener(OnRxScanerListener scanerListener) {
        mScanerListener = scanerListener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mSession = getSession(this, intent);
        if (null == mSession) {
            mSession = Matrix.getInstance(ActivityScanerCode.this).getDefaultSession();
        }

        if (mSession == null) {
            finish();
            return;
        }

        //RxTool.init(this);
        RxBarTool.setNoTitle(this);
        setContentView(R.layout.activity_scaner_code);
        RxBarTool.setTransparentStatusBar(this);
        initView();//界面控件初始化
        initScanerAnimation();//扫描动画初始化
        CameraManager.init(mContext);//初始化 CameraManager
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);//Camera初始化
        } else {
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (!hasSurface) {
                        hasSurface = true;
                        initCamera(holder);
                    }
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    hasSurface = false;

                }
            });
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        mScanerListener = null;
        super.onDestroy();
    }

    private void initView() {
        mIvLight = (ImageView) findViewById(R.id.top_mask);
        mContainer = (RelativeLayout) findViewById(R.id.capture_containter);
        mCropLayout = (RelativeLayout) findViewById(R.id.capture_crop_layout);
        //mLlScanHelp = (LinearLayout) findViewById(R.id.ll_scan_help);
        //请求Camera权限 与 文件读写 权限
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mContext, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void initScanerAnimation() {
        ImageView mQrLineView = (ImageView) findViewById(R.id.capture_scan_line);
        RxAnimationTool.ScaleUpDowm(mQrLineView);
    }

    public int getCropWidth() {
        return mCropWidth;
    }

    public void setCropWidth(int cropWidth) {
        mCropWidth = cropWidth;
        CameraManager.FRAME_WIDTH = mCropWidth;

    }

    public int getCropHeight() {
        return mCropHeight;
    }

    public void setCropHeight(int cropHeight) {
        this.mCropHeight = cropHeight;
        CameraManager.FRAME_HEIGHT = mCropHeight;
    }

    public void btn(View view) {
        int viewId = view.getId();
        if (viewId == R.id.top_mask) {
            light();
        } else if (viewId == R.id.top_back) {
            onBackPressed();
        } else if (viewId == R.id.top_openpicture) {
            RxPhotoTool.openLocalImage(mContext);
        }
    }

    private void light() {
        if (mFlashing) {
            mFlashing = false;
            // 开闪光灯
            CameraManager.get().openLight();
        } else {
            mFlashing = true;
            // 关闪光灯
            CameraManager.get().offLight();
        }

    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
            Point point = CameraManager.get().getCameraResolution();
            AtomicInteger width = new AtomicInteger(point.y);
            AtomicInteger height = new AtomicInteger(point.x);
            int cropWidth = mCropLayout.getWidth() * width.get() / mContainer.getWidth();
            int cropHeight = mCropLayout.getHeight() * height.get() / mContainer.getHeight();
            setCropWidth(cropWidth);
            setCropHeight(cropHeight);
        } catch (IOException | RuntimeException ioe) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(ActivityScanerCode.this);
        }
    }
    //========================================打开本地图片识别二维码 end=================================

    //--------------------------------------打开本地图片识别二维码 start---------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            ContentResolver resolver = getContentResolver();
            // 照片的原始资源地址
            Uri originalUri = data.getData();
            try {
                // 使用ContentProvider通过URI获取原始图片
                Bitmap photo = MediaStore.Images.Media.getBitmap(resolver, originalUri);

                // 开始对图像资源解码
                Result rawResult = RxQrBarTool.decodeFromPhoto(photo);
                if (rawResult != null) {
                    if (mScanerListener == null) {
                        //RxToast.success(rawResult.getText());
                        handleDecode(rawResult);
                    } else {
                        mScanerListener.onSuccess("From to Picture", rawResult);
                    }
                } else {
                    if (mScanerListener == null) {
                        RxToast.error("图片识别失败.");
                    } else {
                        mScanerListener.onFail("From to Picture", "图片识别失败");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //==============================================================================================解析结果 及 后续处理 end

    public void handleDecode(Result result) {
        inactivityTimer.onActivity();
        RxBeepTool.playBeep(mContext, vibrate);//扫描成功之后的振动与声音提示

        String result1 = result.getText();
        Log.v("二维码/条形码 扫描结果", result1);
        if (mScanerListener == null) {
            //mSession.getCredentials().homeServer
            handleScan_result(result1);
        } else {
            mScanerListener.onSuccess("From to Camera", result);
        }
    }

    public Handler getHandler() {
        return handler;
    }

    private void handleScan_result(String result){
        String regEx = "@.*:windsing";
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(result);
        if(matcher.matches()){
            if(!result.equals(mSession.getMyUserId())){
                displaySelectionConfirmationDialog(result);
                return;
            }
        }

        Intent webIntent = new Intent(ActivityScanerCode.this, ActivityWebView.class);
        webIntent.putExtra(ActivityWebView.WEB_VIEW_URL, result);
        startActivity(webIntent);
    }

    private void displaySelectionConfirmationDialog(final String  userId) {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(ActivityScanerCode.this);
        builder.setTitle(R.string.dialog_title_confirmation);

        builder.setMessage(getString(R.string.room_participants_invite_prompt_msg, userId));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String existingRoomId;
                if (null != (existingRoomId = isDirectChatRoomAlreadyExist(userId))) {
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, userId);
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, existingRoomId);
                    CommonActivityUtils.goToRoomPage(ActivityScanerCode.this, mSession, params);
                }else{
                    mParticipants.clear();
                    mParticipants.add(new ParticipantAdapterItem(mSession.getMyUser()));
                    ParticipantAdapterItem firstEntry = new ParticipantAdapterItem(userId, null, userId, true);
                    mParticipants.add(firstEntry);

                    createRoom(mParticipants);
                }
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // nothing to do
            }
        });

        builder.show();
    }

    private void createRoom(final List<ParticipantAdapterItem> participants) {
        mSession.createRoom(new SimpleApiCallback<String>(ActivityScanerCode.this) {
            @Override
            public void onSuccess(final String roomId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inviteParticipants(mSession.getDataHandler().getRoom(roomId), participants, 0);
                    }
                });
            }

            private void onError(final String message) {
                if (null != message) {
                    Toast.makeText(ActivityScanerCode.this, message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Invite some participants.
     * @param room the room
     * @param participants the participants list
     * @param index the start index
     */
    private void inviteParticipants(final Room room, final List<ParticipantAdapterItem> participants, final int index) {
        // detect if all members have been invited
        if (index >= participants.size()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, room.getRoomId());
                    CommonActivityUtils.goToRoomPage(ActivityScanerCode.this, mSession, params);
                }
            });

            return;
        }

        final ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inviteParticipants(room, participants, index + 1);
                    }
                });
            }

            public void onError(final String errorMessage) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ActivityScanerCode.this, errorMessage, Toast.LENGTH_SHORT).show();
                                inviteParticipants(room, participants, index + 1);
                            }
                        });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        };

        String userId = participants.get(index).mUserId;

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(userId).matches()) {
            room.inviteByEmail(userId, callback);
        } else {
            ArrayList<String> userIDs = new ArrayList<>();
            userIDs.add(userId);
            room.invite(userIDs, callback);
        }
    }

    private String isDirectChatRoomAlreadyExist(String aUserId) {
        if(null != mSession) {
            IMXStore store = mSession.getDataHandler().getStore();

            HashMap<String, List<String>> directChatRoomsDict;

            if (null != store.getDirectChatRoomsDict()) {
                directChatRoomsDict = new HashMap<>(store.getDirectChatRoomsDict());

                if (directChatRoomsDict.containsKey(aUserId)) {
                    ArrayList<String> roomIdsList = new ArrayList<>(directChatRoomsDict.get(aUserId));

                    if (0 != roomIdsList.size()) {
                        for(String roomId : roomIdsList) {
                            Room room = mSession.getDataHandler().getRoom(roomId, false);

                            // check if the room is already initialized
                            if ((null != room) && room.isReady() && !room.isInvited() && !room.isLeaving()) {
                                // test if the member did not leave the room
                                Collection<RoomMember> members = room.getActiveMembers();

                                for(RoomMember member : members) {
                                    if (TextUtils.equals(member.getUserId(), aUserId)) {
                                        //org.matrix.androidsdk.util.Log.d(LOG_TAG,"## isDirectChatRoomAlreadyExist(): for user="+aUserId+" roomFound=" + roomId);
                                        return roomId;
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        //org.matrix.androidsdk.util.Log.d(LOG_TAG,"## isDirectChatRoomAlreadyExist(): for user=" + aUserId + " no found room");
        return null;
    }

}