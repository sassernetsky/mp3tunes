package com.binaryelysium.mp3tunes.api;

public interface Track
{
    
    public abstract String toString();
    public abstract Id     getId();
    public abstract String getTitle();
    public abstract String getFileKey();
    public abstract String getPlayUrl();
    public abstract int    getAlbumId();
    public abstract String getAlbumTitle();
    public abstract int    getArtistId();
    public abstract String getArtistName();

}

