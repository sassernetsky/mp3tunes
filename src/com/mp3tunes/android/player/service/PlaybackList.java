package com.mp3tunes.android.player.service;

import java.util.Vector;

public class PlaybackList
{
    Vector<MediaPlayerTrack> mList;
    int                      mCurrentPosition;
    
    public PlaybackList(Vector<MediaPlayerTrack> list)
    {
        mList = list;
        mCurrentPosition = -1;
    }
    
    public MediaPlayerTrack getNext() throws PlaybackListEmptyException, PlaybackListFinishedException
    {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > mCurrentPosition + 1) {
            mCurrentPosition++;
            return mList.get(mCurrentPosition);
        }
        throw new PlaybackListFinishedException();
    }
    
    public MediaPlayerTrack getPrevious() throws PlaybackListEmptyException
    {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mCurrentPosition > 0) {
            mCurrentPosition--;
            return mList.get(mCurrentPosition);
        }
        return mList.get(0);
    }
    
    public MediaPlayerTrack getAt(int position) throws PlaybackListEmptyException, PlaybackListOutOfBounds
    {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > position) {
            mCurrentPosition = position;
            return mList.get(mCurrentPosition);
        }
        throw new PlaybackListOutOfBounds();
    }
    
    public MediaPlayerTrack peekAt(int position) throws PlaybackListEmptyException, PlaybackListOutOfBounds
    {
        if (mList.size() < 1) throw new PlaybackListEmptyException();
        if (mList.size() > position) {
            return mList.get(position);
        }
        throw new PlaybackListOutOfBounds();
    }
    
    public int getCurrentPosition()
    {
        return mCurrentPosition;
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

    public void clear()
    {
        for(MediaPlayerTrack track : mList) {
            try {
                track.stop();
            } catch (NullPointerException e) {}
        }
        mList.clear();
    }
    
//    private void getTracksForList(int[] trackIds, Context c)
//    {
//        LockerDb db = new LockerDb(c);
//        for (int id : trackIds) {
//            Track t = db.getTrack(id);
//            
//        }
//    }
}
