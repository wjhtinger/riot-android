package im.vector.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import im.vector.R;
import im.vector.activity.VectorHomeActivity;
import im.vector.services.EventStreamService;

public class VectorCallManager implements MXCallsManager.MXCallsManagerListener, IMXCall.MXCallListener {
    private static final String LOG_TAG = VectorCallManager.class.getSimpleName();

    private static VectorCallManager sInstance;

    private Context mContext;

    private List<MXCallsManager> mMxCallsManagerList;

    private IMXCall mIncomingCall;
    private IMXCall mCall;

    private VectorCallSoundManager mVectorCallSoundManager;

    /*
     * *********************************************************************************************
     * Singleton
     * *********************************************************************************************
     */

    private VectorCallManager(final Context context) {
        Log.e(LOG_TAG, "Create VectorCallManager instance ");
        mContext = context;
        mMxCallsManagerList = new ArrayList<>();
    }

    public static VectorCallManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("VectorCallManager is not initialized");
        }
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance = new VectorCallManager(context);
        //TODO init call state ?
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void addMXCallsManager(final MXCallsManager callsManager) {
        Log.e(LOG_TAG, "addMXCallsManager");
        if (callsManager != null && mMxCallsManagerList.contains(callsManager)) {
            callsManager.addListener(this);
//            callsManager.checkPendingIncomingCalls();
            mMxCallsManagerList.add(callsManager);
        }
    }

    public void removeMXCallsManager(final MXCallsManager callsManager) {
        Log.e(LOG_TAG, "removeMXCallsManager");
        for (Iterator<MXCallsManager> iterator = mMxCallsManagerList.listIterator(); iterator.hasNext(); ) {
            MXCallsManager mxCallsManager = iterator.next();
            if (mxCallsManager == callsManager) {
                iterator.remove();
            }
        }
    }

    public void setCurrentCall(final IMXCall call) {
        mCall = call;
        call.addListener(this);
    }

    /**
     * Start a call for the given session call manager in the given room
     * @param callsManager
     * @param roomId roomId in which the call should be made
     * @param withVideo whether it is audio only or video
     * @param callback
     */
    public void startCall(final MXCallsManager callsManager, final String roomId,
                          final boolean withVideo, final OnStartCallListener callback) {
        // Make sure it is registered TODO check if necessary
        addMXCallsManager(callsManager);
        // Create the call object
        callsManager.createCallInRoom(roomId, new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                Log.d(LOG_TAG, "## startIpCall(): onSuccess");
                call.setIsVideo(withVideo);
                call.setIsIncoming(false);
                setCurrentCall(call);

                callback.onStartCallSuccess(call);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onNetworkError Msg=" + e.getMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## startIpCall(): onMatrixError Msg=" + e.getLocalizedMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onUnexpectedError Msg=" + e.getLocalizedMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Check whether there is an ongoing call or not
     *
     * @return true if a call is ongoing
     */
    public boolean hasActiveCall() {
        return mCall != null;
    }

    public boolean hasIncomingCall() {
        return mIncomingCall != null;
    }

    /**
     * Provides the active call.
     * The current call is tested to check if it is still valid.
     * It if it is no more valid, any call UIs are dismissed.
     *
     * @return the active call
     */
    public IMXCall getActiveCall() {
        // not currently displayed
        if (mCall != null) {
            // check if the call can be resume
            // or it's still valid
            if (!canCallBeResumed() || (null == mCall.getSession().mCallsManager.getCallWithCallId(mCall.getCallId()))) {
                Log.d(LOG_TAG, "Hide the call notifications because the current one cannot be resumed");
                EventStreamService.getInstance().hideCallNotifications();
                mCall = null;
            }
        }

        return mCall;
    }

    public IMXCall getPendingCall() {
        return mIncomingCall;
    }

    /**
     * @return true if the call can be resumed.
     * i.e this callView can be closed to be re opened later.
     */
    private boolean canCallBeResumed() {
        if (null != mCall) {
            final String state = mCall.getCallState();

            // active call must be
            return
                    (state.equals(IMXCall.CALL_STATE_RINGING) && !mCall.isIncoming()) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTING) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTED) ||
                            state.equals(IMXCall.CALL_STATE_CREATE_ANSWER);
        }

        return false;
    }

    /**
     * @param callId the call Id
     * @return true if the call is the active callId
     */
    public boolean isBackgroundedCallId(String callId) {
        boolean res = false;

        /*if ((null != mCall) && (null == instance)) {
            res = mCall.getCallId().equals(callId);
            // clear unexpected call.
            getActiveCall();
        }*/

        return res;
    }

    /**
     * Return a user friendly message for the given error
     *
     * @param error
     * @return
     */
    public String getUserFriendlyError(final String error) {
        String userFriendlyError = error;
        if (error != null) {
            switch (error) {
                case IMXCall.CALL_ERROR_USER_NOT_RESPONDING:
                    userFriendlyError = mContext.getString(R.string.call_error_user_not_responding);
                    break;
                case IMXCall.CALL_ERROR_ICE_FAILED:
                    userFriendlyError = mContext.getString(R.string.call_error_ice_failed);
                    break;
                case IMXCall.CALL_ERROR_CAMERA_INIT_FAILED:
                    userFriendlyError = mContext.getString(R.string.call_error_camera_init_failed);
                    break;
            }
        }
        return userFriendlyError;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Manage hangup event.
     * The ringing sound is disabled and pending incoming call is dismissed.
     */
    public void hangUp() {
        if (mCall != null) {
            Log.d(LOG_TAG, "hangUp : hide call notification and stopRinging for call " + mCall.getCallId());
            mCall.hangup(null);
            hideCallNotifications();
            VectorCallSoundManager.stopRinging();
            mCall = null;
        }
    }

    private void hideCallNotifications() {
        EventStreamService.getInstance().hideCallNotifications();
    }


    /*
     * *********************************************************************************************
     * CallManager listener
     * *********************************************************************************************
     */

    @Override
    public void onIncomingCall(IMXCall call) {
        if (call != null) {
            Log.e(LOG_TAG, "onIncomingCall for " + call.getSession().getMyUserId());
            if (mCall != null) {
                // Already in call, directly hang up the new call
                call.hangup(null);
            } else {
                // Call can be taken
                mCall = call;
                call.addListener(this);
                // Display notification
                // Ring/vibrate
                EventStreamService.getInstance().displayIncomingCallNotification(call.getSession(), call.getRoom(), null, call.getCallId(), null);

                // Open incoming call screen
                VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();
                if (null == homeActivity) {
                    // if the home activity does not exist : the application has been woken up by a notification)
                    Log.d(LOG_TAG, "onIncomingCall : the home activity does not exist -> launch it");

                    // clear the activity stack to home activity
                    Intent intent = new Intent(mContext, VectorHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, call.getSession().getMyUserId());
                    intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, call.getCallId());
                    mContext.startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "onIncomingCall : the home activity exists : but permissions have to be checked before");
                    // check incoming call required permissions, before allowing the call..
                    homeActivity.startCall(call.getSession().getMyUserId(), call.getCallId());
                }
            }
        }
    }

    @Override
    public void onCallHangUp(final IMXCall call) {
        Log.e(LOG_TAG, "onCallHangUp " + call.getCallState());
        // TODO Different treatment depending if the call is in progress or not

        final VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();
        if (null != homeActivity) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "onCallHangUp : onCallHangUp");
                    homeActivity.onCallEnd(call);
                }
            });
        }
        hangUp();
    }

    @Override
    public void onVoipConferenceStarted(String roomId) {
        Log.e(LOG_TAG, "onVoipConferenceStarted for roomId " + roomId);
    }

    @Override
    public void onVoipConferenceFinished(String roomId) {
        Log.e(LOG_TAG, "onVoipConferenceFinished for roomId " + roomId);
    }

    /*
     * *********************************************************************************************
     * Call listener
     * *********************************************************************************************
     */

    @Override
    public void onStateDidChange(String state) {
        Log.e(LOG_TAG, "onStateDidChange state " + state);
    }

    @Override
    public void onCallError(String error) {
        Log.e(LOG_TAG, "onCallError " + error);
        hangUp();
    }

    @Override
    public void onViewLoading(View callView) {
        Log.e(LOG_TAG, "onViewLoading");
    }

    @Override
    public void onViewReady() {
        Log.e(LOG_TAG, "onViewReady");
    }

    @Override
    public void onCallAnsweredElsewhere() {
        Log.e(LOG_TAG, "onCallAnsweredElsewhere");

        hangUp();
    }

    @Override
    public void onCallEnd(int aReasonId) {
        switch (aReasonId) {
            case IMXCall.END_CALL_REASON_PEER_HANG_UP: // R.string.call_error_peer_cancelled_call)
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_PEER_HANG_UP");
                break;
            case IMXCall.END_CALL_REASON_PEER_HANG_UP_ELSEWHERE: // R.string.call_error_peer_hangup_elsewhere
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_PEER_HANG_UP_ELSEWHERE");
                break;
            case IMXCall.END_CALL_REASON_USER_HIMSELF:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_USER_HIMSELF");
                break;
            case IMXCall.END_CALL_REASON_UNDEFINED:
            default:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_UNDEFINED");
                break;
        }

        hangUp();
    }

    @Override
    public void onPreviewSizeChanged(int width, int height) {
        Log.e(LOG_TAG, "onPreviewSizeChanged");
    }


    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnStartCallListener {
        void onStartCallSuccess(final IMXCall call);
        void onStartCallFailed(final String errorMessage);
    }
}
