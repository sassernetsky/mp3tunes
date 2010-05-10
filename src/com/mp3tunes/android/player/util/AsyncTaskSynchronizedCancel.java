package com.mp3tunes.android.player.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import android.os.AsyncTask;
import android.util.Log;

abstract public class AsyncTaskSynchronizedCancel<Params, Progress, Result> extends AsyncTask<Params, Progress, Result>
{
    private boolean        mCancelled = false;  
    private ReentrantLock  mLock      = new ReentrantLock();
    
    public void cancelSafe()
    {
        Log.w("Mp3tunes", "Cancel locking...");
        mLock.lock();
        Log.w("Mp3tunes", "Cancel locked...");
        try {
            Log.w("Mp3tunes", "Cancel cancelling...");
            mCancelled = true;
            Log.w("Mp3tunes", "Cancel waiting...");
            get();
            Log.w("Mp3tunes", "Cancel other thread should be done...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (CancellationException e) {
            e.printStackTrace();
        } finally {
            Log.w("Mp3tunes", "Cancel unlocking...");
            mLock.unlock();
            Log.w("Mp3tunes", "Cancel unlocked...");
        }
        cleanUp();
    }
    
    protected boolean isSafeCancelled()
    {
        return mCancelled;
    }
    
    protected void lock()
    {
        while (!mLock.tryLock()) {
            if (mCancelled) throw new CancellationException();
        }
    }
    
    protected void unlock()
    {
        mLock.unlock();
    }
    
    
    protected void cleanUp()
    {
        //Do nothing by default
    }

}
