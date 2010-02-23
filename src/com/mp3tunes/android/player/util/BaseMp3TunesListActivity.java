package com.mp3tunes.android.player.util;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class BaseMp3TunesListActivity extends ListActivity
{
    protected final int PROGRESS_DIALOG = 0;
    protected final int ERROR_DIALOG    = 1;
    
    private ProgressDialog mProgressDialog;
    private AlertDialog    mErrorDialog;
    private ReturnToLockerListAction mReturner = new ReturnToLockerListAction(this);
    
    private FetchBrowserCursor mFetchBrowserCursorTask; 
    
    public void init(Cursor cursor)
    {
        
    }
    
    @Override
    protected Dialog onCreateDialog( int id )
    {
        switch ( id )
        {
        case PROGRESS_DIALOG:
            return mProgressDialog;
        case ERROR_DIALOG:
            return mErrorDialog;
        default:
            return null;
        }
    }
    
    public void buildErrorDialog(int messageId)
    {
        mErrorDialog = DialogUtils.buildNetworkProblemDialog(this, messageId, mReturner);
    }
    
    public void buildProgressDialog(int messageId)
    {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setMessage(getText(messageId));
    }
    
    public void fetch(FetchBrowserCursor task)
    {
        mFetchBrowserCursorTask = task;
        mFetchBrowserCursorTask.execute();
    }  
    
    public Id cursorToId(Cursor c)
    {
        if (c.getInt(c.getColumnIndexOrThrow(MediaStore.KEY_LOCAL)) == 0) {
            int id = c.getInt(c.getColumnIndexOrThrow(DbKeys.ID));
            return new LockerId(id);
        } else { 
            int id = c.getInt(c.getColumnIndexOrThrow(DbKeys.ID));
            return new LocalId(id);
        }
    }
    
    public Id[] cursorToIdArray(Cursor c)
    {
        int i = 0;
        int len = c.getCount();
        Id[] list = new Id[len];
        if (c.moveToFirst()) {
            do {
                list[i] = cursorToId(c);
                i++;
            } while (c.moveToNext());
        }
        return list;
    }
    
    abstract protected class FetchBrowserCursor extends AsyncTask<Void, Void, Boolean>
    { 
        private   String   mActivityName;
        protected Cursor   mCursor;

        
        public FetchBrowserCursor()
        {
            mActivityName = BaseMp3TunesListActivity.this.getClass().getSimpleName();
        }
        
        @Override
        public void onPreExecute()
        {
            BaseMp3TunesListActivity.this.showDialog(PROGRESS_DIALOG);
            Music.setSpinnerState(BaseMp3TunesListActivity.this, true);
            Log.w("Mp3Tunes", "Fetching for: " + mActivityName);
        }
        
        @Override
        public void onPostExecute(Boolean result)
        {
            BaseMp3TunesListActivity.this.dismissDialog(PROGRESS_DIALOG);
            Music.setSpinnerState(BaseMp3TunesListActivity.this, false);
            
            if (!result) {
                BaseMp3TunesListActivity.this.showDialog(ERROR_DIALOG);
                Log.e("Mp3Tunes", "Error displaying browser for: " + mActivityName);
                return;
            }
            
            if(mCursor != null)
                BaseMp3TunesListActivity.this.init(mCursor);
            else
                Log.w("Mp3Tunes", "FetchBrowserCursor: got null cursor for: " + mActivityName);
        }

        @Override
        abstract protected Boolean doInBackground(Void... params);
    }
    
//    protected class ArtistListAdapter extends SimpleCursorAdapter 
//    {
//        private final Drawable       mNowPlayingOverlay;
//        private final BitmapDrawable mDefaultIcon;
//        private final String         mUnknownString;
//        
//        class ViewHolder {
//            TextView line1;
//            TextView line2;
//            TextView duration;
//            ImageView play_indicator;
//            ImageView icon;
//        }
//
//        ArtistListAdapter(int layout, String unknownString, int defaultIconId) {
//            super(BaseMp3TunesListActivity.this, layout, null, null, null);
//            
//            mUnknownString = unknownString;
//
//            int nowPlayingId = R.drawable.indicator_ic_mp_playing_list;
//            mNowPlayingOverlay = getResources().getDrawable(nowPlayingId);
//
//            Bitmap b = BitmapFactory.decodeResource(getResources(), defaultIconId);
//            mDefaultIcon = new BitmapDrawable(b);
//            
//            // no filter or dither, it's a lot faster and we can't tell the difference
//            mDefaultIcon.setFilterBitmap(false);
//            mDefaultIcon.setDither(false);
//        }
//
//        public void setMapping()
//        
//        @Override
//        public View newView(Context context, Cursor cursor, ViewGroup parent) {
//           View v        = super.newView(context, cursor, parent);
//           
//           ViewHolder vh     = new ViewHolder();
//           vh.line1          = (TextView) v.findViewById(R.id.line1);
//           vh.line2          = (TextView) v.findViewById(R.id.line2);
//           vh.duration       = (TextView) v.findViewById(R.id.duration);
//           vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
//           vh.icon           = (ImageView) v.findViewById(R.id.icon);
//           
//           vh.icon.setBackgroundDrawable(mDefaultIcon);
//           vh.icon.setPadding(0, 0, 1, 0);
//           
//           v.setTag(vh);
//           return v;
//        }
//
//        @Override
//        public void bindView(View view, Context context, Cursor cursor) {
//            
//            ViewHolder vh = (ViewHolder)view.getTag();
//
//            String name = cursor.getString(mArtistNameIdx);
//            if (name == null || name.equals(LockerDb.UNKNOWN_STRING)) 
//                name = mUnknownString;
//            
//            vh.line1.setText(name);
//            
//            int numalbums = cursor.getInt(mNumAlbumsIdx);
//            int numsongs = cursor.getInt(mNumAlbumsIdx);
//            if (numalbums > 0)
//                name = Music.makeAlbumsLabel(context, numalbums, numsongs, unknown);
//            else
//                name = "";
//            
//            vh.line2.setText(name);
//
//            ImageView iv = vh.icon;
//            iv.setImageDrawable(null);
//            
//            int currentartistid = Music.getCurrentArtistId();
//            int aid = cursor.getInt(mArtistIdIdx);
//            iv = vh.play_indicator;
//            if (currentartistid == aid) {
//                iv.setImageDrawable(mNowPlayingOverlay);
//            } else {
//                iv.setImageDrawable(null);
//            }
//        }
//        
//        @Override
//        public void changeCursor(Cursor cursor) {
//            if (cursor != mActivity.mArtistCursor) {
//                mActivity.mArtistCursor = cursor;
//                getColumnIndices(cursor);
//                super.changeCursor(cursor);
//            }
//        }
//        
//        
//        public Object[] getSections() {
//            return mIndexer.getSections();
//        }
//        
//        public int getPositionForSection(int section) {
//            return mIndexer.getPositionForSection(section);
//        }
//        
//        public int getSectionForPosition(int position) {
//            return 0;
//        }
//    }
}
