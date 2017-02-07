/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.services.EventStreamService;
import im.vector.util.VectorCallManager;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorUtils;

/**
 * InComingCallActivity is Dialog Activity, displayed when an incoming call (audio or a video) over IP
 * is received by the user. The user is asked to accept or ignore.
 */
public class InComingCallActivity extends Activity implements IMXCall.MXCallListener { // do NOT extend from UC*Activity, we do not want to login on this screen!
    private static final String LOG_TAG = InComingCallActivity.class.getSimpleName();

    // only one instance of this class should be displayed
    // TODO find how to avoid multiple creations
    private static InComingCallActivity sharedInstance = null;

    private ImageView mCallingUserAvatarView;
    private TextView mRoomNameTextView;
    private Button mIgnoreCallButton;
    private Button mAcceptCallButton;
    private String mCallId;
    private String mMatrixId;
    private MXSession mSession;
    private IMXCall mMxCall;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static void start(final Context context, final String sessionId, final String callId) {
        final Intent intent = new Intent(context, InComingCallActivity.class);
        intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, sessionId);
        intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, callId);
        context.startActivity(intent);
    }

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setFinishOnTouchOutside(false);

        setContentView(R.layout.vector_incoming_call_dialog);

        // retrieve intent extras
        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "## onCreate(): intent missing");
            finish();
        } else {
            mMatrixId = intent.getStringExtra(VectorCallViewActivity.EXTRA_MATRIX_ID);
            mCallId = intent.getStringExtra(VectorCallViewActivity.EXTRA_CALL_ID);

            if (null == mMatrixId) {
                Log.e(LOG_TAG, "## onCreate(): matrix ID is missing in extras");
                finish();
            } else if (null == mCallId) {
                Log.e(LOG_TAG, "## onCreate(): call ID is missing in extras");
                finish();
            } else if (null == (mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId))) {
                Log.e(LOG_TAG, "## onCreate(): invalid session (null)");
                finish();
            } else if (null == (mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId))) {
                Log.e(LOG_TAG, "## onCreate(): invalid call ID (null)");
                // assume that the user tap on a staled notification
                if (VectorCallSoundManager.isRinging()) {
                    Log.e(LOG_TAG, "## onCreate(): the device was ringing so assume that the call " + mCallId + " does not exist anymore");
                    VectorCallSoundManager.stopRinging();
                }
                EventStreamService.getInstance().hideCallNotifications();
                finish();
            } else {
                synchronized (LOG_TAG) {
                    if (null != sharedInstance) {
                        sharedInstance.finish();
                        Log.e(LOG_TAG, "## onCreate(): kill previous instance");
                    }

                    sharedInstance = this;
                }

                // UI widgets binding
                mCallingUserAvatarView = (ImageView) findViewById(R.id.avatar_img);
                mRoomNameTextView = (TextView) findViewById(R.id.room_name);
                mAcceptCallButton = (Button) findViewById(R.id.button_incoming_call_accept);
                mIgnoreCallButton = (Button) findViewById(R.id.button_incoming_call_ignore);

                mCallingUserAvatarView.post(new Runnable() {
                    @Override
                    public void run() {
                        // set the avatar
                        VectorUtils.loadCallAvatar(InComingCallActivity.this, mSession, mCallingUserAvatarView, mMxCall.getRoom());
                    }
                });

                // set the room display name
                mRoomNameTextView.setText(VectorUtils.getCallingRoomDisplayName(this, mSession, mMxCall.getRoom()));

                // set button handlers
                mIgnoreCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        VectorCallManager.getInstance().hangUp();
                        finish();
                    }
                });

                // the user can only accept if the dedicated permissions are granted
                mAcceptCallButton.setVisibility(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL, InComingCallActivity.this) ? View.VISIBLE : View.INVISIBLE);

                mAcceptCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                        VectorCallViewActivity.start(InComingCallActivity.this);
                    }
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId);

        if (null != mMxCall) {
            mMxCall.addListener(this);
        } else {
            Log.d(LOG_TAG, "## onResume : the call does not exist anymore");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mMxCall) {
            mMxCall.removeListener(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL) {
            // the user can only accept if the dedicated permissions are granted
            mAcceptCallButton.setVisibility(CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults) ? View.VISIBLE : View.INVISIBLE);

            mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId);
            if (null == mMxCall) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        VectorCallManager.getInstance().hangUp();
        super.onBackPressed();
    }

    @Override
    public void finish() {
        Log.d(LOG_TAG, "## finish");
        super.finish();
        synchronized (LOG_TAG) {
            if (this == sharedInstance) {
                sharedInstance = null;
            }
        }
    }
    /*
     * *********************************************************************************************
     *  Call listener
     * *********************************************************************************************
     */

    @Override
    public void onStateDidChange(String s) {
    }

    @Override
    public void onCallError(String s) {
        Log.d(LOG_TAG, "## onCallError");
        finish();
    }

    @Override
    public void onViewLoading(View view) {
    }

    @Override
    public void onViewReady() {
    }

    @Override
    public void onCallAnsweredElsewhere() {
        Log.d(LOG_TAG, "## onCallAnsweredElsewhere(): finish activity");
        finish();
    }

    @Override
    public void onCallEnd(final int aReasonId) {
        Log.d(LOG_TAG, "## onCallEnd(): finish activity");
        finish();
    }

    @Override
    public void onPreviewSizeChanged(int i, int i1) {

    }
}
