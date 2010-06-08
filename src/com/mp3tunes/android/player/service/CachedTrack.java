package com.mp3tunes.android.player.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.util.FileUtils;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;

public class CachedTrack implements Track
{
    Track    mTrack;
    String   mCachedUrl;
    String   mCachedPath;
    File     mCachedFile;
    String   mFormat;
    int      mBitrate;
    int      mStatus;
    Progress mProgress;
    String   mError;
    
    public CachedTrack(Track t, String format, int bitrate) throws AlreadyDownloadedException
    {
        mTrack      = t;
        mFormat     = format;
        mBitrate    = bitrate;
        mProgress   = new Progress();
        mError      = "no error";
        try {
            setCachePath();
            setUrlFromCachePath();
        } catch (IOException e) {
            mError      = "Failed to create cached track";
            mStatus     = Status.failed;
        }
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
        mError      = "no error";
    }
    
    private void setUrlFromCachePath()
    {
        mCachedUrl = HttpServer.pathToUrl(mCachedPath);
    }
    
    private String encode(String text)
    {
        text = text.replace("/", "_slash_");
        text = text.replace(".", "_dot_");
        text = text.replace("%", "_percent_");
        return text;
    }
    
    private File createFile(String dir, boolean tmp)
    {
        String name = encode(getFileKey() + "_" + Integer.toString(mBitrate));
        if (tmp) name += "_tmp";
        name += "." + mFormat;
        File file = new File(dir, name);
        return file;
    }
    
    private void setCachePath() throws IOException, AlreadyDownloadedException
    {
        String dir  = Music.getMP3tunesCacheDir();
        File   file = createFile(dir, false);
        if (file.exists()) {
            mCachedPath = file.getAbsolutePath();
            setStatus(Status.finished);
            mProgress.mProgress = 100;
            return;
        }
        
        file = createFile(dir, true);
        if (file.createNewFile()) {
            mCachedPath = file.getAbsolutePath();
            setStatus(Status.created);
            return; 
        }
        
        throw new AlreadyDownloadedException(file.getAbsolutePath());
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
    
    synchronized public void cacheTrack()
    {
        if (mStatus == CachedTrack.Status.finished ) {
            if (mCachedPath.endsWith("_tmp." + mFormat)) {
                String newFile = mCachedPath.replaceAll("_tmp." + mFormat, "." + mFormat);
                try {
                    FileUtils.copyFile(mCachedPath, newFile);
                    mCachedPath = newFile;
                    setUrlFromCachePath();
                } catch (IOException e) {
                    Logger.log(e);
                }
            }
        }
    }
    
    //For now the caller is responsible for making sure that nothing else is reading the file
    synchronized public boolean deleteTmpCopy()
    {
        String fileName = mCachedPath;
        if (!mCachedPath.endsWith("_tmp." + mFormat))
            fileName = mCachedPath.replace("." + mFormat, "_tmp." + mFormat);
        else
            mStatus = Status.failed;
        File file = new File(fileName);
        if (file.exists()) {
            return file.delete();
        }
        return true;
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
            if ((mProgress % 10) == 0)
            Logger.log("Download of: " + CachedTrack.this.getTitle() + " file key: " + CachedTrack.this.getFileKey() + " at: " + mProgress + "%");
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
    
    public void setErrorMessage(String message)
    {
        mError = message;
    }
    
    public String getErrorMessage()
    {
        return mError;
    }
    
    public class AlreadyDownloadedException extends Exception
    {
        private String mPath;
        private static final long serialVersionUID = -7663492585836784742L;
        public AlreadyDownloadedException(String path)
        {
            mPath = path;
        }
        public String getPath()
        {
            return mPath;
        }
    }
}
