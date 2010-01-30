package com.mp3tunes.android.player.service;

import android.app.Service;
import android.content.Context;

import com.mp3tunes.android.player.service.MediaPlayerTrack.BufferedCallback;
import com.mp3tunes.android.player.service.MediaPlayerTrack.TrackFinishedHandler;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListEmptyException;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListFinishedException;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListOutOfBounds;

public class PlayerHandler
{
    private PlaybackList     mPlaybackList;
    private MediaPlayerTrack mTrack;
    private GuiNotifier      mGuiNotifier;
    private TrackCacher      mCacher;
    
    private PlaybackCompleteHandler mCompleteHandler = new PlaybackCompleteHandler();
    
    PlayerHandler(Service s, Context c)
    {
        mGuiNotifier  = new GuiNotifier(s, c);
        mCacher       = new TrackCacher();
    }
    
    public boolean playNext() 
    {
        try {
            stopIfPlaying();
            mTrack = mPlaybackList.getNext();
            mCacher.cleanPostCache();
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mTrack.setBufferedCallback(mCacher.getPrecacherCallback(mPlaybackList.getCurrentPosition()));
            mGuiNotifier.nextTrack(mTrack.getTrack());
            mCacher.tryPreCache();
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null);
        } catch (PlaybackListFinishedException e) {
            mGuiNotifier.stop(null);
        }
        return false;
    }
    
    public boolean playPrevious() 
    {
        try {
            stopIfPlaying();
            mCacher.cleanPreCache();
            mTrack = mPlaybackList.getPrevious();
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mTrack.setBufferedCallback(mCacher.getPrecacherCallback(mPlaybackList.getCurrentPosition()));
            mGuiNotifier.prevTrack(mTrack.getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null);
        }
        return false;
    }
    
    public boolean playAt(int position) 
    {
        try {
            stopIfPlaying();
            
            //Here we clean the cache because we just jumped position
            //we do not clear though if we are playing the track
            //TODO: We should not clear if the user is selecting a track
            //that is already in the prefetcher
            if (position != mPlaybackList.getCurrentPosition()) {
                mCacher.cleanPostCache();
                mCacher.cleanPreCache();
            }
            
            mTrack = mPlaybackList.getAt(position);
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mTrack.setBufferedCallback(mCacher.getPrecacherCallback(position));
            mGuiNotifier.play(mTrack.getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null);
        } catch (PlaybackListOutOfBounds e) {
            mGuiNotifier.sendPlaybackError(null);
        }
            return false;
    }

    public boolean pause()
    {
        mGuiNotifier.pause(mTrack.getTrack());
        return mTrack.pause();
    }
    
    public boolean unpause()
    {
        mGuiNotifier.play(mTrack.getTrack());
        return mTrack.unpause();
    }
    
    public boolean stop()
    {
        mGuiNotifier.stop(mTrack.getTrack());
        return mTrack.stop();
    }
    
    public void setPlaybackList(PlaybackList list)
    {
        mPlaybackList = list;
        mCacher.setPlaybackList(list);
    }
    
    public MediaPlayerTrack getTrack()
    {
        return mTrack;
    }
    public void destroy()
    {
        // TODO Auto-generated method stub
        
    }

    public int getQueuePosition()
    {
        return mPlaybackList.getCurrentPosition();
    }
    
    class PlaybackCompleteHandler implements TrackFinishedHandler
    {

        public void trackFailed(MediaPlayerTrack track)
        {
            mGuiNotifier.sendPlaybackError(track.getTrack());
        }

        public void trackSucceeded(MediaPlayerTrack track)
        {
            if (!playNext()) mGuiNotifier.sendPlaybackError(track.getTrack());
        }
        
    };
    
    private void stopIfPlaying() 
    {
        if (mTrack != null)
            if (mTrack.isPlaying()) 
                mTrack.stop();
    }
    
    private void cleanOldTrack()
    {
        if (mTrack != null) mTrack.clean();
    }

    public void tooglePlayback()
    {
        if (mTrack.isPaused()) unpause();
        else pause();
    }
    
    class TrackCacher
    {
        int mPreCache  = 1;
        int mPostCache = 1;
        
        PlaybackList mList;
        
        int mLastIndex = -1;
        
        void cleanPostCache()
        {
            int pos = mList.getCurrentPosition() - mPostCache -1;
            if (pos < 0) return;
            try {
                MediaPlayerTrack t = mList.peekAt(pos);
                Logger.logTrack(pos, mTrack.getTrack());
                t.clean();
            } catch (PlaybackListEmptyException e) {
                e.printStackTrace();
            } catch (PlaybackListOutOfBounds e) {
                e.printStackTrace();
            }

        }
        
        void cleanPreCache()
        {
            int pos = mList.getCurrentPosition();
            if (pos < 0) return;
            try {
                boolean ret = true;
                while (ret) {
                    MediaPlayerTrack t = mList.peekAt(pos);
                    Logger.logTrack(pos, mTrack.getTrack());
                    ret = t.clean();
                }
            } catch (PlaybackListEmptyException e) {
                e.printStackTrace();
            } catch (PlaybackListOutOfBounds e) {
            }
        }

        void tryPreCache()
        {
            try {
                if (mLastIndex == -1) return;
                if (mList.peekAt(mLastIndex).isBuffered()) 
                    tryPreCache(mLastIndex);
            } catch (PlaybackListEmptyException e) {
                e.printStackTrace();
            } catch (PlaybackListOutOfBounds e) {
                e.printStackTrace();
            }
        }
        
        //the parameter here is going to be the track that we just got 
        //done buffering which may or may not be the track that is currently
        //playing
        void tryPreCache(int bufferingPos)
        {
            Logger.log("Trying precache");
            int playbackPos = mList.getCurrentPosition();
            
            //if this is true then we have prefetched enough tracks
            if ((playbackPos + mPreCache - bufferingPos) < 1) return;
            
            try {
                //get the next track to buffer
                int nextTrackPos = bufferingPos + 1;
                mLastIndex       = nextTrackPos;
                MediaPlayerTrack t = mList.peekAt(nextTrackPos);
                Logger.logTrack(nextTrackPos, t.getTrack());
                t.setBufferedCallback(new PrecacherCallback(this, nextTrackPos));
                t.requestPreload();
            } catch (PlaybackListEmptyException e) {
                e.printStackTrace();
            } catch (PlaybackListOutOfBounds e) {
                e.printStackTrace();
            }
        }
        
        void setPlaybackList(PlaybackList list)
        {
            mList = list;
        }
        
        public class PrecacherCallback implements BufferedCallback
        {
            private int mPos;
            private TrackCacher mCacher;
            
            public PrecacherCallback(TrackCacher cacher, int pos)
            {
                mPos    = pos;
                mCacher = cacher;
            }
            
            public void run() 
            {
                mCacher.tryPreCache(mPos);
            }
        }
        public PrecacherCallback getPrecacherCallback(int pos)
        {
            return new PrecacherCallback(this, pos);
        }
        
    }
    
}
