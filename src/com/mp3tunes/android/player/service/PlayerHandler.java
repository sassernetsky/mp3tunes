package com.mp3tunes.android.player.service;

import java.util.Vector;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.telephony.TelephonyManager;

import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.service.MediaPlayerTrack.BufferedCallback;
import com.mp3tunes.android.player.service.MediaPlayerTrack.TrackFinishedHandler;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListEmptyException;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListFinishedException;
import com.mp3tunes.android.player.service.PlaybackList.PlaybackListOutOfBounds;
import com.mp3tunes.android.player.util.Timer;

public class PlayerHandler
{
    private PlaybackList     mPlaybackList;
    private MediaPlayerTrack mTrack;
    private GuiNotifier      mGuiNotifier;
    //private TrackCacher      mCacher;
    
    private Mp3TunesPhoneStateListener mPhoneStateListener;
    private TelephonyManager           mTelephonyManager;
    
    private PlaybackCompleteHandler mCompleteHandler = new PlaybackCompleteHandler();
    
    PlayerHandler(Service s, Context c)
    {
        mGuiNotifier  = new GuiNotifier(s, c);
        
        mPhoneStateListener = new Mp3TunesPhoneStateListener(this);
        ContextWrapper cw = new ContextWrapper(c);
        mTelephonyManager = (TelephonyManager)cw.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, Mp3TunesPhoneStateListener.LISTEN_CALL_STATE);
    }

    synchronized public boolean playNext() 
    {
        Logger.log("playNext entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
            stopIfPlaying();
            mTrack = mPlaybackList.getNext();
            mTrack.setPreCaching(false);
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mGuiNotifier.nextTrack(getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null, "Tried to play an empty playlist");
        } catch (PlaybackListFinishedException e) {
            mGuiNotifier.stop(null);
        } finally {
            Logger.log("playNext left from: " + Long.toString(Thread.currentThread().getId()));
        }
        return false;
    }
    
    synchronized public boolean playPrevious() 
    {
        Logger.log("playPrevious entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
            stopIfPlaying(); 
            mTrack = mPlaybackList.getPrevious();
            mTrack.setPreCaching(false);
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mGuiNotifier.prevTrack(getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null, "Tried to play an empty playlist");
        }finally {
            Logger.log("playPrevious left from: " + Long.toString(Thread.currentThread().getId()));
        }
        return false;
    }
    
    synchronized public boolean playAt(int position) 
    {
        Logger.log("playAt entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
            stopIfPlaying();
            
            mTrack = mPlaybackList.getAt(position);
            mTrack.setPreCaching(false);
            mTrack.setTrackFinishedHandler(mCompleteHandler);

            mGuiNotifier.play(getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null, "Tried to play an empty playlist");
        } catch (PlaybackListOutOfBounds e) {
            mGuiNotifier.sendPlaybackError(null, "Tried to play an index not in the playlist");
        } finally {
        Logger.log("playAt left from: " + Long.toString(Thread.currentThread().getId()));
    }
            return false;
    }

    synchronized public boolean pause()
    {
        Logger.log("pause entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        mGuiNotifier.pause(getTrack());
        return mTrack.pause();
        } finally {
            Logger.log("pause left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public boolean unpause()
    {
        Logger.log("unpause entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        mGuiNotifier.play(getTrack());
        return mTrack.unpause();
        } finally {
            Logger.log("unpause left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public boolean stop()
    {
        Logger.log("stop entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        mGuiNotifier.stop(getTrack());
        
        if (mPlaybackList!= null) mPlaybackList.clear();
        return true;
        } finally {
            Logger.log("stop left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public void setPlaybackList(PlaybackList list)
    {
        Logger.log("setPlaybackList entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        mPlaybackList = list;
        } finally {
            Logger.log("setPlaybackList left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public Track getTrack()
    {
        Logger.log("getTrack entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        if (mTrack != null)
            return mTrack.getTrack();
        return null;
        } finally {
            Logger.log("getTrack left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    synchronized public MediaPlayerTrack getMediaPlayerTrack()
    {
        //Logger.log("getMediaPlayerTrack entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        return mTrack;
        } finally {
            //Logger.log("getMediaPlayerTrack left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    synchronized public void destroy()
    {
        try {
            Logger.log("destroy entered from: " + Long.toString(Thread.currentThread().getId()));
            stopIfPlaying();
            mPlaybackList.clear();
            mPlaybackList = null;
            mGuiNotifier.stop(null);
        } catch (Exception e) {
            Logger.log(e);
        }
    }

    synchronized public int getQueuePosition()
    {
        Logger.log("getQueuePosition entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        return mPlaybackList.getCurrentPosition();
        } finally {
            Logger.log("getQueuePosition left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
    class PlaybackCompleteHandler implements TrackFinishedHandler
    {

        public void trackFailed(MediaPlayerTrack track)
        {
            Logger.log("trackFailed entered from: " + Long.toString(Thread.currentThread().getId()));
            synchronized (PlayerHandler.this) {
                //If we get an error while we are on a call, do not move on to the next track
                if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
                }
            
                if (track == mTrack) {
                    //This condition is for when we get an error during play back 
                    //or trying to prepare the track that we are about to play
                
                    //Generally we want to set errored out for conditions that 
                    //indicate that we are screwed for now as far as play back
                    //is concerned.  An example would be an error that lets us
                    //know that we have no service or our play back failed while
                    //paused for a phone call
                    if (track.erroredOut()) {
                        mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
                    } else {
                        //We do this for conditions that indicate there is something
                        //wrong with the file that we are trying to play.  
                        if (!playNext()) mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
                    }
                } else {
                    //This condition happens when we got an error prefetching
                    //a track not sure what we want to do here
                    Logger.log("Got error from track that is not currently owned by the playback handler");
                }
                Logger.log("trackFailed left from: " + Long.toString(Thread.currentThread().getId()));
            }
        }

        public void trackSucceeded(MediaPlayerTrack track)
        {
            Logger.log("trackSucceeded entered from: " + Long.toString(Thread.currentThread().getId()));
            synchronized (PlayerHandler.this) {
                //This is a guard that we may never hit, but there is definitely 
                //something wrong if we ever finish a song successfully while we 
                //are on a phone call.
                if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    Logger.log("Error: Finished a song successfully while on a call");
                    mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
                }
                if (!playNext()) {
                    //mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
                }
            }
            Logger.log("trackSucceeded left from: " + Long.toString(Thread.currentThread().getId()));
        }
        
    };
    
    private void stopIfPlaying() 
    {
        Logger.log("stopIfPlaying entered from: " + Long.toString(Thread.currentThread().getId()));
        if (mTrack != null) {
            mTrack.setPreCaching(true);
            if (mTrack.isPlaying()) 
                mTrack.stop();
            mTrack = null;
        }
        Logger.log("stopIfPlaying left from: " + Long.toString(Thread.currentThread().getId()));
    }

    public void tooglePlayback()
    {
        Logger.log("tooglePlayback entered from: " + Long.toString(Thread.currentThread().getId()));
        if (mTrack.isPaused()) unpause();
        else pause();
        Logger.log("tooglePlayback left from: " + Long.toString(Thread.currentThread().getId()));
    }

    public Vector<Track> getTracks()
    {
        return  mPlaybackList.getTracks();
    }

    public Track getNextTrack()
    {
        Logger.log("getTrack entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
            if (mPlaybackList != null) {
                MediaPlayerTrack track = mPlaybackList.peekAt(mPlaybackList.getCurrentPosition() + 1);
                if (track != null) return track.getTrack();
            }
        } catch (PlaybackListEmptyException e) {
        } catch (PlaybackListOutOfBounds e) {
        } finally {
            Logger.log("getTrack left from: " + Long.toString(Thread.currentThread().getId()));
        }
        return null;
    }

    public void addToPlaybackList(Vector<MediaPlayerTrack> tracksForList)
    {
        Logger.log("addToPlaybackList entered from: " + Long.toString(Thread.currentThread().getId()));
        try {
        mPlaybackList.add(tracksForList);
        } finally {
            Logger.log("addToPlaybackList left from: " + Long.toString(Thread.currentThread().getId()));
        }
    }
    
}
