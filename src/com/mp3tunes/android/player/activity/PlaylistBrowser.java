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
import android.content.res.Resources;
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

import com.mp3tunes.android.player.LockerDb;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.MusicAlphabetIndexer;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;

public class PlaylistBrowser extends BaseMp3TunesListActivity
    implements View.OnCreateContextMenuListener, Music.Defs
{
    private String              mCurrentPlaylistId;
    private String              mCurrentPlaylistName;
    private SimpleCursorAdapter mAdapter;
    private boolean             mAdapterSent;
    
    private static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
    private static final int EDIT_PLAYLIST   = CHILD_MENU_BASE + 2;
    private static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
    
    private AsyncTask<Void, Void, Boolean>   mPlaylistTask;
    private AsyncTask<String, Void, Boolean> mTracksTask;
    
    private int         mWorkingTitle;
    private int         mTitle;
    private Music.Meta  mType;
    private boolean     mIsRadio;
    
    String[] mFrom = new String[] {
            LockerDb.KEY_ID,
            LockerDb.KEY_PLAYLIST_NAME,
            LockerDb.KEY_FILE_COUNT,
            LockerDb.KEY_PLAYLIST_ORDER
      };
    
    int[] mTo = new int[] {
            R.id.icon,
            R.id.line1,
            R.id.line2,
            0
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.bindToService(this);

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
            mAdapter = new SimpleCursorAdapter(this, R.layout.track_list_item, mPlaylistCursor, mFrom, mTo);
            setListAdapter(mAdapter);
            setTitle(mWorkingTitle);
            mAdapter.setViewBinder(new Binder());
            fetch(new FetchPlaylistsTask());
        } else {
            setListAdapter(mAdapter);
            mPlaylistCursor = mAdapter.getCursor();
            if (mPlaylistCursor != null) {
                init(mPlaylistCursor);
            } else {
                setTitle(mWorkingTitle);
                fetch(new FetchPlaylistsTask());
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
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING)
            mTracksTask.cancel( true );
        if( mPlaylistTask != null && mPlaylistTask.getStatus() == AsyncTask.Status.RUNNING)
            mPlaylistTask.cancel( true );
        
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString("selectedalbum", mCurrentPlaylistId);
        outcicle.putString("artist", mArtistId);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onDestroy() 
    {
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING)
            mTracksTask.cancel( true );
        if( mPlaylistTask != null && mPlaylistTask.getStatus() == AsyncTask.Status.RUNNING)
            mPlaylistTask.cancel( true );
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

    public void init(Cursor c) {

        mAdapter.changeCursor(c); // also sets mPlaylistCursor

        if (mPlaylistCursor == null) {
            closeContextMenu();
            return;
        }
        setTitle();
    }

    private void setTitle() {
        setTitle(mTitle);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {        
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;

        menu.add(0, PLAY_SELECTION, 0, R.string.menu_play_selection);

        mPlaylistCursor.moveToPosition(mi.position);
        mCurrentPlaylistName = mPlaylistCursor.getString(Music.PLAYLIST_MAPPING.PLAYLIST_NAME);
        mCurrentPlaylistId = mPlaylistCursor.getString(Music.PLAYLIST_MAPPING.ID);
        menu.setHeaderTitle(mCurrentPlaylistName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected playlist
                mTracksTask = new FetchTracksTask().execute( mCurrentPlaylistId, Integer.toString( PLAY_SELECTION ) );
                return true;
            }

            case DELETE_PLAYLIST: {
                return true;
            }

            case EDIT_PLAYLIST: {
                return true;
            }

            case RENAME_PLAYLIST: {
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        
        Cursor c = (Cursor) getListAdapter().getItem( position );
        String playlist = c.getString(Music.PLAYLIST_MAPPING.ID);
        String playlist_name = c.getString(Music.PLAYLIST_MAPPING.PLAYLIST_NAME);
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
    
  //TODO need to implement caching system for album art this is killing performance
    class Binder implements SimpleCursorAdapter.ViewBinder
    {
        private final BitmapDrawable mDefaultIcon;
        private static final int  mUnknown = R.string.unknown_playlist_name;
        
        public Binder ()
        {
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.playlist_icon);
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
                if(!mIsRadio) {
                    int numalbums = 0;
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
    
//    class PlaylistListAdapter extends SimpleCursorAdapter implements SectionIndexer {
//
//        private final BitmapDrawable mDefaultPlaylistIcon;
//        private int mPlaylistNameIdx;
//        private int mNumSongsIdx;
//        private final Resources mResources;
//        private final String mUnknownPlaylist;
//        private AlphabetIndexer mIndexer;
//        private PlaylistBrowser mActivity;
//        
//        class ViewHolder {
//            TextView line1;
//            TextView line2;
//            TextView duration;
//            ImageView play_indicator;
//            ImageView icon;
//        }
//
//        PlaylistListAdapter(Context context, PlaylistBrowser currentactivity,
//                int layout, Cursor cursor, String[] from, int[] to) {
//            super(context, layout, cursor, from, to);
//
//            mActivity = currentactivity;
//          
//            mUnknownPlaylist = context.getString(R.string.unknown_playlist_name);
//
//            Resources r = context.getResources();
//            r.getDrawable(R.drawable.indicator_ic_mp_playing_list);
//
//            Bitmap b = BitmapFactory.decodeResource(r, R.drawable.playlist_icon);
//            mDefaultPlaylistIcon = new BitmapDrawable(b);
//            // no filter or dither, it's a lot faster and we can't tell the difference
//            mDefaultPlaylistIcon.setFilterBitmap(false);
//            mDefaultPlaylistIcon.setDither(false);
//            getColumnIndices(cursor);
//            mResources = context.getResources();
//        }
//
//        private void getColumnIndices(Cursor cursor) {
//            if (cursor != null) {
//                mPlaylistNameIdx = Music.PLAYLIST_MAPPING.PLAYLIST_NAME;
//                mNumSongsIdx = Music.PLAYLIST_MAPPING.FILE_COUNT;
//                
//                if (mIndexer != null) {
//                    mIndexer.setCursor(cursor);
//                } else {
//                    mIndexer = new MusicAlphabetIndexer(cursor, mPlaylistNameIdx, mResources.getString(
//                            R.string.alphabet));
//                }
//            }
//        }
//        
//        public void setActivity(PlaylistBrowser newactivity) {
//            mActivity = newactivity;
//        }
//
//        @Override
//        public View newView(Context context, Cursor cursor, ViewGroup parent) {
//           View v = super.newView(context, cursor, parent);
//           ViewHolder vh = new ViewHolder();
//           vh.line1 = (TextView) v.findViewById(R.id.line1);
//           vh.line2 = (TextView) v.findViewById(R.id.line2);
//           vh.duration = (TextView) v.findViewById(R.id.duration);
//           vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
//           vh.icon = (ImageView) v.findViewById(R.id.icon);
//           vh.icon.setBackgroundDrawable(mDefaultPlaylistIcon);
//           vh.icon.setPadding(0, 0, 1, 0);
//           v.setTag(vh);
//           return v;
//        }
//
//        @Override
//        public void bindView(View view, Context context, Cursor cursor) {
//            
//            ViewHolder vh = (ViewHolder) view.getTag();
//
//            String name = cursor.getString(mPlaylistNameIdx);
//            String displayname = name;
//            boolean unknown = name == null || name.equals(LockerDb.UNKNOWN_STRING); 
//            if (unknown) {
//                displayname = mUnknownPlaylist;
//            }
//            if (mIsRadio) {
//                displayname = displayname.replaceFirst("\\* ", "").replaceFirst(" \\(PlayMix\\)", "");
//            }
//            vh.line1.setText(displayname);
//            
//            if(!mIsRadio) {
//                int numalbums = cursor.getInt(mNumSongsIdx);
//                int numsongs = cursor.getInt(mNumSongsIdx);
//                displayname = Music.makeAlbumsLabel( context, numalbums, numsongs, true );
//                vh.line2.setText(displayname);
//            }
//        }
//        
//        @Override
//        public void changeCursor(Cursor cursor) {
//            if (cursor != mActivity.mPlaylistCursor) {
//                mActivity.mPlaylistCursor = cursor;
//                getColumnIndices(cursor);
//                super.changeCursor(cursor);
//            }
//        }
//        
//        @Override
//        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
//            return null;
//        }
//        
//        public Object[] getSections() {
//            return mIndexer.getSections();
//        }
//        
//        public int getPositionForSection(int section) {
//            return mIndexer.getPositionForSection(section);
//        }
//        
//        public int getSectionForPosition(int position) {
//            return 0;
//        }
//    }

    private Cursor mPlaylistCursor;
    private String mArtistId;
    
    private class FetchPlaylistsTask extends FetchBrowserCursor
    {
        @Override
        public Boolean doInBackground( Void... params )
        {
            try {
                if (PlaylistBrowser.this.mIsRadio)
                    mCursor = Music.getDb(getBaseContext()).getRadioDataForBrowser(mFrom);
                else 
                    mCursor = Music.getDb(getBaseContext()).getPlaylistDataForBrowser(mFrom);
            } catch ( Exception e ) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }
    
    private class FetchTracksTask extends AsyncTask<String, Void, Boolean>
    {
        //String[] tokens= null;
        Cursor cursor;
        int action = -1;
        @Override
        public void onPreExecute()
        {
            Music.setSpinnerState(PlaylistBrowser.this, true);
        }

        @Override
        public Boolean doInBackground( String... params )
        {
            if(params.length <= 1)
                return false;
            String playlist_id = params[0];
            action = Integer.valueOf( params[1] );
            try {
                cursor = Music.getDb(getBaseContext()).getTracksForPlaylist(playlist_id);
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
            Music.setSpinnerState(PlaylistBrowser.this, false);
            if( cursor != null && result) {
                switch ( action ) {
                case PLAY_SELECTION:
                    Music.playAll(PlaylistBrowser.this, cursor, 0);
                    break;
                }
            } else
                System.out.println("CURSOR NULL");
        }
    }
}

