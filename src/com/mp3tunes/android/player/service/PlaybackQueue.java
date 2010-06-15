package com.mp3tunes.android.player.service;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Vector;

import android.app.Service;
import android.content.Context;

import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.util.LazyTrack;
import com.mp3tunes.android.player.util.Pair;

class PlaybackQueue
{
    Vector<TrackData>      mQueue;
    int                    mPlaybackPosition;
    int                    mDownloadPosition;
    WeakReference<Context> mContext;
    WeakReference<Service> mService;
    TrackDownloader        mDownloader;
    int                    mForwardCacheSize  = 2;
    int                    mPreviousCacheSize = 1;
    
    PlaybackQueue(Service service, Context context, TrackDownloader downloader)
    {
        mContext    = new WeakReference<Context>(context);
        mService    = new WeakReference<Service>(service);
        mDownloader = downloader;
        setPlaybackQueue(null);
    }
    
    synchronized public void addToPlaybackQueue(IdParcel[] tracks)
    {
        for (IdParcel parcel : tracks) {
            mQueue.add(new TrackData(new LazyTrack(parcel.getId(), mContext.get())));
        }
    }
    
    synchronized public void setPlaybackQueue(IdParcel[] tracks)
    {
        if (tracks == null) tracks = new IdParcel[0];
        mQueue = new Vector<TrackData>();
        for (IdParcel parcel : tracks) {
            mQueue.add(new TrackData(new LazyTrack(parcel.getId(), mContext.get())));
        }
        mPlaybackPosition = 0;
        mDownloadPosition = 0;
    }
    
    synchronized public CachedTrack getPlaybackTrack()
    {
        return mQueue.get(mPlaybackPosition).mCachedTrack;
    }
    
    synchronized public Track peekNextPlaybackTrack()
    {
        if (mQueue.size() <= mPlaybackPosition + 1) return null; 
        return mQueue.get(mPlaybackPosition + 1).mTrack;
    }
    
    synchronized public CachedTrack nextPlaybackTrack()
    {
        mPlaybackPosition++;
        if (mQueue.size() > mPlaybackPosition) {
            mDownloader.setMaxPriority(TrackDownloader.Priority.SKIPPEDTRACK);
            fetchTracks();
            mDownloader.cancelOldDownload(mQueue.get(mPlaybackPosition).mCachedTrack);
            return getPlaybackTrack();
        }
        return null;
    }
    
    synchronized public CachedTrack previousPlaybackTrack()
    {
        mPlaybackPosition--;
        if (mPlaybackPosition < 0) mPlaybackPosition = 0;
            mDownloader.setMaxPriority(TrackDownloader.Priority.SKIPPEDTRACK);
            fetchTracks();
            mDownloader.cancelOldDownload(mQueue.get(mPlaybackPosition).mCachedTrack);
            return getPlaybackTrack();
    }
    
    synchronized public int getBufferPercent()
    {
        try {
            TrackData t = mQueue.get(mPlaybackPosition);
            if (t == null)              return 0;
            if (t.mCachedTrack == null) return 0;
            return t.mCachedTrack.getDownloadPercent();
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }
    
    synchronized public int getPlaybackPosition()
    {
        return mPlaybackPosition;
    }
    
    synchronized public boolean setPlaybackPosition(int pos)
    {
        if (pos < mQueue.size() && pos >= 0) {
            mPlaybackPosition = pos;
            mDownloader.setMaxPriority(TrackDownloader.Priority.SKIPPEDTRACK);
            fetchTracks();
            mDownloader.cancelOldDownload(mQueue.get(pos).mCachedTrack);
            return true;
        }
        return false;
    }
    
    synchronized public CachedTrack getTrackByFileKey(String key)
    {
        for (TrackData data : mQueue) {
            if (data.mCachedTrack != null) {
                String fileKey = data.mCachedTrack.getFileKey();
                if (fileKey.equals(key))
                    return data.mCachedTrack;
                
                fileKey = fileKey.replace("/", "_slash_");
                fileKey = fileKey.replace(".", "_dot_");
                fileKey = fileKey.replace("%", "_percent_");
                if (fileKey.equals(key))
                    return data.mCachedTrack;
            }
        } 
        return null;
    }
       
    private void fetchTracks()
    {
        fetchTrack(mQueue.get(mPlaybackPosition), TrackDownloader.Priority.NOWPLAYING);
        
        try {
            for (int i = mPlaybackPosition + 1; i < mPlaybackPosition + mForwardCacheSize; i++) {
                int priority = TrackDownloader.Priority.FUTURETRACK;
                if (i == (mPlaybackPosition + 1)) priority = TrackDownloader.Priority.NEXTTRACK;
                fetchTrack(mQueue.get(i), priority);
            }
        } catch (ArrayIndexOutOfBoundsException e) {}
        
        try {
            for (int i = mPlaybackPosition - 1; i < mPlaybackPosition - mPreviousCacheSize; i++)
                fetchTrack(mQueue.get(i), TrackDownloader.Priority.FUTURETRACK);
        } catch (ArrayIndexOutOfBoundsException e) {}
    }

    private void fetchTrack(TrackData t, int priority)
    {
        Logger.log("fetchTrack() Setting priority of track: '" + t.mTrack.getTitle() + "' by: '" + t.mTrack.getArtistName() + "'" + " Priority: " + priority);
        //Check to see if this is a local track. If it is then no downloading is needed
        if (LocalId.class.isInstance(t.mTrack.getId())) {
            Logger.log("fetchTrack(): have local track");
            t.mCachedTrack = new CachedTrack((LocalId)t.mTrack.getId(), t.mTrack);
            return;
        }
        
        //FIXME: Hack alert.  For some reason MediaStore is returning tracks with LockerIds
        //when they should have LocalIds.  I do not have time to find this problem in MediaStore
        //at the moment so I am placing a guard here
        if (t.mTrack.getFileKey().startsWith("/sdcard")) {
            t.mCachedTrack = new CachedTrack(new LocalId(0), t.mTrack);
            Logger.log("fetchTrack(): should have local track");
            return;
        }
        
        //if we already have a cached track then we are moving arround in the playlist so we just adjust
        //priorities.  Otherwise we need to tell the downloader to cache the track for us.
        if (t.mCachedTrack != null) { 
            Logger.log("fetchTrack() already have CachedTrack");
            synchronized (t.mCachedTrack) {
                int status = t.mCachedTrack.getStatus();
                Logger.log("Status == " + status);
                if (t.mJobId == null)
                    Logger.log("fetchTrack() JobId null");
                if (status == CachedTrack.Status.finished || status == CachedTrack.Status.failed || t.mJobId == null) {
                    Logger.log("fetchTrack() No need to fetch track");
                    return;
                }
                if (status == CachedTrack.Status.created || status == CachedTrack.Status.queued) {
                    mDownloader.resetPriority(t.mJobId, priority);
                    return; 
                }
                if (status == CachedTrack.Status.downloading) {
                    mDownloader.resetPriority(t.mJobId, priority);
                    return;
                }
                Logger.log("fetchTrack() case not matched");
            }
        } else {
            Logger.log("fetchTrack() must create CachedTrack");
            Pair<Integer, CachedTrack> result = mDownloader.downloadTrack(t.mTrack, priority, "mp3", Bitrate.getBitrate(mService.get(), mContext.get()));
            t.mCachedTrack = result.second;
            t.mJobId        = result.first;
        }
    }

    private class TrackData 
    {
        final Track       mTrack;
        CachedTrack mCachedTrack;
        Integer     mJobId;
        
        TrackData(Track t)
        {
            mTrack         = t;
            mCachedTrack   = null;
            mJobId         = null;
        }
    }

    synchronized public void clear()
    {
        setPlaybackQueue(null);
    }

    synchronized public IdParcel[] getTrackIds()
    {
        if  (mQueue != null && mQueue.size() > 0) {
            IdParcel[] ids = new IdParcel[mQueue.size()];
            for (int i = 0; i < mQueue.size(); i++) {
                ids[i] = new IdParcel(mQueue.get(i).mTrack.getId());
            }
            return ids;
        }
        return new IdParcel[] {};
    }

    synchronized public void cleanFailures()
    {
        for (TrackData data : mQueue) {
            if (data.mCachedTrack != null) {
                CachedTrack t = data.mCachedTrack;
                if (t.getStatus() == CachedTrack.Status.failed) {
                    String path = t.getPath();
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            if (!file.delete()) {
                                Logger.log("Failed to delete old file: " + path);
                            }
                        }
                    }
                    data.mCachedTrack = null;
                }
            }
        }        
    }

    synchronized public int size()
    {
        return mQueue.size();
    }
    
    
}
