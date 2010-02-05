package com.binaryelysium.mp3tunes.api.results;

import java.util.ArrayList;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Track;


public class SearchResult
{    
    private ArrayList<Artist> mArtists;
    private ArrayList<Album> mAlbums;
    private ArrayList<Track> mTracks;

    public SearchResult()
    {
        mArtists = new ArrayList<Artist>();
        mAlbums  = new ArrayList<Album>();
        mTracks  = new ArrayList<Track>();
    }
    
    public ArrayList<Artist> getArtists()
    {
        return mArtists;
    }
    
    public ArrayList<Album> getAlbums()
    {
        return mAlbums;
    }
    
    public ArrayList<Track> getTracks()
    {
        return mTracks;
    }
    
    public void setArtists(ArrayList<Artist> artists)
    {
        mArtists = artists;
    }
    
    public void setAlbums(ArrayList<Album> albums)
    {
        mAlbums = albums;
    }
    
    public void setTracks(ArrayList<Track> track)
    {
        mTracks = track;
    }
}
