package com.mp3tunes.android.player.service;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;

public class CachedTrack implements Track
{
    Track  mTrack;
    String mCachedUrl;
    String mCachedPath;
    String mFormat;
    int    mBitrate;
    int    mStatus;
    Progress mProgress;
    
    public CachedTrack(Track t, String path, String url, String format, int bitrate)
    {
        mTrack      = t;
        mCachedPath = path;
        mCachedUrl  = url;
        mFormat     = format;
        mBitrate    = bitrate;
        mStatus     = Status.created;
        mProgress   = new Progress();
    }
    
    public CachedTrack(LocalId id, Track t)
    {
        mTrack      = t;
        mCachedPath = t.getUrl();
        mCachedUrl  = t.getUrl();
        mFormat     = "";
        mBitrate    = 0;
        mStatus     = Status.finished;
        mProgress   = new Progress();
        mProgress.mProgress = 100;
    }
    
    public int getAlbumId()
    {
        return mTrack.getAlbumId();
    }

    public String getAlbumTitle()
    {
        return mTrack.getAlbumTitle();
    }

    public int getArtistId()
    {
        return mTrack.getArtistId();
    }

    public String getArtistName()
    {
        return mTrack.getArtistName();
    }

    public String getFileKey()
    {
        return mTrack.getFileKey();
    }

    public Id getId()
    {
        return mTrack.getId();
    }

    public String getPlayUrl(String container, int requestedBitrate)
    {
        return mTrack.getPlayUrl(container, requestedBitrate);
    }
    
    public String getPlayUrl()
    {
        return mTrack.getPlayUrl(mFormat, mBitrate);
    }
    
    public String getUrl()
    {
        return mTrack.getUrl();
    }

    public String getTitle()
    {
        return mTrack.getTitle();
    }

    public boolean sameMainMetaData(Track t)
    {
        return mTrack.sameMainMetaData(t);
    }

    public String getName()
    {
        return mTrack.getName();
    }
    
    synchronized public void setCachedUrl(String url)
    {
        mCachedUrl = url;
    }
    
    synchronized public String getCachedUrl()
    {
        return mCachedUrl;
    }
    
    synchronized public void setPath(String path)
    {
        mCachedPath = path;
    }
    
    synchronized public String getPath()
    {
        return mCachedPath;
    }
    
    synchronized public void setStatus(int status)
    {
        mStatus = status;
        if (mStatus == Status.finished)
            mProgress.mProgress = 100;
    }
    
    synchronized public int getStatus()
    {
        return mStatus;
    }
    
    public static final class Status
    {
        public static final int created     = 0;
        public static final int queued      = 1;
        public static final int downloading = 2;
        public static final int finished    = 3;
        public static final int failed      = 4;
    }
    
    class Progress implements HttpClientCaller.Progress
    {
        int  mProgress = 0;
        long mTotal    = 0;
        
        public void run(long progress, long total)
        {
            mTotal = total;
            int p = (int)((progress * 100) / total);
            if (mProgress == p) return;
            mProgress = p;
        }
        
    }

    public int getDownloadPercent()
    {
        return mProgress.mProgress;
    }

    public long getContentLength()
    {
        return mProgress.mTotal;
    }
}
