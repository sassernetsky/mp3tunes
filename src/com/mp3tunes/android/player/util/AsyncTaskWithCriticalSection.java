package com.mp3tunes.android.player.util;

import android.os.AsyncTask;

abstract public class AsyncTaskWithCriticalSection<Params, Progress, Result> extends AsyncTask<Params, Progress, Result>
{
    private Boolean mInCriticalSection = false;
    
    protected void enterCriticalSection()
    {
        synchronized (mInCriticalSection) {
            mInCriticalSection = true;
        }
    }
    
    protected void leaveCriticalSection()
    {
        synchronized (mInCriticalSection) {
            mInCriticalSection = false;
        }
    }
    
    public void cancelSafe()
    {
        while (true) {
            synchronized (mInCriticalSection) {
                if (!mInCriticalSection) {
                    if (getStatus() == AsyncTask.Status.RUNNING) 
                        cleanUpResources();
                    cancel(true);
                    break;
                }
            }
        }
    }
    
    protected void cleanUpResources()
    {
        //Do nothing by default
    }
    
}
