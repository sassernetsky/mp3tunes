package com.mp3tunes.android.player.service;

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
        }
        return false;
    }
    
    synchronized public boolean playPrevious() 
    {
        try {
            stopIfPlaying(); 
            mTrack = mPlaybackList.getPrevious();
            mTrack.setPreCaching(false);
            mTrack.setTrackFinishedHandler(mCompleteHandler);
            mGuiNotifier.prevTrack(getTrack());
            return mTrack.play();
        } catch (PlaybackListEmptyException e) {
            mGuiNotifier.sendPlaybackError(null, "Tried to play an empty playlist");
        }
        return false;
    }
    
    synchronized public boolean playAt(int position) 
    {
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
        }
            return false;
    }

    synchronized public boolean pause()
    {
        mGuiNotifier.pause(getTrack());
        return mTrack.pause();
    }
    
    synchronized public boolean unpause()
    {
        mGuiNotifier.play(getTrack());
        return mTrack.unpause();
    }
    
    synchronized public boolean stop()
    {
        mGuiNotifier.stop(getTrack());
        
        if (mPlaybackList!= null) mPlaybackList.clear();
        if (mTrack != null)
            return mTrack.stop();
        return false;
    }
    
    synchronized public void setPlaybackList(PlaybackList list)
    {
        mPlaybackList = list;
    }
    
    synchronized public Track getTrack()
    {
        if (mTrack != null)
            return mTrack.getTrack();
        return null;
    }
    
    synchronized public MediaPlayerTrack getMediaPlayerTrack()
    {
        return mTrack;
    }
    synchronized public void destroy()
    {
        // TODO Auto-generated method stub
        
    }

    synchronized public int getQueuePosition()
    {
        return mPlaybackList.getCurrentPosition();
    }
    
    class PlaybackCompleteHandler implements TrackFinishedHandler
    {

        public void trackFailed(MediaPlayerTrack track)
        {
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
            }
        }

        public void trackSucceeded(MediaPlayerTrack track)
        {
            //This is a guard that we may never hit, but there is definitely 
            //something wrong if we ever finish a song successfully while we 
            //are on a phone call.
            if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                Logger.log("Error: Finished a song successfully while on a call");
                mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
            }
            if (!playNext()) mGuiNotifier.sendPlaybackError(track.getTrack(), mTrack.getErrorCode(), mTrack.getErrorValue());
        }
        
    };
    
    private void stopIfPlaying() 
    {
        if (mTrack != null) {
            mTrack.setPreCaching(true);
            if (mTrack.isPlaying()) 
                mTrack.stop();
        }
    }

    public void tooglePlayback()
    {
        if (mTrack.isPaused()) unpause();
        else pause();
    }
    
}
