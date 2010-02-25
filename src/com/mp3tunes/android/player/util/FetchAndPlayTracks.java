package com.mp3tunes.android.player.util;

import com.mp3tunes.android.player.LockerDb;
import com.mp3tunes.android.player.Music;

import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

public class FetchAndPlayTracks extends AsyncTask<Void, Void, Boolean>
{
    private Cursor mCursor;
    private int    mIdType;
    private String mId;
    
    
    private BaseMp3TunesListActivity mActivity;
    
    static public class FOR {
        static public final int ARTIST   = 0;
        static public final int ALBUM    = 1;
        static public final int PLAYLIST = 2;
        
    }
    
    public FetchAndPlayTracks(int type, String id, BaseMp3TunesListActivity activity)
    {
        mIdType   = type;
        mId       = id;
        mActivity = activity;
    }
    
    @Override
    public void onPreExecute()
    {
        Music.setSpinnerState(mActivity, true);
    }

    @Override
    public Boolean doInBackground(Void... params)
    {
        try {
            LockerDb db = Music.getDb(mActivity);
            switch (mIdType) {
                case FOR.ARTIST:
                    mCursor = db.getTracksForArtist(Integer.parseInt(mId));
                    break;
                case FOR.ALBUM:
                    mCursor = db.getTracksForAlbum(Integer.parseInt(mId));
                    break;
                case FOR.PLAYLIST:
                    mCursor = db.getTracksForPlaylist(mId);
                    break;
            };
        } catch ( Exception e ) {
            System.out.println("Fetching tracks to play failed");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void onPostExecute( Boolean result )
    {
        Music.setSpinnerState(mActivity, false);
        
        Log.w("Mp3Tunes", "onPostExecute");
        if (!result) 
            return;
        
        Log.w("Mp3Tunes", "Got good result");
        
        if( mCursor != null) {
            Log.w("Mp3Tunes", "Playing tracks from cursor");
            Music.playAll(mActivity, mCursor, 0);
        } else {
            System.out.println("Got no tracks to play");
        }
    }
}
