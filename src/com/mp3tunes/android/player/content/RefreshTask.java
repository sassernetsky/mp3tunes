package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.LockerException;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;
import com.mp3tunes.android.player.util.AsyncTaskSynchronizedCancel;


abstract public class RefreshTask extends AsyncTaskSynchronizedCancel <Void, Void, Boolean>
{
    protected LockerDb mDb;
    protected String   mCacheId;
    protected String   mId;
    protected Boolean  mCancelled = false;
    
    public RefreshTask(LockerDb db)
    {
        mDb      = db;
    }
    
    public RefreshTask(LockerDb db, String cacheId, String id)
    {
        mDb      = db;
        mCacheId = cacheId;
        mId      = id;
    }
    
    public void publish()
    {
        publishProgress();
    }
    
    @Override
    protected Boolean doInBackground(Void... params)
    {
        try {
            Log.w("Mp3Tunes", "Starting RefreshTask with cache id: " + mCacheId + " and id: " + mId);
            return refresh(mCacheId, mId);
        } catch (Exception e) {
            e.printStackTrace();
            }
        return false;
    }
    
    protected boolean refresh(String cacheId, String id)
    {
        try {
            int state = mDb.mCache.getCacheState(cacheId);
            if (state != LockerCache.CacheState.CACHED) {
                if (state == LockerCache.CacheState.UNCACHED)
                    mDb.mCache.beginCaching(cacheId, System.currentTimeMillis());
                LockerCache.Progress p = mDb.mCache.getProgress(cacheId);
                
                //here we are beginning to enter a critical section 
                Log.w("Mp3tunes", "Refresh locking...");
                lock();
                Log.w("Mp3tunes", "Refresh locked...");
                while (dispatch(cacheId, p, id)) {
                    p.mCurrentSet++;
                    Log.w("Mp3tunes", "Refresh unlocking...");
                    unlock();
                    publish();
                    Log.w("Mp3tunes", "Refresh locking...");
                    lock();
                    Log.w("Mp3tunes", "Refresh locked...");
                }
                Log.w("Mp3Tunes", "Finished Caching: " + cacheId);
                mDb.mCache.finishCaching(cacheId);
                Log.w("Mp3tunes", "Refresh unlocking...");
                unlock();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } 
        return false;
    }
    
    protected void cleanUp()
    {
        mDb.mCache.saveCache(mDb);
    }
    
    //classes that override the dispatch can unlock the lock but if they do then they must relock it before they return.
    abstract protected boolean dispatch(String cacheId, LockerCache.Progress p, String id) throws SQLiteException, IOException, LockerException, MakeQueryException;
}
