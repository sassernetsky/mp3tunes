package com.binaryelysium.mp3tunes.api;

public interface Track extends LockerData
{
    
    public abstract String toString();
    public abstract Id     getId();
    public abstract String getTitle();
    public abstract String getFileKey();
    public abstract int    getAlbumId();
    public abstract String getPlayUrl(int requestedBitrate);
    public abstract String getAlbumTitle();
    public abstract int    getArtistId();
    public abstract String getArtistName();
    public abstract boolean sameMainMetaData(Track mTrack);
}
