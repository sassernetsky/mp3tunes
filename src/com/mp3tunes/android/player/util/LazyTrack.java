package com.mp3tunes.android.player.util;

import android.content.Context;

import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LockerDb;

public class LazyTrack implements Track
{
    private ConcreteTrack mTrack;
    private int           mTrackId;
    private Context       mContext;
    
    public LazyTrack(int id, Context context)
    {
        mTrackId = id;
        mContext = context;
    }
    
//    public String getAlbumArt()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getAlbumArt();
//    }

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

//    public String getAlbumYear()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getAlbumYear();
//    }

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

//    public String getDownloadUrl()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getDownloadUrl();
//    }

//    public Double getDuration()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getDuration();
//    }

    public String getFileKey()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getFileKey();
    }

//    public String getFileName()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getFileName();
//    }

//    public int getFileSize()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getFileSize();
//    }

    public int getId()
    {   
        return mTrackId;
    }

//    public int getNumber()
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.getNumber();
//    }

    public String getPlayUrl()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getPlayUrl();
    }

    public String getTitle()
    {
        if (mTrack == null)  createTrack();
        
        return mTrack.getTitle();
    }

//    public boolean sameRemoteFile(Track t)
//    {
//        if (mTrack == null)  createTrack();
//        
//        return mTrack.sameRemoteFile(t);
//    }
    
    private void createTrack()
    {
        LockerDb db = new LockerDb(mContext);
        mTrack = (ConcreteTrack)db.getTrack(mTrackId);
        db.close();
    }

}
