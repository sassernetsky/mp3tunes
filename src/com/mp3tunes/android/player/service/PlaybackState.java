package com.mp3tunes.android.player.service;

import android.os.Parcel;
import android.os.Parcelable;

public class PlaybackState implements Parcelable
{   
    int  mState;
    int  mCurrentProgress;
    int  mBufferProgress;
    long mCurrentTime;
    long mTotalTime;
    long mRemainingTime;
    
    
    
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
    
    public int describeContents()
    {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags)
    {
        out.writeInt(mState);
        out.writeInt(mCurrentProgress);
        out.writeInt(mBufferProgress);
        out.writeLong(mCurrentTime);
        out.writeLong(mTotalTime);    
        out.writeLong(mRemainingTime);
    }
    
    public void readFromParcel(Parcel in)
    {
        mState           = in.readInt();
        mCurrentProgress = in.readInt();
        mBufferProgress  = in.readInt();
        mCurrentTime     = in.readLong();
        mTotalTime       = in.readLong();
        mRemainingTime   = in.readLong();
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

    public PlaybackState(int state, int playbackProgress, int bufferProgress, long currentTime, long totalTime, long remaining)
    {
        mState           = state;
        mCurrentProgress = playbackProgress;
        mBufferProgress  = bufferProgress;
        mCurrentTime     = currentTime;
        mTotalTime       = totalTime;
        mRemainingTime   = remaining;
    }
    
    
}
