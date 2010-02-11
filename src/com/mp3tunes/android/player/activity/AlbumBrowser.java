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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.mp3tunes.android.player.AlbumArtImageView;
import com.mp3tunes.android.player.LockerDb;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.MusicAlphabetIndexer;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.RemoteImageView;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;
import com.mp3tunes.android.player.util.ImageDownloaderListener;

public class AlbumBrowser extends BaseMp3TunesListActivity
    implements View.OnCreateContextMenuListener, Music.Defs
{
    private String mCurrentAlbumId;
    private String mCurrentAlbumName;
    private Cursor mAlbumCursor;
    private String mArtistId;
    private SimpleCursorAdapter mAdapter;
    private AsyncTask<Void, Integer, Boolean> mArtFetcher;
    private AsyncTask<Void, Void, Boolean>    mAlbumFetcher;
    private AsyncTask<Integer, Void, Boolean> mTracksTask;
    private boolean mAdapterSent;
    private final static int SEARCH = CHILD_MENU_BASE;
    
    static final String[] mFrom = new String[] {
            LockerDb.KEY_ID,
            LockerDb.KEY_ALBUM_NAME,
            LockerDb.KEY_ARTIST_NAME,
      };
    
    static final int[] mTo = new int[] {
            R.id.icon,
            R.id.line1,
            R.id.line2,
    };

    static class FROM_MAPPING {
        static final int ID          = 0;
        static final int NAME        = 1;
        static final int ARTIST_NAME = 2;
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        if (icicle != null) {
            mCurrentAlbumId = icicle.getString("selectedalbum");
            mArtistId = icicle.getString("artist");
        } else {
            mArtistId = getIntent().getStringExtra("artist");
        }
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.bindToService(this);

        buildErrorDialog(R.string.ablum_browser_error);
        buildProgressDialog(R.string.loading_albums);

        setContentView(R.layout.media_picker_activity);
        ListView lv = getListView();
        lv.setFastScrollEnabled(true);
        lv.setOnCreateContextMenuListener(this);
        lv.setTextFilterEnabled(true);

        mAdapter = (SimpleCursorAdapter) getLastNonConfigurationInstance();
        
        if (mAdapter == null) {
            mAdapter = new SimpleCursorAdapter(this, R.layout.album_list_item, mAlbumCursor, mFrom, mTo);
            setListAdapter(mAdapter);
            setTitle(R.string.title_working_albums);
            mAdapter.setViewBinder(new Binder());
            fetch(new FetchAlbumsTask());
        } else {
            setListAdapter(mAdapter);
            mAlbumCursor = mAdapter.getCursor();
            if (mAlbumCursor != null) {
                init(mAlbumCursor);
            } else {
                fetch(new FetchAlbumsTask());
            }
        }
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
        outcicle.putString("selectedalbum", mCurrentAlbumId);
        outcicle.putString("artist", mArtistId);
        super.onSaveInstanceState(outcicle);
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

    public void init(Cursor c) {

        mAdapter.changeCursor(c); // also sets mAlbumCursor

        if (mAlbumCursor == null) {
            closeContextMenu();
            return;
        }
        
        setTitle();
    }

    private void setTitle() {
        //CharSequence fancyName = "";
        //if (mAlbumCursor != null && mAlbumCursor.getCount() > 0) {
        //    mAlbumCursor.moveToFirst();
        //    fancyName = mAlbumCursor.getString(Music.ALBUM_MAPPING.ARTIST_NAME);
        //    if (fancyName == null || fancyName.equals(LockerDb.UNKNOWN_STRING))
        //        fancyName = getText(R.string.unknown_artist_name);
        //}

        //if (mArtistId != null && fancyName != null)
        //    setTitle(fancyName);
        //else
            setTitle(R.string.title_albums);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        menu.add(0, QUEUE, 0, R.string.menu_play_selection);
        menu.add(0, PLAY_SELECTION, 0, R.string.menu_play_selection);
        
        menu.add(0, SEARCH, 0, R.string.search);

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mAlbumCursor.moveToPosition(mi.position);
        mCurrentAlbumId = mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
        mCurrentAlbumName = mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));

        menu.setHeaderTitle(mCurrentAlbumName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected album
                mTracksTask = new FetchTracksTask().execute( Integer.valueOf( mCurrentAlbumId ), PLAY_SELECTION );
                return true;
            }

            case QUEUE: {
                mTracksTask = new FetchTracksTask();
                mTracksTask.execute( Integer.valueOf( mCurrentAlbumId ), QUEUE );
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                return true;
            }
            case DELETE_ITEM: {
                return true;
            }
            case SEARCH:
                return true;

        }
        return super.onContextItemSelected(item);
    }

    void doSearch() {
        CharSequence title = null;
        String query = null;
        
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        
        title = mCurrentAlbumName;
        i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentAlbumName);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                }
                break;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Cursor c = (Cursor) getListAdapter().getItem( position );
        String artist = c.getString(Music.ALBUM_MAPPING.ARTIST_ID);
        String album = c.getString(Music.ARTIST_MAPPING.ID);
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
        intent.putExtra("album", album);
        intent.putExtra("artist", artist);
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

            case SHUFFLE_ALL:
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
                int albumId = cursor.getInt(columnIndex);
                String id   = Integer.toString(albumId);
                view.getRemoteArtwork(id);
            }
            return true;
        }
    }
    

    
    private void killTasks(Bundle state)
    {
        if( mAlbumFetcher != null && mAlbumFetcher.getStatus() == AsyncTask.Status.RUNNING)
            mAlbumFetcher.cancel(true);
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING)
            mTracksTask.cancel( true );
        if( mArtFetcher != null && mArtFetcher.getStatus() == AsyncTask.Status.RUNNING) {
            mArtFetcher.cancel(true);
            if (state != null)
                state.putBoolean( "artfetch_in_progress", true );
            mArtFetcher = null;
        }
    }

    private class FetchAlbumsTask extends FetchBrowserCursor
    {
        @Override
        public Boolean doInBackground( Void... params )
        {
            try {
                mCursor = Music.getDb(getBaseContext()).getAlbumDataForBrowser(mFrom, mArtistId);
            } catch (Exception e) {
            	Log.w("Mp3Tunes", Log.getStackTraceString(e));
                return false;
            }
            return true;
        }
    }
    
    private class FetchTracksTask extends AsyncTask<Integer, Void, Boolean>
    {
        Cursor cursor;
        int action = -1;
        @Override
        public void onPreExecute()
        {
            Music.setSpinnerState(AlbumBrowser.this, true);
        }

        @Override
        public Boolean doInBackground( Integer... params )
        {
            if(params.length <= 1)
                return false;
            int album_id = params[0];
            action = params[1];
            try {
                    cursor = Music.getDb(getBaseContext()).getTracksForAlbum(album_id);
            } catch ( Exception e ) {
                System.out.println("Fetching tracks failed");
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        public void onPostExecute( Boolean result )
        {
            Music.setSpinnerState(AlbumBrowser.this, false);
            if( cursor != null && result) {
                switch ( action )
                {
                case PLAY_SELECTION:
                    Music.playAll(AlbumBrowser.this, cursor, 0);
                    break;
                case QUEUE:
                }
            } else
                System.out.println("CURSOR NULL");
        }
    }
   
}
