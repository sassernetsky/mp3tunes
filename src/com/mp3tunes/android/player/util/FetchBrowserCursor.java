package com.mp3tunes.android.player.util;

import com.mp3tunes.android.player.Music;

import android.app.Activity;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

abstract public class FetchBrowserCursor extends AsyncTask<Void, Void, Boolean>
{
    private   Activity mActivity;
    private   int      mProgressDialogId;
    private   int      mErrorDialogId;  
    protected Cursor   mCursor;

    
    public FetchBrowserCursor(Activity activity, int progressDialogId, int errorDialogId)
    {
        mActivity         = activity;
        mProgressDialogId = progressDialogId;
        mErrorDialogId    = errorDialogId;
    }
    
    @Override
    public void onPreExecute()
    {
        mActivity.showDialog(mProgressDialogId);
        Music.setSpinnerState(mActivity, true);
        Log.w("Mp3Tunes", "Fetching ");
    }
    
    @Override
    public void onPostExecute(Boolean result)
    {
        mActivity.dismissDialog(mProgressDialogId);
        Music.setSpinnerState(mActivity, false);
        
        if (!result) {
            mActivity.showDialog(mErrorDialogId);
            Log.e("Mp3Tunes", "Error displaying the Artists Browser");
            return;
        }
        
        //if(mCursor != null)
        //    PlaylistBrowser.this.init(cursor);
        //else
        //    System.out.println("CURSOR NULL");
    }

    @Override
    abstract protected Boolean doInBackground(Void... params);
    
}
