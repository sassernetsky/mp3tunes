package com.mp3tunes.android.player.service;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class PlaybackState implements Parcelable
{   
    private int     mState;
    private int     mCurrentProgress;
    private int     mBufferProgress;
    private long    mCurrentTime;
    private long    mTotalTime;
    private long    mRemainingTime;
    private boolean mPaused;
    
    private boolean isLogging = false;
    
    private void log(String func, String message)
    {
        if (isLogging)
            Log.w("Mp3Tunes PlaybackState." + func, message);
    }
    
    private void logState(String func)
    {
        log("writeToParcel", "mState: "            + mState           + 
                             " mCurrentProgress:"  + mCurrentProgress + 
                             " mBufferProgress:"   + mBufferProgress  + 
                             " mCurrentTime:"      + mCurrentTime     + 
                             " mTotalTime:"        + mTotalTime       + 
                             " mRemainingTime:"    + mRemainingTime   + 
                             " mPaused:"           + mPaused);
    }
    
    public static final class State {
        public static final int STARTING       = 0;
        public static final int CHANGING_TRACK = 1;
        public static final int BUFFERING      = 2;
        public static final int PLAYING        = 3;
    };
    
    public int getState()
    {
        return mState;
    }
    
    public int  getCurrentProgress()
    {
        return mCurrentProgress;
    }
    
    public int  getBufferProgress()
    {
        return mBufferProgress;
    }
    
    public long getCurrentTime()
    {
        return mCurrentTime;
    }
    
    public long getTotalTime()
    {
        return mTotalTime;
    }
    
    public long getRemainingTime()
    {
        return mRemainingTime;
    }
    
    public boolean isPaused()
    {
        return mPaused;
    }
    
    public int describeContents()
    {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags)
    {
        logState("writeToParcel");
        out.writeInt(mState);
        out.writeInt(mCurrentProgress);
        out.writeInt(mBufferProgress);
        out.writeLong(mCurrentTime);
        out.writeLong(mTotalTime);    
        out.writeLong(mRemainingTime);
        out.writeInt(mPaused ? 1 : 0);
    }
    
    public void readFromParcel(Parcel in)
    {
        mState           = in.readInt();
        mCurrentProgress = in.readInt();
        mBufferProgress  = in.readInt();
        mCurrentTime     = in.readLong();
        mTotalTime       = in.readLong();
        mRemainingTime   = in.readLong();
        mPaused          = (in.readInt() == 1) ? true : false;
        logState("readFromParcel");
    }

    public static final Parcelable.Creator<PlaybackState> CREATOR
        = new Parcelable.Creator<PlaybackState>() {
        public PlaybackState createFromParcel(Parcel in) {
            return new PlaybackState(in);
        }

        public PlaybackState[] newArray(int size) {
            return new PlaybackState[size];
        }
    };
    
    private PlaybackState(Parcel in) 
    {
        readFromParcel(in);
    }

    public PlaybackState(int state, int playbackProgress, int bufferProgress, long currentTime, long totalTime, long remaining, boolean paused)
    {
        mState           = state;
        mCurrentProgress = playbackProgress;
        mBufferProgress  = bufferProgress;
        mCurrentTime     = currentTime;
        mTotalTime       = totalTime;
        mRemainingTime   = remaining;
        mPaused          = paused;
        logState("PlaybackState");
    }
    
    
}
