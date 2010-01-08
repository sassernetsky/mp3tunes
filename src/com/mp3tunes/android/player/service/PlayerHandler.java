package com.mp3tunes.android.player.service;

import java.io.IOException;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.CurrentPlaylist;
import com.mp3tunes.android.player.MP3tunesApplication;

public class PlayerHandler
{
    private MediaPlayer          mMp = new MediaPlayer();
    private Locker               mLocker;
    private Service              mService;
    private Context              mContext;
    
    private int     mServiceState = STATE.STOPPED;
    private boolean mActive       = false;
    private int     mBufferPercent;
    
    private CurrentPlaylist mCp;
    
    private int     mCurrentPosition = 0;
    private Track   mCurrentTrack    = null;
    
    Mp3TunesPhoneStateListener mPhoneStateListener;
    private TelephonyManager   mTelephonyManager;
    
    private OnCompletionListener      mOnCompletionListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnPreparedListener        mOnPreparedListener;
    private OnErrorListener           mOnErrorListener;
    
    public static final String UNKNOWN = "com.mp3tunes.android.player.unknown";

    
    private static class STATE
    {

        private final static int STOPPED     = 0;
        private final static int PREPARING   = 1;
        private final static int PLAYING     = 2;
        private final static int SKIPPING    = 3;
        private final static int PAUSED      = 4;
        private final static int SYSTEMPAUSE = 5;
        private final static int IDLE        = 6;
    }
    
    public PlayerHandler(Service service, Context context) throws PlaybackException
    {
        mService = service;
        mContext = context;
        
        mLocker = MP3tunesApplication.getInstance().getLocker();
        
        mPhoneStateListener = new Mp3TunesPhoneStateListener(this);
        
        ContextWrapper cw = new ContextWrapper(mContext);
        mTelephonyManager = (TelephonyManager) cw.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, Mp3TunesPhoneStateListener.LISTEN_CALL_STATE);

        // establish a connection with the database
        try {
            mCp = new CurrentPlaylist(context);
        } catch (Exception ex) {
            // database connection failed.
            // Show an error and exit gracefully.
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            mServiceState = STATE.STOPPED;
            throw new PlaybackException("Unable to get current playlist db");
        }
    }
    
    public void destroy()
    {
        mCp.close();
        mMp.stop();
        mMp.release();
    }
    
    public void setOnCompletionListener(OnCompletionListener listener)
    {
        mOnCompletionListener = listener;
    }
    
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener)
    {
        mOnBufferingUpdateListener = listener;
    }
    
    public void setOnPreparedListener(OnPreparedListener listener)
    {
        mOnPreparedListener = listener;
    }
    
    public void setOnErrorListener(OnErrorListener listener)
    {
        mOnErrorListener = listener;
    }
    
    //Player control methods
    
    public Track start(MediaPlayer mp) throws PlaybackException
    {
        if (mp == mMp) {
            if (mServiceState == STATE.PREPARING) {
                mp.start();
                mServiceState = STATE.PLAYING;
                return mCurrentTrack;
            } else {
                throw new PlaybackException("tried to start playback from an illegal state");
            }
        }
        return null;
    }

    public Track prepareTrack(int index) throws PlaybackException
    {
        if (mCp.getQueueSize() <= 0 || mCp.getQueueSize() < index)
            throw new PlaybackException("Queue is empty");
        return prepareTrack(mCp.getTrackQueue(index), index, mMp);
    }
    
    private Track prepareTrack(Track track, int playlist_index, MediaPlayer p) throws PlaybackException
    {
        try {
            if (mServiceState == STATE.PREPARING || track == null)
                return null;

            if (p == mMp) {
                mCurrentTrack = track;
                mCurrentPosition = playlist_index;
            }

            int bitrate = chooseBitrate();
            String url = track.getPlayUrl() + "&fileformat=mp3" + "&bitrate="
                    + bitrate;
            Log.i("Mp3Tunes", "Playing: " + track.getTitle());
            p.reset();
            url = updateUrlSession(url);
            p.setDataSource(url);
            p.setOnCompletionListener(mOnCompletionListener);
            p.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            p.setOnPreparedListener(mOnPreparedListener);
            p.setOnErrorListener(mOnErrorListener);

            // We do this because there has been bugs in our phonecall fade code
            // that resulted in the music never becoming audible again after a
            // call.
            // Leave this precaution here please.
            p.setVolume(1.0f, 1.0f);

            if (p == mMp)
                mServiceState = STATE.PREPARING;
            p.prepareAsync();
        } catch (IllegalStateException e) {
            Log.e("Mp3Tunes", Log.getStackTraceString(e));
            throw new PlaybackException("Tried to prepare from an Illegal State");
        } catch (IOException e) {
            Log.e("Mp3Tunes", Log.getStackTraceString(e));
            throw new PlaybackException("IO problem");
        }
        return track;
    }

    private String updateUrlSession(String url)
    {
        if (mLocker == null && mLocker.getCurrentSession() == null)
            return url;
        String sid = mLocker.getCurrentSession().getSessionId();
        url = url.replaceFirst("sid=(.*?)&", "sid=" + sid + "&");
        System.out.println("fixed url: " + url);
        return url;
    }

    private int chooseBitrate()
    {
        int bitrate = Integer.valueOf(PreferenceManager
                .getDefaultSharedPreferences(mService).getString("bitrate", "-1"));

        if (bitrate == -1) {
            // Resources resources = getResources();
            // int[] vals = resources.getIntArray(R.array.rate_values);
            // TODO Figure out why the above code does not work
            int[] vals = new int[] { -1, 24000, 56000, 96000, 128000, 192000 };

            ContextWrapper      cw = new ContextWrapper(mContext);
            ConnectivityManager cm = (ConnectivityManager)cw.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return bitrate;
            int type = cm.getActiveNetworkInfo().getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                bitrate = vals[4]; // 5 = 128000
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager tm = (TelephonyManager) cw.getSystemService(Context.TELEPHONY_SERVICE);

                switch (tm.getNetworkType()) {
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                        bitrate = vals[1]; // 1 = 24000
                        break;
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        bitrate = vals[1]; // 1 = 24000
                        break;
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                        bitrate = vals[2]; // 2 = 56000
                        break;
                }
            }
        }
        return bitrate;
    }

    public Track prevTrack() throws PlaybackException, ChangeTrackException
    {
        System.out.println("prev track called");
        if (mServiceState == STATE.SKIPPING /* || mServiceState == STATE.STOPPED */)
            return null;

        mServiceState = STATE.SKIPPING;

        // Check again, if size still == 0 then the playlist is empty.
        if (mCp.getQueueSize() > 0) {
            // playTrack will check if mStopping is true, and stop us if the
            // user has
            // pressed stop while we were fetching the playlist
            int pos = mCurrentPosition - 1;
            if (pos < 1)
                pos = 1;
            System.out.println("preparing prev track index: "
                    + Integer.toString(pos));
            Track t = mCp.getTrackQueue(pos);
            System.out.println("track: " + t.getTitle() + " by "
                    + t.getArtistName());
            prepareTrack(t, pos, mMp);
            return t;
        } else {
            throw new ChangeTrackException("No previous track");
            // playlist finished
        }
    }

    public Track nextTrack() throws PlaybackException, ChangeTrackException
    {
        System.out.println("next track called");
        if (mServiceState == STATE.SKIPPING)
            return null;

        mServiceState = STATE.SKIPPING;

        // Check again, if size still == 0 then the playlist is empty.
        if (mCp.getQueueSize() > mCurrentPosition) {
            // playTrack will check if mStopping is true, and stop us if the
            // user has
            // pressed stop while we were fetching the playlist
            int pos = mCurrentPosition + 1;
            System.out.println("preparing next track");
            Track track = mCp.getTrackQueue(pos);
            System.out.println("track: " + track.getTitle() + " by "
                    + track.getArtistName());
            prepareTrack(track, pos, mMp);
            return track;
        } else {
            // playlist finished
            throw new ChangeTrackException("No next track");
        }
    }

    public void systemPause()
    {
        pause(STATE.SYSTEMPAUSE);
    }
    
    public Track pause()
    {
        return pause(STATE.PAUSED);
    }
    
    public Track pause(int state)
    {
        if (mServiceState == STATE.STOPPED ||
                mServiceState == STATE.PAUSED && state == STATE.SYSTEMPAUSE)
            return null;
        
        if (mServiceState == STATE.PAUSED || mServiceState == STATE.SYSTEMPAUSE) {
            mMp.start();
            mServiceState = STATE.PLAYING;
        } else {
            if (mActive == false)
            mMp.pause();
            mServiceState = state;
        }
        return mCurrentTrack;
    }

    public Track stop()
    {
        if (mServiceState == STATE.PLAYING) {
            mMp.stop();
        }
        mServiceState = STATE.STOPPED;
        return mCurrentTrack;
    }

    public boolean setCurrentPosition(int msec)
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return false;

        mMp.seekTo(msec);
        // because when we move to an other pos the music starts
        if (mServiceState == STATE.PAUSED)
            mMp.pause();
        return true;

    }
    
    public boolean handleError(MediaPlayer mp, int what, int extra)
    {
        String error = "unknown";
        String state = "unknown";
        if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
            error = PlaybackErrorCodes.getError(extra);
            if (error == null) 
                error = "Error Code: " + Integer.toString(extra);
            if (extra == -26) {
                error += "This is an authentication error, perhaps the session is bad?";
                new RefreshSessionTask(mContext, mLocker).execute((Void) null);
                return true;
            }
        }
        
        if (mServiceState == STATE.PREPARING) {
            state = "PREPARING";
        } else if (mServiceState == STATE.PLAYING) {
            state = "PLAYING";
        } else if (mServiceState == STATE.IDLE) {
            state = "IDLE";
        } else if (mServiceState == STATE.PAUSED) {
            state = "PAUSED";
        } else if (mServiceState == STATE.SKIPPING) {
            state = "SKIPPING";
        } else if (mServiceState == STATE.STOPPED) {
            state = "STOPPED";
        } else if (mServiceState == STATE.SYSTEMPAUSE) {
            state = "SYSTEMPAUSE";
        } 
        
        Log.e("Mp3Tunes", "MediaPlayer returned error: " + error + " while in state: " + state);
        return false;
    }
 
    private String parseFromInt(int num)
    {
        String val;
        try {
            val = Integer.toString(num);
        } catch (Exception e) {
            val = UNKNOWN;
        }
        return val;
    }
    
    public String[] getMetadata()
    {
        String[] data;
        if (mCurrentTrack == null)
            data = new String[] { UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                    UNKNOWN, UNKNOWN };
        else {
            String id         = parseFromInt(mCurrentTrack.getId());
            String artistId   = parseFromInt(mCurrentTrack.getArtistId());
            String albumId    = parseFromInt(mCurrentTrack.getAlbumId());
            String title      = mCurrentTrack.getTitle();
            String artistName = mCurrentTrack.getArtistName();
            String albumTitle = mCurrentTrack.getAlbumTitle();
         
            data = new String[] { 
                    title,
                    id,
                    artistName,
                    artistId,
                    albumTitle,
                    albumId
            };
        }
        return data;
    }
    
    public void setVolume(float left, float right)
    {
        mMp.setVolume(left, right);
    }
    
    public long getDuration() 
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return 0;
        else
            return mMp.getDuration();
    }

    public long getPosition() 
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return 0;
        else
            return mMp.getCurrentPosition();
    }
    
    public Track getCurrentTrack()
    {
        return mCurrentTrack;
    }

    public boolean isPaused()
    {
        return mServiceState == STATE.PAUSED;
    }
    
    public boolean isSystemPaused()
    {
        return mServiceState == STATE.SYSTEMPAUSE;
    }

    public int getQueuePosition()
    {
        return mCurrentPosition;
    }
    
    public void updateBuffering(MediaPlayer mp, int percent) {
        if (mp == mMp)
            mBufferPercent = percent;
    }
    
//    private class NextTrackTask extends AsyncTask<Void, Void, Boolean>
//    {
//
//        public Boolean doInBackground(Void... input)
//        {
//            boolean success = false;
//            try {
//                nextTrack();
//                success = true;
//            } catch (Exception e) {
//                success = false;
//            }
//            return success;
//        }
//
//        @Override
//        public void onPostExecute(Boolean result)
//        {
//            if (!result) {
//            }
//        }
//    }
    
    public class PlaybackException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = 2434639988504705066L;

        public PlaybackException(String message) {
            super(message);
        }
    }
    
    public class ChangeTrackException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = -3881847611458506497L;

        public ChangeTrackException(String message) {
            super(message);
        }
    }

    public boolean isPlaying()
    {
        return mServiceState != STATE.STOPPED;
    }

    public int getBufferPercent()
    {
        return mBufferPercent;
    }

}
