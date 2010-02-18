package com.mp3tunes.android.player.service;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.RefreshSessionTask;

import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;

public class MediaPlayerTrack
{
    private MediaPlayer mMp;
    private Track       mTrack;
    private boolean     mIsInitialized;
    private boolean     mErroredOut;
    private boolean     mBuffered;
    private int         mPercent;
    private Service     mService;
    private Context     mContext;
    
    private int mErrorCode;
    private int mErrorValue;
    
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnCompletionListener      mOnCompletionListener;
    private OnErrorListener           mOnErrorListener;
    private OnPreparedListener        mOnPreparedListener;
    private TrackFinishedHandler      mTrackFinishedHandler;
    private BufferedCallback          mBufferedCallback;
    
    public MediaPlayerTrack(Track track, Service service, Context context)
    {
        mMp            = null;
        mTrack         = track;
        mIsInitialized = false;
        mErroredOut    = false;
        mBuffered      = false;
        mPercent       = 0;
        mService       = service;
        mContext       = context;
        mErrorCode     = 0;
        mErrorValue    = 0;
        
        mOnBufferingUpdateListener = new MyOnBufferingUpdateListener();
        mOnCompletionListener      = new MyOnCompletionListener(this);
        mOnErrorListener           = new MyOnErrorListener();
        mOnPreparedListener        = new MyOnPreparedListener();
    }
    
    synchronized public Track getTrack()
    {
        return mTrack;
    }
   
    synchronized public boolean play()
    {
        if (!mIsInitialized) {
          //state preparing
            if (!prepare(true))
                //state error
                return false;
        } else {
            start();
        }
        return true;
    }
    
    synchronized public boolean pause()
    {
        if (mIsInitialized) {
            if (mMp.isPlaying()) {
                //state paused
                mMp.pause();
                return true;
            }
        }
        return false;
    }
    
    synchronized public boolean unpause()
    {
        if (mIsInitialized) {
            if (!mMp.isPlaying()) {
                //state paused
                mMp.start();
                return true;
            }
        }
        return false;
    }
    
    synchronized public boolean stop()
    {
        if (mIsInitialized) {
            //state stopped
            mIsInitialized = false;
            mMp.stop();
            return true;
        }
        return true;
    }
    
    synchronized public boolean isPlaying() 
    {
        if (mIsInitialized) 
            return mMp.isPlaying();
        return false;
    }
    
    synchronized public boolean isBuffered()
    {
        return mBuffered;
    }
    
    synchronized public int getBufferPercent()
    {
        return mPercent;
    }
    
    synchronized public boolean requestPreload()
    {   
        return prepare(false);
    }
    
    synchronized private boolean start()
    {
        if (mIsInitialized) {
            mMp.start();
            return true;
        }
        return false;
    }
    
    synchronized private boolean prepare(boolean async)
    {
        try {
            String url;
            if (AddTrackToMediaStore.isInStore(mTrack, mContext)) {
                url = AddTrackToMediaStore.getTrackUrl(mTrack, mContext);
                mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
            } else {
                try {
                    RemoteMethod method = new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_PLAY)
                        .addFileKey(mTrack.getFileKey())
                        .addParam("fileformat", "mp3")
                        .addParam("bitrate", Integer.toString(Bitrate.getBitrate(mService, mContext)))
                        .create();
                    url = method.getCall();
                } catch (InvalidSessionException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        
            //State Idle
            mMp = new MediaPlayer();
            mMp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
            setListeners();
        
            //State Initialized
            Logger.log("playing: " + url);
            mMp.setDataSource(url);
            
        
            if (async) {
                //State preparing
                mMp.setOnPreparedListener(mOnPreparedListener);
                mMp.prepareAsync();
            } else {
                //State prepared
                mMp.prepare();
                Logger.log("MediaPlayer prepared");
                mIsInitialized = true;
            }
           
            //make sure volume is up
            mMp.setVolume(1.0f, 1.0f);
            
            return true;
        } catch (IllegalStateException e) {
            mErroredOut = true;
            e.printStackTrace();
        } catch (IOException e) {
            mErroredOut = true;
            e.printStackTrace();
        }
        mIsInitialized = false;
        return false;
    }
    
    synchronized private void setListeners()
    {
        mMp.setOnCompletionListener(mOnCompletionListener);
        mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mMp.setOnErrorListener(mOnErrorListener);
    }
    
    private class MyOnCompletionListener implements MediaPlayer.OnCompletionListener 
    {    
        private MediaPlayerTrack mMediaPlayerTrack;
        
        MyOnCompletionListener(MediaPlayerTrack track)
        {
            mMediaPlayerTrack = track;
        }
        
        synchronized public void onCompletion(MediaPlayer mp)
        {
            //If we are still in the initialized state then that means that we
            //completed our play back without an error
            Logger.log("MediaPlayer completed track");
            if (mIsInitialized) {
                mTrackFinishedHandler.trackSucceeded(mMediaPlayerTrack);
            } else {
                mTrackFinishedHandler.trackFailed(mMediaPlayerTrack);
            }
        }
    };
    
    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener
    {

        synchronized public void onPrepared(MediaPlayer mp)
        {
            Logger.log("MediaPlayer prepared async");
            //state prepared
            mIsInitialized = true;
            start();
        }
        
    };
    
    private class MyOnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
    {
        long mOldPosition   = 0;
        int  mCount         = 0;
        boolean mBuffering = true;
        synchronized public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            try {
                if ((percent % 25) == 0) Logger.log("Buffering: " + percent);
                checkForEndlessBuffering(percent);
                mPercent = percent;
                tryRunBufferedCallback(percent);
            } catch (Exception e) {
                //No error class this should be fatal
                Logger.log(e);
            }
        }
        
        public void runBufferedCallback()
        {
            mBuffered = true;
            if (mBufferedCallback != null) {
                mBufferedCallback.run();
            }
        }

        private void tryRunBufferedCallback(int percent)
        {
            if (mBuffered) return;
            if (percent == 100) {
                Logger.log("Buffering Got to 100% before buffered condition was met");
                runBufferedCallback();
            }
            long duration = getDuration();            
            if (duration <= 0) return;
            
            long bufferingPos = (duration * percent) / 100;
            long pos = getPosition();
            
            //try to start buffering the next song once we get one minute
            //ahead of our play back position
            if ((bufferingPos - pos) > 60000) {
                Logger.log("Buffering condition was met");
                runBufferedCallback();
            }
        }
        
        private void checkForEndlessBuffering(int percent)
        {
            mBuffering = setBuffering(mPercent, percent);
            if (mBuffering) {
                mCount++;
                Logger.log("buffering count set to: " + Integer.toString(mCount));
                if (mCount > 15 && !mMp.isPlaying()) {
                    Logger.log("Looks like we lost our network connection and the MediaPlayer does not realize it");
                    MediaPlayerTrack.this.stop();
                    mErroredOut = true;
                    mIsInitialized = false;
                    MediaPlayerTrack.this.mOnCompletionListener.onCompletion(mMp);
                }
            } else {
                mCount = 0;
            }
        }
        
        //This function tries to determine if we have stopped playback to buffer.
        //It is not even close to perfect, but hopefully it will give us some good
        //to help make a decision as to whether we are stuck.
        private boolean setBuffering(int oldPercent, int newPercent)
        {
            if (newPercent == 100) return false;
            long pos = MediaPlayerTrack.this.getPosition();
            if (oldPercent == newPercent) {
                Logger.log("buffering stalled");
                if (mOldPosition == pos) {
                    Logger.log("playback stalled while buffering");
                    if (positionCloseToPercent()) {
                        return true;
                    }
                }        
            }
            mOldPosition = pos;
            return false;
        }
        
        //It would be best if the player could tell us if we have reached
        //the end of the buffered content but it looks like we have to do this
        private boolean positionCloseToPercent()
        {
            long duration = MediaPlayerTrack.this.getDuration();
            if (duration == 0) return false;
            long playbackPercent = (mOldPosition * 100) / duration;
            if (Math.abs(mPercent - playbackPercent) <= 3) return true;
            return false;
        }
    };
    
    private class MyOnErrorListener implements MediaPlayer.OnErrorListener
    {
        synchronized public boolean onError(MediaPlayer mp, int what, int extra)
        {
            Logger.log("MediaPlayer got error");
            //State error
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                if (handleUnknownErrors(extra)) return true;
            }
            
            mIsInitialized = false;
            mErroredOut    = true;
            mErrorCode     = what;
            mErrorValue    = extra;
            
            //state idle
            mMp.reset();
            //returning false will call OnCompletionHandler
            return false;
        }
        
        private boolean handleUnknownErrors(int extra)
        {
            mErroredOut = PlaybackErrorCodes.isFatalError(extra);
            
            //Error 26 is an authentication error it most likely means that the session
            //for the user has expired.  Here we will want to try to refresh the session
            //and try the song again.
            if (extra == -26 && !mIsInitialized) {
                RefreshSessionTask task = new RefreshSessionTask(mContext);
                if (task.doInForground()) {
                    if (prepare(false)) {
                        if (play())
                            return true;
                    }
                }
            } else if (extra == -1) {
                Locker l = new Locker();
                if (!l.testSession()) {
                    Logger.log("Session likely invalid trying to refresh and play again");
                    RefreshSessionTask task = new RefreshSessionTask(mContext);
                    if (task.doInForground()) {
                        if (prepare(false)) {
                            if (play())
                                return true;
                        }
                    }
                }
            }
            return false;
        }
    };
    
    public interface TrackFinishedHandler
    {
        public void trackSucceeded(MediaPlayerTrack track);
        public void trackFailed(MediaPlayerTrack track);
    }
    
    public interface BufferedCallback
    {
        void run();
    }

    synchronized public long getDuration()
    {
        if (mIsInitialized)
            return mMp.getDuration();
        return 0;
    }

    synchronized public long getPosition()
    {
        if (mIsInitialized)
            return mMp.getCurrentPosition();
        return 0;
    }

    synchronized public boolean isPaused()
    {
        return mIsInitialized && !isPlaying();
    }
    
    synchronized public void setTrackFinishedHandler(TrackFinishedHandler handler)
    {
        mTrackFinishedHandler = handler;
    }
    
    synchronized public void setBufferedCallback(BufferedCallback callback)
    {
        mBufferedCallback = callback;
    }
        
    synchronized public boolean clean()
    {
        boolean ret = false;
        if (mMp != null) {
            Logger.log("MediaPlayer marked for GC");
            mMp = null;
            ret = true;
        }
        mIsInitialized = false;
        mPercent       = 0;
        return ret;
    }

    public void setVolume(float leftVolume, float rightVolume)
    {
        if (mIsInitialized)
            mMp.setVolume(leftVolume, rightVolume);
    }
    
    public boolean erroredOut()
    {
        return mErroredOut;
    }

    public int getErrorValue()
    {
        return mErrorValue;
    }

    public int getErrorCode()
    {
        return mErrorCode;
    }
}
