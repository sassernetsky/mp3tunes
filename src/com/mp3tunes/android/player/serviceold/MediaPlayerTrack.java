package com.mp3tunes.android.player.serviceold;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerId;
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
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;

//It is a logic error to request the mState lock while holding the mPreCaching lock

public class MediaPlayerTrack
{
    private final Track       mTrack;
    
    private MediaPlayer mMp;
    private Integer     mState;
    private boolean     mBuffered;
    private Boolean     mPreCaching;
    private Integer     mPercent;
    private Service     mService;
    private Context     mContext;
    
    private int mErrorCode;
    private int mErrorValue;
    
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnCompletionListener      mOnCompletionListener;
    private OnErrorListener           mOnErrorListener;
    private OnInfoListener            mOnInfoListener;
    private OnPreparedListener        mOnPreparedListener;
    private TrackFinishedHandler      mTrackFinishedHandler;
    private BufferedCallback          mBufferedCallback;
    
    private class STATE {
        static final int CREATED     = -1;
        static final int PREPARING   = 0;
        static final int INITIALIZED = 1;
        static final int ERRORED     = 2;
        static final int DONE        = 3;
    }
    
    public MediaPlayerTrack(Track track, Service service, Context context)
    {
        mTrack      = track;
        mService    = service;
        mContext    = context;
        mState      = STATE.CREATED;
        mPreCaching = true;
        init();
    }
    
    synchronized void init()
    {
        synchronized (mState) {
            synchronized (mPreCaching) {
                mState         = STATE.CREATED;
                mMp            = null;
                mBuffered      = false;
                mPercent       = 0;
                mErrorCode     = 0;
                mErrorValue    = 0;
                mPreCaching    = true;
        
                mOnBufferingUpdateListener = new MyOnBufferingUpdateListener();
                mOnCompletionListener      = new MyOnCompletionListener(this);
                mOnErrorListener           = new MyOnErrorListener();
                mOnPreparedListener        = new MyOnPreparedListener();
                mOnInfoListener            = new MyOnInfoListener();
            }
        }
    }
    
    public Track getTrack()
    {
        return mTrack;
    }
    
    public void setPreCaching(boolean b)
    {
        synchronized (mPreCaching) {
            mPreCaching = b;
        }
    }
   
    public boolean play()
    {
        synchronized (mState) {
            if (mState == STATE.PREPARING) {
                
                //If we are already preparing when we call play then that means
                //we started a precache of this track and then called play on it
                //before the onPrepared handler was called.  In this case we want 
                //to set precaching to false so that the onPrepared handler will start
                //playing the track when in gets called
                synchronized (mPreCaching) {
                    mPreCaching = false;
                    return true;
                }
            } else if (mState == STATE.INITIALIZED) {
                
                //This state is when the track has been precached and is ready for
                //immediate playback.
                mMp.start();
                return true;
            } else {
                
                //This is when the track has not been precached play will be called when this 
                //track is prepared
                if (!prepare(true)) {
                    mState = STATE.ERRORED;
                    return false;
                }
                return true;
            }
        }
    }
    
    public boolean pause()
    {
        //It does not make sense to call pause unless the player is playing
        synchronized (mState) {
            synchronized (mPreCaching) {
                if (mState == STATE.INITIALIZED && !mPreCaching && mMp.isPlaying()) {
                    mMp.pause();
                    return true;
                }
                return false;
            }
        }
    }
    
    public boolean unpause()
    {
        //It does not make sense to call unpause unless the player is playing initialized
        //and not precaching.  Calling unpause while precaching could lead to playing more
        //than one track at a time.
        synchronized (mState) {
            synchronized (mPreCaching) {
                if (mState == STATE.INITIALIZED && !mPreCaching && !mMp.isPlaying()) {
                    mMp.start();
                    return true;
                }
                return false;
            }
        }
    }
    
    public boolean stop()
    {
        synchronized (mState) {
            //Mainly we need to make sure that we do not call stop while the Media player
            //is preparing
            if (mState == STATE.INITIALIZED || mState == STATE.DONE) {
                mState = STATE.DONE;
                mMp.stop();
                return true;
            }
            return false;
        }
    }
    
    public boolean isPlaying() 
    {
        synchronized (mState) {
        if (mState == STATE.INITIALIZED) 
                return mMp.isPlaying();
            return false;
        }
    }
    
    public boolean isBuffered()
    {
        return mBuffered;
    }
    
    public int getBufferPercent()
    {
        return mPercent;
    }
    
    public boolean requestPreload()
    {
        synchronized (mState) {
            return prepare(true);
        }
    }
    
    //This function must only be called by functions that hold both the mState lock and the mPreCaching locks
    private boolean prepare(boolean async)
    {
        Logger.log("preparing track: " + mTrack.getTitle());
        Logger.log("Artist:          " + mTrack.getArtistName());
        Logger.log("Album:           " + mTrack.getArtistName());
        
        try {
            String url = mTrack.getPlayUrl("mp3", Bitrate.getBitrate(mService, mContext));
            if (LockerId.class.isInstance(mTrack.getId())) {
                Logger.log("checking local store");
                if (AddTrackToMediaStore.isInStore(mTrack, mContext)) {
                    mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
                    url = AddTrackToMediaStore.getTrackUrl(mTrack, mContext);
                }
            } else {
                mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
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
                mState = STATE.PREPARING;
                mMp.setOnPreparedListener(mOnPreparedListener);
                mMp.prepareAsync();
            } else {
                //State prepared
                mMp.prepare();
                Logger.log("MediaPlayer prepared");
                mState = STATE.INITIALIZED;
            }
           
            //make sure volume is up
            mMp.setVolume(1.0f, 1.0f);
            
            return true;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mState = STATE.ERRORED;
        return false;
    }
    
    private void setListeners()
    {
        mMp.setOnCompletionListener(mOnCompletionListener);
        mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mMp.setOnErrorListener(mOnErrorListener);
        mMp.setOnInfoListener(mOnInfoListener);
    }
    
    private class MyOnCompletionListener implements MediaPlayer.OnCompletionListener 
    {    
        private MediaPlayerTrack mMediaPlayerTrack;
        
        MyOnCompletionListener(MediaPlayerTrack track)
        {
            mMediaPlayerTrack = track;
        }
        
        public void onCompletion(MediaPlayer mp)
        {
            //If we are still in the initialized state then that means that we
            //completed our play back without an error
            Logger.log("MediaPlayer completed track");
            synchronized (mState) {
                if (mTrackFinishedHandler == null) {
                    Logger.log("MediaPlayer in onCompletionHandler without a finished handler.  What do we do?");
                    return;
                }
                
                if (mState == STATE.INITIALIZED) {
                    mTrackFinishedHandler.trackSucceeded(mMediaPlayerTrack);
                } else {
                    mTrackFinishedHandler.trackFailed(mMediaPlayerTrack);
                }
            }
        }
    };
   
    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener
    {
        public void onPrepared(MediaPlayer mp)
        {
            //If this track is precaching the we do not start it we just
            //move it to the initialized state.
            synchronized (mState) {
                synchronized (mPreCaching) {
                        mState = STATE.INITIALIZED;
                        if (!mPreCaching) {
                            mMp.start();
                        }
                }

            }   
        }
    }
    
    private class MyOnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
    {
        long mOldPosition   = 0;
        int  mCount         = 0;
        boolean mBuffering = true;
        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            synchronized (mPercent) {
                try {
                    if ((percent % 10) == 0) Logger.log(mTrack.getTitle() + ": Buffering: " + percent);
                    checkForEndlessBuffering(percent);
                    mPercent = percent;
                    tryRunBufferedCallback(percent);
                } catch (Exception e) {
                    //No error class this should be fatal
                    Logger.log(e);
                }
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
                    mState = STATE.ERRORED;
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
                if (mOldPosition == pos) {
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
        private boolean refreshAndTryAgain()
        {
            Logger.log("Trying to refresh and play again");
            RefreshSessionTask task = new RefreshSessionTask(mContext);
            if (task.doInForground()) {
                if (prepare(false)) {
                    if (play())
                        return true;
                }
            }
            return false;
        }
        
        private boolean handleUnknownErrors(int extra)
        {
            if (mPreCaching) return false; 
            if (PlaybackErrorCodes.isFatalError(extra)) 
                mState = STATE.ERRORED;
            
            //Error 26 is an authentication error it most likely means that the session
            //for the user has expired.  Here we will want to try to refresh the session
            //and try the song again.
            if (extra == -26 && mState == STATE.PREPARING) {
                return refreshAndTryAgain();
            } else if (extra == -11){ 
                mState = STATE.ERRORED;
                return false;
            } else if (extra == -1) {
                Locker l = new Locker();
                if (!l.testSession()) {
                    return refreshAndTryAgain();
                }
            }
            return false;
        }
        
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
            Logger.log("MediaPlayer got error name: " + mTrack.getTitle());
            //State error
            synchronized (mState) {
                if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    if (handleUnknownErrors(extra)) return true;
                }
            
                mState      = STATE.ERRORED;
                mErrorCode  = what;
                mErrorValue = extra;
            
                //state idle
                mMp.reset();
                //returning false will call OnCompletionHandler
                return false;
            }
        }
    };
    
    private class MyOnInfoListener implements MediaPlayer.OnInfoListener
    {  
        public boolean onInfo(MediaPlayer mp, int what, int extra)
        {
            if (what == 1)
                Logger.log(PlaybackErrorCodes.getInfo(extra));
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

    public long getDuration()
    {
        synchronized (mState) {
            try {
                if (mState == STATE.INITIALIZED) {
                    return mMp.getDuration();
                } else if (mState == STATE.PREPARING || mState == STATE.CREATED) {
                    return 0;
                }
                return -1;
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public long getPosition()
    {
        synchronized (mState) {
            try {
                if (mState == STATE.INITIALIZED) {
                    return mMp.getCurrentPosition();
                } else if (mState == STATE.PREPARING || mState == STATE.CREATED) {
                    return 0;
                } else {
                    return -1;
                }
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public boolean isPaused()
    {
        synchronized (mState) {
            return mState == STATE.INITIALIZED && !isPlaying();
        }
    }
    
    public void setTrackFinishedHandler(TrackFinishedHandler handler)
    {
        synchronized (mState) {
            mTrackFinishedHandler = handler;
        }
    }
    
    public void setBufferedCallback(BufferedCallback callback)
    {
        synchronized (mState) {
            mBufferedCallback = callback;
        }
    }
        
    public boolean clean()
    {
        synchronized (mState) {
            boolean ret = mState != STATE.CREATED;
            init();
            return ret;
        }
    }

    public void setVolume(float leftVolume, float rightVolume)
    {
        synchronized (mState) {
            if (mState == STATE.INITIALIZED) {
                mMp.setVolume(leftVolume, rightVolume);
            }
        }
    }
    
    public boolean erroredOut()
    {
        synchronized (mState) {
            if (mState == STATE.ERRORED)
                return true;
            else
                return false;
        }
    }

    public int getErrorValue()
    {
        return mErrorValue;
    }

    public int getErrorCode()
    {
        return mErrorCode;
    }

    public boolean seekTo(int i)
    {
        synchronized (mState) {
            if (mState == STATE.INITIALIZED) {
                Logger.log("Moving service to: " + Integer.toString(i));
                int total   = mMp.getDuration();
                int percent = i / 10;
                Logger.log("Moving service to: " + Integer.toString(percent) + " of: " + total);
                int msecs   = (total * percent) / 100;
                Logger.log("Moving service to msec: " + Integer.toString(msecs));
                mMp.seekTo(msecs);
                return true;
            }
            return false;
        }
        
    }
    
}
