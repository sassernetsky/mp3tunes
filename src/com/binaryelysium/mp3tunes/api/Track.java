package com.binaryelysium.mp3tunes.api;

public interface Track
{

    public abstract String toString();

    public abstract int getId();

    public abstract String getTitle();

    public abstract int getNumber();

    public abstract Double getDuration();

    public abstract String getFileName();

    public abstract String getFileKey();

    public abstract int getFileSize();

    public abstract String getDownloadUrl();

    public abstract String getPlayUrl();

    public abstract int getAlbumId();

    public abstract String getAlbumTitle();

    public abstract String getAlbumYear();

    public abstract int getArtistId();

    public abstract String getArtistName();

    public abstract String getAlbumArt();

    public abstract boolean sameRemoteFile(Track t);

}