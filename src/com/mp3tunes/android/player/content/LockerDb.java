/***************************************************************************
 *   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
 *   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
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

package com.mp3tunes.android.player.content;

import java.io.IOException;
import java.util.List;

import org.json.JSONException;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.Session.LoginException;
import com.binaryelysium.mp3tunes.api.results.SearchResult;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.content.LockerCache.Progress;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;

/**
 * This class is essentially a wrapper for storing MP3tunes locker data in an
 * sqlite databse. It acts as a local cache of the metadata in a user's locker.
 * 
 * It is also used to handle the current playlist.
 * 
 */
public class LockerDb
{

    Context         mContext;
    Locker          mLocker;
    SQLiteDatabase  mDb;
    Queries         mQueries;
    LockerCache     mCache;
    
    public LockerDb(Context context)
    {
        mLocker = new Locker();
        // Open the database
        mDb = (new LockerDbHelper(context, null)).getWritableDatabase();
        if (mDb == null) {
            throw new SQLiteDiskIOException("Error creating database");
        }
        mDb.setLockingEnabled(true);

        mQueries = new Queries(this);

        mContext = context;
        mCache = LockerCache.loadCache(this);
    }

    public void close()
    {
        if (mDb != null) {
            mCache.saveCache(this);
            mDb.close();
        }
    }

    public void clearDB()
    {
        mCache.clearCache();
        mDb.delete(DbTables.TRACK, null, null);
        mDb.delete(DbTables.ALBUM, null, null);
        mDb.delete(DbTables.ARTIST, null, null);
        mDb.delete(DbTables.PLAYLIST, null, null);
        mDb.delete(DbTables.PLAYLIST_TRACKS, null, null);
        mDb.delete(DbTables.TOKEN, null, null);
        mDb.delete(DbTables.CACHE, null, null);
    }
 
    //Basic Getters returning api data structures
    
    public Track getTrack(String name)
    {
        String[] args  = new String[] {name};
        String   where = DbKeys.TITLE + "=?";
        return cursorToTrack(mDb.query(DbTables.TRACK, Music.TRACK, where, args, null, null, null));
    }
    
    public Track getTrack(LockerId id)
    {
        return cursorToTrack(mDb.query(DbTables.TRACK, Music.TRACK, "_id=" + id.asString(), null, null, null, null));
    }
    
    public Artist getArtistById(Id id)
    {
        return cursorToArtist(mDb.query(DbTables.ARTIST, Music.ARTIST, DbKeys.ID + "=" + id.asString(), null, null, null, null));
    }
    
    public Artist getArtistByName(String artistName)
    {
        return cursorToArtist(mDb.query(DbTables.ARTIST, Music.ARTIST, DbKeys.ARTIST_NAME + "=\"" + artistName + "\"", null, null, null, null));
    }

    public Album getAlbum(LockerId id)
    {
        return cursorToAlbum(mDb.query(DbTables.ALBUM, Music.ALBUM, DbKeys.ID + "=" + id.asInt(), null, null, null, null));
    };
    
    public Album getAlbum(String name)
    {
        return cursorToAlbum(mDb.query(DbTables.ALBUM, Music.ALBUM, DbKeys.ALBUM_NAME + "=\"" + name + "\"", null, null, null, null));
    }
    
    //Getters returning cursors
    
    public Cursor getRadioData(String[] from)throws IOException, LockerException
    {
        return mDb.query(DbTables.PLAYLIST, from, DbKeys.ID + " like 'PLAYMIX_GENRE_D%'", null, null,
                null, DbKeys.PLAYLIST_ORDER);
    }
    
    public Cursor getPlaylistData(String[] from)throws IOException, LockerException
    {
        return mDb.query(DbTables.PLAYLIST, from, DbKeys.ID + " not like 'PLAYMIX_GENRE_D%'", null, null,
                null, DbKeys.PLAYLIST_ORDER);
    }
    
    public Cursor getArtistData(String[] from, String where)throws IOException, LockerException
    {
        return mDb.query(DbTables.ARTIST, from, where, null, null, null,
                         DbKeys.ARTIST_NAME);
    }
    
    public Cursor getAlbumData(String[] from, String where) throws SQLiteException, IOException, LockerException
    {
        return mDb.query(DbTables.ALBUM, from, where, null, null, null,
                         DbKeys.ALBUM_NAME);
    }
    
    public Cursor getAlbumDataByArtist(String[] from, LockerId id) throws SQLiteException, IOException, LockerException, MakeQueryException
    {
            refreshAlbumsForArtist(id.asInt());
            return mDb.query(DbTables.ALBUM, from, DbKeys.ARTIST_ID + "=" + id.asString(), null, null, null,
                    DbKeys.ALBUM_NAME);
    }
    
    public Cursor getTrackDataByAlbum(String[] from, LockerId id) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + id.asString());
        if (id == null) return null;
        return mDb.query(DbTables.TRACK, from, DbKeys.ALBUM_ID + "=" + id.asString(), 
                null, null, null, DbKeys.TITLE);
    }
    
    public Cursor getTrackDataByArtist(String[] from, LockerId mId) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + mId.asString());
        if (mId == null) return null;
        return mDb.query(DbTables.TRACK, from, DbKeys.ARTIST_ID + "=" + mId.asString(), 
                null, null, null, DbKeys.TITLE);
    }
    
    //replace big query with view
    public Cursor getTrackDataByPlaylist(String[] from, LockerId mId) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + mId.asString());
        if (mId == null) return null;
        
        StringBuilder query = new StringBuilder("SELECT DISTINCT ")
        .append(DbTables.TRACK).append(".").append(DbKeys.ID).append(" ");
        for (String key : from) {
            query.append(key).append(", ");
        }
        query.append(DbKeys.PLAYLIST_INDEX).append(" ").append("FROM ")
        .append(DbTables.PLAYLIST).append(" JOIN ")
        .append(DbTables.PLAYLIST_TRACKS).append(" ").append("ON ")
        .append(DbTables.PLAYLIST).append(".").append(DbKeys.ID)
        .append(" = ").append(DbTables.PLAYLIST_TRACKS).append(".")
        .append(DbKeys.PLAYLIST_ID).append(" ").append("JOIN ")
        .append(DbTables.TRACK).append(" ").append("ON ")
        .append(DbTables.PLAYLIST_TRACKS).append(".").append(DbKeys.TRACK_ID)
        .append(" = ").append(DbTables.TRACK).append(".").append(DbKeys.ID)
        .append(" ").append("WHERE ").append(DbKeys.PLAYLIST_ID).append("='")
        .append(mId.asString()).append("' ").append("ORDER BY ")
        .append(DbKeys.PLAYLIST_INDEX);

        return mDb.rawQuery(query.toString(), null);
    }

    

    
    
    class DataInserter
    {
        
    private <T> void insert(T object, int index) throws IOException, SQLiteException
    {
        if (Track.class.isInstance(object)) {
            insert((Track)object, index);
        } else if (Artist.class.isInstance(object)) {
            insert((Artist)object, index);
        } else if (Album.class.isInstance(object)) {
            insert((Album)object, index);
        } else if (Playlist.class.isInstance(object)) {
            insert((Playlist)object, index);
        }
    }
    
    private void insert(Track track, int index) throws IOException, SQLiteException
    {

        if (track == null) {
            System.out.println("OMG TRACK NULL");
            return;
        }

        try {
            // Insert artist info to the artist table
            if (track.getArtistName().length() > 0) {
                if (!mQueries.artistExists(track.getArtistId())) {
                    mQueries.insertArtist(track);
                }
            }

            // Insert album info to the album table
            if (track.getAlbumTitle().length() > 0) {
                if (!mQueries.albumExists(track.getAlbumId())) {
                    mQueries.insertAlbum(track);
                }
            }

            // Insert track info
            if (!mQueries.trackExists(track.getId().asInt())) {
                mQueries.insertTrack(track);
            }
        } catch (SQLiteException e) {
            throw e;
        }
    }
    
    private void insert(Artist artist, int index) throws IOException, SQLiteException
    {
        if (artist == null) {
            System.out.println("OMG Artist NULL");
            return;
        }
        try {
            if (artist.getName().length() > 0) {
                if (mQueries.artistExists(artist.getId().asInt())) {
                    mQueries.updateArtist(artist);
                } else {
                    mQueries.insertArtist(artist);
                }
            }
        } catch (SQLiteException e) {
            throw e;
        }
    }

    private void insert(Album album, int index) throws IOException, SQLiteException
    {
        if (album == null) {
            System.out.println("OMG Album NULL");
            return;
        }
        try {
            if (album.getName().length() > 0) {
                if (mQueries.albumExists(album.getId().asInt())) {
                    mQueries.updateAlbum(album);
                } else {
                    mQueries.insertAlbum(album);
                }
            }
        } catch (SQLiteException e) {

            throw e;
        }
    }
    
    private void insert(final Playlist playlist, final int index) throws IOException, SQLiteException
    {
        if (playlist == null) {
            System.out.println("OMG Playlist NULL");
            return;
        }
        try {
            if (playlist.getName().length() > 0) {
                if (mQueries.playlistExists(playlist.getId().asString())) {
                    mQueries.updatePlaylist(playlist, index);
                } else {
                    mQueries.insertPlaylist(playlist, index);
                }
            }
        } catch (SQLiteException e) {
            throw e;
        }
    }
    
    }
    
    <T> boolean multiInsert(List<T> list, LockerCache.Progress progress) throws SQLiteException, IOException, MakeQueryException
    {
        DataInserter inserter = new DataInserter();
        mDb.beginTransaction();
        try {
            int i = 0;
            if (progress != null) i = progress.mCurrentSet * progress.mCount;
            for (T o: list) {
                inserter.insert(o, i);
                i++;
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return list.size() > 0;
    }


    
    private void refreshAlbumsForArtist(final int artist_id) throws SQLiteException,
            IOException, LockerException, MakeQueryException
    {
        DataInserter inserter = new DataInserter();
        List<Album> albums = mLocker.getAlbumsForArtist(artist_id);

        System.out.println("beginning insertion of " + albums.size()
                + " albums for artist id " + artist_id);
        mDb.beginTransaction();
        try {
            for (Album a : albums) {
                inserter.insert(a, 0);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }  
        System.out.println("insertion complete");
    }
    
    

    
    void deleteOldPlaylistTracks(String playlist_id)
    {
        String   where     = "playlist_id=?";
        String[] whereArgs = new String[] {playlist_id};
        mDb.delete(DbTables.PLAYLIST_TRACKS, where, whereArgs);
    }
    
    boolean insertTracksForPlaylist(List<Track> tracks, String playlist_id, LockerCache.Progress progress) throws SQLiteException, IOException, MakeQueryException
    {
        System.out.println("beginning insertion of " + tracks.size()
                + " tracks for playlist id " + playlist_id);
        
        multiInsert(tracks, progress);
        
        int index = 0 + (progress.mCount * progress.mCurrentSet);
        ContentValues cv = new ContentValues();
        for (Track t : tracks) {
            if (!mQueries.trackInPlaylist(playlist_id, t.getId().asInt())) {
                cv.put(DbKeys.PLAYLIST_ID, playlist_id);
                cv.put(DbKeys.TRACK_ID, t.getId().asInt());
                cv.put(DbKeys.PLAYLIST_INDEX, index);
                mDb.insert(DbTables.PLAYLIST_TRACKS, DbKeys.UNKNOWN_STRING, cv);
                index++;
            }
        }
        return tracks.size() > 0;
    }
    
    
    
    void updateCache(String id, long time, int state, LockerCache.Progress progress) throws MakeQueryException 
    {
        try {
            if (mQueries.cacheExists(id)) {
                mQueries.updateCache(id, time, state, progress);
            } else {
                mQueries.insertCache(id, time, state, progress);
            }
        } catch (SQLiteException e) {
            throw e;
        }
    }
    
    public Cursor getCache(String[] projection)
    {
        return mDb.query(DbTables.CACHE, projection, null, null, null, null, null);
    }
    

    //helpers to turn cursors into api data structures
    
    private Track cursorToTrack(Cursor c) 
    {
        if (c == null) return null;
        try {
            if (c.moveToFirst()) {
                int id              = c.getInt(Music.TRACK_MAPPING.ID);
                int artist_id       = c.getInt(Music.TRACK_MAPPING.ARTIST_ID);
                int album_id        = c.getInt(Music.TRACK_MAPPING.ALBUM_ID);
                String play_url     = c.getString(Music.TRACK_MAPPING.PLAY_URL);
                String title        = c.getString(Music.TRACK_MAPPING.TITLE);
                String artist_name  = c.getString(Music.TRACK_MAPPING.ARTIST_NAME);
                String album_name   = c.getString(Music.TRACK_MAPPING.ALBUM_NAME);
                Track t = new ConcreteTrack(new LockerId(id), play_url, title,
                                artist_id, artist_name, album_id, album_name);
                return t;
            }
        } finally {
            c.close();
        }
        return null;
    }
    
    private Artist cursorToArtist(Cursor c)
    {
        if (c == null) return null;
        try {
        if (c.moveToFirst()) {
            Artist a = new Artist(new LockerId(c.getInt(Music.ARTIST_MAPPING.ID)), c.getString(Music.ARTIST_MAPPING.ARTIST_NAME));
            c.close();
            return a;
        }
        } finally {
            c.close();
        }
        return null;
    }
    
    private Album cursorToAlbum(Cursor c)
    {
        if (c.getCount() > 1)
            Log.e("Mp3Tunes", "Got more than one album");
        if (c.moveToFirst()) {
            Album a = new Album(new LockerId(c.getInt(Music.ALBUM_MAPPING.ID)), c.getString(Music.ALBUM_MAPPING.ALBUM_NAME));
            c.close();
            return a;
        }
        c.close();
        return null;
    }
    

    //Refresh tasks that are not cached
    
    static public class RefreshAlbumTracksTask extends RefreshTask
    {
        Id mId;
        
        public RefreshAlbumTracksTask(LockerDb db, Id id)
        {
            super(db);
            mId = id;
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            Log.w("Mp3Tunes", "Starting RefreshTracks for album");
                try {
                    AlbumGetter getter = new AlbumGetter(mDb, mDb.mContext.getContentResolver());
                    LockerId id = getter.getLockerId(mId);
                    if (id == null) return true;
                    List<Track> tracks = mDb.mLocker.getTracksForAlbumFromJson(id.asInt());
                    mDb.multiInsert(tracks, null);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException,
                MakeQueryException
        {
            return false;
        }
    }
    
    static public class RefreshArtistTracksTask extends RefreshTask
    {
        Id mId;
        
        public RefreshArtistTracksTask(LockerDb db, Id id)
        {
            super(db);
            mId = id;
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            Log.w("Mp3Tunes", "Starting RefreshTracks for artist");
            try {            
                ArtistGetter getter = new ArtistGetter(mDb, mDb.mContext.getContentResolver());
                LockerId id = getter.getLockerId(mId);
                if (id == null) return true;
                List<Track> tracks = mDb.mLocker.getTracksForArtistFromJson(id.asInt());
                mDb.multiInsert(tracks, null);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException,
                MakeQueryException
        {
            return false;
        }
    }
    
    
    static public class RefreshSearchTask extends RefreshTask
    {
        private   String[]       mTrack;
        private   String[]       mArtist;
        private   DbSearchQuery  mQuery;
        protected DbSearchResult mResult;
        
        public RefreshSearchTask(LockerDb db, DbSearchQuery query, String[] track, String[] artist)
        {
            super(db);
            mQuery  = query;
            mTrack  = track;
            mArtist = artist;
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                refresh();
                System.out.println("insertion complete");
                
                mResult = new DbSearchResult();
                if (mQuery.mTracks)
                    mResult.mTracks = querySearch(mQuery.mQuery, Music.Meta.TRACK, mTrack);
                if (mQuery.mArtists)
                    mResult.mArtists = querySearch(mQuery.mQuery, Music.Meta.ARTIST, mArtist);
                
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        
        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException,
                MakeQueryException
        {
            return false;
        }
        
        public void refresh() throws LockerException, InvalidSessionException, JSONException, LoginException, SQLiteException, IOException
        {
            SearchResult results = null;
            results = mDb.mLocker.search(mQuery.mQuery);
        
            DataInserter inserter = mDb.new DataInserter();
            for (Artist a : results.getArtists()) {
                inserter.insert(a, 0);
            }
            for (Album a : results.getAlbums()) {
                inserter.insert(a, 0);
            }
            for (Track t : results.getTracks()) {
                inserter.insert(t, 0);
            }
            System.out.println("insertion complete");
        }
        
        private Cursor querySearch(String query, Music.Meta type, String[] columns)
        {
            String table;
            String selection;
            String[] selectionArgs = new String[] {"%" + query + "%"};
            switch (type) {
                case TRACK:
                    table = "track";
                    selection = "lower(title) LIKE lower(?)";
                    break;
                case ARTIST:
                    table = "artist";
                    columns = Music.ARTIST;
                    selection = "lower(artist_name) LIKE lower(?)";
                    break;
                case ALBUM:
                    table = "album";
                    selection = "lower(album_name) LIKE lower(?)";
                    break;
                default:
                    return null;
            }
            return mDb.mDb.query(table, columns, selection, selectionArgs, null, null, null,
                    null);
        }
        
        static public class DbSearchResult
        {
            public Cursor mArtists = null;
            public Cursor mAlbums  = null;
            public Cursor mTracks  = null;
        }
        
        static public class DbSearchQuery
        {
            public DbSearchQuery(String query, boolean artists, boolean albums,
                    boolean tracks)
            {
                mQuery = query;
                mArtists = artists;
                mAlbums = albums;
                mTracks = tracks;
            }

            public String  mQuery;
            public boolean mArtists;
            public boolean mAlbums;
            public boolean mTracks;
        }
    }
    
    
//    //The code that manages search
//    //TODO: There is perhaps no code in the project that needs rewriting more than this
//    
//    public DbSearchResult search(DbSearchQuery query, String[] track, String[] artist) throws MakeQueryException
//    {
//        try {
//            // Perform the single http search call
//            refreshSearch(query.mQuery);
//            DbSearchResult res = new DbSearchResult();
//            if (query.mTracks)
//                res.mTracks = querySearch(query.mQuery, Music.Meta.TRACK, track);
//            //if (query.mAlbums)
//            //    res.mAlbums = querySearch(query.mQuery, Music.Meta.ALBUM);
//            if (query.mArtists)
//                res.mArtists = querySearch(query.mQuery, Music.Meta.ARTIST, artist);
//
//            System.out.println("Got artists: " + res.mArtists.getCount());
//            System.out.println("Got tracks: " + res.mTracks.getCount());
//            return res;
//        } catch (SQLiteException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (LockerException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//    
//    public void refreshSearch(String query) throws SQLiteException, IOException, LockerException, MakeQueryException
//    {
//        SearchResult results = null;
//        try {
//            results = mLocker.search(query);
//        } catch (InvalidSessionException e) {
//            throw new LockerException("Bad Session Data");
//        } catch (JSONException e) {
//            throw new LockerException("Sever Sent Corrupt Data");
//        } catch (LoginException e) {
//            throw new LockerException("Unable to refresh session");
//        }
//        
//        DataInserter inserter = new DataInserter();
//        for (Artist a : results.getArtists()) {
//            inserter.insert(a, 0);
//        }
//        for (Album a : results.getAlbums()) {
//            inserter.insert(a, 0);
//        }
//        for (Track t : results.getTracks()) {
//            inserter.insert(t, 0);
//        }
//        System.out.println("insertion complete");
//    }
//    private Cursor querySearch(String query, Music.Meta type, String[] columns)
//    {
//        String table;
//        String selection;
//        String[] selectionArgs = new String[] {"%" + query + "%"};
//        switch (type) {
//            case TRACK:
//                table = "track";
//                selection = "lower(title) LIKE lower(?)";
//                break;
//            case ARTIST:
//                table = "artist";
//                columns = Music.ARTIST;
//                selection = "lower(artist_name) LIKE lower(?)";
//                break;
//            case ALBUM:
//                table = "album";
//                selection = "lower(album_name) LIKE lower(?)";
//                break;
//            default:
//                return null;
//        }
//        return mDb.query(table, columns, selection, selectionArgs, null, null, null,
//                null);
//    }
//    public class DbSearchResult
//    {
//        public Cursor mArtists = null;
//        public Cursor mAlbums  = null;
//        public Cursor mTracks  = null;
//    }
//    public class DbSearchQuery
//    {
//        public DbSearchQuery(String query, boolean artists, boolean albums,
//                boolean tracks)
//        {
//            mQuery = query;
//            mArtists = artists;
//            mAlbums = albums;
//            mTracks = tracks;
//        }
//
//        public String  mQuery;
//        public boolean mArtists;
//        public boolean mAlbums;
//        public boolean mTracks;
//    }
    
}
