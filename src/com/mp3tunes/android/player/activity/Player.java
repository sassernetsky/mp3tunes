package com.mp3tunes.android.player.activity;

import android.app.Activity;
import android.app.Dialog;
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
import android.util.TimingLogger;
import android.view.KeyEvent;
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
    private boolean mShowingOptions;
    
    private Worker mAlbumArtWorker;
    private RemoteImageHandler mAlbumArtHandler;
    private IntentFilter mIntentFilter;
    private AsyncTask<Void, Void, Boolean> mArtTask;
    
    @Override
    public void onCreate( Bundle icicle )
    {
        Timer timings = new Timer("onCreate");
        super.onCreate(icicle);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);
        Music.ensureSession(this);
        
        mShowingOptions = false;
        
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
        timings.push();
    }
    
    @Override
    public void onStart() {
        Timer timings = new Timer("onStart");
        super.onStart();
        paused = false;
        long next = refreshNow();
        queueNextRefresh(next);
        timings.push();
    }
    
    @Override
    public void onStop() {
        Timer timings = new Timer("onStop");
        paused = true;
        mHandler.removeMessages(REFRESH);

        super.onStop();
        timings.push();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Timer timings = new Timer("onSaveInstanceState");
        outState.putBoolean("configchange", getChangingConfigurations() != 0);
        super.onSaveInstanceState(outState);
        timings.push();
    }

    @Override
    protected void onPause() {
        Timer timings = new Timer("onPause");
        unregisterReceiver(mStatusListener);
        super.onPause();
        timings.push();
    }
    
    @Override
    public void onResume() {
        Timer timings = new Timer("onResume");
        registerReceiver(mStatusListener, mIntentFilter);
        updateTrackInfo();
        setPauseButtonImage();

        super.onResume();
        timings.push();
    }

    @Override
    public void onDestroy() {
        Timer timings = new Timer("onDestroy");
        Music.unbindFromService( this );
        mAlbumArtWorker.quit();
        super.onDestroy();
        timings.push();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Timer timings = new Timer("onCreateOptionsMenu");
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.player, menu);
        timings.push();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Timer timings = new Timer("onPrepareOptionsMenu");
        mShowingOptions = true;
        try {
            dismissDialog(BUFFERING_DIALOG);
        } catch (IllegalArgumentException e) {}
        timings.push();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Timer timings = new Timer("onOptionsItemSelected");
        try {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_opt_main:
                intent = new Intent();
                intent.setClass( this, LockerList.class );
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
                startActivity(intent);
                return true;
            case R.id.menu_opt_stop:
                if (Music.sService == null) return false;
                try {
                    Music.sService.stop();
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
        }
        } finally {
            timings.push();
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu)
    {
        Timer timings = new Timer("onOptionsMenuClosed");
        mShowingOptions = false;
        timings.push();
    }
    
    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            Timer timings = new Timer("mPrevListener");
            if (Music.sService == null) return;
            try {
                Music.sService.prev();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            timings.push();
        }
    };
    
    private View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            Timer timings = new Timer("mPlayListener");
            if (Music.sService== null) return;
            try {
                Music.sService.togglePlayback();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            timings.push();
        }
    };
    
    private void setPauseButtonImage()
    {
        Timer timings = new Timer("setPauseButtonImage");
         try {
            if (Music.sService != null && Music.sService.isPaused()) {
                mPlayButton.setImageResource( R.drawable.play_button );
            } else {
                mPlayButton.setImageResource( R.drawable.pause_button );
            }
        } catch ( RemoteException ex ) {}
        timings.push();
    }
    
    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            Timer timings = new Timer("mNextListener");
            if (Music.sService== null)
                return;
            try {
                Music.sService.next();
            } catch ( RemoteException e ) {
                e.printStackTrace();
            }
            timings.push();
        }
    };
    
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) 
        {
            Timer timings = new Timer("mStatusListener");
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
            timings.push();
        }
    };
    
    private void updateTrackInfo() 
    {
        Timer timings = new Timer("updateTrackInfo");
        try {
            if (Music.sService== null)
                return;
            Track track = Music.sService.getTrack();
            int album_id = track.getAlbumId();
            mArtistName.setText(track.getArtistName());
            mTrackName.setText(track.getTitle());

            Bitmap bit = null;
            //= Music.getArtworkQuick( Player.this, album_id, mAlbum.getWidth(), mAlbum.getHeight() );
            //mAlbum.setArtwork( bit );
            //mAlbum.invalidate();
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
        } finally  {
            timings.push();
        }
    }
    
    private void queueNextRefresh(long delay) {
        //Timer timings = new Timer("queueNextRefresh");
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
        //timings.push();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Timer timings = new Timer("onCreateDialog");
        Dialog d = null;
        if(id == BUFFERING_DIALOG){
            createBufferingDialog();
            d = mBufferingDialog;
        }
        timings.push();
        return d;
    }
    
    private void createBufferingDialog()
    {
        Timer timings = new Timer("createBufferingDialog");
        mBufferingDialog = new ProgressDialog(this);
        mBufferingDialog.setTitle("");
        mBufferingDialog.setMessage("Buffering");
        mBufferingDialog.setIndeterminate(true);
        mBufferingDialog.setCancelable(true);
        timings.push();
    }

    private long refreshNow() {
        Timer timings = new Timer("refreshNow");
        if (Music.sService != null) {
            try {
                mDuration = Music.sService.getDuration();
                long pos  = Music.sService.getPosition();
                int buffpercent = Music.sService.getBufferPercent();
                long remaining = 1000 - (pos % 1000);
                boolean showBuffering;
                
                if (pos > 0 && mDuration > 0 && pos <= mDuration) {
                    mCurrentTime.setText(Music.makeTimeString(this, pos / 1000));
                    mTotalTime.setText(Music.makeTimeString(this, mDuration / 1000));    
                    mProgress.setProgress((int)(1000 * pos / mDuration));
                    mProgress.setSecondaryProgress(buffpercent * 10);
                    showBuffering = false;
                } else {
                    mCurrentTime.setText("--:--");
                    mTotalTime.setText("--:--");
                    mProgress.setProgress(0);
                    if(pos < 1) showBuffering = true;
                    else showBuffering = false;
                }
                
                if (showBuffering && !mShowingOptions)
                    showDialog(BUFFERING_DIALOG);
                else
                    dismissDialog(BUFFERING_DIALOG);
                
                
                // return the number of milliseconds until the next full second, so
                // the counter can be updated at just the right time
                return remaining;
            } catch (IllegalArgumentException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            } catch (RemoteException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }
        timings.push();
        return 500;
    }

    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            Timer timings = new Timer("mHandler");
            switch (msg.what) 
            {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                case RemoteImageHandler.REMOTE_IMAGE_DECODED:
                    mAlbum.setArtwork((Bitmap) msg.obj);
                    mAlbum.invalidate();
                    timings.push();
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
            Timer timings = new Timer("setArtUrl");
            RemoteMethod method 
            = new RemoteMethod.Builder(RemoteMethod.METHODS.ALBUM_ART_GET)
                    .addFileKey(t.getFileKey())
                    .create();
            artUrl = method.getCall();
            timings.push();
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
            Timer timings = new Timer("onPostExecute");
            if (result) {
                System.out.println("Art url: " + artUrl);
                if (artUrl != GuiNotifier.UNKNOWN) {
                    mAlbumArtHandler.removeMessages(RemoteImageHandler.GET_REMOTE_IMAGE);
                    mAlbumArtHandler.obtainMessage(RemoteImageHandler.GET_REMOTE_IMAGE, artUrl).sendToTarget();
                }
            } else {
                System.out.println("Art url: unknown"); 
            }
            timings.push();
        }
    }

    class ProgressDialog extends android.app.ProgressDialog
    {

        public ProgressDialog(Context context)
        {
            super(context);
        }
        
        public ProgressDialog(Context context, int theme)
        {
            super(context, theme);
        }
        
        @Override
        public boolean onKeyUp(int code, KeyEvent event)
        {
            Log.w("Mp3Tunes", "Got KeyUp: " + Integer.toString(code));
            if (code == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                Log.w("Mp3Tunes", "Opening options menu");
                mShowingOptions = true;
                Player.this.dismissDialog(BUFFERING_DIALOG);
                Player.this.openOptionsMenu();
                return true;
            }
            return super.onKeyUp(code, event); 
        }
        
    };
    
    class Timer
    {
        long   mT1;
        long   mT2;
        String mText;
        
        Timer(String text)
        {
            mText = text;
            mT1 = System.currentTimeMillis();
        }
        
        void start(String text)
        {
            mText = text;
            mT1 = System.currentTimeMillis();
        }
        
        void push() 
        {
            mT2 = System.currentTimeMillis();
            Log.w("Mp3Tunes", mText + " done: " + Long.toString(mT2 - mT1) + " elapsed");
        }
    }
}
