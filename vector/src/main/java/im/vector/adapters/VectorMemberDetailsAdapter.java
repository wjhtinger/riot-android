/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.util.VectorUtils;
import im.vector.view.VectorCircularImageView;

/**
 * An adapter which can display the available actions list
 */
public class VectorMemberDetailsAdapter extends BaseExpandableListAdapter {

    // the layout
    private final LayoutInflater mLayoutInflater;

    // the used layout
    private final int mRowItemLayoutResourceId;
    private final int mHeaderLayoutResourceId;

    // context
    private final Context mContext;

    // sessiop
    private final MXSession mSession;

    // listener
    private IEnablingActions mActionListener;

    // actions list
    private List<AdapterMemberActionItems> mAdminActionsList = new ArrayList<>();
    private List<AdapterMemberActionItems> mCallActionsList = new ArrayList<>();
    private List<AdapterMemberActionItems> mDirectCallsList = new ArrayList<>();
    private List<AdapterMemberActionItems> mDevicesList = new ArrayList<>();

    // list of actions list
    private ArrayList<List<AdapterMemberActionItems>> mActionsList = new ArrayList<>();

    // group position
    private int mAdminGroupPosition = -1;
    private int mCallGroupPosition = -1;
    private int mDirectCallsGroupPosition = -1;
    private int mDevicesGroupPosition = -1;

    /**
     * Interface proxy to perform actions
     */
    public interface IEnablingActions {
        /**
         * @param aActionType the action type
         */
        void performItemAction(int aActionType);

        /**
         * @param room the selected room
         */
        void selectRoom(Room room);
    }

    /**
     * Recycle view holder class.
     */
    private static class MemberDetailsViewHolder {
        final VectorCircularImageView mVectorCircularImageView;
        final ImageView mActionImageView;
        final TextView mActionDescTextView;
        final View mRoomAvatarLayout;

        MemberDetailsViewHolder(View aParentView) {
            mActionImageView = (ImageView) aParentView.findViewById(R.id.adapter_member_details_icon);
            mActionDescTextView = (TextView) aParentView.findViewById(R.id.adapter_member_details_action_text);
            mVectorCircularImageView = (VectorCircularImageView) aParentView.findViewById(R.id.room_avatar_image_view);
            mRoomAvatarLayout = aParentView.findViewById(R.id.room_avatar_layout);
        }
    }

    /**
     * List view data model class.
     * The model consists of an icon, a text and the associated internal action type.
     */
    static public class AdapterMemberActionItems {
        // either a room member action
        final public int mIconResourceId;
        final public String mActionDescText;
        final public int mActionType; // ban, kick..

        // or room selection
        final public Room mRoom;

        /**
         * Constructor for a dedicated action
         *
         * @param aIconResourceId the icon
         * @param aText           the text to display
         * @param aActionType     the action type
         */
        public AdapterMemberActionItems(int aIconResourceId, String aText, int aActionType) {
            mIconResourceId = aIconResourceId;
            mActionDescText = aText;
            mActionType = aActionType;
            mRoom = null;
        }

        public AdapterMemberActionItems(Room room) {
            mIconResourceId = -1;
            mActionDescText = null;
            mActionType = -1;
            mRoom = room;
        }
    }

    /**
     * Construct an adapter where the items layout and the data model collection is provided
     *
     * @param aContext                 Android App context
     * @param aRowItemLayoutResourceId the layout of the list view item (row)
     */
    public VectorMemberDetailsAdapter(Context aContext, MXSession session, int aRowItemLayoutResourceId, int headerLayoutResourceId) {
        mContext = aContext;
        mSession = session;
        mRowItemLayoutResourceId = aRowItemLayoutResourceId;
        mHeaderLayoutResourceId = headerLayoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    /**
     * Set the action listener
     *
     * @param aActionListener the action listenet
     */
    public void setActionListener(IEnablingActions aActionListener) {
        mActionListener = aActionListener;
    }

    /**
     * Update the admin actions list
     *
     * @param adminActionsList the admin actions list
     */
    public void setAdminActionsList(List<AdapterMemberActionItems> adminActionsList) {
        mAdminActionsList = adminActionsList;
    }

    /**
     * Update the call actions list
     *
     * @param callActionsList the call actions list
     */
    public void setCallActionsList(List<AdapterMemberActionItems> callActionsList) {
        mCallActionsList = callActionsList;
    }

    /**
     * Update the call actions list
     *
     * @param directCallActionsList the call actions list
     */
    public void setDirectCallsActionsList(List<AdapterMemberActionItems> directCallActionsList) {
        mDirectCallsList = directCallActionsList;
    }

    /**
     * Update the devices actions list.<br>
     *
     * @param deviceActionsList the call actions list
     */
    public void setDevicesActionsList(List<AdapterMemberActionItems> deviceActionsList) {
        mDevicesList = deviceActionsList;
    }

    @Override
    public void notifyDataSetChanged() {
        // refresh the list of list
        mActionsList = new ArrayList<>();

        int groupPos = 0;
        mAdminGroupPosition = -1;
        mCallGroupPosition = -1;
        mDirectCallsGroupPosition = -1;
        mDevicesGroupPosition = -1;

        if (0 != mAdminActionsList.size()) {
            mActionsList.add(mAdminActionsList);
            mAdminGroupPosition = groupPos;
            groupPos++;
        }

        if (0 != mCallActionsList.size()) {
            mActionsList.add(mCallActionsList);
            mCallGroupPosition = groupPos;
            groupPos++;
        }

//        if (0 != mDevicesList.size()) {
//            mActionsList.add(mDevicesList);
//            mDevicesGroupPosition = groupPos;
//            groupPos++;
//        }

        if (0 != mDirectCallsList.size()) {
            mActionsList.add(mDirectCallsList);
            mDirectCallsGroupPosition = groupPos;
            groupPos++;
        }

        // and refresh
        super.notifyDataSetChanged();
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public int getGroupCount() {
        return mActionsList.size();
    }

    /**
     * Provides the group title for a dedicated group position
     *
     * @param groupPosition the group position
     * @return the group title.
     */
    private String getGroupTitle(int groupPosition) {
        if (groupPosition == mAdminGroupPosition) {
            return mContext.getResources().getString(R.string.room_participants_header_admin_tools);
        } else if (groupPosition == mCallGroupPosition) {
            return mContext.getResources().getString(R.string.room_participants_header_call);
        } else if (groupPosition == mDirectCallsGroupPosition) {
            return mContext.getResources().getString(R.string.room_participants_header_direct_chats);
        } else if (groupPosition == mDevicesGroupPosition) {
            return mContext.getResources().getString(R.string.room_participants_header_devices);
        }

        return "???";
    }

    @Override
    public Object getGroup(int groupPosition) {
        return getGroupTitle(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroupTitle(groupPosition).hashCode();
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {

        if (null == convertView) {
            convertView = this.mLayoutInflater.inflate(this.mHeaderLayoutResourceId, null);
        }

        TextView sectionNameTxtView = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.heading);

        if (null != sectionNameTxtView) {
            sectionNameTxtView.setText(getGroupTitle(groupPosition));
        }

        ImageView imageView = (ImageView) convertView.findViewById(org.matrix.androidsdk.R.id.heading_image);
        imageView.setVisibility(View.GONE);

        return convertView;
    }


    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0L;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        // sanity check
        if (groupPosition < mActionsList.size()) {
            return mActionsList.get(groupPosition).size();
        }

        return 0;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        MemberDetailsViewHolder viewHolder;

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowItemLayoutResourceId, parent, false);
            viewHolder = new MemberDetailsViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            // recycle previous view..
            viewHolder = (MemberDetailsViewHolder) convertView.getTag();
        }

        // sanity checks
        if ((groupPosition >= mActionsList.size()) || (childPosition >= mActionsList.get(groupPosition).size())) {
            return convertView;
        }

        // get current item
        final AdapterMemberActionItems currentItem = mActionsList.get(groupPosition).get(childPosition);

        // room selection
        if (null != currentItem.mRoom) {
            // room name
            viewHolder.mActionDescTextView.setTextColor(mContext.getResources().getColor(R.color.material_grey_900));
            viewHolder.mActionDescTextView.setText(VectorUtils.getRoomDisplayName(mContext, mSession, currentItem.mRoom));

            // room avatar
            viewHolder.mActionImageView.setVisibility(View.GONE);
            viewHolder.mRoomAvatarLayout.setVisibility(View.VISIBLE);
            VectorUtils.loadRoomAvatar(mContext, mSession, viewHolder.mVectorCircularImageView, currentItem.mRoom);

            // set the action associated to the item
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (null != mActionListener) {
                        mActionListener.selectRoom(currentItem.mRoom);
                    }
                }
            });
        } else {
            // update the icon and the action text
            viewHolder.mActionDescTextView.setText(currentItem.mActionDescText);

            viewHolder.mActionImageView.setVisibility(View.VISIBLE);
            viewHolder.mRoomAvatarLayout.setVisibility(View.GONE);

            viewHolder.mActionImageView.setImageResource(currentItem.mIconResourceId);

            // update the text colour: specific colour is required for the remove action
            int colourTxt = mContext.getResources().getColor(R.color.material_grey_900);

            if (VectorMemberDetailsActivity.ITEM_ACTION_KICK == currentItem.mActionType) {
                colourTxt = mContext.getResources().getColor(R.color.vector_fuchsia_color);
            }

            viewHolder.mActionDescTextView.setTextColor(colourTxt);

            // set the action associated to the item
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (null != mActionListener) {
                        mActionListener.performItemAction(currentItem.mActionType);
                    }
                }
            });
        }

        return convertView;
    }
}
