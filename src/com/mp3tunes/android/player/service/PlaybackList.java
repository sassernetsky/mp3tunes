package com.mp3tunes.android.player.service;

import java.util.Vector;

import com.binaryelysium.mp3tunes.api.Track;

public class PlaybackList
{
    Vector<MediaPlayerTrack> mList;
    int                      mCurrentPosition;
    private TrackCacher      mCacher;
    
    public PlaybackList(Vector<MediaPlayerTrack> list)
    {
        mList = list;
        mCurrentPosition = -1;
        mCacher       = new TrackCacher(this);
    }
    
    synchronized public MediaPlayerTrack getNext() throws PlaybackListEmptyException, PlaybackListFinishedException
    {
        Logger.log("PlaybackList.getNext entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > mCurrentPosition + 1) {
            mCurrentPosition++;
            MediaPlayerTrack t = mList.get(mCurrentPosition);
            mCacher.cleanPostCache();
            t.setBufferedCallback(mCacher.getPrecacherCallback(mCurrentPosition));
            mCacher.tryPreCache();
            return t;
        }
        throw new PlaybackListFinishedException();
        } finally {
            Logger.log("PlaybackListgetNext. left from: " + Long.toString(Thread.currentThread().getId()));
        }
        
    }
    
    synchronized public MediaPlayerTrack getPrevious() throws PlaybackListEmptyException
    {
        Logger.log("PlaybackList.getPrevious entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mCurrentPosition > 0) {
            mCurrentPosition--;
            MediaPlayerTrack t = mList.get(mCurrentPosition);
            mCacher.cleanPreCache();
            t.setBufferedCallback(mCacher.getPrecacherCallback(mCurrentPosition));
            return t;
        }
        return mList.get(0);
        } finally {
            Logger.log("PlaybackList.getPrevious left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public MediaPlayerTrack getAt(int position) throws PlaybackListEmptyException, PlaybackListOutOfBounds
    {
        Logger.log("PlaybackList.getAt entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > position) {
            mCurrentPosition = position;
            
            //Here we clean the cache because we just jumped position
            //we do not clear though if we are playing the track
            //TODO: We should not clear if the user is selecting a track
            //that is already in the prefetcher
            if (position != mCurrentPosition) {
                mCacher.cleanPostCache();
                mCacher.cleanPreCache();
            }
            MediaPlayerTrack t = mList.get(mCurrentPosition);
            t.setBufferedCallback(mCacher.getPrecacherCallback(mCurrentPosition));
            return t;
        }
        throw new PlaybackListOutOfBounds();
        } finally {
            Logger.log("PlaybackList.getAt left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public MediaPlayerTrack peekAt(int position) throws PlaybackListEmptyException, PlaybackListOutOfBounds
    {
        Logger.log("PlaybackList.peekAt entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > position) {
            return mList.get(position);
        }
        throw new PlaybackListOutOfBounds();
        } finally {
            Logger.log("PlaybackList.peekAt left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public int getCurrentPosition()
    {
        Logger.log("PlaybackList.getCurrentPosition entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        return mCurrentPosition;
        } finally {
            Logger.log("PlaybackList.getCurrentPosition left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    public class PlaybackListEmptyException extends Exception
    {
        private static final long serialVersionUID = -7907875229527554730L;   
    }
    
    public class PlaybackListFinishedException extends Exception
    {
        private static final long serialVersionUID = 418363165930331395L;
    }
    
    public class PlaybackListOutOfBounds extends Exception
    {
        private static final long serialVersionUID = 3760111211345101285L;
    }

    synchronized public void clear()
    {
        Logger.log("PlaybackList.clear entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        for(MediaPlayerTrack track : mList) {
            try {
                track.stop();
                track.init();
            } catch (NullPointerException e) {}
        }
        mList.clear();
        } finally {
            Logger.log("PlaybackList.clear left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }

    synchronized public Vector<Track> getTracks()
    {
        Vector<Track> tracks = new Vector<Track>();
        for (MediaPlayerTrack track : mList) {
            tracks.add(track.getTrack());
        }
        return tracks;
    }
}
