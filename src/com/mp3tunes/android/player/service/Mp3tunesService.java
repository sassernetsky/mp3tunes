/***************************************************************************
 *   Copyright 2008 Casey Link <unnamedrambler@gmail.com>                  *
 *   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
 *   Copyright 2005-2009 Last.fm Ltd.                                      *
 *   Portions contributed by Casey Link, Lukasz Wisniewski,                *
 *   Mike Jennings, and Michael Novak Jr.                                  *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/
package com.mp3tunes.android.player.service;

import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.MP3tunesApplication;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.PrivateAPIKey;
import com.mp3tunes.android.player.service.ITunesService;
import com.mp3tunes.android.player.service.PlayerHandler.ChangeTrackException;
import com.mp3tunes.android.player.service.PlayerHandler.PlaybackException;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class Mp3tunesService extends Service
{
    private PlayerHandler        mPlayerHandler;
    private GuiNotifier          mGuiNotifier;
    private MusicPlayStateLocker mPlayStateLocker;
    private Locker               mLocker;
    
    private Bitmap  mAlbumArt;

    public static final String META_CHANGED           = "com.mp3tunes.android.player.metachanged";
    public static final String QUEUE_CHANGED          = "com.mp3tunes.android.player.queuechanged";
    public static final String PLAYBACK_FINISHED      = "com.mp3tunes.android.player.playbackcomplete";
    public static final String PLAYBACK_STATE_CHANGED = "com.mp3tunes.android.player.playstatechanged";
    public static final String PLAYBACK_ERROR         = "com.mp3tunes.android.player.playbackerror";
    public static final String DATABASE_ERROR         = "com.mp3tunes.android.player.databaseerror";
    public static final String UNKNOWN                = "com.mp3tunes.android.player.unknown";
    
    //Service Overrides
    
    @Override
    public void onCreate()
    {
        super.onCreate();

        // we don't want the service to be killed while playing
        setForeground(true);
        
        mPlayStateLocker = new MusicPlayStateLocker(getBaseContext());
        mGuiNotifier     = new GuiNotifier(this, getBaseContext());
        try {
            mPlayerHandler   = new PlayerHandler(this, getBaseContext());
        } catch (PlaybackException e) {
            mPlayStateLocker.release();
            e.printStackTrace();
        }
        
        mPlayerHandler.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mPlayerHandler.setOnCompletionListener(mOnCompletionListener);
        mPlayerHandler.setOnErrorListener(mOnErrorListener);
        mPlayerHandler.setOnPreparedListener(mOnPreparedListener);

        mLocker = MP3tunesApplication.getInstance().getLocker();       
    }

    @Override
    public void onDestroy()
    {
        mPlayerHandler.destroy();
        mGuiNotifier.stop(null);
        mPlayStateLocker.release();
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        mDeferredStopHandler.cancelStopSelf();
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        if (mPlayerHandler.isPlaying())
            return true;

        mDeferredStopHandler.deferredStopSelf();
        return true;
    }
 
    //MediaPlayer Listeners
    
    private OnCompletionListener mOnCompletionListener = new OnCompletionListener() {

        public void onCompletion(MediaPlayer mp)
        {       
            Track t = null;
            try {
                t = mPlayerHandler.nextTrack();
                if (t == null) return;
                mGuiNotifier.nextTrack(t);
            } catch (PlaybackException e) {
             // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ChangeTrackException e) {
                mGuiNotifier.stop(t);
            }
        }
    };

    private OnBufferingUpdateListener mOnBufferingUpdateListener = new OnBufferingUpdateListener() {

        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            mPlayerHandler.updateBuffering(mp, percent);
            Log.w("Mp3Tunes", "Buffering: " + Integer.toString(percent));
        }
    };

    private OnErrorListener mOnErrorListener = new OnErrorListener() {

        public boolean onError(MediaPlayer mp, int what, int extra)
        {
            if (mPlayerHandler.handleError(mp, what, extra)) 
                return true;
            try {
                mBinder.next();
            } catch (RemoteException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    };

    private OnPreparedListener mOnPreparedListener = new OnPreparedListener() {

        public void onPrepared(MediaPlayer mp)
        {
            try {
                Track t = mPlayerHandler.start(mp);
                if (t == null) return;
                mGuiNotifier.play(t);
            } catch (PlaybackException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };
    
    /**
     * Deferred stop implementation from the five music player for android:
     * http://code.google.com/p/five/ (C) 2008 jasta00
     */
    private final DeferredStopHandler mDeferredStopHandler = new DeferredStopHandler();

    private class DeferredStopHandler extends Handler
    {

        /* Wait 1 minute before vanishing. */
        public static final long DEFERRAL_DELAY = 1 * (60 * 1000);

        private static final int DEFERRED_STOP = 0;

        public void handleMessage(Message msg)
        {

            switch (msg.what) {
                case DEFERRED_STOP:
                    stopSelf();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        public void deferredStopSelf()
        {

            Log.i("Mp3tunes", "Service stop scheduled "
                    + (DEFERRAL_DELAY / 1000 / 60) + " minutes from now.");
            sendMessageDelayed(obtainMessage(DEFERRED_STOP), DEFERRAL_DELAY);
        }

        public void cancelStopSelf()
        {

            if (hasMessages(DEFERRED_STOP) == true) {
                Log.i("Mp3tunes", "Service stop cancelled.");
                removeMessages(DEFERRED_STOP);
            }
        }
    };

    private final ITunesService.Stub mBinder = new ITunesService.Stub() {

        public int getBufferPercent() throws RemoteException
        {

            return mPlayerHandler.getBufferPercent();
        }

        public long getDuration() throws RemoteException
        {
            return mPlayerHandler.getDuration();
        }

        public long getPosition() throws RemoteException
        {
            return mPlayerHandler.getPosition();
        }

        public int getRepeatMode() throws RemoteException
        {
            return Music.RepeatMode.NO_REPEAT;
        }

        public int getShuffleMode() throws RemoteException
        {
            return Music.ShuffleMode.NORMAL;
        }

        public boolean isPlaying() throws RemoteException
        {
            return mPlayerHandler.isPlaying();
        }

        public void next() throws RemoteException
        {
            try {
                Track t = mPlayerHandler.nextTrack();
                if (t == null) return;
                mGuiNotifier.nextTrack(t);
            } catch (PlaybackException e) {
                e.printStackTrace();
                throw new RemoteException();
            } catch (ChangeTrackException e) {
                e.printStackTrace();
                throw new RemoteException();
            }
        }

        public void pause() throws RemoteException
        {
            Track t = mPlayerHandler.pause();
            if (t == null) return;
            mGuiNotifier.pause(t);
        }

        public void prev() throws RemoteException
        {
            try {
                Track t = mPlayerHandler.prevTrack();
                if (t == null) return;
                mGuiNotifier.prevTrack(t);
            } catch (PlaybackException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ChangeTrackException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public boolean setPosition(int msec) throws RemoteException
        {
            return mPlayerHandler.setCurrentPosition(msec);
        }

        public void setRepeatMode(int mode) throws RemoteException
        {
        }

        public void setShuffleMode(int mode) throws RemoteException
        {
        }

        public void start() throws RemoteException
        {
            startAt(1);
        }

        public void startAt(int pos) throws RemoteException
        {
            try {
                Track t = mPlayerHandler.prepareTrack(pos);
                if (t == null) return;
                mGuiNotifier.play(t);
            } catch (PlaybackException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void stop() throws RemoteException
        {
            Track t = mPlayerHandler.stop();
            if (t == null) return;
            mGuiNotifier.stop(t);
        }

        
        //Most of this here is silly.  We should just provide a function
        //that returns a Track and make it the caller's responsibilty to 
        //get the data it needs
        public void setAlbumArt(Bitmap art) throws RemoteException
        {
            mAlbumArt = art;
        }

        public Bitmap getAlbumArt() throws RemoteException
        {
            return mAlbumArt;
        }

        /*
         * Returns the meta data of the current track 0: track name 1: track id
         * 2: artist name 3: artist id 4: album name 5: album id
         */
        public String[] getMetadata() throws RemoteException
        {
            return mPlayerHandler.getMetadata();
        }

        public String getArtUrl() throws RemoteException
        {
            Track t = mPlayerHandler.getCurrentTrack();
            return "http://content.mp3tunes.com/storage/albumartget/" + 
                    t.getAlbumId() + "?sid=" + mLocker.getCurrentSession().getSessionId() + "&partner_token=" + PrivateAPIKey.KEY;

        }

        public boolean isPaused() throws RemoteException
        {
            return mPlayerHandler.isPaused();
        }

        public int getQueuePosition() throws RemoteException
        {
            return mPlayerHandler.getQueuePosition();
        }

        public void moveQueueItem(int index1, int index2)
                throws RemoteException
        {
            // TODO Auto-generated method stub
            
        }

        public int removeQueueItem(int first, int last) throws RemoteException
        {
            // TODO Auto-generated method stub
            return 0;
        }
    };
}
