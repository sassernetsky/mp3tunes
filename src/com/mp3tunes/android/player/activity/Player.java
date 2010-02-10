package com.mp3tunes.android.player.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.RemoteImageHandler;
import com.mp3tunes.android.player.RemoteImageView;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.Worker;


public class Player extends Activity
{
    private static final int REFRESH = 0;
    private static final int BUFFERING_DIALOG = 0;  

    private ImageButton mPrevButton;
    private ImageButton mPlayButton;
    private ImageButton mNextButton;
    private RemoteImageView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private ProgressDialog mBufferingDialog;
    
    private long mDuration;
    private boolean paused;
    
    private Worker mAlbumArtWorker;
    private RemoteImageHandler mAlbumArtHandler;
    private IntentFilter mIntentFilter;
    private AsyncTask<Void, Void, Boolean> mArtTask;
    
    @Override
    public void onCreate( Bundle icicle )
    {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);
        
        mCurrentTime = (TextView)findViewById(R.id.currenttime);
        mTotalTime   = (TextView)findViewById(R.id.totaltime);
        mAlbum       = (RemoteImageView)findViewById(R.id.album);
        mArtistName  = (TextView)findViewById(R.id.track_artist);
        mTrackName   = (TextView)findViewById(R.id.track_title);
        mProgress    = (ProgressBar)findViewById(android.R.id.progress);
        mProgress.setMax(1000);
        
        mPrevButton = ( ImageButton ) findViewById( R.id.rew );
        mPlayButton = ( ImageButton ) findViewById( R.id.play );
        mNextButton = ( ImageButton ) findViewById( R.id.fwd );
        
        mPrevButton.setOnClickListener(mPrevListener);
        mPlayButton.setOnClickListener(mPlayListener);
        mNextButton.setOnClickListener(mNextListener);
        
        mPlayButton.requestFocus();
        
        mAlbumArtWorker  = new Worker("album art worker");
        mAlbumArtHandler = new RemoteImageHandler(mAlbumArtWorker.getLooper(), mHandler);
        
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(GuiNotifier.META_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_FINISHED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_STATE_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_ERROR);
        mIntentFilter.addAction(GuiNotifier.DATABASE_ERROR);
        
        Music.bindToService(this);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        paused = false;
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onStop() {

        paused = true;
        mHandler.removeMessages(REFRESH);

        super.onStop();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putBoolean("configchange", getChangingConfigurations() != 0);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mStatusListener);
        super.onPause();
    }
    
    @Override
    public void onResume() {
        registerReceiver(mStatusListener, mIntentFilter);
        updateTrackInfo();
        setPauseButtonImage();

        super.onResume();
    }

    @Override
    public void onDestroy() {
        Music.unbindFromService( this );
        mAlbumArtWorker.quit();
        super.onDestroy();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.player, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_opt_main:
                intent = new Intent();
                intent.setClass( this, LockerList.class );
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            if (Music.sService == null) return;
            try {
                Music.sService.prev();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    
    private View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View v) 
        {

            if (Music.sService== null) return;
            try {
                Music.sService.togglePlayback();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    
    private void setPauseButtonImage()
    {
         try {
            if (Music.sService != null && Music.sService.isPaused()) {
                mPlayButton.setImageResource( R.drawable.play_button );
            } else {
                mPlayButton.setImageResource( R.drawable.pause_button );
            }
        } catch ( RemoteException ex ) {}
    }
    
    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (Music.sService== null)
                return;
            try {
                Music.sService.next();
            } catch ( RemoteException e ) {
                e.printStackTrace();
            }
        }
    };
    
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) 
        {
            try {
                String action = intent.getAction();
                if (action.equals(GuiNotifier.META_CHANGED)) {
                    updateTrackInfo();
                } else if (action.equals(GuiNotifier.PLAYBACK_FINISHED)) {
                    dismissDialog(BUFFERING_DIALOG);
                    finish();
                } else if (action.equals(GuiNotifier.PLAYBACK_ERROR)) {
                    dismissDialog(BUFFERING_DIALOG);
                    finish();
                } else if(action.equals( GuiNotifier.PLAYBACK_STATE_CHANGED )) {
                    setPauseButtonImage();
                }
            } catch (IllegalArgumentException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }
    };
    
    private void updateTrackInfo() 
    {
        try {
            if (Music.sService== null)
                return;
            Track track = Music.sService.getTrack();
            int album_id = track.getAlbumId();
            mArtistName.setText(track.getArtistName());
            mTrackName.setText(track.getTitle());

            Bitmap bit = Music.getArtworkQuick( Player.this, album_id, mAlbum.getWidth(), mAlbum.getHeight() );
            mAlbum.setArtwork( bit );
            mAlbum.invalidate();
            if ( bit == null )
            { 
                mArtTask = new LoadAlbumArtTask();
                mArtTask.execute((Void) null);
            }
            setPauseButtonImage();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    private void queueNextRefresh(long delay) {

        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog d = null;
        if(id == BUFFERING_DIALOG){
            createBufferingDialog();
            d = mBufferingDialog;
        }
        return d;
    }
    
    private void createBufferingDialog()
    {
        mBufferingDialog = new ProgressDialog(this);
        mBufferingDialog.setTitle("");
        mBufferingDialog.setMessage("Buffering");
        mBufferingDialog.setIndeterminate(true);
        mBufferingDialog.setCancelable(true);
    }

    private long refreshNow() {

        if (Music.sService != null) {
            try {
                mDuration = Music.sService.getDuration();
                long pos  = Music.sService.getPosition();
                int buffpercent = Music.sService.getBufferPercent();
                long remaining = 1000 - (pos % 1000);
                if (pos > 0 && mDuration > 0 && pos <= mDuration) {
                    mCurrentTime.setText(Music.makeTimeString(this, pos / 1000));
                    mTotalTime.setText(Music.makeTimeString(this, mDuration / 1000));
                    mProgress.setProgress((int)(1000 * pos / mDuration));
                    mProgress.setSecondaryProgress(buffpercent * 10);
                    dismissDialog(BUFFERING_DIALOG);
                } else {
                    mCurrentTime.setText("--:--");
                    mTotalTime.setText("--:--");
                    mProgress.setProgress(0);
                    if(pos < 1) showDialog(BUFFERING_DIALOG);
                }
                // return the number of milliseconds until the next full second, so
                // the counter can be updated at just the right time
                return remaining;
            } catch (IllegalArgumentException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            } catch (RemoteException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }
        return 500;
    }

    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {

            switch (msg.what) 
            {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                case RemoteImageHandler.REMOTE_IMAGE_DECODED:
                    mAlbum.setArtwork((Bitmap) msg.obj);
                    mAlbum.invalidate();
                    break;
    
                default:
                    break;
            }
        }
    };
    
    private class LoadAlbumArtTask extends AsyncTask<Void, Void, Boolean> 
    {
        String artUrl;

        private void setArtUrl(Track t) throws InvalidSessionException {
            RemoteMethod method 
            = new RemoteMethod.Builder(RemoteMethod.METHODS.ALBUM_ART_GET)
                    .addFileKey(t.getFileKey())
                    .create();
            artUrl = method.getCall();
        }
        
        @Override
        public void onPreExecute() {
        }

        @Override
        public Boolean doInBackground(Void... params) 
        {
            try {
                if (Music.sService!= null) {
                    setArtUrl(Music.sService.getTrack());
                    return true;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
        

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                System.out.println("Art url: " + artUrl);
                if (artUrl != GuiNotifier.UNKNOWN) {
                    mAlbumArtHandler.removeMessages(RemoteImageHandler.GET_REMOTE_IMAGE);
                    mAlbumArtHandler.obtainMessage(RemoteImageHandler.GET_REMOTE_IMAGE, artUrl).sendToTarget();
                }
            } else {
                System.out.println("Art url: unknown"); 
            }
        }
    }

}
