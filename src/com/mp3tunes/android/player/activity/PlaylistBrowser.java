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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.content.LockerDb.RefreshPlaylistsTask;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;
import com.mp3tunes.android.player.util.FetchAndPlayTracks;
import com.mp3tunes.android.player.util.ReindexingCursorWrapper;
import com.mp3tunes.android.player.util.ReindexingCursorWrapper.CursorIndexer;

public class PlaylistBrowser extends BaseMp3TunesListActivity
    implements View.OnCreateContextMenuListener, Music.Defs
{
    public static final String DOWNLOADED_TRACKS_ID   = "-1";
    public static final String DOWNLOADED_TRACKS_NAME = "Local music";
    
    private LockerId            mCurrentPlaylistId;
    private String              mCurrentPlaylistName;
    private SimpleCursorAdapter mAdapter;
    private boolean             mAdapterSent;
    
    private boolean mShowingDialog;
    
    private AsyncTask<Void, Void, Boolean> mPlayTracksTask;
    
    private int         mWorkingTitle;
    private int         mTitle;
    private Music.Meta  mType;
    private boolean     mIsRadio;
    
    String[] mFrom = new String[] {
            DbKeys.ID,
            DbKeys.PLAYLIST_NAME,
            DbKeys.FILE_COUNT,
            DbKeys.PLAYLIST_ORDER
      };
    
    int[] mTo = new int[] {
            R.id.icon,
            R.id.line1,
            R.id.line2,
            0
    };
    
    static class FROM_MAPPING {
        static final int ID             = 0;
        static final int NAME           = 1;
        static final int PLAYLIST_COUNT = 2;
        static final int PLAYLIST_ORDER = 3;
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.bindToService(this);
        Music.ensureSession(this);

        setContentView(R.layout.media_picker_activity);
        ListView lv = getListView();
        lv.setFastScrollEnabled(true);
        lv.setOnCreateContextMenuListener(this);
        lv.setTextFilterEnabled(true);

        Intent intent = getIntent();
        String mimeType = intent.getType();
        if (mimeType.equals("vnd.mp3tunes.android.dir/playlist"))
            mIsRadio = false;
        else if (mimeType.equals("vnd.mp3tunes.android.dir/radio"))
            mIsRadio = true;
        else {
            Log.e("Mp3Tunes Intent", "\"" + mimeType + "\"");
            mIsRadio = false;
        }
        
        int progressText;
        int errorText;
        if (!mIsRadio) {
            mTitle        = R.string.title_playlists;
            mWorkingTitle = R.string.title_working_playlists;
            mType         = Music.Meta.PLAYLIST;
            progressText  = R.string.loading_playlists;
            errorText     = R.string.playlist_browser_error;
        } else {
            mTitle        = R.string.title_radio;
            mWorkingTitle = R.string.title_working_radio;
            mType         = Music.Meta.RADIO;
            progressText  = R.string.loading_playmix;
            errorText     = R.string.playmix_browser_error;
        }
        
        buildErrorDialog(errorText);
        buildProgressDialog(progressText);
               
        mAdapter = (SimpleCursorAdapter) getLastNonConfigurationInstance();
        if (mAdapter == null) {
            mAdapter = new SimpleCursorAdapter(this, R.layout.track_list_item, mCursor, mFrom, mTo);
            setListAdapter(mAdapter);
            setTitle(mWorkingTitle);
            mAdapter.setViewBinder(new Binder());
            mLoadingCursor = true;
            mCursorTask = new FetchPlaylistsTask(Music.getDb(getBaseContext()));
            mCursorTask.execute((Void[])null);
            showDialog(PROGRESS_DIALOG);
            mShowingDialog = true;
        } else {
            setListAdapter(mAdapter);
            mCursor = mAdapter.getCursor();
            if (mCursor == null) {
                setTitle(mWorkingTitle);
                mLoadingCursor = true;
                mCursorTask = new FetchPlaylistsTask(Music.getDb(getBaseContext()));
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
        killTasks();
        
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        if (mCurrentPlaylistId != null)
            outcicle.putString("selectedalbum", mCurrentPlaylistId.asString());
        outcicle.putString("artist", mArtistId);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onStop()
    {
        killTasks();
        super.onStop();
    }
    
    @Override
    public void onDestroy() 
    {
        killTasks();

        Music.unbindFromService(this);
        if (!mAdapterSent) {
            Cursor c = mAdapter.getCursor();
            if (c != null) {
                c.close();
            }
        }
        Music.unconnectFromDb( this );
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

    public void init(Cursor c, int refreshNext) 
    {
        tryDismissProgress(mShowingDialog, c);
        mAdapter.changeCursor(c);

        mCursor = c;
        setTitle();
        super.init(c, refreshNext);
    }

    private void setTitle() {
        setTitle(mTitle);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {        
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;

        menu.add(0, PLAY_SELECTION, 0, R.string.menu_play_selection);

        mCursor.moveToPosition(mi.position);
        mCurrentPlaylistName = mCursor.getString(FROM_MAPPING.NAME);
        mCurrentPlaylistId = new LockerId(mCursor.getString(FROM_MAPPING.ID));
        
        if (mIsRadio) {
            mCurrentPlaylistName = mCurrentPlaylistName
                                       .replaceFirst("\\* ", "")
                                       .replaceFirst(" \\(PlayMix\\)", "");
        }
        menu.setHeaderTitle(mCurrentPlaylistName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected playlist
                mPlayTracksTask = new FetchAndPlayTracks(FetchAndPlayTracks.FOR.PLAYLIST, mCurrentPlaylistId, this).execute();
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        
        Cursor c = (Cursor) getListAdapter().getItem( position );
        String playlist = c.getString(FROM_MAPPING.ID);
        String playlist_name = c.getString(FROM_MAPPING.NAME);
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
        intent.putExtra("playlist", playlist);
        intent.putExtra("playlist_name", playlist_name);
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
        menu.findItem(R.id.menu_opt_shuffleall).setVisible( false );
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
        }
        return super.onOptionsItemSelected(item);
    }
    
    class Binder implements SimpleCursorAdapter.ViewBinder
    {
        private final BitmapDrawable mDefaultIcon;
        private static final int  mUnknown = R.string.unknown_playlist_name;
        
        public Binder ()
        {
            Bitmap b;
            if (mIsRadio)
                b = BitmapFactory.decodeResource(getResources(), R.drawable.radio_icon);
            else 
                b = BitmapFactory.decodeResource(getResources(), R.drawable.playlist_icon);

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
                if (val == null) view.setText(mUnknown);
                if (mIsRadio) {
                    val = val.replaceFirst("\\* ", "").replaceFirst(" \\(PlayMix\\)", "");
                }
                view.setText(val);
            } else if (columnIndex == 2) {
                String text = "";
                if(!mIsRadio && !cursor.getString(1).equals("Inbox") && !cursor.getString(0).equals(DOWNLOADED_TRACKS_ID)) {
                    int numsongs  = cursor.getInt(columnIndex);
                    text = Music.makeAlbumsLabel(getBaseContext(), numsongs, numsongs, true);
                }
                TextView view = ((TextView)v.findViewById(R.id.line2));
                view.setText(text);
            } else if (columnIndex == 0) {
                ImageView view = (ImageView)v.findViewById(R.id.icon);
                view.setImageDrawable(mDefaultIcon);
                view.setPadding(0, 0, 1, 0);
            }
            
            return true;
        }
    }
    
    private void killTasks()
    {
        Log.w("Mp3Tunes", "Trying to kill tasks");
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING) {
            Log.w("Mp3Tunes", "Killing tracks task");
            mTracksTask.cancel(true);
        }
        if( mCursorTask != null && mCursorTask.getStatus() == AsyncTask.Status.RUNNING) {
            Log.w("Mp3Tunes", "Killing playlist task");
            mCursorTask.cancel(true);
            mLoadingCursor = false;
        }
    }
    
    private String mArtistId;
    
    
    private class FetchPlaylistsTask extends RefreshPlaylistsTask
    {
        public FetchPlaylistsTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            mLoadingCursor = false;
            if (!result) {
                Log.w("Mp3Tunes", "Got Error Fetching Playlists");
            }
            mTracksTask = new LockerDb.RefreshTracksTask(Music.getDb(getBaseContext()));
            mTracksTask.execute((Void[])null);
        }
    };
    
    @Override
    protected void updateCursor()
    {
        try {
            if (PlaylistBrowser.this.mIsRadio)
                mCursor = Music.getDb(getBaseContext()).getRadioData(mFrom);
            else {
                //Hack to create a downloaded tracks playlist Until we can merge local and remote playlists
                Cursor c = Music.getDb(getBaseContext()).getPlaylistData(mFrom);
                MatrixCursor cursor = new MatrixCursor(mFrom);
                MatrixCursor.RowBuilder builder = cursor.newRow();
                builder.add(DOWNLOADED_TRACKS_ID);
                builder.add(DOWNLOADED_TRACKS_NAME);
                builder.add("0");
                builder.add("0");
                mCursor = new ReindexingCursorWrapper(new MergeCursor(new Cursor[] {cursor, c}), new PlaylistIndexer(), FROM_MAPPING.NAME);
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    
    class PlaylistIndexer implements CursorIndexer
    {   
        
        public int[] get(Cursor c, int column)
        {
            List <Pair> list = Pair.getListOfPairs(c, column);       
            list = alphabetizeGenerated(list);
            return Pair.createIndicies(list);
        }
        
        List<Pair> alphabetizeGenerated(List<Pair> list)
        {
            List<Pair> generated    = new LinkedList<Pair>();
            List<Pair> nonGenerated = new LinkedList<Pair>();
            for (Pair p : list) {
                if (Playlist.isDynamicPlaylistByName(p.value))
                    generated.add(p);
                else if (p.value.equals(DOWNLOADED_TRACKS_NAME))
                    generated.add(p);
                else
                    nonGenerated.add(p);
            }
            Collections.sort(generated, new PairComparator());
            generated.addAll(nonGenerated);
            return generated;
        }
    }
}

