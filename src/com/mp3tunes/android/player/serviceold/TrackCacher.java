package com.mp3tunes.android.player.serviceold;

import com.mp3tunes.android.player.service.Logger;

import com.mp3tunes.android.player.util.Timer;

class TrackCacher
{
//    int mPreCache  = 1;
//    int mPostCache = 0;
//    
//    PlaybackList mList;
//    
//    int mLastIndex = -1;
//    
//    void cleanPostCache()
//    {
//        try {
//            int pos = mList.getCurrentPosition() - mPostCache -1;
//            if (pos < 0) return;
//            MediaPlayerTrack t = mList.peekAt(pos);
//            t.clean();
//        } catch (PlaybackListEmptyException e) {
//            e.printStackTrace();
//        } catch (PlaybackListOutOfBounds e) {
//            e.printStackTrace();
//        } catch (NullPointerException e) {}
//
//    }
//    
//    void cleanPreCache()
//    {
//        try {
//            int pos = mList.getCurrentPosition();
//            if (pos < 0) return;
//            boolean ret = true;
//            while (ret) {
//                MediaPlayerTrack t = mList.peekAt(pos);
//                ret = t.clean();
//            }
//        } catch (PlaybackListEmptyException e) {
//            e.printStackTrace();
//        } catch (PlaybackListOutOfBounds e) {
//        } catch (NullPointerException e) {}
//    }
//
//    void tryPreCache()
//    {
//        Timer timings = new Timer("tryPrecache");
//        Logger.log("Trying precache");
//        try {
//            if (mLastIndex == -1) {
//                Logger.log("Last index not set");
//                return;
//            }
//            Logger.log("Last index set: " + Integer.toString(mLastIndex));
//            if (mList.peekAt(mLastIndex).isBuffered()) 
//                tryPreCache(mLastIndex);
//        } catch (PlaybackListEmptyException e) {
//            e.printStackTrace();
//        } catch (PlaybackListOutOfBounds e) {
//            e.printStackTrace();
//        } catch (NullPointerException e) {
//        } finally {
//            timings.push();
//        }
//    }
//    
//    //the parameter here is going to be the track that we just got 
//    //done buffering which may or may not be the track that is currently
//    //playing
//    void tryPreCache(int bufferingPos)
//    {
//        Timer timings = new Timer("tryPrecache pos");
//        try {
//            Logger.log("Trying precache at: " + Integer.toString(bufferingPos));
//            int playbackPos = mList.getCurrentPosition();
//        
//            if (bufferingPos < playbackPos) return;
//        
//            //if this is true then we have prefetched enough tracks
//            if ((playbackPos + mPreCache - bufferingPos) < 1) return;
//        
//            //get the next track to buffer
//            int nextTrackPos = bufferingPos + 1;
//            mLastIndex       = nextTrackPos;
//            MediaPlayerTrack t = mList.peekAt(nextTrackPos);
//            Logger.logTrack(nextTrackPos, t.getTrack());
//            t.setBufferedCallback(new PrecacherCallback(this, nextTrackPos));
//            
//            Timer timings2 = new Timer("request preload");
//            t.requestPreload();
//            timings2.push();
//        } catch (PlaybackListEmptyException e) {
//            e.printStackTrace();
//        } catch (PlaybackListOutOfBounds e) {
//            e.printStackTrace();
//        } catch (NullPointerException e) {}
//        timings.push();
//    }
//    
//    TrackCacher(PlaybackList list)
//    {
//        mList = list;
//    }
//    
//    public class PrecacherCallback implements BufferedCallback
//    {
//        private int mPos;
//        private TrackCacher mCacher;
//        
//        public PrecacherCallback(TrackCacher cacher, int pos)
//        {
//            mPos    = pos;
//            mCacher = cacher;
//        }
//        
//        public void run() 
//        {
//            Logger.log("Trying to precache at: " + Integer.toString(mPos));
//            mCacher.tryPreCache(mPos);
//        }
//    }
//    public PrecacherCallback getPrecacherCallback(int pos)
//    {
//        return new PrecacherCallback(this, pos);
//    }
//    
}