/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;

import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;

import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.example.gpy.whiteboard.view.widget.floatingactionmenu.FloatingActionsMenu;
import com.example.gpy.whiteboard.view.widget.floatingactionmenu.FloatingImageButton;
import com.github.guanpy.library.ann.ReceiveEvents;
import com.github.guanpy.wblib.bean.DrawPoint;
import com.github.guanpy.wblib.utils.Events;
import com.github.guanpy.wblib.utils.OperationUtils;
import com.github.guanpy.wblib.utils.WhiteBoardVariable;
import com.github.guanpy.wblib.widget.DrawPenView;
import com.github.guanpy.wblib.widget.DrawTextLayout;
import com.github.guanpy.wblib.widget.DrawTextView;
import com.windsing.DetectManager;
import com.windsing.common.FileControl;
import com.windsing.ui.CmdDialogFragment;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomEmailInvitation;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.view.AutoScrollDownListView;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.fragments.VectorMessageListFragment;
import im.vector.fragments.VectorUnknownDevicesFragment;
import im.vector.services.EventStreamService;
import im.vector.util.NotificationUtils;
import im.vector.util.ResourceUtils;
import im.vector.util.SharedDataItem;
import im.vector.util.SlashComandsParser;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorMarkdownParser;
import im.vector.util.VectorRoomMediasSender;
import im.vector.util.VectorUtils;
import im.vector.view.VectorOngoingConferenceCallView;
import im.vector.view.VectorPendingCallView;
import io.github.rockerhieu.emojicon.EmojiconGridFragment;
import io.github.rockerhieu.emojicon.EmojiconsFragment;
import io.github.rockerhieu.emojicon.emoji.Emojicon;

import com.github.guanpy.library.EventBus;
import com.xj.images.adapters.ImageRecyclerAdapter;
import com.xj.images.beans.Image;
import com.xj.images.presenter.DefaultPresenterImpl;
import com.xj.images.presenter.Presenter;
import com.xj.images.view.ViewInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a single room with messages.
 */
public class VectorRoomActivity extends MXCActionBarActivity implements ViewInterface, View.OnClickListener, EmojiconGridFragment.OnEmojiconClickedListener, EmojiconsFragment.OnEmojiconBackspaceClickedListener, MatrixMessageListFragment.IRoomPreviewDataListener, MatrixMessageListFragment.IEventSendingListener, MatrixMessageListFragment.IOnScrollListener {

    /**
     * the session
     **/
    public static final String EXTRA_MATRIX_ID = MXCActionBarActivity.EXTRA_MATRIX_ID;
    /**
     * the room id (string)
     **/
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    /**
     * the event id (universal link management - string)
     **/
    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";
    /**
     * the forwarded data (list of media uris)
     **/
    public static final String EXTRA_ROOM_INTENT = "EXTRA_ROOM_INTENT";
    /**
     * the room is opened in preview mode (string)
     **/
    public static final String EXTRA_ROOM_PREVIEW_ID = "EXTRA_ROOM_PREVIEW_ID";
    /**
     * the room alias of the room in preview mode (string)
     **/
    public static final String EXTRA_ROOM_PREVIEW_ROOM_ALIAS = "EXTRA_ROOM_PREVIEW_ROOM_ALIAS";
    /**
     * expand the room header when the activity is launched (boolean)
     **/
    public static final String EXTRA_EXPAND_ROOM_HEADER = "EXTRA_EXPAND_ROOM_HEADER";

    // display the room information while joining a room.
    // until the join is done.
    public static final String EXTRA_DEFAULT_NAME = "EXTRA_DEFAULT_NAME";
    public static final String EXTRA_DEFAULT_TOPIC = "EXTRA_DEFAULT_TOPIC";

    private static final boolean SHOW_ACTION_BAR_HEADER = true;
    private static final boolean HIDE_ACTION_BAR_HEADER = false;

    // the room is launched but it expects to start the dedicated call activity
    public static final String EXTRA_START_CALL_ID = "EXTRA_START_CALL_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_ATTACHMENTS_DIALOG = "TAG_FRAGMENT_ATTACHMENTS_DIALOG";
    private static final String TAG_FRAGMENT_CALL_OPTIONS = "TAG_FRAGMENT_CALL_OPTIONS";

    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String FIRST_VISIBLE_ROW = "FIRST_VISIBLE_ROW";

    // activity result request code
    private static final int REQUEST_FILES_REQUEST_CODE = 0;
    private static final int TAKE_IMAGE_REQUEST_CODE = 1;
    public static final int GET_MENTION_REQUEST_CODE = 2;
    private static final int REQUEST_ROOM_AVATAR_CODE = 3;
    private static final int REQUEST_WB_IMAGE_REQUEST_CODE = 4;
    private static final int REQUEST_WB_MRDIA_VIWE_REQUEST_CODE = 5;
    private static final int REQUEST_PIC_SEARCH_VIWE_REQUEST_CODE = 6;

    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private String mMyUserId;
    // the parameter is too big to be sent by the intent
    // so use a static variable to send it
    public static RoomPreviewData sRoomPreviewData = null;
    private String mEventId;
    private String mDefaultRoomName;
    private String mDefaultTopic;

    private MXLatestChatMessageCache mLatestChatMessageCache;

    private View mSendingMessagesLayout;
    private View mSendButtonLayout;
    private ImageView mSendImageView;
    private EditText mEditText;
    private ImageView mAvatarImageView;
    private View mCanNotPostTextView;
    private ImageView mE2eImageView;

    // call
    private View mStartCallLayout;
    private View mStopCallLayout;

    // action bar header
    private android.support.v7.widget.Toolbar mToolbar;
    private TextView mActionBarCustomTitle;
    private TextView mActionBarCustomTopic;
    private ImageView mActionBarCustomArrowImageView;
    private RelativeLayout mRoomHeaderView;
    private TextView mActionBarHeaderRoomName;
    private TextView mActionBarHeaderActiveMembers;
    private TextView mActionBarHeaderRoomTopic;
    private ImageView mActionBarHeaderRoomAvatar;
    private View mActionBarHeaderInviteMemberView;

    // notifications area
    private View mNotificationsArea;
    private ImageView mNotificationIconImageView;
    private TextView mNotificationTextView;
    private String mLatestTypingMessage;
    private Boolean mIsScrolledToTheBottom;
    private Event mLatestDisplayedEvent; // the event at the bottom of the list

    // room preview
    private View mRoomPreviewLayout;

    private MenuItem mLiveMenuItem;
    private MenuItem mResendUnsentMenuItem;
    private MenuItem mResendDeleteMenuItem;
    private MenuItem mSearchInRoomMenuItem;

    // medias sending helper
    private VectorRoomMediasSender mVectorRoomMediasSender;

    // pending call
    private VectorPendingCallView mVectorPendingCallView;

    // outgoing call
    private VectorOngoingConferenceCallView mVectorOngoingConferenceCallView;

    // network events
    private final IMXNetworkEventListener mNetworkEventListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                    refreshCallButtons();
                }
            });
        }
    };

    private String mCallId = null;

    private static String mLatestTakePictureCameraUri = null; // has to be String not Uri because of Serializable

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long mLastTypingDate = 0;

    // scroll to a dedicated index
    private int mScrollToIndex = -1;

    private boolean mIgnoreTextUpdate = false;

    // https://github.com/vector-im/vector-android/issues/323
    // on some devices, the toolbar background is set to transparent
    // when an activity is opened from this one.
    // It should not but it does.
    private boolean mIsHeaderViewDisplayed = false;

    /** **/
    private final ApiCallback<Void> mDirectMessageListener = new SimpleApiCallback<Void>(this) {
        @Override
        public void onMatrixError(MatrixError e) {
            if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                Toast.makeText(VectorRoomActivity.this, e.error, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onSuccess(Void info) {
        }

        @Override
        public void onNetworkError(Exception e) {
            Toast.makeText(VectorRoomActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Toast.makeText(VectorRoomActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    };

    /**
     * Presence and room preview listeners
     */
    private final MXEventListener mGlobalEventListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, User user) {
            // the header displays active members
            updateRoomHeaderMembersStatus();
        }

        @Override
        public void onLeaveRoom(String roomId) {
            // test if the user reject the invitation
            if ((null != sRoomPreviewData) && TextUtils.equals(sRoomPreviewData.getRoomId(), roomId)) {
                Log.d(LOG_TAG, "The room invitation has been declined from another client");
                onDeclined();
            }
        }

        @Override
        public void onJoinRoom(String roomId) {
            // test if the user accepts the invitation
            if ((null != sRoomPreviewData) && TextUtils.equals(sRoomPreviewData.getRoomId(), roomId)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "The room invitation has been accepted from another client");
                        onJoined();
                    }
                });
            }
        }
    };

    /**
     * The room events listener
     */
    private final MXEventListener mRoomEventListener = new MXEventListener() {
        @Override
        public void onRoomFlush(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateActionBarTitleAndTopic();
                    updateRoomHeaderMembersStatus();
                    updateRoomHeaderAvatar();
                }
            });
        }

        @Override
        public void onLeaveRoom(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VectorRoomActivity.this.finish();
                }
            });
        }

        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            VectorRoomActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    String eventType = event.getType();

                    // The various events that could possibly change the room title
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)) {
                        setTitle();
                        updateRoomHeaderMembersStatus();
                        updateRoomHeaderAvatar();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType)) {
                        checkSendEventStatus();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)) {
                        Log.d(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.getContent());
                        setTopic(roomState.topic);
                    } else if (Event.EVENT_TYPE_TYPING.equals(eventType)) {
                        Log.d(LOG_TAG, "on room typing");
                        onRoomTypings();
                    }
                    // header room specific
                    else if (Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(eventType)) {
                        Log.d(LOG_TAG, "Event room avatar");
                        updateRoomHeaderAvatar();
                    } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
                        boolean canSendEncryptedEvent = mRoom.isEncrypted() && mSession.isCryptoEnabled();
                        mE2eImageView.setImageResource(canSendEncryptedEvent ? R.drawable.e2e_verified : R.drawable.e2e_unencrypted);
                        mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());
                    }

                    if (!VectorApp.isAppInBackground()) {
                        // do not send read receipt for the typing events
                        // they are ephemeral ones.
                        if (!Event.EVENT_TYPE_TYPING.equals(eventType)) {
                            if (null != mRoom) {
                                refreshNotificationsArea();
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onRoomInitialSyncComplete(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // set general room information
                    mVectorMessageListFragment.onInitialMessagesLoaded();
                    updateActionBarTitleAndTopic();
                }
            });
        }

        @Override
        public void onBingRulesUpdate() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateActionBarTitleAndTopic();
                    mVectorMessageListFragment.onBingRulesUpdate();
                }
            });
        }

        @Override
        public void onEventEncrypted(Event event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onSentEvent(Event event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onFailedSendingEvent(Event event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onReceiptEvent(String roomId, List<String> senderIds) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }
    };

    private final IMXCall.MXCallListener mCallListener = new IMXCall.MXCallListener() {
        @Override
        public void onStateDidChange(String state) {
        }

        @Override
        public void onCallError(String error) {
            refreshCallButtons();
        }

        @Override
        public void onViewLoading(View callview) {

        }

        @Override
        public void onViewReady() {
        }

        @Override
        public void onCallAnsweredElsewhere() {
            refreshCallButtons();
        }

        @Override
        public void onCallEnd(final int aReasonId) {
            refreshCallButtons();

            // catch the flow where the hangup is done in VectorRoomActivity
            VectorCallSoundManager.releaseAudioFocus();
            // and play a lovely sound
            VectorCallSoundManager.startEndCallSound();
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
        }
    };

    //================================================================================
    // Activity classes
    //================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "onCreate : Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        final Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        mSession = MXCActionBarActivity.getSession(this, intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

        // ensure that the preview mode is really expected
        if (!intent.hasExtra(EXTRA_ROOM_PREVIEW_ID)) {
            sRoomPreviewData = null;
            Matrix.getInstance(this).clearTmpStoresList();
        }

        if (CommonActivityUtils.isGoingToSplash(this, mSession.getMyUserId(), roomId)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        //setDragEdge(SwipeBackLayout.DragEdge.LEFT);

        // bind the widgets of the room header view. The room header view is displayed by
        // clicking on the title of the action bar
        mRoomHeaderView = (RelativeLayout) findViewById(R.id.action_bar_header);
        mActionBarHeaderRoomTopic = (TextView) findViewById(R.id.action_bar_header_room_topic);
        mActionBarHeaderRoomName = (TextView) findViewById(R.id.action_bar_header_room_title);
        mActionBarHeaderActiveMembers = (TextView) findViewById(R.id.action_bar_header_room_members);
        mActionBarHeaderRoomAvatar = (ImageView) mRoomHeaderView.findViewById(R.id.avatar_img);
        mActionBarHeaderInviteMemberView = mRoomHeaderView.findViewById(R.id.action_bar_header_invite_members);
        mRoomPreviewLayout = findViewById(R.id.room_preview_info_layout);
        mVectorPendingCallView = (VectorPendingCallView) findViewById(R.id.room_pending_call_view);
        mVectorOngoingConferenceCallView = (VectorOngoingConferenceCallView) findViewById(R.id.room_ongoing_conference_call_view);
        mE2eImageView = (ImageView) findViewById(R.id.room_encrypted_image_view);

        // hide the header room as soon as the bottom layout (text edit zone) is touched
        findViewById(R.id.room_bottom_layout).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                return false;
            }
        });

        // use a toolbar instead of the actionbar
        // to be able to display an expandable header
        mToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.room_toolbar);
        this.setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set the default custom action bar layout,
        // that will be displayed from the custom action bar layout
        setActionBarDefaultCustomLayout();

        mCallId = intent.getStringExtra(EXTRA_START_CALL_ID);
        mEventId = intent.getStringExtra(EXTRA_EVENT_ID);
        mDefaultRoomName = intent.getStringExtra(EXTRA_DEFAULT_NAME);
        mDefaultTopic = intent.getStringExtra(EXTRA_DEFAULT_TOPIC);

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(NotificationUtils.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }

        Log.d(LOG_TAG, "Displaying " + roomId);

        mEditText = (EditText) findViewById(R.id.editText_messageBox);

        // hide the header room as soon as the message input text area is touched
        mEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hidePadType(0);
                hidePadType(1);
                hidePadType(2);
                hidePadType(3);
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    }
                });
            }
        });

        // IME's DONE button is treated as a send action
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                int imeActionId = actionId & EditorInfo.IME_MASK_ACTION;

                if (EditorInfo.IME_ACTION_DONE == imeActionId) {
                    sendTextMessage();
                }

                return false;
            }
        });

        mSendingMessagesLayout = findViewById(R.id.room_sending_message_layout);
        mSendImageView = (ImageView) findViewById(R.id.room_send_image_view);
        mSendButtonLayout = findViewById(R.id.room_send_layout);
        mSendButtonLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(mEditText.getText())) {
                    ObjectAnimator.ofFloat(v, "translationX", 0F, 20F, 0F).setDuration(300).start();//
                    sendTextMessage();
                } else {
                    // hide the header room
//                    enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
//
//                    FragmentManager fm = getSupportFragmentManager();
//                    IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_ATTACHMENTS_DIALOG);
//
//                    if (fragment != null) {
//                        fragment.dismissAllowingStateLoss();
//                    }
//
//                    final Integer[] messages = new Integer[]{
//                            R.string.option_send_files,
//                            R.string.option_take_photo_video,
//                    };
//
//                    final Integer[] icons = new Integer[]{
//                            R.drawable.ic_material_file,  // R.string.option_send_files
//                            R.drawable.ic_material_camera, // R.string.option_take_photo
//                    };
//
//
//                    fragment = IconAndTextDialogFragment.newInstance(icons, messages, null, ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_text_black_color));
//                    fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
//                        @Override
//                        public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
//                            Integer selectedVal = messages[position];
//
//                            if (selectedVal == R.string.option_send_files) {
//                                VectorRoomActivity.this.launchFileSelectionIntent();
//                            } else if (selectedVal == R.string.option_take_photo_video) {
//                                if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)) {
//                                    launchCamera();
//                                }
//                            }
//                        }
//                    });
//
//                    fragment.show(fm, TAG_FRAGMENT_ATTACHMENTS_DIALOG);

                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(imm != null) {
                        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                freshButtomPad();
                            }
                        });
                    }else{
                        freshButtomPad();
                    }
                }
            }
        });


        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (null != mRoom) {
                    MXLatestChatMessageCache latestChatMessageCache = VectorRoomActivity.this.mLatestChatMessageCache;
                    String textInPlace = latestChatMessageCache.getLatestText(VectorRoomActivity.this, mRoom.getRoomId());

                    // check if there is really an update
                    // avoid useless updates (initializations..)
                    if (!mIgnoreTextUpdate && !textInPlace.equals(mEditText.getText().toString())) {
                        latestChatMessageCache.updateLatestMessage(VectorRoomActivity.this, mRoom.getRoomId(), mEditText.getText().toString());
                        handleTypingNotification(mEditText.getText().length() != 0);
                    }

                    manageSendMoreButtons();
                    refreshCallButtons();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mVectorPendingCallView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IMXCall call = VectorCallViewActivity.getActiveCall();
                if (null != call) {
                    final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);
                    intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
                    intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorRoomActivity.this.startActivity(intent);
                        }
                    });
                } else {
                    // if the call is no more active, just remove the view
                    mVectorPendingCallView.onCallTerminated();
                }
            }
        });

        mActionBarHeaderInviteMemberView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
            }
        });

        // notifications area
        mNotificationsArea = findViewById(R.id.room_notifications_area);
        mNotificationIconImageView = (ImageView) mNotificationsArea.findViewById(R.id.room_notification_icon);
        mNotificationTextView = (TextView) mNotificationsArea.findViewById(R.id.room_notification_message);

        mCanNotPostTextView = findViewById(R.id.room_cannot_post_textview);

        // increase the clickable area to open the keyboard.
        // when there is no text, it is quite small and some user thought the edition was disabled.
        findViewById(R.id.room_sending_message_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditText.requestFocus()) {
                    hidePadType(0);
                    hidePadType(1);
                    hidePadType(2);
                    hidePadType(3);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                        }
                    });

                }
            }
        });

        mStartCallLayout = findViewById(R.id.room_start_call_layout);
        mStartCallLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if ((null != mRoom) && mRoom.isEncrypted() && (mRoom.getActiveMembers().size() > 2)) {
//                    // display the dialog with the info text
//                    AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
//                    Resources resource = getResources();
//                    permissionsInfoDialog.setMessage(resource.getString(R.string.room_no_conference_call_in_encrypted_rooms));
//                    permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
//                    permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
//                    permissionsInfoDialog.show();
//
//                } else if (isUserAllowedToStartConfCall()) {
//                    displayVideoCallIpDialog();
//                } else {
//                    displayConfCallNotAllowed();
//                }

                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm != null) {
                    imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                            showEmojiPad();
                        }
                    });
                }else{
                    showEmojiPad();
                }
            }
        });

        mStopCallLayout = findViewById(R.id.room_end_call_layout);
        mStopCallLayout.setOnClickListener(new View.OnClickListener() {
                                               @Override
                                               public void onClick(View v) {
                                                   IMXCall call = mSession.mCallsManager.getCallWithRoomId(mRoom.getRoomId());

                                                   if (null != call) {
                                                       call.hangup(null);
                                                   }
                                               }
                                           }
        );

        findViewById(R.id.room_button_margin_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // extend the right side of right button
                // to avoid clicking in the void
                if (mStopCallLayout.getVisibility() == View.VISIBLE) {
                    mStopCallLayout.performClick();
                } else if (mStartCallLayout.getVisibility() == View.VISIBLE) {
                    mStartCallLayout.performClick();
                } else if (mSendButtonLayout.getVisibility() == View.VISIBLE) {
                    mSendButtonLayout.performClick();
                }
            }
        });

        mMyUserId = mSession.getCredentials().userId;

        CommonActivityUtils.resumeEventStream(this);

        mRoom = mSession.getDataHandler().getRoom(roomId, false);

        FragmentManager fm = getSupportFragmentManager();
        mVectorMessageListFragment = (VectorMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);

        if (mVectorMessageListFragment == null) {
            Log.d(LOG_TAG, "Create VectorMessageListFragment");

            // this fragment displays messages and handles all message logic
            mVectorMessageListFragment = VectorMessageListFragment.newInstance(mMyUserId, roomId, mEventId, (null == sRoomPreviewData) ? null : VectorMessageListFragment.PREVIEW_MODE_READ_ONLY, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mVectorMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        } else {
            Log.d(LOG_TAG, "Reuse VectorMessageListFragment");
        }

        mVectorRoomMediasSender = new VectorRoomMediasSender(this, mVectorMessageListFragment, Matrix.getInstance(this).getMediasCache());
        mVectorRoomMediasSender.onRestoreInstanceState(savedInstanceState);

        manageRoomPreview();

        addRoomHeaderClickListeners();

        // in timeline mode (i.e search in the forward and backward room history)
        // or in room preview mode
        // the edition items are not displayed
        if (!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData)) {
            mNotificationsArea.setVisibility(View.GONE);
            findViewById(R.id.bottom_separator).setVisibility(View.GONE);
            findViewById(R.id.room_notification_separator).setVisibility(View.GONE);
            findViewById(R.id.room_notifications_area).setVisibility(View.GONE);

            View v = findViewById(R.id.room_bottom_layout);
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.height = 0;
            v.setLayoutParams(params);
        }

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            final Intent mediaIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);

            // sanity check
            if (null != mediaIntent) {
                mEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        intent.removeExtra(EXTRA_ROOM_INTENT);
                        sendMediasIntent(mediaIntent);
                    }
                }, 1000);
            }
        }

        mVectorOngoingConferenceCallView.initRoomInfo(mSession, mRoom);
        mVectorOngoingConferenceCallView.setCallClickListener(new VectorOngoingConferenceCallView.ICallClickListener() {

            private void startCall(boolean isVideo) {
                if (CommonActivityUtils.checkPermissions(isVideo ? CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL : CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL,
                        VectorRoomActivity.this)) {
                    startIpCall(isVideo);
                }
            }

            @Override
            public void onVoiceCallClick() {
                startCall(false);
            }

            @Override
            public void onVideoCallClick() {
                startCall(true);
            }
        });

        mAvatarImageView = (ImageView)findViewById(R.id.room_self_avatar);
        if (null != mAvatarImageView) {
            VectorUtils.loadUserAvatar(this, mSession, mAvatarImageView, mSession.getMyUser());
        }
        mAvatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Editable text = mEditText.getText();
                if (!TextUtils.isEmpty(text)){
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mAvatarImageView.getWindowToken(), 0);
                    moreImg = R.drawable.ic_material_file2;
                    mSendImageView.setImageResource(moreImg);
                    mEditText.setText("");
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showPicSearch(text.toString());;
                        }
                    }, 50);
                }
            }
        });

//        if (null != avatarLayout) {
//            mAvatarImageView = (ImageView) avatarLayout.findViewById(R.id.avatar_img);
//        }

//        refreshSelfAvatar();

        // in case a "Send as" dialog was in progress when the activity was destroyed (life cycle)
        mVectorRoomMediasSender.resumeResizeMediaAndSend();

        // header visibility has launched
        enableActionBarHeader(intent.getBooleanExtra(EXTRA_EXPAND_ROOM_HEADER, false) ? SHOW_ACTION_BAR_HEADER : HIDE_ACTION_BAR_HEADER);

        // the both flags are only used once
        intent.removeExtra(EXTRA_EXPAND_ROOM_HEADER);

        setEmojiconFragment(false);

        Log.d(LOG_TAG, "End of create");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        mVectorRoomMediasSender.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(FIRST_VISIBLE_ROW, mVectorMessageListFragment.mMessageListView.getFirstVisiblePosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // the listView will be refreshed so the offset might be lost.
        mScrollToIndex = savedInstanceState.getInt(FIRST_VISIBLE_ROW, -1);
    }

    @Override
    public void onDestroy() {
        if (null != mVectorMessageListFragment) {
            mVectorMessageListFragment.onDestroy();
        }

        if (null != mVectorOngoingConferenceCallView) {
            mVectorOngoingConferenceCallView.setCallClickListener(null);
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // warn other member that the typing is ended
        cancelTypingNotification();

        if (null != mRoom) {
            // listen for room name or topic changes
            mRoom.removeEventListener(mRoomEventListener);
        }

        Matrix.getInstance(this).removeNetworkEventListener(mNetworkEventListener);

        if (mSession.isAlive()) {
            // GA reports a null dataHandler instance event if it seems impossible
            if (null != mSession.getDataHandler()) {
                mSession.getDataHandler().removeListener(mGlobalEventListener);
            }
        }

        mVectorOngoingConferenceCallView.onActivityPause();

        // to have notifications for this room
        ViewedRoomTracker.getInstance().setViewedRoomId(null);
        ViewedRoomTracker.getInstance().setMatrixId(null);

        EventBus.unregisterAnnotatedReceiver(this);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "++ Resume the activity");
        super.onResume();

        ViewedRoomTracker.getInstance().setMatrixId(mSession.getCredentials().userId);

        if (null != mRoom) {
            // to do not trigger notifications for this room
            // because it is displayed.
            ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());

            // check if the room has been left from another client.
            if (mRoom.isReady()) {
                if ((null == mRoom.getMember(mMyUserId)) || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
                    VectorRoomActivity.this.finish();
                    return;
                }
            }

            // listen for room name or topic changes
            mRoom.addEventListener(mRoomEventListener);

            mEditText.setHint(mRoom.isEncrypted() ? R.string.room_message_placeholder_encrypted : R.string.room_message_placeholder_not_encrypted);
        }

        mSession.getDataHandler().addListener(mGlobalEventListener);

        Matrix.getInstance(this).addNetworkEventListener(mNetworkEventListener);

        if (null != mRoom) {
            EventStreamService.cancelNotificationsForRoomId(mSession.getCredentials().userId, mRoom.getRoomId());
        }

        // sanity checks
        if ((null != mRoom) && (null != Matrix.getInstance(this).getDefaultLatestChatMessageCache())) {
            String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

            if (!cachedText.equals(mEditText.getText().toString())) {
                mIgnoreTextUpdate = true;
                mEditText.setText("");
                mEditText.append(cachedText);
                mIgnoreTextUpdate = false;
            }

            mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());

            boolean canSendEncryptedEvent = mRoom.isEncrypted() && mSession.isCryptoEnabled();
            mE2eImageView.setImageResource(canSendEncryptedEvent ? R.drawable.e2e_verified : R.drawable.e2e_unencrypted);
            mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());
        }

        manageSendMoreButtons();

        updateActionBarTitleAndTopic();

        sendReadReceipt();

        refreshCallButtons();

        updateRoomHeaderMembersStatus();

        checkSendEventStatus();

        enableActionBarHeader(mIsHeaderViewDisplayed);

        // refresh the UI : the timezone could have been updated
        mVectorMessageListFragment.refresh();

        // the list automatically scrolls down when its top moves down
        if (mVectorMessageListFragment.mMessageListView instanceof AutoScrollDownListView) {
            ((AutoScrollDownListView) mVectorMessageListFragment.mMessageListView).lockSelectionOnResize();
        }

        // the device has been rotated
        // so try to keep the same top/left item;
        if (mScrollToIndex > 0) {
            mVectorMessageListFragment.scrollToIndexWhenLoaded(mScrollToIndex);
            mScrollToIndex = -1;
        }

        if (null != mCallId) {
            IMXCall call = VectorCallViewActivity.getActiveCall();

            // can only manage one call instance.
            // either there is no active call or resume the active one
            if ((null == call) || call.getCallId().equals(mCallId)) {
                final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);
                intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, mCallId);

                if (null == call) {
                    intent.putExtra(VectorCallViewActivity.EXTRA_AUTO_ACCEPT, "anything");
                }

                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        VectorRoomActivity.this.startActivity(intent);
                    }
                });

            }

            mCallId = null;
        }

        if (null != mRoom) {
            // check if the room has been left from another activity
            if (mRoom.isLeaving() || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {

                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      VectorRoomActivity.this.finish();
                                  }
                              }
                );
            }
        }

        // the pending call view is only displayed with "active " room
        if ((null == sRoomPreviewData) && (null == mEventId)) {
            mVectorPendingCallView.checkPendingCall();
            mVectorOngoingConferenceCallView.onActivityResume();
        }

        displayE2eRoomAlert();

        EventBus.registerAnnotatedReceiver(this);

        Log.d(LOG_TAG, "-- Resume the activity");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if ((requestCode == REQUEST_FILES_REQUEST_CODE) || (requestCode == TAKE_IMAGE_REQUEST_CODE)) {
                sendMediasIntent(data);
            } else if (requestCode == GET_MENTION_REQUEST_CODE) {
                insertUserDisplayNameInTextEditor(data.getStringExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID));
            } else if (requestCode == REQUEST_ROOM_AVATAR_CODE) {
                onActivityResultRoomAvatarUpdate(data);
            } else if (requestCode == REQUEST_WB_IMAGE_REQUEST_CODE){
                setWBBackground(data);
            }else if (requestCode == REQUEST_WB_MRDIA_VIWE_REQUEST_CODE){
                setWBBackgroundFromMediaView(data);
            }else if(requestCode == REQUEST_PIC_SEARCH_VIWE_REQUEST_CODE){
                handlePicSearch(data);
            }
        }
    }

    //================================================================================
    // IEventSendingListener
    //================================================================================

    @Override
    public void onMessageSendingSucceeded(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onMessageSendingFailed(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onMessageRedacted(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onUnknownDevices(Event event, MXCryptoError error) {
        refreshNotificationsArea();
        CommonActivityUtils.displayUnknownDevicesDialog(mSession, this, (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData, new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
            @Override
            public void onSendAnyway() {
                mVectorMessageListFragment.resendUnsentMessages();
                refreshNotificationsArea();
            }
        });
    }

    //================================================================================
    // IOnScrollListener
    //================================================================================

    /**
     * Send a read receipt to the latest displayed event.
     */
    private void sendReadReceipt() {
        if ((null != mRoom) && (null == sRoomPreviewData)) {
            // send the read receipt
            mRoom.sendReadReceipt(mLatestDisplayedEvent, null);
            refreshNotificationsArea();
        }
    }

    @Override
    public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        Event eventAtBottom = mVectorMessageListFragment.getEvent(firstVisibleItem + visibleItemCount - 1);

        if ((null != eventAtBottom) && ((null == mLatestDisplayedEvent) || !TextUtils.equals(eventAtBottom.eventId, mLatestDisplayedEvent.eventId))) {

            Log.d(LOG_TAG, "## onScroll firstVisibleItem " + firstVisibleItem + " visibleItemCount " + visibleItemCount + " totalItemCount " + totalItemCount);
            mLatestDisplayedEvent = eventAtBottom;

            // don't send receive if the app is in background
            if (!VectorApp.isAppInBackground()) {
                sendReadReceipt();
            } else {
                Log.d(LOG_TAG, "## onScroll : the app is in background");
            }
        }
    }

    @Override
    public void onLatestEventDisplay(boolean isDisplayed) {
        // not yet initialized or a new value
        if ((null == mIsScrolledToTheBottom) || (isDisplayed != mIsScrolledToTheBottom)) {
            Log.d(LOG_TAG, "## onLatestEventDisplay : isDisplayed " + isDisplayed);

            if (isDisplayed && (null != mRoom)) {
                mLatestDisplayedEvent = mRoom.getDataHandler().getStore().getLatestEvent(mRoom.getRoomId());
                // ensure that the latest message is displayed
                mRoom.sendReadReceipt(null);
            }

            mIsScrolledToTheBottom = isDisplayed;
            refreshNotificationsArea();
        }
    }

    //================================================================================
    // Menu management
    //================================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        // GA : mSession is null
        if (CommonActivityUtils.shouldRestartApp(this) || (null == mSession)) {
            return false;
        }

        // the menu is only displayed when the current activity does not display a timeline search
        if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.vector_room, menu);


            mLiveMenuItem = menu.findItem(R.id.ic_action_room_leave);
            mResendUnsentMenuItem = menu.findItem(R.id.ic_action_room_resend_unsent);
            mResendDeleteMenuItem = menu.findItem(R.id.ic_action_room_delete_unsent);
            mSearchInRoomMenuItem =  menu.findItem(R.id.ic_action_search_in_room);

            // hide / show the unsent / resend all entries.
            refreshNotificationsArea();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_search_in_room) {
            try {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                final Intent searchIntent = new Intent(VectorRoomActivity.this, VectorUnifiedSearchActivity.class);
                searchIntent.putExtra(VectorUnifiedSearchActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                VectorRoomActivity.this.startActivity(searchIntent);

            } catch (Exception e) {
                Log.i(LOG_TAG, "## onOptionsItemSelected(): ");
            }
        } else if (id == R.id.ic_action_room_settings) {
            launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
        } else if (id == R.id.ic_action_room_resend_unsent) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_delete_unsent) {
            mVectorMessageListFragment.deleteUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_leave) {
            if (null != mRoom) {
                Log.d(LOG_TAG, "Leave the room " + mRoom.getRoomId());
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                        .setTitle(R.string.room_participants_leave_prompt_title)
                        .setMessage(R.string.room_participants_leave_prompt_msg)
                        .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                setProgressVisibility(View.VISIBLE);

                                mRoom.leave(new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        Log.d(LOG_TAG, "The room " + mRoom.getRoomId() + " is left");
                                        // close the activity
                                        finish();
                                    }

                                    private void onError(String errorMessage) {
                                        setProgressVisibility(View.GONE);
                                        Log.e(LOG_TAG, "Cannot leave the room " + mRoom.getRoomId() + " : " + errorMessage);
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
                                });
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Check if the current user is allowed to perform a conf call.
     * The user power level is checked against the invite power level.
     * <p>To start a conf call, the user needs to invite the CFU to the room.
     *
     * @return true if the user is allowed, false otherwise
     */
    private boolean isUserAllowedToStartConfCall() {
        boolean isAllowed = false;

        if (mRoom.isOngoingConferenceCall()) {
            // if a conf is in progress, the user can join the established conf anyway
            Log.d(LOG_TAG, "## isUserAllowedToStartConfCall(): conference in progress");
            isAllowed = true;
        } else if ((null != mRoom) && (mRoom.getActiveMembers().size() > 2)) {
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                // to start a conf call, the user MUST have the power to invite someone (CFU)
                isAllowed = powerLevels.getUserPowerLevel(mSession.getMyUserId()) >= powerLevels.invite;
            }
        } else {
            // 1:1 call
            isAllowed = true;
        }

        //TODO, now not support conference call
        if(mRoom.getActiveMembers().size() > 2){
            isAllowed = false;
        }

        Log.d(LOG_TAG, "## isUserAllowedToStartConfCall(): isAllowed=" + isAllowed);
        return isAllowed;
    }

    /**
     * Display a dialog box to indicate that the conf call can no be performed.
     * <p>See {@link #isUserAllowedToStartConfCall()}
     */
    private void displayConfCallNotAllowed() {
        // display the dialog with the info text
        AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
        Resources resource = getResources();

        if ((null != resource) && (null != permissionsInfoDialog)) {
            permissionsInfoDialog.setTitle(resource.getString(R.string.missing_permissions_title_to_start_conf_call));
            permissionsInfoDialog.setMessage(resource.getString(R.string.missing_permissions_to_start_conf_call));

            permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
            permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
            permissionsInfoDialog.show();
        } else {
            Log.e(LOG_TAG, "## displayConfCallNotAllowed(): impossible to create dialog");
        }
    }

    /**
     * Start an IP call with the management of the corresponding permissions.
     * According to the IP call, the corresponding permissions are asked: {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_AUDIO_IP_CALL}
     * or {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_VIDEO_IP_CALL}.
     */
    private void displayVideoCallIpDialog() {
        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final Integer[] lIcons = new Integer[]{R.drawable.voice_call_black, R.drawable.video_call_black};
        final Integer[] lTexts = new Integer[]{R.string.action_voice_call, R.string.action_video_call};

        IconAndTextDialogFragment fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts);
        fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
            @Override
            public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                boolean isVideoCall = false;
                int requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL;

                if (1 == position) {
                    isVideoCall = true;
                    requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL;
                }

                if (CommonActivityUtils.checkPermissions(requestCode, VectorRoomActivity.this)) {
                    startIpCall(isVideoCall);
                }
            }
        });

        // display the fragment dialog
        fragment.show(getSupportFragmentManager(), TAG_FRAGMENT_CALL_OPTIONS);
    }

    /**
     * Start an IP call: audio call if aIsVideoCall is false or video call if aIsVideoCall
     * is true.
     *
     * @param aIsVideoCall true to video call, false to audio call
     */
    private void startIpCall(final boolean aIsVideoCall) {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        setProgressVisibility(View.VISIBLE);

        // create the call object
        mSession.mCallsManager.createCallInRoom(mRoom.getRoomId(), new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                Log.d(LOG_TAG, "## startIpCall(): onSuccess");
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressVisibility(View.GONE);
                        call.setIsVideo(aIsVideoCall);
                        call.setIsIncoming(false);

                        final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);

                        intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VectorRoomActivity.this.startActivity(intent);
                            }
                        });
                    }
                });
            }

            private void onError(final String errorMessage) {
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressVisibility(View.GONE);
                        Activity activity = VectorRoomActivity.this;
                        CommonActivityUtils.displayToastOnUiThread(activity, activity.getString(R.string.cannot_start_call) + " (" + errorMessage + ")");
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onNetworkError Msg=" + e.getMessage());
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## startIpCall(): onMatrixError Msg=" + e.getLocalizedMessage());

                if (e instanceof MXCryptoError) {
                    MXCryptoError cryptoError = (MXCryptoError)e;
                    if (MXCryptoError.UNKNOWN_DEVICES_CODE.equals(cryptoError.errcode)) {
                        setProgressVisibility(View.GONE);
                        CommonActivityUtils.displayUnknownDevicesDialog(mSession, VectorRoomActivity.this, (MXUsersDevicesMap<MXDeviceInfo>)cryptoError.mExceptionData, new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
                            @Override
                            public void onSendAnyway() {
                                startIpCall(aIsVideoCall);
                            }
                        });

                        return;
                    }
                }

                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onUnexpectedError Msg=" + e.getLocalizedMessage());
                onError(e.getLocalizedMessage());
            }
        });
    }

    //================================================================================
    // messages sending
    //================================================================================

    /**
     * Cancels the room selection mode.
     */
    public void cancelSelectionMode() {
        mVectorMessageListFragment.cancelSelectionMode();
    }

    /**
     * Send the editText text.
     */
    private void sendTextMessage() {
        VectorApp.markdownToHtml(mEditText.getText().toString().trim(), new VectorMarkdownParser.IVectorMarkdownParserListener() {
            @Override
            public void onMarkdownParsed(final String text, final String HTMLText) {
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                        sendMessage(text, TextUtils.equals(text, HTMLText) ? null : HTMLText, Message.FORMAT_MATRIX_HTML);

                        new Handler().postDelayed(new Runnable(){
                            public void run() {
                                mEditText.setText("");
                            }
                        }, 300);
                    }
                });
            }
        });
    }

    /**
     * Send a text message with its formatted format
     *
     * @param body          the text message.
     * @param formattedBody the formatted message
     * @param format        the message format
     */
    public void sendMessage(String body, String formattedBody, String format) {
        if (!TextUtils.isEmpty(body)) {
            if (!SlashComandsParser.manageSplashCommand(this, mSession, mRoom, body, formattedBody, format)) {
                cancelSelectionMode();
                mVectorMessageListFragment.sendTextMessage(body, formattedBody, format);
            }
        }
    }

    /**
     * Send an emote in the opened room
     *
     * @param emote the emote
     */
    public void sendEmote(String emote, String formattedEmote, String format) {
        if (null != mVectorMessageListFragment) {
            mVectorMessageListFragment.sendEmote(emote, formattedEmote, format);
        }
    }

    @SuppressLint("NewApi")
    /**
     * Send the medias defined in the intent.
     * They are listed, checked and sent when it is possible.
     */
    private void sendMediasIntent(final Intent intent) {
        // sanity check
        if ((null == intent) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        ArrayList<SharedDataItem> sharedDataItems = new ArrayList<>();

        if (null != intent) {
            sharedDataItems = new ArrayList<>(SharedDataItem.listSharedDataItems(intent));
        } else if (null != mLatestTakePictureCameraUri) {
            sharedDataItems.add(new SharedDataItem(Uri.parse(mLatestTakePictureCameraUri)));
            mLatestTakePictureCameraUri = null;
        }

        // check the extras
        if (0 == sharedDataItems.size()) {
            Bundle bundle = intent.getExtras();

            // sanity checks
            if (null != bundle) {
                bundle.setClassLoader(SharedDataItem.class.getClassLoader());

                if (bundle.containsKey(Intent.EXTRA_STREAM)) {
                    try {
                        Object streamUri = bundle.get(Intent.EXTRA_STREAM);

                        if (streamUri instanceof Uri) {
                            sharedDataItems.add(new SharedDataItem((Uri) streamUri));
                        } else if (streamUri instanceof List) {
                            List<Object> streams = (List<Object>) streamUri;

                            for (Object object : streams) {
                                if (object instanceof Uri) {
                                    sharedDataItems.add(new SharedDataItem((Uri) object));
                                } else if (object instanceof SharedDataItem) {
                                    sharedDataItems.add((SharedDataItem) object);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "fail to extract the extra stream");
                    }
                } else if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                    mEditText.setText(mEditText.getText() + bundle.getString(Intent.EXTRA_TEXT));

                    mEditText.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.setSelection(mEditText.getText().length());
                        }
                    });
                }
            }
        }

        if (0 != sharedDataItems.size()) {
            mVectorRoomMediasSender.sendMedias(sharedDataItems);
        }
    }

    //================================================================================
    // typing
    //================================================================================

    /**
     * send a typing event notification
     *
     * @param isTyping typing param
     */
    private void handleTypingNotification(boolean isTyping) {
        int notificationTimeoutMS = -1;
        if (isTyping) {
            // Check whether a typing event has been already reported to server (We wait for the end of the local timeout before considering this new event)
            if (null != mTypingTimer) {
                // Refresh date of the last observed typing
                System.currentTimeMillis();
                mLastTypingDate = System.currentTimeMillis();
                return;
            }

            int timerTimeoutInMs = TYPING_TIMEOUT_MS;

            if (0 != mLastTypingDate) {
                long lastTypingAge = System.currentTimeMillis() - mLastTypingDate;
                if (lastTypingAge < timerTimeoutInMs) {
                    // Subtract the time interval since last typing from the timer timeout
                    timerTimeoutInMs -= lastTypingAge;
                } else {
                    timerTimeoutInMs = 0;
                }
            } else {
                // Keep date of this typing event
                mLastTypingDate = System.currentTimeMillis();
            }

            if (timerTimeoutInMs > 0) {

                mTypingTimerTask = new TimerTask() {
                    public void run() {
                        synchronized (LOG_TAG) {
                            if (mTypingTimerTask != null) {
                                mTypingTimerTask.cancel();
                                mTypingTimerTask = null;
                            }

                            if (mTypingTimer != null) {
                                mTypingTimer.cancel();
                                mTypingTimer = null;
                            }
                            // Post a new typing notification
                            VectorRoomActivity.this.handleTypingNotification(0 != mLastTypingDate);
                        }
                    }
                };

                try {
                    synchronized (LOG_TAG) {
                        mTypingTimer = new Timer();
                        mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "fails to launch typing timer " + e.getLocalizedMessage());
                }

                // Compute the notification timeout in ms (consider the double of the local typing timeout)
                notificationTimeoutMS = TYPING_TIMEOUT_MS * 2;
            } else {
                // This typing event is too old, we will ignore it
                isTyping = false;
            }
        } else {
            // Cancel any typing timer
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }

            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }
            // Reset last typing date
            mLastTypingDate = 0;
        }

        final boolean typingStatus = isTyping;

        mRoom.sendTypingNotification(typingStatus, notificationTimeoutMS, new SimpleApiCallback<Void>(VectorRoomActivity.this) {
            @Override
            public void onSuccess(Void info) {
                // Reset last typing date
                mLastTypingDate = 0;
            }

            @Override
            public void onNetworkError(Exception e) {
                if (mTypingTimerTask != null) {
                    mTypingTimerTask.cancel();
                    mTypingTimerTask = null;
                }

                if (mTypingTimer != null) {
                    mTypingTimer.cancel();
                    mTypingTimer = null;
                }
                // do not send again
                // assume that the typing event is optional
            }
        });
    }

    private void cancelTypingNotification() {
        if (0 != mLastTypingDate) {
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }
            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }

            mLastTypingDate = 0;

            mRoom.sendTypingNotification(false, -1, new SimpleApiCallback<Void>(VectorRoomActivity.this) {
            });
        }
    }

    //================================================================================
    // Actions
    //================================================================================

    /**
     * Update the spinner visibility.
     *
     * @param visibility the visibility.
     */
    public void setProgressVisibility(int visibility) {
        View progressLayout = findViewById(R.id.main_progress_layout);

        if ((null != progressLayout) && (progressLayout.getVisibility() != visibility)) {
            progressLayout.setVisibility(visibility);
        }
    }

    /**
     * Launch the room details activity with a selected tab.
     *
     * @param selectedTab the selected tab index.
     */
    private void launchRoomDetails(int selectedTab) {
        if ((null != mRoom) && (null != mRoom.getMember(mSession.getMyUserId()))) {
            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

            // pop to the home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorRoomDetailsActivity.class);
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_SELECTED_TAB_ID, selectedTab);
            startActivityForResult(intent, GET_MENTION_REQUEST_CODE);
        }
    }

    /**
     * Launch the files selection intent
     */
    @SuppressLint("NewApi")
    private void launchFileSelectionIntent() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_FILES_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent intent = new Intent(this, VectorMediasPickerActivity.class);
        intent.putExtra(VectorMediasPickerActivity.EXTRA_VIDEO_RECORDING_MODE, true);
        startActivityForResult(intent, TAKE_IMAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_ROOM_DETAILS) {
            boolean isCameraPermissionGranted = false;

            for (int i = 0; i < aPermissions.length; i++) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): " + aPermissions[i] + "=" + aGrantResults[i]);

                if (Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }
            }

            // the user allows to use to the camera.
            if (isCameraPermissionGranted) {
                Intent intent = new Intent(VectorRoomActivity.this, VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                startActivityForResult(intent, REQUEST_ROOM_AVATAR_CODE);
            } else {
                launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {
            boolean isCameraPermissionGranted = false;

            for (int i = 0; i < aPermissions.length; i++) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): " + aPermissions[i] + "=" + aGrantResults[i]);

                if (Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }

                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission granted");
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission not granted");
                    }
                }
            }

            // Because external storage permission is not mandatory to launch the camera,
            // external storage permission is not tested.
            if (isCameraPermissionGranted) {
                launchCamera();
            } else {
                CommonActivityUtils.displayToast(this, getString(R.string.missing_permissions_warning));
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultAudioIpCall(this, aPermissions, aGrantResults)) {
                startIpCall(false);
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults)) {
                startIpCall(true);
            }
        } else {
            Log.w(LOG_TAG, "## onRequestPermissionsResult(): Unknown requestCode =" + aRequestCode);
        }
    }

    /**
     * Display UI buttons according to user input text.
     */
    private void manageSendMoreButtons() {
        boolean hasText = (mEditText.getText().length() > 0);
        mSendImageView.setImageResource(hasText ? R.drawable.ic_material_send_green : moreImg);
    }

    /**
     * Refresh the Account avatar
     */
    private void refreshSelfAvatar() {
        // sanity check
        if (null != mAvatarImageView) {
            VectorUtils.loadUserAvatar(this, mSession, mAvatarImageView, mSession.getMyUser());
        }
    }

    /**
     * Sanitize the display name.
     *
     * @param displayName the display name to sanitize
     * @return the sanitized display name
     */
    private static String sanitizeDisplayname(String displayName) {
        // sanity checks
        if (!TextUtils.isEmpty(displayName)) {
            final String ircPattern = " (IRC)";

            if (displayName.endsWith(ircPattern)) {
                displayName = displayName.substring(0, displayName.length() - ircPattern.length());
            }
        }

        return displayName;
    }

    /**
     * Insert an user displayname  in the message editor.
     *
     * @param text the text to insert.
     */
    public void insertUserDisplayNameInTextEditor(String text) {
        if (null != text) {
            if (TextUtils.equals(mSession.getMyUser().displayname, text)) {
                // current user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    mEditText.setText(String.format("%s ", SlashComandsParser.CMD_EMOTE));
                    mEditText.setSelection(mEditText.getText().length());
                }
            } else {
                // another user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    mEditText.append(sanitizeDisplayname(text) + ": ");
                } else {
                    mEditText.getText().insert(mEditText.getSelectionStart(), sanitizeDisplayname(text) + " ");
                }
            }
        }
    }

    /**
     * Insert a quote  in the message editor.
     *
     * @param quote the quote to insert.
     */
    public void insertQuoteInTextEditor(String quote) {
        if (!TextUtils.isEmpty(quote)) {
            if (TextUtils.isEmpty(mEditText.getText())) {
                mEditText.setText("");
                mEditText.append(quote);
            } else {
                mEditText.getText().insert(mEditText.getSelectionStart(), "\n" + quote);
            }
        }
    }

    //================================================================================
    // Notifications area management (... is typing and so on)
    //================================================================================

    /**
     * Track the cancel all click.
     */
    private class cancelAllClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            mVectorMessageListFragment.deleteUnsentMessages();
            refreshNotificationsArea();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(getResources().getColor(R.color.vector_fuchsia_color));
            ds.bgColor = 0;
            ds.setFakeBoldText(true);
        }
    }

    /**
     * Track the resend all click.
     */
    private class resendAllClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(getResources().getColor(R.color.vector_fuchsia_color));
            ds.bgColor = 0;
            ds.setFakeBoldText(true);
        }
    }

    /**
     * Refresh the notifications area.
     */
    private void refreshNotificationsArea() {
        // sanity check
        // might happen when the application is logged out
        if ((null == mSession.getDataHandler()) || (null == mRoom) || (null != sRoomPreviewData)) {
            return;
        }

        int iconId = -1;
        int textColor = -1;
        boolean isAreaVisible = false;
        SpannableString text = new SpannableString("");
        boolean hasUnsentEvent = false;

        // remove any listeners
        mNotificationTextView.setOnClickListener(null);
        mNotificationIconImageView.setOnClickListener(null);

        //  no network
        if (!Matrix.getInstance(this).isConnected()) {
            isAreaVisible = true;
            iconId = R.drawable.error;
            textColor = R.color.vector_fuchsia_color;
            text = new SpannableString(getResources().getString(R.string.room_offline_notification));
        } else {
            List<Event> undeliveredEvents = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());
            List<Event> unknownDeviceEvents = mSession.getDataHandler().getStore().getUnknownDeviceEvents(mRoom.getRoomId());

            boolean hasUndeliverableEvents = (null != undeliveredEvents) && (undeliveredEvents.size() > 0);
            boolean hasUnknownDeviceEvents = (null != unknownDeviceEvents) && (unknownDeviceEvents.size() > 0);

            if (hasUndeliverableEvents || hasUnknownDeviceEvents) {
                hasUnsentEvent = true;
                isAreaVisible = true;
                iconId = R.drawable.error;

                String cancelAll = getResources().getString(R.string.room_prompt_cancel);
                String resendAll = getResources().getString(R.string.room_prompt_resend);
                String message = getResources().getString(hasUnknownDeviceEvents ? R.string.room_unknown_devices_messages_notification : R.string.room_unsent_messages_notification, resendAll, cancelAll);

                int cancelAllPos = message.indexOf(cancelAll);
                int resendAllPos = message.indexOf(resendAll);

                text = new SpannableString(message);

                // cancelAllPos should always be > 0 but a GA crash reported here
                if (cancelAllPos >= 0) {
                    text.setSpan(new cancelAllClickableSpan(), cancelAllPos, cancelAllPos + cancelAll.length(), 0);
                }

                // resendAllPos should always be > 0 but a GA crash reported here
                if (resendAllPos >= 0) {
                    text.setSpan(new resendAllClickableSpan(), resendAllPos, resendAllPos + resendAll.length(), 0);
                }

                mNotificationTextView.setMovementMethod(LinkMovementMethod.getInstance());
                textColor = R.color.vector_fuchsia_color;

            } else if ((null != mIsScrolledToTheBottom) && (!mIsScrolledToTheBottom)) {
                isAreaVisible = true;

                int unreadCount = 0;

                RoomSummary summary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());

                if (null != summary) {
                    unreadCount = mRoom.getDataHandler().getStore().eventsCountAfter(mRoom.getRoomId(), summary.getLatestReadEventId());
                }

                if (unreadCount > 0) {
                    iconId = R.drawable.newmessages;
                    textColor = R.color.vector_fuchsia_color;

                    if (unreadCount == 1) {
                        text = new SpannableString(getResources().getString(R.string.room_new_message_notification));
                    } else {
                        text = new SpannableString(getResources().getString(R.string.room_new_messages_notification, unreadCount));
                    }
                } else {
                    iconId = R.drawable.scrolldown;
                    textColor = R.color.vector_text_gray_color;

                    if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                        text = new SpannableString(mLatestTypingMessage);
                    }
                }

                mNotificationTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mVectorMessageListFragment.scrollToBottom(0);
                    }
                });

                mNotificationIconImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mVectorMessageListFragment.scrollToBottom(0);
                    }
                });

            } else if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                isAreaVisible = true;

                iconId = R.drawable.vector_typing;
                text = new SpannableString(mLatestTypingMessage);
                textColor = R.color.vector_text_gray_color;
            }
        }

        if (TextUtils.isEmpty(mEventId)) {
            mNotificationsArea.setVisibility(isAreaVisible ? View.VISIBLE : View.INVISIBLE);
        }

        if ((-1 != iconId) && (-1 != textColor)) {
            mNotificationIconImageView.setImageResource(iconId);
            mNotificationTextView.setText(text);
            mNotificationTextView.setTextColor(getResources().getColor(textColor));
        }

        //
        if (null != mResendUnsentMenuItem) {
            mResendUnsentMenuItem.setVisible(false);
        }

        if (null != mResendDeleteMenuItem) {
            mResendDeleteMenuItem.setVisible(false);
        }

        if (null != mLiveMenuItem) {
            mLiveMenuItem.setVisible(false);
        }

        if (null != mSearchInRoomMenuItem) {
            // the server search does not work on encrypted rooms.
            mSearchInRoomMenuItem.setVisible(!mRoom.isEncrypted());
        }
    }

    /**
     * Refresh the call buttons display.
     */
    private void refreshCallButtons() {
        if ((null == sRoomPreviewData) && (null == mEventId) && canSendMessages()) {
            boolean isCallSupported = mRoom.canPerformCall() && mSession.isVoipCallSupported();
            IMXCall call = VectorCallViewActivity.getActiveCall();

            if (null == call) {
                //mStartCallLayout.setVisibility((isCallSupported && (mEditText.getText().length() == 0)) ? View.VISIBLE : View.GONE);
                mStopCallLayout.setVisibility(View.GONE);
            } else {
                // ensure that the listener is defined once
                call.removeListener(mCallListener);
                call.addListener(mCallListener);

                IMXCall roomCall = mSession.mCallsManager.getCallWithRoomId(mRoom.getRoomId());

                mStartCallLayout.setVisibility(View.GONE);
                mStopCallLayout.setVisibility((call == roomCall) ? View.VISIBLE : View.GONE);
            }

            mVectorOngoingConferenceCallView.refresh();
        }
    }

    /**
     * Display the typing status in the notification area.
     */
    private void onRoomTypings() {
        mLatestTypingMessage = null;

        List<String> typingUsers = mRoom.getTypingUsers();

        if ((null != typingUsers) && (typingUsers.size() > 0)) {
            String myUserId = mSession.getMyUserId();

            // get the room member names
            ArrayList<String> names = new ArrayList<>();

            for (int i = 0; i < typingUsers.size(); i++) {
                RoomMember member = mRoom.getMember(typingUsers.get(i));

                // check if the user is known and not oneself
                if ((null != member) && !TextUtils.equals(myUserId, member.getUserId()) && (null != member.displayname)) {
                    names.add(member.displayname);
                }
            }

            // nothing to display ?
            if (0 == names.size()) {
                mLatestTypingMessage = null;
            } else if (1 == names.size()) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_one_user_is_typing), names.get(0));
            } else if (2 == names.size()) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_two_users_are_typing), names.get(0), names.get(1));
            } else if (names.size() > 2) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_many_users_are_typing), names.get(0), names.get(1));
            }
        }

        refreshNotificationsArea();
    }

    //================================================================================
    // expandable header management command
    //================================================================================

    /**
     * Refresh the collapsed or the expanded headers
     */
    private void updateActionBarTitleAndTopic() {
        setTitle();
        setTopic();
    }

    /**
     * Set the topic
     */
    private void setTopic() {
        String topic = null;

        if (null != mRoom) {
            topic = mRoom.getTopic();
        } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getRoomState())) {
            topic = sRoomPreviewData.getRoomState().topic;
        }

        setTopic(topic);
    }

    /**
     * Set the topic.
     *
     * @param aTopicValue the new topic value
     */
    private void setTopic(String aTopicValue) {
        // in search mode, the topic is not displayed
        if (!TextUtils.isEmpty(mEventId)) {
            mActionBarCustomTopic.setVisibility(View.GONE);
        } else {
            // update the topic of the room header
            updateRoomHeaderTopic();

            // update the action bar topic anyway
            mActionBarCustomTopic.setText(aTopicValue);

            // set the visibility of topic on the custom action bar only
            // if header room view is gone, otherwise skipp it
            if (View.GONE == mRoomHeaderView.getVisibility()) {
                // topic is only displayed if its content is not empty
                if (TextUtils.isEmpty(aTopicValue)) {
                    mActionBarCustomTopic.setVisibility(View.GONE);
                } else {
                    mActionBarCustomTopic.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Refresh the room avatar.
     */
    private void updateRoomHeaderAvatar() {
        if (null != mRoom) {
            VectorUtils.loadRoomAvatar(this, mSession, mActionBarHeaderRoomAvatar, mRoom);
        } else if (null != sRoomPreviewData) {
            String roomName = sRoomPreviewData.getRoomName();
            if (TextUtils.isEmpty(roomName)) {
                roomName = " ";
            }
            VectorUtils.loadUserAvatar(this, sRoomPreviewData.getSession(), mActionBarHeaderRoomAvatar, sRoomPreviewData.getRoomAvatarUrl(), sRoomPreviewData.getRoomId(), roomName);
        }
    }


    /**
     * Create a custom action bar layout to process the room header view.
     * <p>
     * This action bar layout will contain a title, a topic and an arrow.
     * The arrow is updated (down/up) according to if the room header is
     * displayed or not.
     */
    private void setActionBarDefaultCustomLayout() {
        // binding the widgets of the custom view
        mActionBarCustomTitle = (TextView) findViewById(R.id.room_action_bar_title);
        mActionBarCustomTopic = (TextView) findViewById(R.id.room_action_bar_topic);
        mActionBarCustomArrowImageView = (ImageView) findViewById(R.id.open_chat_header_arrow);

        // custom header
        View headerTextsContainer = findViewById(R.id.header_texts_container);

        // add click listener on custom action bar to display/hide the header view
        mActionBarCustomArrowImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (null != mRoomHeaderView) {
                    if (View.GONE == mRoomHeaderView.getVisibility()) {
                        enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
                    } else {
                        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                    }
                }
            }
        });

        headerTextsContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
                    enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
                }
            }
        });

        // add touch listener on the header view itself
        if (null != mRoomHeaderView) {
            mRoomHeaderView.setOnTouchListener(new View.OnTouchListener() {
                // last position
                private float mStartX;
                private float mStartY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mStartX = event.getX();
                        mStartY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        float curX = event.getX();
                        float curY = event.getY();

                        float deltaX = curX - mStartX;
                        float deltaY = curY - mStartY;

                        // swipe up to hide room header
                        if ((Math.abs(deltaY) > Math.abs(deltaX)) && (deltaY < 0)) {
                            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                        } else {
                            // wait the touch up to display the room settings page
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                    return true;
                }
            });
        }
    }

    /**
     * Set the title value in the action bar and in the
     * room header layout
     */
    private void setTitle() {
        String titleToApply = mDefaultRoomName;
        if ((null != mSession) && (null != mRoom)) {
            titleToApply = VectorUtils.getRoomDisplayName(this, mSession, mRoom);

            if (TextUtils.isEmpty(titleToApply)) {
                titleToApply = mDefaultRoomName;
            }

            // in context mode, add search to the title.
            if (!TextUtils.isEmpty(mEventId)) {
                titleToApply = getResources().getText(R.string.search) + " : " + titleToApply;
            }
        } else if (null != sRoomPreviewData) {
            titleToApply = sRoomPreviewData.getRoomName();
        }

        // set action bar title
        if (null != mActionBarCustomTitle) {
            mActionBarCustomTitle.setText(titleToApply);
        } else {
            setTitle(titleToApply);
        }

        // set title in the room header (no matter if not displayed)
        if (null != mActionBarHeaderRoomName) {
            mActionBarHeaderRoomName.setText(titleToApply);
        }
    }

    /**
     * Update the UI content of the action bar header view
     */
    private void updateActionBarHeaderView() {
        // update room avatar content
        updateRoomHeaderAvatar();

        // update the room name
        if (null != mRoom) {
            mActionBarHeaderRoomName.setText(VectorUtils.getRoomDisplayName(this, mSession, mRoom));
        } else if (null != sRoomPreviewData) {
            mActionBarHeaderRoomName.setText(sRoomPreviewData.getRoomName());
        } else {
            mActionBarHeaderRoomName.setText("");
        }

        // update topic and members status
        updateRoomHeaderTopic();
        updateRoomHeaderMembersStatus();
    }

    private void updateRoomHeaderTopic() {
        if (null != mActionBarCustomTopic) {
            String value = null;

            if (null != mRoom) {
                value = mRoom.isReady() ? mRoom.getTopic() : mDefaultTopic;
            } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getRoomState())) {
                value = sRoomPreviewData.getRoomState().topic;
            }

            // if topic value is empty, just hide the topic TextView
            if (TextUtils.isEmpty(value)) {
                mActionBarHeaderRoomTopic.setVisibility(View.GONE);
            } else {
                mActionBarHeaderRoomTopic.setVisibility(View.VISIBLE);
                mActionBarHeaderRoomTopic.setText(value);
            }
        }
    }

    /**
     * Tell if the user can send a message in this room.
     *
     * @return true if the user is allowed to send messages in this room.
     */
    private boolean canSendMessages() {
        boolean canSendMessage = false;

        if ((null != mRoom) && (null != mRoom.getLiveState())) {
            canSendMessage = true;
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                canSendMessage = powerLevels.maySendMessage(mMyUserId);
            }
        }

        return canSendMessage;
    }

    /**
     * Check if the user can send a message in this room
     */
    private void checkSendEventStatus() {
        if ((null != mRoom) && (null != mRoom.getLiveState())) {
            boolean canSendMessage = canSendMessages();
            mSendingMessagesLayout.setVisibility(canSendMessage ? View.VISIBLE : View.GONE);
            mCanNotPostTextView.setVisibility(!canSendMessage ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Display the active members count / members count in the expendable header.
     */
    private void updateRoomHeaderMembersStatus() {
        if (null != mActionBarHeaderActiveMembers) {
            // refresh only if the action bar is hidden
            if (mActionBarCustomTitle.getVisibility() == View.GONE) {

                if ((null != mRoom) || (null != sRoomPreviewData)) {
                    // update the members status: "active members"/"members"
                    int joinedMembersCount = 0;
                    int activeMembersCount = 0;

                    RoomState roomState = (null != sRoomPreviewData) ? sRoomPreviewData.getRoomState() : mRoom.getState();

                    if (null != roomState) {
                        Collection<RoomMember> members = roomState.getDisplayableMembers();

                        for (RoomMember member : members) {
                            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                                joinedMembersCount++;

                                User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                                if ((null != user) && user.isActive()) {
                                    activeMembersCount++;
                                }
                            }
                        }

                        // in preview mode, the room state might be a publicRoom
                        // so try to use the public room info.
                        if ((roomState instanceof PublicRoom) && (0 == joinedMembersCount)) {
                            activeMembersCount = joinedMembersCount = ((PublicRoom) roomState).numJoinedMembers;
                        }

                        boolean displayInvite = TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData) && (1 == joinedMembersCount);

                        if (displayInvite) {
                            mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                            mActionBarHeaderInviteMemberView.setVisibility(View.VISIBLE);
                        } else {
                            mActionBarHeaderInviteMemberView.setVisibility(View.GONE);
                            String text = null;
                            if (null != sRoomPreviewData) {
                                if (joinedMembersCount == 1) {
                                    text = getResources().getString(R.string.room_title_one_member);
                                } else if (joinedMembersCount > 0) {
                                    text = getResources().getString(R.string.room_title_members, joinedMembersCount);
                                }
                            } else {
                                text = getString(R.string.room_header_active_members, activeMembersCount, joinedMembersCount);
                            }

                            if (!TextUtils.isEmpty(text)) {
                                mActionBarHeaderActiveMembers.setText(text);
                                mActionBarHeaderActiveMembers.setVisibility(View.VISIBLE);
                            } else {
                                mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                        mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                    }
                }

            } else {
                mActionBarHeaderActiveMembers.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Show or hide the action bar header view according to aIsHeaderViewDisplayed
     *
     * @param aIsHeaderViewDisplayed true to show the header view, false to hide
     */
    private void enableActionBarHeader(boolean aIsHeaderViewDisplayed) {

        mIsHeaderViewDisplayed = aIsHeaderViewDisplayed;
        if (SHOW_ACTION_BAR_HEADER == aIsHeaderViewDisplayed) {

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);

            // hide the name and the topic in the action bar.
            // these items are hidden when the header view is opened
            mActionBarCustomTitle.setVisibility(View.GONE);
            mActionBarCustomTopic.setVisibility(View.GONE);

            // update the UI content of the action bar header
            updateActionBarHeaderView();
            // set the arrow to up
            mActionBarCustomArrowImageView.setImageResource(R.drawable.ic_arrow_drop_up_white);
            // enable the header view to make it visible
            mRoomHeaderView.setVisibility(View.VISIBLE);
            mToolbar.setBackgroundColor(Color.TRANSPARENT);
        } else {
            // hide the room header only if it is displayed
            if (View.VISIBLE == mRoomHeaderView.getVisibility()) {
                // show the name and the topic in the action bar.
                mActionBarCustomTitle.setVisibility(View.VISIBLE);
                // if the topic is empty, do not show it
                if (!TextUtils.isEmpty(mActionBarCustomTopic.getText())) {
                    mActionBarCustomTopic.setVisibility(View.VISIBLE);
                }

                // update title and topic (action bar)
                updateActionBarTitleAndTopic();

                // hide the action bar header view and reset the arrow image (arrow reset to down)
                mActionBarCustomArrowImageView.setImageResource(R.drawable.ic_arrow_drop_down_white);
                mRoomHeaderView.setVisibility(View.GONE);
                mToolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.vector_green_color));
            }
        }
    }

    //================================================================================
    // Room preview management
    //================================================================================

    @Override
    public RoomPreviewData getRoomPreviewData() {
        return sRoomPreviewData;
    }

    /**
     * Manage the room preview buttons area
     */
    private void manageRoomPreview() {
        if (null != sRoomPreviewData) {
            mRoomPreviewLayout.setVisibility(View.VISIBLE);

            TextView invitationTextView = (TextView) findViewById(R.id.room_preview_invitation_textview);
            TextView subInvitationTextView = (TextView) findViewById(R.id.room_preview_subinvitation_textview);

            Button joinButton = (Button) findViewById(R.id.button_join_room);
            Button declineButton = (Button) findViewById(R.id.button_decline);

            final RoomEmailInvitation roomEmailInvitation = sRoomPreviewData.getRoomEmailInvitation();

            String roomName = sRoomPreviewData.getRoomName();
            if (TextUtils.isEmpty(roomName)) {
                roomName = " ";
            }

            Log.d(LOG_TAG, "Preview the room " + sRoomPreviewData.getRoomId());


            // if the room already exists
            if (null != mRoom) {
                Log.d(LOG_TAG, "manageRoomPreview : The room is known");

                String inviter = "";

                if (null != roomEmailInvitation) {
                    inviter = roomEmailInvitation.inviterName;
                }

                if (TextUtils.isEmpty(inviter)) {
                    Collection<RoomMember> members = mRoom.getActiveMembers();
                    for (RoomMember member : members) {
                        if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                            inviter = TextUtils.isEmpty(member.displayname) ? member.getUserId() : member.displayname;
                        }
                    }
                }

                invitationTextView.setText(getResources().getString(R.string.room_preview_invitation_format, inviter));

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The user clicked on decline.");

                        setProgressVisibility(View.VISIBLE);

                        mRoom.leave(new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "The invitation is rejected");
                                onDeclined();
                            }

                            private void onError(String errorMessage) {
                                Log.d(LOG_TAG, "The invitation rejection failed " + errorMessage);
                                CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
                                setProgressVisibility(View.GONE);
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
                        });
                    }
                });

            } else {
                if ((null != roomEmailInvitation) && !TextUtils.isEmpty(roomEmailInvitation.email)) {
                    invitationTextView.setText(getResources().getString(R.string.room_preview_invitation_format, roomEmailInvitation.inviterName));
                    subInvitationTextView.setText(getResources().getString(R.string.room_preview_unlinked_email_warning, roomEmailInvitation.email));
                } else {
                    invitationTextView.setText(getResources().getString(R.string.room_preview_try_join_an_unknown_room, TextUtils.isEmpty(sRoomPreviewData.getRoomName()) ? getResources().getString(R.string.room_preview_try_join_an_unknown_room_default) : roomName));

                    // the room preview has some messages
                    if ((null != sRoomPreviewData.getRoomResponse()) && (null != sRoomPreviewData.getRoomResponse().messages)) {
                        subInvitationTextView.setText(getResources().getString(R.string.room_preview_room_interactions_disabled));
                    }
                }

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The invitation is declined (unknown room)");

                        sRoomPreviewData = null;
                        VectorRoomActivity.this.finish();
                    }
                });
            }

            joinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(LOG_TAG, "The user clicked on Join.");

                    Room room = sRoomPreviewData.getSession().getDataHandler().getRoom(sRoomPreviewData.getRoomId());

                    String signUrl = null;

                    if (null != roomEmailInvitation) {
                        signUrl = roomEmailInvitation.signUrl;
                    }

                    setProgressVisibility(View.VISIBLE);

                    room.joinWithThirdPartySigned(sRoomPreviewData.getRoomIdOrAlias(), signUrl, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            onJoined();
                        }

                        private void onError(String errorMessage) {
                            CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
                            setProgressVisibility(View.GONE);
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
                    });

                }
            });

            enableActionBarHeader(SHOW_ACTION_BAR_HEADER);

        } else {
            mRoomPreviewLayout.setVisibility(View.GONE);
        }
    }

    /**
     * The room invitation has been declined
     */
    private void onDeclined() {
        if (null != sRoomPreviewData) {
            VectorRoomActivity.this.finish();
            sRoomPreviewData = null;
        }
    }

    /**
     * the room has been joined
     */
    private void onJoined() {
        if (null != sRoomPreviewData) {
            HashMap<String, Object> params = new HashMap<>();

            processDirectMessageRoom();

            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, sRoomPreviewData.getRoomId());

            if (null != sRoomPreviewData.getEventId()) {
                params.put(VectorRoomActivity.EXTRA_EVENT_ID, sRoomPreviewData.getEventId());
            }

            // clear the activity stack to home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, params);
            VectorRoomActivity.this.startActivity(intent);

            sRoomPreviewData = null;
        }
    }

    /**
     * If the joined room was tagged as "direct chat room", it is required to update the
     * room as a "direct chat room" (account_data)
     */
    private void processDirectMessageRoom() {
        Room room = sRoomPreviewData.getSession().getDataHandler().getRoom(sRoomPreviewData.getRoomId());
        if ((null != room) && (room.isDirectChatInvitation())) {
            String myUserId = mSession.getMyUserId();
            Collection<RoomMember> members = mRoom.getMembers();

            if (2 == members.size()) {
                String participantUserId;

                // test if room is already seen as "direct message"
                if (mSession.getDirectChatRoomIdsList().indexOf(sRoomPreviewData.getRoomId()) < 0) {
                    for (RoomMember member : members) {
                        // search for the second participant
                        if (!member.getUserId().equals(myUserId)) {
                            participantUserId = member.getUserId();
                            CommonActivityUtils.setToggleDirectMessageRoom(mSession, sRoomPreviewData.getRoomId(), participantUserId, this, mDirectMessageListener);
                            break;
                        }
                    }
                } else {
                    Log.d(LOG_TAG, "## processDirectMessageRoom(): attempt to add an already direct message room");
                }
            }
        }
    }

    //================================================================================
    // Room header clicks management.
    //================================================================================

    /**
     * Update the avatar from the data provided the medias picker.
     *
     * @param aData the provided data.
     */
    private void onActivityResultRoomAvatarUpdate(final Intent aData) {
        // sanity check
        if (null == mSession) {
            return;
        }

        Uri thumbnailUri = VectorUtils.getThumbnailUriFromIntent(this, aData, mSession.getMediasCache());

        if (null != thumbnailUri) {
            setProgressVisibility(View.VISIBLE);

            // save the bitmap URL on the server
            ResourceUtils.Resource resource = ResourceUtils.openResource(this, thumbnailUri, null);
            if (null != resource) {
                mSession.getMediasCache().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new MXMediaUploadListener() {
                    @Override
                    public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                        Log.e(LOG_TAG, "Fail to upload the avatar");
                    }

                    @Override
                    public void onUploadComplete(final String uploadId, final String contentUri) {
                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
                                mRoom.updateAvatarUrl(contentUri, new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                        }

                                        setProgressVisibility(View.GONE);
                                        updateRoomHeaderAvatar();
                                    }

                                    @Override
                                    public void onSuccess(Void info) {
                                        onDone(null);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }
                                });
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * The user clicks on the room title.
     * Assume he wants to update it.
     */
    private void onRoomTitleClick() {
        LayoutInflater inflater = LayoutInflater.from(this);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        View dialogView = inflater.inflate(R.layout.dialog_text_edittext, null);
        alertDialogBuilder.setView(dialogView);

        TextView titleText = (TextView) dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_name));

        final EditText textInput = (EditText) dialogView.findViewById(R.id.dialog_edit_text);
        textInput.setText(mRoom.getLiveState().name);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                setProgressVisibility(View.VISIBLE);

                                mRoom.updateName(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                        }

                                        setProgressVisibility(View.GONE);
                                        updateActionBarTitleAndTopic();
                                    }

                                    @Override
                                    public void onSuccess(Void info) {
                                        onDone(null);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * The user clicks on the room topic.
     * Assume he wants to update it.
     */
    private void onRoomTopicClick() {
        LayoutInflater inflater = LayoutInflater.from(this);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        View dialogView = inflater.inflate(R.layout.dialog_text_edittext, null);
        alertDialogBuilder.setView(dialogView);

        TextView titleText = (TextView) dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_topic));

        final EditText textInput = (EditText) dialogView.findViewById(R.id.dialog_edit_text);
        textInput.setText(mRoom.getLiveState().topic);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                setProgressVisibility(View.VISIBLE);

                                mRoom.updateTopic(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                        }

                                        setProgressVisibility(View.GONE);
                                        updateActionBarTitleAndTopic();
                                    }

                                    @Override
                                    public void onSuccess(Void info) {
                                        onDone(null);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * Add click management on expanded header
     */
    private void addRoomHeaderClickListeners() {
        // tap on the expanded room avatar
        View roomAvatarView = findViewById(R.id.room_avatar);

        if (null != roomAvatarView) {
            roomAvatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        if (CommonActivityUtils.isPowerLevelEnoughForAvatarUpdate(mRoom, mSession)) {
                            // need to check if the camera permission has been granted
                            if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_ROOM_DETAILS, VectorRoomActivity.this)) {
                                Intent intent = new Intent(VectorRoomActivity.this, VectorMediasPickerActivity.class);
                                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                                startActivityForResult(intent, REQUEST_ROOM_AVATAR_CODE);
                            }
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        // tap on the room name to update it
        View titleText = findViewById(R.id.action_bar_header_room_title);

        if (null != titleText) {
            titleText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        boolean canUpdateTitle = false;
                        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                        if (null != powerLevels) {
                            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                            canUpdateTitle = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
                        }

                        if (canUpdateTitle) {
                            onRoomTitleClick();
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        // tap on the room name to update it
        View topicText = findViewById(R.id.action_bar_header_room_topic);

        if (null != topicText) {
            topicText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        boolean canUpdateTopic = false;
                        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                        if (null != powerLevels) {
                            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                            canUpdateTopic = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
                        }

                        if (canUpdateTopic) {
                            onRoomTopicClick();
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        View membersListTextView = findViewById(R.id.action_bar_header_room_members);

        if (null != membersListTextView) {
            membersListTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
                }
            });
        }
    }

    private static final String E2E_WARNINGS_PREFERENCES = "E2E_WARNINGS_PREFERENCES";

    /**
     * Display an e2e alert for the first opened room.
     */
    private void displayE2eRoomAlert() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (!preferences.contains(E2E_WARNINGS_PREFERENCES) && (null != mRoom) && mRoom.isEncrypted()) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(E2E_WARNINGS_PREFERENCES, false);
            editor.commit();

            android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
            builder.setTitle(R.string.room_e2e_alert_title);
            builder.setMessage(R.string.room_e2e_alert_message);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // NOP
                }
            });
            builder.create().show();
        }
    }


    private float x1 = 0;
    private float x2 = 0;
    private float y1 = 0;
    private float y2 = 0;
    private boolean functionPadDetect = false;
    private int moreImg = R.drawable.ic_material_file;

    public void roomFunctionPadClick(View view) {
        Log.d(LOG_TAG, "roomFunctionPadClick");

        if(mRoom == null){
            return;
        }

        switch (view.getId()){
            case R.id.room_function_pad_0:
                VectorRoomActivity.this.launchFileSelectionIntent();
                break;
            case R.id.room_function_pad_1:
                if(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)){
                    launchCamera();
                }
                break;
            case R.id.room_function_pad_2:
                showWhiteheBoard(null);
                break;
            case R.id.room_function_pad_2_5:
                showPicSearch(null);
                break;
            case R.id.room_function_pad_3:
                lunchCallAV();
                break;
            case R.id.room_function_pad_4:
                detectOptionDialog(DetectManager.detectType.MOTION);
                break;
            case R.id.room_function_pad_5:
                detectOptionDialog(DetectManager.detectType.AUDIO);
                break;
            case R.id.room_function_pad_6:
                detectOptionDialog(DetectManager.detectType.TIMER);
                break;
            case R.id.room_function_pad_7:
                detectOptionDialog(DetectManager.detectType.FACE);
                break;
            case R.id.room_function_pad_8:
                sendMessage(DetectManager.instance(getApplicationContext()).sendStopDetectAll(mSession, mRoom), null, Message.FORMAT_MATRIX_HTML);
                break;
            case R.id.room_function_pad_9:
                sendMessage(DetectManager.instance(getApplicationContext()).sendDetectStatus(mSession, mRoom), null, Message.FORMAT_MATRIX_HTML);
            default:
                break;
        }
    }

    private void lunchCall(int type){
        if ((null != mRoom) && mRoom.isEncrypted() && (mRoom.getActiveMembers().size() > 2))  {
            // display the dialog with the info text
            AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
            Resources resource = getResources();
            permissionsInfoDialog.setMessage(resource.getString(R.string.room_no_conference_call_in_encrypted_rooms));
            permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
            permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok),null);
            permissionsInfoDialog.show();

        } else if(isUserAllowedToStartConfCall()) {
            if(type == 0){
                if(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL, VectorRoomActivity.this)){
                    startIpCall(true);
                }
            }
            if(type == 1){
                if(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL, VectorRoomActivity.this)){
                    startIpCall(false);
                }
            }
        } else {
            displayConfCallNotAllowed();
        }
    }

    private void lunchCallAV(){
        if ((null != mRoom) && mRoom.isEncrypted() && (mRoom.getActiveMembers().size() > 2)) {
        // display the dialog with the info text
        AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
        Resources resource = getResources();
        permissionsInfoDialog.setMessage(resource.getString(R.string.room_no_conference_call_in_encrypted_rooms));
        permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
        permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
        permissionsInfoDialog.show();

        } else if (isUserAllowedToStartConfCall()) {
            displayVideoCallIpDialog();
        } else {
            displayConfCallNotAllowed();
        }
    }

    private void displayFaceCallNotAllowed() {
        // display the dialog with the info text
        AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
        Resources resource = getResources();

        if ((null != resource) && (null != permissionsInfoDialog)) {
            permissionsInfoDialog.setTitle(resource.getString(R.string.detect_face));
            permissionsInfoDialog.setMessage(resource.getString(R.string.detect_echo_face_videocall_member_limit));

            permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
            permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
            permissionsInfoDialog.show();
        } else {
            Log.e(LOG_TAG, "## displayFaceCallNotAllowed(): impossible to create dialog");
        }
    }

    private void detectOptionDialog(final DetectManager.detectType type){
        FragmentManager fm = getSupportFragmentManager();
        CmdDialogFragment fragment = (CmdDialogFragment) fm.findFragmentByTag("TAG_FRAGMENT_ATTACHMENTS_DIALOG_TEST");
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }

        fragment = CmdDialogFragment.newInstance(type, null, ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_text_black_color));
        fragment.setOnClickListener(new CmdDialogFragment.OnItemClickListener() {
            @Override
            public void onItemClick(CmdDialogFragment dialogFragment, int sel, int[] param) {
                Log.d(LOG_TAG, "detectOptionDialog onItemClick:" + param.toString());

                DetectManager detectManager = DetectManager.instance(getApplicationContext());

                String cmdStr = null;
                if(sel == 0){
                    switch (type){
                        case MOTION:
                            if(param[1] == 0){
                                cmdStr = detectManager.sendStartMotionDetect(mSession, mRoom, DetectManager.detectContent_type.PICTURE, param[0]);
                            }else{
                                cmdStr = detectManager.sendStartMotionDetect(mSession, mRoom, DetectManager.detectContent_type.VIDEO, param[0]);
                            }
                            break;

                        case AUDIO:
                            if(param[1] == 0){
                                cmdStr = detectManager.sendStartAudioDetect(mSession, mRoom, DetectManager.detectContent_type.AUDIO, param[0]);
                            }else{
                                cmdStr = detectManager.sendStartAudioDetect(mSession, mRoom, DetectManager.detectContent_type.VIDEO, param[0]);
                            }
                            break;

                        case TIMER:
                            if(param[1] == 0){
                                cmdStr = detectManager.sendStartTimerDetect(mSession, mRoom, DetectManager.detectContent_type.PICTURE, param[0], param[2]);
                            }else{
                                cmdStr = detectManager.sendStartTimerDetect(mSession, mRoom, DetectManager.detectContent_type.VIDEO, param[0], param[2]);
                            }
                            break;

                        case FACE:
                            if(param[1] == 0){
                                if(mRoom.getActiveMembers().size() > 2){
                                    displayFaceCallNotAllowed();
                                }else{
                                    cmdStr = detectManager.sendStartFaceDetect(mSession, mRoom, DetectManager.detectContent_type.VIDEO_CALL, param[0]);
                                }
                            }else{
                                cmdStr = detectManager.sendStartFaceDetect(mSession, mRoom, DetectManager.detectContent_type.VIDEO, param[0]);
                            }
                            break;

                        default:
                            Log.e(LOG_TAG, "detectOptionDialog type error!");
                            break;
                    }
                }else if (sel == 1){
                    cmdStr = detectManager.sendStopDetect(mSession, mRoom, type, param[0]);
                }

                if(cmdStr != null){
                    sendMessage(cmdStr, null, Message.FORMAT_MATRIX_HTML);
                }
            }
        });

        fragment.show(fm, TAG_FRAGMENT_ATTACHMENTS_DIALOG);
    }

    public void roomFunctionPadDetectShowClick(View view) {
        TranslateAnimation mHiddenAction;
        TableRow padDetectGeneral = (TableRow)findViewById(R.id.room_function_pad_general);
        TableRow padDetect0 = (TableRow)findViewById(R.id.room_function_pad_detect0);
        TableRow padDetect1 = (TableRow)findViewById(R.id.room_function_pad_detect1);
        ImageView padArrow  = (ImageView)findViewById(R.id.room_function_pad_arrow);

        functionPadDetect = !functionPadDetect;

        if(functionPadDetect){
            mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            padDetect0.setVisibility(View.VISIBLE);
            padDetect1.setVisibility(View.VISIBLE);
            padArrow.setImageResource(R.drawable.room_function_arrow_reverse);
        }else {
            mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            padDetect0.setVisibility(View.GONE);
            padDetect1.setVisibility(View.GONE);
            padArrow.setImageResource(R.drawable.room_function_arrow);
        }
        mHiddenAction.setDuration(500);

        padDetectGeneral.startAnimation(mHiddenAction);
        padDetect0.startAnimation(mHiddenAction);
        padDetect1.startAnimation(mHiddenAction);
    }

    private void freshButtomPad(){
        hidePadType(0);
        //hidePadType(2);

        View room_function_pad = findViewById(R.id.room_function_pad);
        View room_functtion_pad_white_board = findViewById(R.id.wb_content);
        View room_functtion_pad_pic_search_board = findViewById(R.id.pic_search_pad);
        View function_pad_separator = findViewById(R.id.function_pad_separator);
        Float arc0, arc1;
        int duration;
        if(room_function_pad.getVisibility() == View.GONE
                && (room_functtion_pad_white_board.getVisibility() == View.GONE && room_functtion_pad_pic_search_board.getVisibility() == View.GONE)){
            TranslateAnimation mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            mHiddenAction.setDuration(200);
            room_function_pad.startAnimation(mHiddenAction);
            room_function_pad.setVisibility(View.VISIBLE);
            //function_pad_separator.setVisibility(View.VISIBLE);
            moreImg = R.drawable.ic_material_file2;
            arc0 = 370F;
            arc1 = 360F;
            duration = 1000;
        }else {
            room_function_pad.setVisibility(View.GONE);
            room_functtion_pad_white_board.setVisibility(View.GONE);
            room_functtion_pad_pic_search_board.setVisibility(View.GONE);
            function_pad_separator.setVisibility(View.GONE);
            moreImg = R.drawable.ic_material_file;
            arc0 = -370F;
            arc1 = -360F;
            duration = 500;
        }

        ObjectAnimator animator = new ObjectAnimator().ofFloat(mSendImageView, "rotation", 0F, arc0, arc1);
        animator.setDuration(duration);
        animator.start();

        mSendImageView.setImageResource(moreImg);
    }


    private void sendMsgT(MXSession session, Room room, String bodyString){
        android.util.Log.d(LOG_TAG, "sendMsg:" + bodyString);

        Message message = new Message();
        message.msgtype = Message.MSGTYPE_TEXT;
        message.body = bodyString;
        final Event event = new Event(message, session.getCredentials().userId, room.getRoomId());
        //room.storeOutgoingEvent(event);
        //session.getDataHandler().getStore().commit();
        room.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                android.util.Log.d(LOG_TAG, "Send message : onSuccess ");
            }

            @Override
            public void onNetworkError(Exception e) {
                android.util.Log.d(LOG_TAG, "Send message : onNetworkError " + e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                android.util.Log.d(LOG_TAG, "Send message : onMatrixError " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                android.util.Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.getLocalizedMessage());
            }
        });
    }

    private void sendImage(String file){
        Uri uri =  Uri.fromFile(new File(file));
        ArrayList<SharedDataItem> sharedDataItems = new ArrayList<>();
        sharedDataItems.add(new SharedDataItem(uri));

        if (0 != sharedDataItems.size()) {
            mVectorRoomMediasSender.sendMedias(sharedDataItems);
        }
    }

    private void showEmojiPad(){
        hidePadType(1);
        hidePadType(2);
        hidePadType(3);

        View room_functtion_pad_emojicons = findViewById(R.id.emojicons);
        View function_pad_separator = findViewById(R.id.function_pad_separator);

        int softInputHeight = getButtomPadHeight();
        if(softInputHeight == 0){
            softInputHeight = 600;
        }

        room_functtion_pad_emojicons.getLayoutParams().height = softInputHeight - 4;

        Float arc0, arc1, arc2, arc3;
        int duration;
        int moreImgEmoji;
        if(room_functtion_pad_emojicons.getVisibility() == View.GONE){
            TranslateAnimation mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            mHiddenAction.setDuration(200);
            room_functtion_pad_emojicons.startAnimation(mHiddenAction);
            room_functtion_pad_emojicons.setVisibility(View.VISIBLE);
            //function_pad_separator.setVisibility(View.VISIBLE);
            moreImgEmoji = R.drawable.ic_material_emoji2;
            arc0 = 50F;
            arc1 = -50F;
            arc2 = 10F;
            arc3 = 0F;
            duration = 1000;
        }else {
            room_functtion_pad_emojicons.setVisibility(View.GONE);
            function_pad_separator.setVisibility(View.GONE);
            moreImgEmoji = R.drawable.ic_material_emoji;
            arc0 = -50F;
            arc1 = 50F;
            arc2 = -10F;
            arc3 = 0F;
            duration = 500;
        }

        ImageView mEmojiImageView = (ImageView)findViewById(R.id.room_start_call_image_view);
        ObjectAnimator animator = new ObjectAnimator().ofFloat(mEmojiImageView, "rotation", 0F, arc0, arc1, arc2, arc3);
        animator.setDuration(duration);
        animator.start();

        mEmojiImageView.setImageResource(moreImgEmoji);
    }

    private void hidePadType(int type){
        if(type == 0){
            View room_functtion_pad_emojicons = findViewById(R.id.emojicons);
            if (room_functtion_pad_emojicons.getVisibility() == View.VISIBLE) {
                room_functtion_pad_emojicons.setVisibility(View.GONE);
                ImageView mEmojiImageView = (ImageView)findViewById(R.id.room_start_call_image_view);
                mEmojiImageView.setImageResource(R.drawable.ic_material_emoji);
            }
        }else if(type == 1){
            View room_function_pad = findViewById(R.id.room_function_pad);
            View room_functtion_pad_white_board = findViewById(R.id.wb_content);
            if (room_function_pad.getVisibility() == View.VISIBLE) {
                room_function_pad.setVisibility(View.GONE);
                mSendImageView.setImageResource(R.drawable.ic_material_file);
                moreImg = R.drawable.ic_material_file;
            }
            if(room_functtion_pad_white_board.getVisibility() == View.VISIBLE){
                room_functtion_pad_white_board.setVisibility(View.GONE);
                mSendImageView.setImageResource(R.drawable.ic_material_file);
                moreImg = R.drawable.ic_material_file;
            }
        }else if(type == 2){
            View room_functtion_pad_white_board = findViewById(R.id.wb_content);
            if (room_functtion_pad_white_board.getVisibility() == View.VISIBLE) {
                room_functtion_pad_white_board.setVisibility(View.GONE);
            }
        }else if(type == 3){
            View room_functtion_pad_pic_search_board = findViewById(R.id.pic_search_pad);
            if (room_functtion_pad_pic_search_board.getVisibility() == View.VISIBLE) {
                room_functtion_pad_pic_search_board.setVisibility(View.GONE);
                mSendImageView.setImageResource(R.drawable.ic_material_file);
                moreImg = R.drawable.ic_material_file;
            }
        }
    }

    private void setEmojiconFragment(boolean useSystemDefault) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.emojicons, EmojiconsFragment.newInstance(useSystemDefault))
                .commit();
    }

    @Override
    public void onEmojiconClicked(Emojicon emojicon) {
        EmojiconsFragment.input(mEditText, emojicon);
    }

    @Override
    public void onEmojiconBackspaceClicked(View v) {
        EmojiconsFragment.backspace(mEditText);
    }


    private int mSoftHeight = 0;
    private int getSoftButtonsBarHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        this.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        if (realHeight > usableHeight) {
            return realHeight - usableHeight;
        } else {
            return 0;
        }
    }

    private int getSupportSoftInputHeight() {
        Rect r = new Rect();
        this.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
        int screenHeight = this.getWindow().getDecorView().getRootView().getHeight();
        int softInputHeight = screenHeight - r.bottom;
        if (Build.VERSION.SDK_INT >= 20) {
            // When SDK Level >= 20 (Android L), the softInputHeight will contain the height of softButtonsBar (if has)
            softInputHeight = softInputHeight - getSoftButtonsBarHeight();
        }

        return softInputHeight;
    }

    private int getButtomPadHeight(){
        if(mSoftHeight == 0){
            SharedPreferences updateSettings= VectorApp.getInstance().getApplicationContext().getSharedPreferences("uiSettings", 0);
            mSoftHeight = updateSettings.getInt("buttomPadHeight", 0);
        }

        if(mSoftHeight < 400){
            mSoftHeight = getSupportSoftInputHeight();
            if (mSoftHeight < 400) {
                mSoftHeight = 0;
            }else {
                SharedPreferences updateSettings= VectorApp.getInstance().getApplicationContext().getSharedPreferences("uiSettings", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = updateSettings.edit();
                editor.putInt("buttomPadHeight", mSoftHeight);
                editor.commit();
            }
        }

        return mSoftHeight;
    }


    //white board functions/////////////////////////////////////////////////////////////////////////
    View mVBottomBack;
    FrameLayout mFlView;
    ImageView mBackroundview;
    DrawPenView mDbView;
    DrawTextLayout mDtView;

    FloatingActionsMenu mFabMenuSize;
    FloatingImageButton mBtSizeLarge;
    FloatingImageButton mBtSizeMiddle;
    FloatingImageButton mBtSizeMini;
    FloatingImageButton mBtSizeMiniMini;

    FloatingActionsMenu mFabMenuColor;
    FloatingImageButton mBtColorGreen;
    FloatingImageButton mBtColorPurple;
    FloatingImageButton mBtColorPink;
    FloatingImageButton mBtColorOrange;
    FloatingImageButton mBtColorBlack;

    FloatingActionsMenu mFabMenuText;
    FloatingImageButton mBtTextUnderline;
    FloatingImageButton mBtTextItalics;
    FloatingImageButton mBtTextBold;
    ImageView mIvWhiteBoardQuit;
    ImageView mIvWhiteBoardConfirm;

    FloatingActionsMenu mFabMenuEraser;
    FloatingImageButton mBtEraserLarge;
    FloatingImageButton mBtEraserMiddle;
    FloatingImageButton mBtEraserMini;

    ImageView mIvWhiteBoardUndo;
    ImageView mIvWhiteBoardRedo;

    ImageView mIvWhiteBoardAdd;

    ImageView mIvWhiteBoardSend;
    ImageView mIvWhiteBoardDisable;
    ImageView mIvWhiteBoardExtend;
    ImageView mIvWhiteBoardSetbackground;
    RelativeLayout mRlBottom;

    public void showWhiteheBoard(String backgournd){
        //hidePadType(1);
        View room_function_pad = findViewById(R.id.room_function_pad);
        if (room_function_pad.getVisibility() == View.VISIBLE) {
            room_function_pad.setVisibility(View.GONE);
        }

        View room_functtion_pad_white_board = findViewById(R.id.wb_content);
        View function_pad_separator = findViewById(R.id.function_pad_separator);

        int softInputHeight = getButtomPadHeight();
        if(softInputHeight == 0){
            softInputHeight = 800;
        }
        room_functtion_pad_white_board.getLayoutParams().height = softInputHeight;

        //function_pad_separator.setVisibility(View.VISIBLE);
        room_functtion_pad_white_board.setVisibility(View.VISIBLE);

        mVBottomBack = findViewById(R.id.v_bottom_back);
        mFlView = (FrameLayout)findViewById(R.id.fl_view);
        mBackroundview = (ImageView)findViewById(R.id.backround_view);
        mDbView = (DrawPenView)findViewById(R.id.db_view);
        mDtView = (DrawTextLayout)findViewById(R.id.dt_view);
        //text
        mFabMenuSize = (FloatingActionsMenu)findViewById(R.id.fab_menu_size);
        mBtSizeLarge = (FloatingImageButton)findViewById(R.id.bt_size_large);
        mBtSizeMiddle = (FloatingImageButton)findViewById(R.id.bt_size_middle);
        mBtSizeMini = (FloatingImageButton)findViewById(R.id.bt_size_mini);
        mBtSizeMiniMini = (FloatingImageButton)findViewById(R.id.bt_size_minimini);
        //color
        mFabMenuColor = (FloatingActionsMenu)findViewById(R.id.fab_menu_color);
        mBtColorGreen = (FloatingImageButton)findViewById(R.id.bt_color_green);
        mBtColorPurple = (FloatingImageButton)findViewById(R.id.bt_color_purple);
        mBtColorPink = (FloatingImageButton)findViewById(R.id.bt_color_pink);
        mBtColorOrange = (FloatingImageButton)findViewById(R.id.bt_color_orange);
        mBtColorBlack = (FloatingImageButton)findViewById(R.id.bt_color_black);
        //text
        mFabMenuText = (FloatingActionsMenu)findViewById(R.id.fab_menu_text);
        mBtTextUnderline = (FloatingImageButton)findViewById(R.id.bt_text_underline);
        mBtTextItalics = (FloatingImageButton)findViewById(R.id.bt_text_italics);
        mBtTextBold = (FloatingImageButton)findViewById(R.id.bt_text_bold);
        mIvWhiteBoardQuit = (ImageView)findViewById(R.id.iv_white_board_quit);
        mIvWhiteBoardConfirm = (ImageView)findViewById(R.id.iv_white_board_confirm);

        //eraser
        mFabMenuEraser = (FloatingActionsMenu)findViewById(R.id.fab_menu_eraser);
        mBtEraserLarge = (FloatingImageButton)findViewById(R.id.bt_eraser_large);
        mBtEraserMiddle = (FloatingImageButton)findViewById(R.id.bt_eraser_middle);
        mBtEraserMini = (FloatingImageButton)findViewById(R.id.bt_eraser_mini);
        //undo&redo
        mIvWhiteBoardUndo = (ImageView)findViewById(R.id.iv_white_board_undo);
        mIvWhiteBoardRedo = (ImageView)findViewById(R.id.iv_white_board_redo);

        //new board
        mIvWhiteBoardAdd = (ImageView)findViewById(R.id.iv_white_board_add);

        mIvWhiteBoardSend = (ImageView)findViewById(R.id.iv_white_board_send);
        mIvWhiteBoardExtend = (ImageView)findViewById(R.id.iv_white_board_extend);
        mIvWhiteBoardSetbackground = (ImageView)findViewById(R.id.iv_white_board_set_background);
        mIvWhiteBoardDisable = (ImageView)findViewById(R.id.iv_white_board_disable);
        mRlBottom = (RelativeLayout)findViewById(R.id.rl_bottom);

        mFabMenuSize.setOnFloatingActionsMenuClickListener(new FloatingActionsMenu.OnFloatingActionsMenuClickListener() {
            @Override
            public void addButtonLister() {
                ToolsOperation(WhiteBoardVariable.Operation.PEN_CLICK);
            }
        });
        mBtSizeLarge.setOnClickListener(this);
        mBtSizeMiddle.setOnClickListener(this);
        mBtSizeMini.setOnClickListener(this);
        mBtSizeMiniMini.setOnClickListener(this);

        mFabMenuColor.setOnFloatingActionsMenuClickListener(new FloatingActionsMenu.OnFloatingActionsMenuClickListener() {
            @Override
            public void addButtonLister() {
                ToolsOperation(WhiteBoardVariable.Operation.COLOR_CLICK);
            }
        });
        mBtColorGreen.setOnClickListener(this);
        mBtColorPurple.setOnClickListener(this);
        mBtColorPink.setOnClickListener(this);
        mBtColorOrange.setOnClickListener(this);
        mBtColorBlack.setOnClickListener(this);

        mFabMenuText.setOnFloatingActionsMenuClickListener(new FloatingActionsMenu.OnFloatingActionsMenuClickListener() {
            @Override
            public void addButtonLister() {
                ToolsOperation(WhiteBoardVariable.Operation.TEXT_CLICK);
            }
        });
        mBtTextUnderline.setOnClickListener(this);
        mBtTextItalics.setOnClickListener(this);
        mBtTextBold.setOnClickListener(this);
        mIvWhiteBoardQuit.setOnClickListener(this);
        mIvWhiteBoardConfirm.setOnClickListener(this);

        mFabMenuEraser.setOnFloatingActionsMenuClickListener(new FloatingActionsMenu.OnFloatingActionsMenuClickListener() {
            @Override
            public void addButtonLister() {
                ToolsOperation(WhiteBoardVariable.Operation.ERASER_CLICK);
            }
        });
        mBtEraserLarge.setOnClickListener(this);
        mBtEraserMiddle.setOnClickListener(this);
        mBtEraserMini.setOnClickListener(this);

        mIvWhiteBoardUndo.setOnClickListener(this);
        mIvWhiteBoardRedo.setOnClickListener(this);

        mIvWhiteBoardAdd.setOnClickListener(this);

        mIvWhiteBoardSend.setOnClickListener(this);
        mIvWhiteBoardExtend.setOnClickListener(this);
        mIvWhiteBoardSetbackground.setOnClickListener(this);
        mIvWhiteBoardDisable.setOnClickListener(this);

        mVBottomBack.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(v.getId() == R.id.v_bottom_back){
                    if(event.getAction() == MotionEvent.ACTION_DOWN){
                        ToolsOperation(WhiteBoardVariable.Operation.OUTSIDE_CLICK);
                    }
                }
                return false;
            }
        });

        if(backgournd != null){
            mBackroundview.setImageURI(Uri.parse(backgournd));
        }
        initView();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_white_board_quit://退出文字编辑
                afterEdit(false);
                break;
            case R.id.iv_white_board_confirm://保存文字编辑
                afterEdit(true);
                break;
            case R.id.bt_size_large://设置画笔尺寸-大号
                setPenSize(WhiteBoardVariable.PenSize.LARRGE);
                break;
            case R.id.bt_size_middle://设置画笔尺寸-中号
                setPenSize(WhiteBoardVariable.PenSize.MIDDLE);
                break;
            case R.id.bt_size_mini://设置画笔尺寸-小号
                setPenSize(WhiteBoardVariable.PenSize.MINI);
                break;
            case R.id.bt_size_minimini://设置画笔尺寸-小号
                setPenSize(WhiteBoardVariable.PenSize.MINI / 2);
                break;
            case R.id.bt_color_green://设置颜色-绿色
                setColor(WhiteBoardVariable.Color.GREEN);
                break;
            case R.id.bt_color_purple://设置颜色-紫色
                setColor(WhiteBoardVariable.Color.PURPLE);
                break;
            case R.id.bt_color_pink://设置颜色-粉色
                setColor(WhiteBoardVariable.Color.PINK);
                break;
            case R.id.bt_color_orange://设置颜色-橙色
                setColor(WhiteBoardVariable.Color.ORANGE);
                break;
            case R.id.bt_color_black://设置颜色-黑色
                setColor(WhiteBoardVariable.Color.BLACK);
                break;

            case R.id.bt_text_underline://设置文字样式-下划线
                setTextStyle(WhiteBoardVariable.TextStyle.CHANGE_UNDERLINE);
                break;
            case R.id.bt_text_italics://设置文字样式-斜体
                setTextStyle(WhiteBoardVariable.TextStyle.CHANGE_ITALICS);
                break;
            case R.id.bt_text_bold://设置文字样式-粗体
                setTextStyle(WhiteBoardVariable.TextStyle.CHANGE_BOLD);
                break;

            case R.id.bt_eraser_large://设置橡皮擦尺寸-大号
                setEraserSize(WhiteBoardVariable.EraserSize.LARRGE);
                break;
            case R.id.bt_eraser_middle://设置橡皮擦尺寸-中号
                setEraserSize(WhiteBoardVariable.EraserSize.MIDDLE);
                break;
            case R.id.bt_eraser_mini://设置橡皮擦尺寸-小号
                setEraserSize(WhiteBoardVariable.EraserSize.MINI);
                break;
            case R.id.iv_white_board_undo://撤销
                ToolsOperation(WhiteBoardVariable.Operation.OUTSIDE_CLICK);
                if (OperationUtils.getInstance().DISABLE) {
                    undo();
                }
                break;
            case R.id.iv_white_board_redo://重做
                ToolsOperation(WhiteBoardVariable.Operation.OUTSIDE_CLICK);
                if (OperationUtils.getInstance().DISABLE) {
                    redo();
                }
                break;
            case R.id.iv_white_board_add://新建白板
                ToolsOperation(WhiteBoardVariable.Operation.OUTSIDE_CLICK);
                newPage();
                break;
            case R.id.iv_white_board_disable://禁止/解禁按钮
                ToolsOperation(WhiteBoardVariable.Operation.OUTSIDE_CLICK);
                if (mRlBottom.getVisibility() == View.VISIBLE) {
                   //OperationUtils.getInstance().DISABLE = false;
                    mIvWhiteBoardDisable.setImageResource(R.drawable.white_board_undisable_selector);
                    mRlBottom.setVisibility(View.GONE);
                    mIvWhiteBoardSend.setVisibility(View.GONE);
                    mIvWhiteBoardExtend.setVisibility(View.GONE);
                    mIvWhiteBoardSetbackground.setVisibility(View.GONE);
                } else {
                    //OperationUtils.getInstance().DISABLE = true;
                    mIvWhiteBoardDisable.setImageResource(R.drawable.white_board_disable_selector);
                    mRlBottom.setVisibility(View.VISIBLE);
                    if(!OperationUtils.getInstance().getSavePoints().isEmpty()){
                        mIvWhiteBoardSend.setVisibility(View.VISIBLE);
                    }
                    mIvWhiteBoardExtend.setVisibility(View.VISIBLE);
                    mIvWhiteBoardSetbackground.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.iv_white_board_send:
                saveImage();
                break;
            case R.id.iv_white_board_extend:
                View room_functtion_pad_white_board = findViewById(R.id.wb_content);

                if(room_functtion_pad_white_board.getLayoutParams().height == RelativeLayout.LayoutParams.WRAP_CONTENT){
                    int softInputHeight = getButtomPadHeight();
                    if(softInputHeight == 0){
                        softInputHeight = 800;
                    }
                    room_functtion_pad_white_board.getLayoutParams().height = softInputHeight;
                    getSupportActionBar().show();
                    //TODO,这个方式不好，需找到根本方案
                    mVectorMessageListFragment.scrollToBottom(300);
                }else{
                    room_functtion_pad_white_board.getLayoutParams().height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                    //room_functtion_pad_white_board.invalidate();
                    getSupportActionBar().hide();
                }
                room_functtion_pad_white_board.setVisibility(View.GONE);
                room_functtion_pad_white_board.setVisibility(View.VISIBLE);
                mDbView.post(new Runnable() {
                    @Override
                    public void run() {
                        showPoints();
                    }
                });

                break;
            case R.id.iv_white_board_set_background:
                launchFileSelectionIntentWB();
                break;
        }
    }

    private void initView() {
        OperationUtils.getInstance().mCurrentPenSize = WhiteBoardVariable.PenSize.MINI;
        changePenBack();
        changeColorBack();
        changeEraserBack();
        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_NORMAL;
        ToolsOperation(WhiteBoardVariable.Operation.PEN_CLICK);
        //mFabMenuSize.collapse();

        mDbView.post(new Runnable() {
            @Override
            public void run() {
                showPoints();
            }
        });
    }

    /**
     * 设置画笔尺寸
     */
    private void setPenSize(int size) {
        OperationUtils.getInstance().mCurrentPenSize = size;
        changePenBack();
        mDbView.setPenSize();
    }

    /**
     * 切换画笔尺寸按按钮背景
     */
    private void changePenBack() {
        if (OperationUtils.getInstance().mCurrentPenSize == WhiteBoardVariable.PenSize.LARRGE) {
            mBtSizeLarge.drawCircleAndRing(WhiteBoardVariable.PenSize.LARRGE, OperationUtils.getInstance().mCurrentColor);
            mBtSizeMiddle.drawCircle(WhiteBoardVariable.PenSize.MIDDLE);
            mBtSizeMini.drawCircle(WhiteBoardVariable.PenSize.MINI);
            mBtSizeMiniMini.drawCircle(WhiteBoardVariable.PenSize.MINI/2);
        } else if (OperationUtils.getInstance().mCurrentPenSize == WhiteBoardVariable.PenSize.MIDDLE) {
            mBtSizeLarge.drawCircle(WhiteBoardVariable.PenSize.LARRGE);
            mBtSizeMiddle.drawCircleAndRing(WhiteBoardVariable.PenSize.MIDDLE, OperationUtils.getInstance().mCurrentColor);
            mBtSizeMini.drawCircle(WhiteBoardVariable.PenSize.MINI);
            mBtSizeMiniMini.drawCircle(WhiteBoardVariable.PenSize.MINI/2);
        } else if (OperationUtils.getInstance().mCurrentPenSize == WhiteBoardVariable.PenSize.MINI) {
            mBtSizeLarge.drawCircle(WhiteBoardVariable.PenSize.LARRGE);
            mBtSizeMiddle.drawCircle(WhiteBoardVariable.PenSize.MIDDLE);
            mBtSizeMini.drawCircleAndRing(WhiteBoardVariable.PenSize.MINI, OperationUtils.getInstance().mCurrentColor);
            mBtSizeMiniMini.drawCircle(WhiteBoardVariable.PenSize.MINI/2);
        }else if (OperationUtils.getInstance().mCurrentPenSize == WhiteBoardVariable.PenSize.MINI/2) {
            mBtSizeLarge.drawCircle(WhiteBoardVariable.PenSize.LARRGE);
            mBtSizeMiddle.drawCircle(WhiteBoardVariable.PenSize.MIDDLE);
            mBtSizeMini.drawCircle(WhiteBoardVariable.PenSize.MINI);
            mBtSizeMiniMini.drawCircleAndRing(WhiteBoardVariable.PenSize.MINI/2, OperationUtils.getInstance().mCurrentColor);
        }

    }

    /**
     * 设置颜色
     */
    private void setColor(int color) {
        OperationUtils.getInstance().mCurrentColor = color;
        changeColorBack();
        setPenSize(OperationUtils.getInstance().mCurrentPenSize);
        mDbView.setPenColor();
        mDtView.setTextColor();
    }

    /**
     * 切换颜色控制按钮背景
     */
    private void changeColorBack() {
        if (OperationUtils.getInstance().mCurrentColor == WhiteBoardVariable.Color.BLACK) {
            mFabMenuColor.setAddButtonBackground(R.drawable.white_board_color_black_selector);
        } else if (OperationUtils.getInstance().mCurrentColor == WhiteBoardVariable.Color.ORANGE) {
            mFabMenuColor.setAddButtonBackground(R.drawable.white_board_color_orange_selector);
        } else if (OperationUtils.getInstance().mCurrentColor == WhiteBoardVariable.Color.PINK) {
            mFabMenuColor.setAddButtonBackground(R.drawable.white_board_color_pink_selector);
        } else if (OperationUtils.getInstance().mCurrentColor == WhiteBoardVariable.Color.PURPLE) {
            mFabMenuColor.setAddButtonBackground(R.drawable.white_board_color_purple_selector);
        } else if (OperationUtils.getInstance().mCurrentColor == WhiteBoardVariable.Color.GREEN) {
            mFabMenuColor.setAddButtonBackground(R.drawable.white_board_color_green_selector);
        }
    }

    /**
     * 设置文字风格
     */
    private void setTextStyle(int textStyle) {
        mDtView.setTextStyle(textStyle);
        changeTextBack();
    }

    /**
     * 切换文字相关按钮背景
     */
    private void changeTextBack() {
        int size = OperationUtils.getInstance().getSavePoints().size();
        if (size < 1) {
            return;
        }
        DrawPoint dp = OperationUtils.getInstance().getSavePoints().get(size - 1);
        if (dp.getType() != OperationUtils.DRAW_TEXT) {
            return;
        }
        if (dp.getDrawText().getIsUnderline()) {
            mBtTextUnderline.setBackgroundResource(R.drawable.white_board_text_underline_selected_selector);
        } else {
            mBtTextUnderline.setBackgroundResource(R.drawable.white_board_text_underline_selector);
        }

        if (dp.getDrawText().getIsItalics()) {
            mBtTextItalics.setBackgroundResource(R.drawable.white_board_text_italics_selected_selector);
        } else {
            mBtTextItalics.setBackgroundResource(R.drawable.white_board_text_italics_selector);
        }

        if (dp.getDrawText().getIsBold()) {
            mBtTextBold.setBackgroundResource(R.drawable.white_board_text_bold_selected_selector);
        } else {
            mBtTextBold.setBackgroundResource(R.drawable.white_board_text_bold_selector);
        }
    }

    /**
     * 设置橡皮擦尺寸
     */
    private void setEraserSize(int size) {
        OperationUtils.getInstance().mCurrentEraserSize = size;
        changeEraserBack();
        mDbView.setEraserSize();

    }

    /**
     * 切换橡皮擦尺寸按钮背景
     */
    private void changeEraserBack() {
        if (OperationUtils.getInstance().mCurrentEraserSize == WhiteBoardVariable.EraserSize.LARRGE) {
            mBtEraserLarge.drawCircleAndRing(WhiteBoardVariable.EraserSize.LARRGE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMiddle.drawCircle(WhiteBoardVariable.EraserSize.MIDDLE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMini.drawCircle(WhiteBoardVariable.EraserSize.MINI, WhiteBoardVariable.Color.BLACK);
        } else if (OperationUtils.getInstance().mCurrentEraserSize == WhiteBoardVariable.EraserSize.MIDDLE) {
            mBtEraserLarge.drawCircle(WhiteBoardVariable.EraserSize.LARRGE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMiddle.drawCircleAndRing(WhiteBoardVariable.EraserSize.MIDDLE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMini.drawCircle(WhiteBoardVariable.EraserSize.MINI, WhiteBoardVariable.Color.BLACK);
        } else if (OperationUtils.getInstance().mCurrentEraserSize == WhiteBoardVariable.EraserSize.MINI) {
            mBtEraserLarge.drawCircle(WhiteBoardVariable.EraserSize.LARRGE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMiddle.drawCircle(WhiteBoardVariable.EraserSize.MIDDLE, WhiteBoardVariable.Color.BLACK);
            mBtEraserMini.drawCircleAndRing(WhiteBoardVariable.EraserSize.MINI, WhiteBoardVariable.Color.BLACK);

        }
    }

    /**
     * 新建白板
     */
    private void newPage() {
        mBackroundview.setImageURI(null);
        OperationUtils.getInstance().newPage();
        mDbView.post(new Runnable() {
            @Override
            public void run() {
                showPoints();
            }
        });
        mBackroundview.setImageDrawable(null);
    }

    /**
     * 重新显示白板
     */
    private void showPoints() {
        mDbView.showPoints();
        mDtView.showPoints();
        showUndoRedo();
    }

    /**
     * 撤销
     */
    private void undo() {
        int size = OperationUtils.getInstance().getSavePoints().size();
        if (size == 0) {
            return;
        } else {
            OperationUtils.getInstance().getDeletePoints().add(OperationUtils.getInstance().getSavePoints().get(size - 1));
            OperationUtils.getInstance().getSavePoints().remove(size - 1);
            size = OperationUtils.getInstance().getDeletePoints().size();
            if (OperationUtils.getInstance().getDeletePoints().get(size - 1).getType() == OperationUtils.DRAW_PEN) {
                mDbView.undo();
            } else if (OperationUtils.getInstance().getDeletePoints().get(size - 1).getType() == OperationUtils.DRAW_TEXT) {
                mDtView.undo();
            }
            showUndoRedo();
        }

    }

    /**
     * 重做
     */
    private void redo() {
        int size = OperationUtils.getInstance().getDeletePoints().size();
        if (size == 0) {
            return;
        } else {
            OperationUtils.getInstance().getSavePoints().add(OperationUtils.getInstance().getDeletePoints().get(size - 1));
            OperationUtils.getInstance().getDeletePoints().remove(size - 1);
            size = OperationUtils.getInstance().getSavePoints().size();
            if (OperationUtils.getInstance().getSavePoints().get(size - 1).getType() == OperationUtils.DRAW_PEN) {
                mDbView.redo();
            } else if (OperationUtils.getInstance().getSavePoints().get(size - 1).getType() == OperationUtils.DRAW_TEXT) {
                mDtView.redo();
            }
            showUndoRedo();
        }

    }

    /**
     * 文字编辑之后
     */
    private void afterEdit(boolean isSave) {
        mRlBottom.setVisibility(View.VISIBLE);
        mIvWhiteBoardDisable.setVisibility(View.VISIBLE);
        mIvWhiteBoardExtend.setVisibility(View.VISIBLE);
        mIvWhiteBoardSetbackground.setVisibility(View.VISIBLE);
        mIvWhiteBoardQuit.setVisibility(View.GONE);
        mIvWhiteBoardConfirm.setVisibility(View.GONE);
        if(mRlBottom.getVisibility() == View.VISIBLE){
            mIvWhiteBoardSend.setVisibility(View.VISIBLE);
        }
        mDbView.showPoints();
        mDtView.afterEdit(isSave);
    }

    @ReceiveEvents(name = Events.WHITE_BOARD_TEXT_EDIT)
    private void textEdit() {//文字编辑
        mRlBottom.setVisibility(View.GONE);
        mIvWhiteBoardSend.setVisibility(View.GONE);
        mIvWhiteBoardDisable.setVisibility(View.GONE);
        mIvWhiteBoardExtend.setVisibility(View.GONE);
        mIvWhiteBoardSetbackground.setVisibility(View.GONE);
        mIvWhiteBoardQuit.setVisibility(View.VISIBLE);
        mIvWhiteBoardConfirm.setVisibility(View.VISIBLE);
    }

    @ReceiveEvents(name = Events.WHITE_BOARD_UNDO_REDO)
    private void showUndoRedo() {//是否显示撤销、重装按钮
        if (OperationUtils.getInstance().getSavePoints().isEmpty()) {
            mIvWhiteBoardUndo.setVisibility(View.INVISIBLE);
            mIvWhiteBoardSend.setVisibility(View.INVISIBLE);
        } else {
            mIvWhiteBoardUndo.setVisibility(View.VISIBLE);
            if(mRlBottom.getVisibility() == View.VISIBLE){
                mIvWhiteBoardSend.setVisibility(View.VISIBLE);
            }
        }
        if (OperationUtils.getInstance().getDeletePoints().isEmpty()) {
            mIvWhiteBoardRedo.setVisibility(View.INVISIBLE);
        } else {
            mIvWhiteBoardRedo.setVisibility(View.VISIBLE);
        }
    }

    private void ToolsOperation(int currentOperation) {
        setPenOperation(currentOperation);
        setColorOperation(currentOperation);
        setTextOperation(currentOperation);
        setEraserOperation(currentOperation);
        showOutSideView();
    }

    /**
     * 显示挡板
     */
    private void showOutSideView() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (OperationUtils.getInstance().mCurrentOPerationPen == WhiteBoardVariable.Operation.PEN_EXPAND
                        || OperationUtils.getInstance().mCurrentOPerationColor == WhiteBoardVariable.Operation.COLOR_EXPAND
                        || OperationUtils.getInstance().mCurrentOPerationText == WhiteBoardVariable.Operation.TEXT_EXPAND
                        || OperationUtils.getInstance().mCurrentOPerationEraser == WhiteBoardVariable.Operation.ERASER_EXPAND) {
                    mVBottomBack.setVisibility(View.VISIBLE);
                } else {
                    mVBottomBack.setVisibility(View.GONE);
                }
            }
        }, 100);

    }

    /**
     * 白板工具栏点击切换操作-画笔
     */
    private void setPenOperation(int currentOperation) {
        switch (currentOperation) {
            case WhiteBoardVariable.Operation.PEN_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationPen) {
                    case WhiteBoardVariable.Operation.PEN_NORMAL:
                        OperationUtils.getInstance().mCurrentDrawType = OperationUtils.DRAW_PEN;
                        mDbView.setPaint(null);
                        mFabMenuSize.setAddButtonBackground(R.drawable.white_board_pen_selected_selector);
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_CLICK;
                        break;
                    case WhiteBoardVariable.Operation.PEN_CLICK:
                        mFabMenuSize.expand();
                        changePenBack();
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_EXPAND;
                        break;
                    case WhiteBoardVariable.Operation.PEN_EXPAND:
                        mFabMenuSize.collapse();
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_CLICK;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.TEXT_CLICK:
            case WhiteBoardVariable.Operation.ERASER_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationPen) {
                    case WhiteBoardVariable.Operation.PEN_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.PEN_CLICK:
                        mFabMenuSize.clearDraw();
                        mFabMenuSize.setAddButtonBackground(R.drawable.white_board_pen_selector);
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_NORMAL;
                        break;
                    case WhiteBoardVariable.Operation.PEN_EXPAND:
                        mFabMenuSize.collapse();
                        mFabMenuSize.clearDraw();
                        mFabMenuSize.setAddButtonBackground(R.drawable.white_board_pen_selector);
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_NORMAL;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.COLOR_CLICK:
            case WhiteBoardVariable.Operation.OUTSIDE_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationPen) {
                    case WhiteBoardVariable.Operation.PEN_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.PEN_CLICK:
                        break;
                    case WhiteBoardVariable.Operation.PEN_EXPAND:
                        mFabMenuSize.collapse();
                        OperationUtils.getInstance().mCurrentOPerationPen = WhiteBoardVariable.Operation.PEN_CLICK;
                        break;
                }
                break;

        }

    }

    /**
     * 白板工具栏点击切换操作-颜色
     */
    private void setColorOperation(int currentOperation) {
        switch (currentOperation) {
            case WhiteBoardVariable.Operation.PEN_CLICK:
            case WhiteBoardVariable.Operation.TEXT_CLICK:
            case WhiteBoardVariable.Operation.ERASER_CLICK:
            case WhiteBoardVariable.Operation.OUTSIDE_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationColor) {
                    case WhiteBoardVariable.Operation.COLOR_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.COLOR_EXPAND:
                        mFabMenuColor.collapse();
                        OperationUtils.getInstance().mCurrentOPerationColor = WhiteBoardVariable.Operation.COLOR_NORMAL;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.COLOR_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationColor) {
                    case WhiteBoardVariable.Operation.COLOR_NORMAL:
                        mFabMenuColor.expand();
                        OperationUtils.getInstance().mCurrentOPerationColor = WhiteBoardVariable.Operation.COLOR_EXPAND;
                        break;
                    case WhiteBoardVariable.Operation.COLOR_EXPAND:
                        mFabMenuColor.collapse();
                        OperationUtils.getInstance().mCurrentOPerationColor = WhiteBoardVariable.Operation.COLOR_NORMAL;
                        break;
                }
                break;

        }

    }

    /**
     * 白板工具栏点击切换操作-文字
     */
    private void setTextOperation(int currentOperation) {
        switch (currentOperation) {
            case WhiteBoardVariable.Operation.TEXT_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationText) {
                    case WhiteBoardVariable.Operation.TEXT_NORMAL:
                        OperationUtils.getInstance().mCurrentDrawType = OperationUtils.DRAW_TEXT;
                        mFabMenuText.setAddButtonBackground(R.drawable.white_board_text_selected_selector);
                        OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_CLICK;
                        break;
                    case WhiteBoardVariable.Operation.TEXT_CLICK:
                        int size = OperationUtils.getInstance().getSavePoints().size();
                        if (size > 0) {
                            DrawPoint dp = OperationUtils.getInstance().getSavePoints().get(size - 1);
                            if (dp.getType() == OperationUtils.DRAW_TEXT && dp.getDrawText().getStatus() == DrawTextView.TEXT_DETAIL) {
                                changeTextBack();
                                mFabMenuText.expand();
                                OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_EXPAND;
                            }
                        }
                        break;
                    case WhiteBoardVariable.Operation.TEXT_EXPAND:
                        mFabMenuText.collapse();
                        OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_CLICK;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.PEN_CLICK:
            case WhiteBoardVariable.Operation.ERASER_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationText) {
                    case WhiteBoardVariable.Operation.TEXT_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.TEXT_CLICK:
                        mFabMenuText.clearDraw();
                        mFabMenuText.setAddButtonBackground(R.drawable.white_board_text_selector);
                        OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_NORMAL;
                        break;
                    case WhiteBoardVariable.Operation.TEXT_EXPAND:
                        mFabMenuText.collapse();
                        mFabMenuText.clearDraw();
                        mFabMenuText.setAddButtonBackground(R.drawable.white_board_text_selector);
                        OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_NORMAL;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.COLOR_CLICK:
            case WhiteBoardVariable.Operation.OUTSIDE_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationText) {
                    case WhiteBoardVariable.Operation.TEXT_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.TEXT_CLICK:
                        break;
                    case WhiteBoardVariable.Operation.TEXT_EXPAND:
                        mFabMenuText.collapse();
                        OperationUtils.getInstance().mCurrentOPerationText = WhiteBoardVariable.Operation.TEXT_CLICK;
                        break;
                }
                break;

        }

    }

    /**
     * 白板工具栏点击切换操作-橡皮擦
     */
    private void setEraserOperation(int currentOperation) {
        switch (currentOperation) {
            case WhiteBoardVariable.Operation.ERASER_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationEraser) {
                    case WhiteBoardVariable.Operation.ERASER_NORMAL:
                        OperationUtils.getInstance().mCurrentDrawType = OperationUtils.DRAW_ERASER;
                        mDbView.changeEraser();
                        mFabMenuEraser.setAddButtonBackground(R.drawable.white_board_eraser_selected_selector);
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_CLICK;
                        break;
                    case WhiteBoardVariable.Operation.ERASER_CLICK:
                        mFabMenuEraser.expand();
                        changeEraserBack();
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_EXPAND;
                        break;
                    case WhiteBoardVariable.Operation.ERASER_EXPAND:
                        mFabMenuEraser.collapse();
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_CLICK;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.TEXT_CLICK:
            case WhiteBoardVariable.Operation.PEN_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationEraser) {
                    case WhiteBoardVariable.Operation.ERASER_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.ERASER_CLICK:
                        mFabMenuEraser.clearDraw();
                        mFabMenuEraser.setAddButtonBackground(R.drawable.white_board_eraser_selector);
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_NORMAL;
                        break;
                    case WhiteBoardVariable.Operation.ERASER_EXPAND:
                        mFabMenuEraser.collapse();
                        mFabMenuEraser.clearDraw();
                        mFabMenuEraser.setAddButtonBackground(R.drawable.white_board_eraser_selector);
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_NORMAL;
                        break;
                }
                break;
            case WhiteBoardVariable.Operation.COLOR_CLICK:
            case WhiteBoardVariable.Operation.OUTSIDE_CLICK:
                switch (OperationUtils.getInstance().mCurrentOPerationEraser) {
                    case WhiteBoardVariable.Operation.ERASER_NORMAL:
                        break;
                    case WhiteBoardVariable.Operation.ERASER_CLICK:
                        break;
                    case WhiteBoardVariable.Operation.ERASER_EXPAND:
                        mFabMenuEraser.collapse();
                        OperationUtils.getInstance().mCurrentOPerationEraser = WhiteBoardVariable.Operation.ERASER_CLICK;
                        break;
                }
                break;

        }

    }

    /**
     * 保存当前白板为图片
     */
    private void saveImage() {
        String fileName = FileControl.getFileString("Whiteboard", "wb") + ".png";

        File file = new File(fileName);
        try {
            File directory = file.getParentFile();
            if (!directory.exists() && !directory.mkdirs()) {
                showMessage(("涂鸦文件目录错误"));
                return;
            }
            file.createNewFile();
            FileOutputStream out = new FileOutputStream(file);
            mFlView.setDrawingCacheEnabled(true);
            mFlView.buildDrawingCache();
            Bitmap bitmap = mFlView.getDrawingCache();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            mFlView.destroyDrawingCache();

//            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//            Uri uri = Uri.fromFile(file);
//            intent.setData(uri);
//            sendBroadcast(intent);//这个广播的目的就是更新图库

            //showMessage(getString(R.string.white_board_export_tip) + fileName);
        } catch (Exception e) {
            showMessage(("涂鸦文件生成失败！"));
            e.printStackTrace();
        }

        sendImage(fileName);
        freshButtomPad();
    }

    @SuppressLint("NewApi")
    private void launchFileSelectionIntentWB() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        fileIntent.setType("image/*");
        startActivityForResult(fileIntent, REQUEST_WB_IMAGE_REQUEST_CODE);
    }

    private void setWBBackground(final Intent aData) {

        Uri uri = aData.getData();
        //Drawable d = Drawable.createFromStream(this.getContentResolver().openInputStream(uri), null);
        //mFlView.setBackgroundDrawable(d);
        //mBackroundview.setImageDrawable(d);
        mBackroundview.setImageURI(uri);
    }

    private void setWBBackgroundFromMediaView(final  Intent aData){
        String url = aData.getStringExtra("WbMediaUrl");
        String mineType = aData.getStringExtra("WbMediaMineType");

        File file = mSession.getMediasCache().mediaCacheFile(url, mineType);
        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Drawable d = Drawable.createFromStream(in, null);
        showWhiteheBoard(null);
        newPage();
        mBackroundview.setImageDrawable(d);

        moreImg = R.drawable.ic_material_file2;
        mSendImageView.setImageResource(moreImg);
    }

    private void showMessage(CharSequence msg) {
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

    static public int getRequestId(){
        return REQUEST_WB_MRDIA_VIWE_REQUEST_CODE;
    }



    //阅后即焚
    private void delMessageAll(){
        int count = mVectorMessageListFragment.mMessageListView.getCount();
        Event event;
        for(int i = 0; i < count; i++){
            event = mVectorMessageListFragment.getEvent(i);
            if(event != null){
//                if (event.isUndeliverable() || event.isUnkownDevice()) {
//                    // delete from the store
//                    mSession.getDataHandler().deleteRoomEvent(event);
//
//                    // remove from the adapter
//                    mVectorMessageListFragment.mAdapter.removeEventById(event.eventId);
//                    mVectorMessageListFragment.mAdapter.notifyDataSetChanged();
//                    mVectorMessageListFragment.mEventSendingListener.onMessageRedacted(event);
//                } else {
//                    mVectorMessageListFragment.redactEvent(event.eventId);
//                }
                if(!mSession.getMyUserId().equals(event.getSender())){
                    mRoom.redact(event.eventId, new ApiCallback<Event>() {
                        @Override
                        public void onSuccess(Event event) {

                        }

                        @Override
                        public void onNetworkError(Exception e) {

                        }

                        @Override
                        public void onMatrixError(MatrixError matrixError) {

                        }

                        @Override
                        public void onUnexpectedError(Exception e) {

                        }
                    });
                }
            }
        }
    }




    //图片搜索
    private RecyclerView mRecyclerView;
    private ArrayList<Image> mImages = new ArrayList<Image>();
    private ImageRecyclerAdapter mAdapter;
    private StaggeredGridLayoutManager mLayoutManager;
    private Presenter mPresenter;
    private Button mPicSearchButton;
    private EditText mPicSearchEdit;
    private View mRoom_functtion_pad_pic_search_board;
    private int mSoftInputHeight;

    private void showPicSearch(String picString) {
        //hidePadType(1);
        View room_function_pad = findViewById(R.id.room_function_pad);
        if (room_function_pad.getVisibility() == View.VISIBLE) {
            room_function_pad.setVisibility(View.GONE);
        }

        mRoom_functtion_pad_pic_search_board = findViewById(R.id.pic_search_pad);
        //View function_pad_separator = findViewById(R.id.function_pad_separator);

        mSoftInputHeight = getButtomPadHeight();
        if (mSoftInputHeight == 0) {
            mSoftInputHeight = 800;
        }
        mRoom_functtion_pad_pic_search_board.getLayoutParams().height = mSoftInputHeight;

        //function_pad_separator.setVisibility(View.VISIBLE);
        mRoom_functtion_pad_pic_search_board.setVisibility(View.VISIBLE);

        picSearchInit();

        if(picString != null && !picString.equals("")){
            mPresenter.setQueryKeyWord(picString);
            mPresenter.loadIamges();
            mPicSearchEdit.setText(picString);
        }
    }

    private void picSearchInit(){
        if(mPresenter != null){
            return;
        }

        mPresenter = new DefaultPresenterImpl(this);

        mRecyclerView = (RecyclerView)findViewById(R.id.pic_search_recycler_view);
        mLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new ImageRecyclerAdapter(this,mImages);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(newState == RecyclerView.SCROLL_STATE_IDLE /*|| newState == RecyclerView.SCROLL_STATE_SETTLING*/){
                    int pos[] = mLayoutManager.findLastVisibleItemPositions(null);
                    if (null != pos && 0 < pos.length){
                        android.util.Log.d("alanF", (mImages.size() - 1) + "====" + pos[pos.length -1]);
                        if(Math.abs((mImages.size() - 1) - pos[pos.length -1]) < 4) {
                            android.util.Log.d("alanF", "load data");
                            mPresenter.loadIamges();
                        }
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        mPicSearchButton = (Button) findViewById(R.id.pic_search_button);
        mPicSearchEdit = (EditText) findViewById(R.id.pic_search_edit);
        mPicSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keyWord = mPicSearchEdit.getText().toString();
                if(keyWord != null && !keyWord.equals("")){
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mPicSearchButton.getWindowToken(), 0);

                    mPresenter.setQueryKeyWord(keyWord);
                    mPresenter.loadIamges();

                    //mPicSearchButton.setEnabled(false);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mRoom_functtion_pad_pic_search_board.getLayoutParams().height = mSoftInputHeight;
                        }
                    }, 100);
                }
            }
        });

        mPicSearchEdit.requestFocus();
        mPicSearchEdit.requestFocusFromTouch();
        mPicSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mPicSearchButton.setEnabled(true);
            }
        });

        mPicSearchEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            ViewGroup.LayoutParams para = mRoom_functtion_pad_pic_search_board.getLayoutParams();//.height = 150;
            para.height = 150;
            mRoom_functtion_pad_pic_search_board.setLayoutParams(para);

            //todo, 某些手机会高度设置不成功，所以再延时设置一次
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    ViewGroup.LayoutParams para = mRoom_functtion_pad_pic_search_board.getLayoutParams();//.height = 150;
                    para.height = 150;
                    mRoom_functtion_pad_pic_search_board.setLayoutParams(para);
                }
            }, 50);
            }
        });

        mPicSearchEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String keyWord = mPicSearchEdit.getText().toString();
                    if(keyWord != null && !keyWord.equals("")) {
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                .hideSoftInputFromWindow(mPicSearchEdit.getWindowToken(), 0);

                        mPresenter.setQueryKeyWord(keyWord);
                        mPresenter.loadIamges();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mRoom_functtion_pad_pic_search_board.getLayoutParams().height = mSoftInputHeight;
                            }
                        }, 50);
                    }
                }

                return true;
            }
        });
    }

    private void handlePicSearch(final  Intent aData){
        String fileName = aData.getStringExtra("PicSearchFileName");
        int action = aData.getIntExtra("PicSearchAction", 0);

        if(action == 0){
            sendImage(fileName);
        }else if(action == 1){
            hidePadType(3);
            InputStream in = null;
            try {
                in = new FileInputStream(fileName);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            Drawable d = Drawable.createFromStream(in, null);
            showWhiteheBoard(null);
            newPage();
            mBackroundview.setImageDrawable(d);

            moreImg = R.drawable.ic_material_file2;
            mSendImageView.setImageResource(moreImg);
        }
    }

    @Override
    public void notifyDataChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void addImage(Image image) {
        mImages.add(image);
    }

    @Override
    public void addImages(@NonNull List<Image> images) {
        mImages.addAll(images);
    }

    @Override
    public void clearAllDatas() {
        mImages.clear();
    }

    static public int getPicSearchRequestId(){
        return REQUEST_PIC_SEARCH_VIWE_REQUEST_CODE;
    }
}


