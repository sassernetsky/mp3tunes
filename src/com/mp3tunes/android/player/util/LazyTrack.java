package com.mp3tunes.android.player.util;

import android.content.Context;

import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;

public class LazyTrack implements Track
{
    private ConcreteTrack mTrack;
    private Id           mTrackId;
    private Context       mContext;
    
    public LazyTrack(Id id, Context context)
    {
        mTrackId = id;
        mContext = context;
    }
    
    public String getName()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getName();
    }
    

    public int getAlbumId()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getAlbumId();
    }

    public String getAlbumTitle()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getAlbumTitle();
    }

    public int getArtistId()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getArtistId();
    }

    public String getArtistName()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getArtistName();
    }
    
    public String getFileKey()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getFileKey();
    }

    public Id getId()
    {   
        if (mTrack != null) return mTrack.getId();
        return mTrackId;
    }

    public String getPlayUrl(int requestedBitrate)
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getPlayUrl(requestedBitrate);
    }

    public String getTitle()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getTitle();
    }
    
    private void createTrack()
    {
        LockerDb db = Music.getDb(mContext);
        MediaStore store = new MediaStore(db, mContext.getContentResolver());
        mTrack = (ConcreteTrack)store.getTrack(mTrackId);
    }

    public boolean sameMainMetaData(Track t)
    {
        if (mTrack == null)  createTrack();

        return mTrack.sameMainMetaData(t);
    }

}
