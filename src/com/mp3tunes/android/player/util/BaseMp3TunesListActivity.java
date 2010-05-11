package com.mp3tunes.android.player.util;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.content.RefreshTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BaseMp3TunesListActivity extends LifetimeLoggingListActivity
{
    protected final int PROGRESS_DIALOG = 0;
    protected final int ERROR_DIALOG    = 1;
    protected final int REFRESH         = 0;
    
    private ProgressDialog mProgressDialog;
    private AlertDialog    mErrorDialog;
    private ReturnToLockerListAction mReturner = new ReturnToLockerListAction(this);
    
    protected FetchBrowserCursor mFetchBrowserCursorTask; 
    protected RefreshTask mCursorTask;
    protected RefreshTask mTracksTask;
    protected Cursor mCursor;
    protected boolean mLoadingCursor;
    
    @Override
    public void onStop()
    {
        mHandler.removeMessages(REFRESH);
        mHandler.removeCallbacks(mUpdateList);
        
        super.onStop();
    }
    
    public void init(Cursor cursor)
    {
        
    }
    
    public void init(Cursor cursor, int refreshNext)
    {
        queueNextRefresh(refreshNext);
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
    
    protected void updateCursor() {}
    
    private void queueNextRefresh(long delay) 
    {
        if (mLoadingCursor) {
            forceQueueNextRefresh(delay);
        } else {
            Music.setSpinnerState(this, false);
        }
    }
    
    protected void forceQueueNextRefresh(long delay)
    {
        Message msg = mHandler.obtainMessage(REFRESH);
        mHandler.removeMessages(REFRESH);
        mHandler.sendMessageDelayed(msg, delay);
    }

    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            if (msg.what == REFRESH) {
                Thread t = new Thread() {
                    public void run() {
                        Log.w("Mp3Tunes", "Thread refreshing");
                        updateCursor();
                        mHandler.post(mUpdateList);
                    }
                };
                t.start();
            }
        }
    };

    Runnable mUpdateList = new Runnable() {
        public void run()
        {
            init(mCursor, 2000);
        }
    };
    
    protected void tryDismissProgress(boolean showing, Cursor c) 
    {
        if (showing) {
            if (c != null && c.getCount() > 0) {
                dismissDialog(PROGRESS_DIALOG);
            }
            if (mCursorTask != null && mCursorTask.getStatus() == AsyncTask.Status.FINISHED) {
                dismissDialog(PROGRESS_DIALOG);
            }
        }
    }
    
    
    abstract protected class FetchBrowserCursor extends AsyncTask<Void, Void, Boolean>
    { 
        private   String   mActivityName;
        protected Cursor   mCursor;
        private   boolean  mShowDialog;
        
        public FetchBrowserCursor()
        {
            mShowDialog = true;
            mActivityName = BaseMp3TunesListActivity.this.getClass().getSimpleName();
        }
        
        public FetchBrowserCursor(boolean showLoading)
        {
            mShowDialog = showLoading;
            mActivityName = BaseMp3TunesListActivity.this.getClass().getSimpleName();
        }
        
        @Override
        public void onPreExecute()
        {
            if (mShowDialog) BaseMp3TunesListActivity.this.showDialog(PROGRESS_DIALOG);
            Music.setSpinnerState(BaseMp3TunesListActivity.this, true);
            Log.w("Mp3Tunes", "Fetching for: " + mActivityName);
        }
        
        @Override
        public void onPostExecute(Boolean result)
        {
            if (mShowDialog) BaseMp3TunesListActivity.this.dismissDialog(PROGRESS_DIALOG);
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
        public void onProgressUpdate(Void... values)
        {
            
        }

        @Override
        abstract protected Boolean doInBackground(Void... params);
    }
}
