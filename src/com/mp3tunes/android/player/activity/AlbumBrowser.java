/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mp3tunes.android.player.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.binaryelysium.mp3tunes.api.Id;
import com.mp3tunes.android.player.AlbumArtImageView;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.content.LockerDb.RefreshAlbumsTask;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;
import com.mp3tunes.android.player.util.FetchAndPlayTracks;

public class AlbumBrowser extends BaseMp3TunesListActivity
    implements View.OnCreateContextMenuListener, Music.Defs
{
    private Id mCurrentAlbumId;
    private Id mArtistId;
    private SimpleCursorAdapter mAdapter;
    private AsyncTask<Void, Integer, Boolean> mArtFetcher;
    private AsyncTask<Void, Void, Boolean> mPlayTracksTask;
    private boolean mAdapterSent;
    
    private boolean mShowingDialog;
    
    static final String[] mFrom = new String[] {
            DbKeys.ID,
            DbKeys.ALBUM_NAME,
            DbKeys.ARTIST_NAME,
            MediaStore.KEY_LOCAL
      };
    
    static final int[] mTo = new int[] {
            R.id.icon,
            R.id.line1,
            R.id.line2,
            0
    };

    static class FROM_MAPPING {
        static final int ID          = 0;
        static final int NAME        = 1;
        static final int ARTIST_NAME = 2;
        static final int LOCAL       = 3;
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        if (icicle != null) {
            mCurrentAlbumId = IdParcel.idParcelToId(getIntent().getParcelableExtra("selectedalbum"));
            mArtistId   = IdParcel.idParcelToId(getIntent().getParcelableExtra("artist"));
        } else {
            mArtistId   = IdParcel.idParcelToId(getIntent().getParcelableExtra("artist"));
        }
        super.onCreate(icicle); 
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.bindToService(this);
        //Music.bindToCacheService(this);
        Music.ensureSession(this);

        buildErrorDialog(R.string.ablum_browser_error);
        buildProgressDialog(R.string.loading_albums);

        setContentView(R.layout.media_picker_activity);
        ListView lv = getListView();
        lv.setFastScrollEnabled(true);
        lv.setOnCreateContextMenuListener(this);
        lv.setTextFilterEnabled(true);

        mAdapter = (SimpleCursorAdapter) getLastNonConfigurationInstance();
        
        if (mAdapter == null) {
            mAdapter = new SimpleCursorAdapter(this, R.layout.album_list_item, mCursor, mFrom, mTo);
            setListAdapter(mAdapter);
            setTitle(R.string.title_working_albums);
            mAdapter.setViewBinder(new Binder());
            mLoadingCursor = true;
            mCursorTask = new FetchAlbumsTask(Music.getDb(getBaseContext()));
            mCursorTask.execute((Void[])null);
            showDialog(PROGRESS_DIALOG);
            mShowingDialog = true;
        } else {
            setListAdapter(mAdapter);
            mCursor = mAdapter.getCursor();
            if (mCursor == null) {
                mLoadingCursor = true;
                mCursorTask = new FetchAlbumsTask(Music.getDb(getBaseContext()));
                mCursorTask.execute((Void[])null);
                showDialog(PROGRESS_DIALOG);
                mShowingDialog = true;
            } else {
                mShowingDialog = false;
            }
        }
        init(mCursor, 100);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mAdapterSent = true;
        return mAdapter;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) 
    {
        killTasks(outcicle);
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        if (mCurrentAlbumId != null)
            outcicle.putParcelable("selectedalbum", new IdParcel(mCurrentAlbumId));
        if (mArtistId != null)
            outcicle.putParcelable("artist", new IdParcel(mArtistId));
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onStop()
    {
        killTasks(null);
        super.onStop();
    }
    
    @Override
    public void onDestroy() {
        killTasks(null);
        Music.unbindFromService(this);
        Music.unconnectFromDb( this );
        if (!mAdapterSent) {
            Cursor c = mAdapter.getCursor();
            if (c != null) {
                c.close();
            }
        }
        super.onDestroy();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(GuiNotifier.META_CHANGED);
        f.addAction(GuiNotifier.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
        mTrackListListener.onReceive(null, null);
    }

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
        }
    };

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        super.onPause();
    }

    public void init(Cursor c, int nextRefresh) 
    {
        tryDismissProgress(mShowingDialog, c);
        
        mAdapter.changeCursor(c); // also sets mAlbumCursor
        mCursor = c;
        
        setTitle(R.string.title_albums);
        super.init(c, nextRefresh);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) 
    {
        menu.add(0, PLAY_SELECTION, 0, R.string.menu_play_selection);   

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        int pos = mi.position;
        if (mCursor != null) {
            mCursor.moveToPosition(pos);
            mCurrentAlbumId = this.cursorToId(mCursor);
            String name = mCursor.getString(FROM_MAPPING.NAME);
            menu.setHeaderTitle(name);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected album
                mPlayTracksTask = new FetchAndPlayTracks(FetchAndPlayTracks.FOR.ALBUM, mCurrentAlbumId, this).execute();
                return true;
            }
            default:
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                }
                break;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Cursor c = (Cursor) getListAdapter().getItem( position );
        //String album = c.getInt(FROM_MAPPING.ID);
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
        intent.putExtra("album", new IdParcel(cursorToId(c)));
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.artists, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_opt_player).setVisible( Music.isMusicPlaying() );
        menu.findItem(R.id.menu_opt_playall).setVisible( false );
        menu.findItem(R.id.menu_opt_shuffleall).setVisible( false );
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_opt_home:
                intent = new Intent();
                intent.setClass(this, LockerList.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;

            case R.id.menu_opt_player:
                intent = new Intent("com.mp3tunes.android.player.PLAYER");
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }    
    
    
    //TODO need to implement caching system for album art this is killing performance
    class Binder implements SimpleCursorAdapter.ViewBinder
    {
        private final BitmapDrawable mDefaultIcon;
        private static final int  mUnknownArtist = R.string.unknown_artist_name;
        private static final int  mUnknownAlbum  = R.string.unknown_album_name;
        
        public Binder ()
        {
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.albumart_mp_unknown_list);
            mDefaultIcon = new BitmapDrawable(b);
             //no filter or dither, it's a lot faster and we can't tell the difference
            mDefaultIcon.setFilterBitmap(false);
            mDefaultIcon.setDither(false);
        }
        public boolean setViewValue(View v, Cursor cursor, int columnIndex)
        {
            if (columnIndex == 1) {
                TextView view = (TextView)v.findViewById(R.id.line1);
                String val = cursor.getString(columnIndex);
                if (val == null) view.setText(mUnknownAlbum);
                view.setText(val);
            } else if (columnIndex == 2) {
                TextView view = ((TextView)v.findViewById(R.id.line2));
                String val = cursor.getString(columnIndex);
                if (val == null) view.setText(mUnknownArtist);
                view.setText(val);
            } else if (columnIndex == 0) {
                AlbumArtImageView view = (AlbumArtImageView)v.findViewById(R.id.icon);
                view.setDefaultImage(mDefaultIcon);
                view.setPadding(0, 0, 1, 0);
                //int albumId = cursor.getInt(columnIndex);
                //String id   = Integer.toString(albumId);
                //view.getRemoteArtwork(id);
            }
            return true;
        }
    }
    

    
    private void killTasks(Bundle state)
    {
        if( mCursorTask != null && mCursorTask.getStatus() == AsyncTask.Status.RUNNING) {
            mCursorTask.cancel(true);
            mLoadingCursor = false;
        }
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING)
            mTracksTask.cancel(true);
        if( mArtFetcher != null && mArtFetcher.getStatus() == AsyncTask.Status.RUNNING) {
            mArtFetcher.cancel(true);
            if (state != null)
                state.putBoolean( "artfetch_in_progress", true );
            mArtFetcher = null;
        }
    }
    
    private class FetchAlbumsTask extends RefreshAlbumsTask
    {

        public FetchAlbumsTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            mLoadingCursor = false;
            if (!result) {
                    Log.w("Mp3Tunes", "Got Error Fetching Albums");
            } else {
                mTracksTask = new LockerDb.RefreshTracksTask(Music.getDb(getBaseContext()));
                mTracksTask.execute((Void[])null);
            }
        }
    };

    @Override
    protected void updateCursor()
    {
        try {
            Log.w("Mp3Tunes", "Updating cursor");
            MediaStore ms = new MediaStore(Music.getDb(getBaseContext()), getContentResolver());
            if (mArtistId == null) {
                mCursor = ms.getAlbumData(mFrom);
            } else {
                mCursor = ms.getAlbumDataByArtist(mFrom, mArtistId);
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
   
}
