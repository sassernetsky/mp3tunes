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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.json.JSONException;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
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
import com.mp3tunes.android.player.Music.Meta;
import com.mp3tunes.android.player.Music.TRACK_MAPPING;

/**
 * This class is essentially a wrapper for storing MP3tunes locker data in an
 * sqlite databse. It acts as a local cache of the metadata in a user's locker.
 * 
 * It is also used to handle the current playlist.
 * 
 */
public class LockerDb
{

    private Context            mContext;
    private Locker             mLocker;
    SQLiteDatabase             mDb;
    private Queries            mQueries;
    private LockerCache        mCache;
    
    public LockerDb(Context context)
    {
        mLocker = new Locker();
        // Open the database
        mDb = (new LockerDbHelper(context, null)).getWritableDatabase();
        if (mDb == null) {
            throw new SQLiteDiskIOException("Error creating database");
        }

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
                         "lower(" + DbKeys.ARTIST_NAME + ")");
    }
    
    public Cursor getAlbumData(String[] from, String where) throws SQLiteException, IOException, LockerException
    {
        return mDb.query(DbTables.ALBUM, from, where, null, null, null,
                         "lower(" + DbKeys.ALBUM_NAME + ")");
    }
    
    public Cursor getAlbumDataByArtist(String[] from, LockerId id) throws SQLiteException, IOException, LockerException
    {
            refreshAlbumsForArtist(id.asInt());
            return mDb.query(DbTables.ALBUM, from, DbKeys.ARTIST_ID + "=" + id.asString(), null, null, null,
                    "lower(" + DbKeys.ALBUM_NAME + ")");
    }
    
    public Cursor getTrackDataByAlbum(String[] from, LockerId id) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + id.asString());
        if (id == null) return null;
//        SQLiteStatement albumTrackCount = mDb.compileStatement("SELECT " + DbKeys.TRACK_COUNT
//                + " FROM album WHERE " + DbKeys.ID + "=" + id.asString());
        
//        int idNum = id.asInt();
//        try {
//            long count = albumTrackCount.simpleQueryForLong();
//            
//            Cursor c = mDb.query(DbTables.TRACK, from, DbKeys.ALBUM_ID + "=" + id.asString(), 
//                                 null, null, null, "lower(" + DbKeys.TITLE + ")");
//            if (c.getCount() == count)
//                return c;
//            else
//                c.close();
//        } catch (SQLiteDoneException e) {}
//        refreshTracksforAlbum(idNum);
        return mDb.query(DbTables.TRACK, from, DbKeys.ALBUM_ID + "=" + id.asString(), 
                null, null, null, "lower(" + DbKeys.TITLE + ")");
    }
    
    public Cursor getTrackDataByArtist(String[] from, LockerId mId) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + mId.asString());
        if (mId == null) return null;
//        SQLiteStatement albumTrackCount = mDb.compileStatement("SELECT " + DbKeys.TRACK_COUNT
//                + " FROM artist WHERE " + DbKeys.ID + "=" + mId.asString());
//        int idNum = mId.asInt();
//        try {
//            long count = albumTrackCount.simpleQueryForLong();
//            
//            Cursor c = mDb.query(DbTables.TRACK, from, DbKeys.ARTIST_ID + "=" + mId.asString(), 
//                    null, null, null, "lower(" + DbKeys.TITLE + ")");
//            if (c.getCount() == count)
//                return c;
//            else
//                c.close();
//        } catch (SQLiteDoneException e) {}
//        refreshTracksforArtist(idNum);
        return mDb.query(DbTables.TRACK, from, DbKeys.ARTIST_ID + "=" + mId.asString(), 
                null, null, null, "lower(" + DbKeys.TITLE + ")");
    }
    
    public Cursor getTrackDataByPlaylist(String[] from, LockerId mId) throws SQLiteException, IOException, LockerException
    {
        System.out.println("querying for tracks on album: " + mId.asString());
        if (mId == null) return null;
//        SQLiteStatement albumTrackCount = mDb.compileStatement("SELECT " + DbKeys.FILE_COUNT
//                + " FROM playlist WHERE " + DbKeys.ID + "=\"" + mId.asString() + "\"");
        
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

        Cursor c = mDb.rawQuery(query.toString(), null);
        
        
        return mDb.rawQuery(query.toString(), null);
    }
    
    
    public Track getTrack(String name)
    {
        
        Cursor c = mDb.query(DbTables.TRACK, Music.TRACK, DbKeys.TITLE + "=\"" + name + "\"", null, null, null, null);
        if (c.moveToFirst()) {
            int id              = c.getInt(Music.TRACK_MAPPING.ID);
            int track           = c.getInt(Music.TRACK_MAPPING.TRACKNUM);
            int artist_id       = c.getInt(Music.TRACK_MAPPING.ARTIST_ID);
            int album_id        = c.getInt(Music.TRACK_MAPPING.ALBUM_ID);
            String play_url     = c.getString(Music.TRACK_MAPPING.PLAY_URL);
            String download_url = c.getString(Music.TRACK_MAPPING.DOWNLOAD_URL);
            String title        = c.getString(Music.TRACK_MAPPING.TITLE);
            String artist_name  = c.getString(Music.TRACK_MAPPING.ARTIST_NAME);
            String album_name   = c.getString(Music.TRACK_MAPPING.ALBUM_NAME);
            String cover_url    = c.getString(Music.TRACK_MAPPING.COVER_URL);
            Track t = new ConcreteTrack(new LockerId(id), play_url, title,
                                artist_id, artist_name, album_id, album_name);
            c.close();
            return t;
        }
        c.close();
        return null;
    }
    
    public Track getTrack(LockerId id)
    {
        
        Cursor c = mDb.query(DbTables.TRACK, Music.TRACK, "_id=" + id.asString(), null, null, null, null);
        if (c.moveToFirst()) {
            int track           = c.getInt(Music.TRACK_MAPPING.TRACKNUM);
            int artist_id       = c.getInt(Music.TRACK_MAPPING.ARTIST_ID);
            int album_id        = c.getInt(Music.TRACK_MAPPING.ALBUM_ID);
            String play_url     = c.getString(Music.TRACK_MAPPING.PLAY_URL);
            String download_url = c.getString(Music.TRACK_MAPPING.DOWNLOAD_URL);
            String title        = c.getString(Music.TRACK_MAPPING.TITLE);
            String artist_name  = c.getString(Music.TRACK_MAPPING.ARTIST_NAME);
            String album_name   = c.getString(Music.TRACK_MAPPING.ALBUM_NAME);
            String cover_url    = c.getString(Music.TRACK_MAPPING.COVER_URL);

            Track t = new ConcreteTrack(id, play_url, title, artist_id, artist_name, album_id, album_name);

            c.close();
            return t;
        }
        c.close();
        return null;
    }
    
    public Artist getArtistById(Id id)
    {
        Cursor c = mDb.query(DbTables.ARTIST, Music.ARTIST, DbKeys.ID + "=" + id.asString(), null, null, null, null);
        
        if (c.getCount() > 1)
            Log.e("Mp3Tunes", "Got more than one id for artist name: " + id.asString());
        if (c.moveToFirst()) {
            Artist a = new Artist(new LockerId(c.getInt(Music.ARTIST_MAPPING.ID)), c.getString(Music.ARTIST_MAPPING.ARTIST_NAME));
            c.close();
            return a;
        }
        c.close();
        return null;
    }
    
    public Artist getArtistByName(String artistName)
    {
        Cursor c = mDb.query(DbTables.ARTIST, Music.ARTIST, DbKeys.ARTIST_NAME + "=\"" + artistName + "\"", null, null, null, null);
        
        if (c.getCount() > 1)
            Log.e("Mp3Tunes", "Got more than one id for artist name: " + artistName);
        if (c.moveToFirst()) {
            Artist a = new Artist(new LockerId(c.getInt(Music.ARTIST_MAPPING.ID)), c.getString(Music.ARTIST_MAPPING.ARTIST_NAME));
            c.close();
            return a;
        }
        c.close();
        return null;
    }

    
    private Album buildAlbum(Cursor c)
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
    
    public Album getAlbum(LockerId id)
    {
        Cursor c = mDb.query(DbTables.ALBUM, Music.ALBUM, DbKeys.ID + "=" + id.asInt(), null, null, null, null);
        return buildAlbum(c);
    };
    
    public Album getAlbum(String name)
    {
        Cursor c = mDb.query(DbTables.ALBUM, Music.ALBUM, DbKeys.ALBUM_NAME + "=\"" + name + "\"", null, null, null, null);
        return buildAlbum(c);
    }

    public DbSearchResult search(DbSearchQuery query, String[] track, String[] artist)
    {
        try {
            // Perform the single http search call
            refreshSearch(query.mQuery);
            DbSearchResult res = new DbSearchResult();
            if (query.mTracks)
                res.mTracks = querySearch(query.mQuery, Music.Meta.TRACK, track);
            //if (query.mAlbums)
            //    res.mAlbums = querySearch(query.mQuery, Music.Meta.ALBUM);
            if (query.mArtists)
                res.mArtists = querySearch(query.mQuery, Music.Meta.ARTIST, artist);

            System.out.println("Got artists: " + res.mArtists.getCount());
            System.out.println("Got tracks: " + res.mTracks.getCount());
            return res;
        } catch (SQLiteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LockerException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void insertTrack(Track track) throws IOException, SQLiteException
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
    
    public void insertArtist(Artist artist) throws IOException,
            SQLiteException
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

    public void insertAlbum(Album album) throws IOException, SQLiteException
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

    public void insertPlaylist(Playlist playlist, int index)
            throws IOException, SQLiteException
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

    private List<Artist> getArtists(LockerCache.Progress progress) throws LockerException
    {
        List<Artist> artists;
        try {
            artists = mLocker.getArtists(progress.mCount, progress.mCurrentSet);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        return artists;
    }
    
    private boolean insertArtists(List<Artist> artists) throws SQLiteException, IOException
    {
        mDb.beginTransaction();
        try {
            for (Artist a : artists) {
                insertArtist(a);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return artists.size() > 0;
    }
    
    private boolean refreshArtists(LockerCache.Progress progress) throws SQLiteException, IOException, LockerException
    {
        return insertArtists(getArtists(progress));
    }

    private List<Album> getAlbums(LockerCache.Progress progress) throws LockerException
    {
        List<Album> albums;
        try {
            albums = mLocker.getAlbums(progress.mCount, progress.mCurrentSet);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        return albums;
    }
    
    private boolean insertAlbums(List<Album> albums) throws SQLiteException, IOException
    {
        mDb.beginTransaction();
        try {
            for (Album a : albums) {
                insertAlbum(a);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return albums.size() > 0;
    }
    
    private boolean refreshAlbums(LockerCache.Progress progress) throws SQLiteException, IOException, LockerException
    {
        return insertAlbums(getAlbums(progress));
    }
    
    private List<Playlist> getPlaylists(LockerCache.Progress progress) throws LockerException
    {
        List<Playlist> playlists;
        try {
            playlists = mLocker.getPlaylists(progress.mCount, progress.mCurrentSet);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        return playlists;
    }
    
    private boolean insertPlaylists(List<Playlist> playlists, LockerCache.Progress progress) throws SQLiteException, IOException
    {
        System.out.println("beginning insertion of " +playlists.size()
                + " playlists");
        int i = progress.mCurrentSet * progress.mCount;
        mDb.beginTransaction();
        try {
            for (Playlist p : playlists) {
                insertPlaylist(p, i);
                i++;
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return playlists.size() > 0;
    }
    
    
    private boolean refreshPlaylists(LockerCache.Progress progress) throws SQLiteException, IOException, LockerException
    {
        return insertPlaylists(getPlaylists(progress), progress);
    }

    private void refreshAlbumsForArtist(final int artist_id) throws SQLiteException,
            IOException, LockerException
    {
        List<Album> albums;
        try {
            albums = mLocker.getAlbumsForArtist(artist_id);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }

        System.out.println("beginning insertion of " + albums.size()
                + " albums for artist id " + artist_id);
        mDb.beginTransaction();
        try {
            for (Album a : albums) {
                insertAlbum(a);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }  
        System.out.println("insertion complete");
    }
    
    private List<Track> getTracks(LockerCache.Progress progress) throws LockerException
    {
        List<Track> tracks;
        try {
            tracks = mLocker.getTracks(progress.mCount, progress.mCurrentSet);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        return tracks;
    }
    
    private boolean insertTracks(List<Track> tracks) throws SQLiteException, IOException
    {
        mDb.beginTransaction();
        try {
            for (Track t : tracks) {
                insertTrack(t);
            }
        mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }  
        return tracks.size() > 0;
    }
    
    private boolean refreshTracks(LockerCache.Progress progress) throws SQLiteException,
    IOException, LockerException
    {
        return insertTracks(getTracks(progress));
    }

    private void refreshTracksforAlbum(final int album_id) throws SQLiteException,
            IOException, LockerException
    {
        List<Track> tracks;
        try {
            tracks = mLocker.getTracksForAlbumFromJson(album_id);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }

        System.out.println("beginning insertion of " + tracks.size()
                + " tracks for album id " + album_id);
        mDb.beginTransaction();
        try {
            for (Track t : tracks) {
                insertTrack(t);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        System.out.println("insertion complete");
    }

    private void refreshTracksforArtist(final int artist_id) throws SQLiteException,
            IOException, LockerException
    {
        List<Track> tracks;
        try {
            tracks = mLocker.getTracksForArtistFromJson(artist_id);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }

        System.out.println("beginning insertion of " + tracks.size()
                + " tracks for artist id " + artist_id);
        mDb.beginTransaction();
        try {
            for (Track t : tracks) {
                insertTrack(t);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }  
        System.out.println("insertion complete");
    }

    private List<Track> getTracksForPlaylist(LockerCache.Progress progress, String playlist_id) throws LockerException
    {
        List<Track> tracks;
        try {
            tracks = mLocker.getTracksForPlaylist(playlist_id, progress.mCount, progress.mCurrentSet);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        return tracks;
    }
    
    private void deleteOldPlaylistTracks(String playlist_id)
    {
        String   where     = "playlist_id=?";
        String[] whereArgs = new String[] {playlist_id};
        mDb.delete(DbTables.PLAYLIST_TRACKS, where, whereArgs);
    }
    
    private boolean insertTracksForPlaylist(List<Track> tracks, String playlist_id, LockerCache.Progress progress) throws SQLiteException, IOException
    {
        System.out.println("beginning insertion of " + tracks.size()
                + " tracks for playlist id " + playlist_id);
        
        insertTracks(tracks);
        
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
    
    private void refreshTracksforPlaylist(final String playlist_id)
            throws SQLiteException, IOException, LockerException
    {
        List<Track> tracks = null;
//        try {
//            tracks = mLocker.getTracksForPlaylistFromJson(playlist_id);
//        } catch (InvalidSessionException e) {
//            throw new LockerException("Bad Session Data");
//        } catch (JSONException e) {
//            throw new LockerException("Sever Sent Corrupt Data");
//        } catch (LoginException e) {
//            throw new LockerException("Unable to refresh session");
//        }
        System.out.println("beginning insertion of " + tracks.size()
                + " tracks for playlist id " + playlist_id);

        mDb.delete("playlist_tracks", "playlist_id='" + playlist_id + "'", null);
        
        int index = 0;
        for (Track t : tracks) {
            ContentValues cv = new ContentValues(); 
            insertTrack(t);
            cv.put("playlist_id", playlist_id);
            cv.put("track_id", t.getId().asInt());
            cv.put("playlist_index", index);
            mDb.insert("playlist_tracks", DbKeys.UNKNOWN_STRING, cv);
            index++;
        }
        System.out.println("insertion complete");
    }
    
    private void refreshSearch(String query) throws SQLiteException, IOException, LockerException
    {
        SearchResult results = null;
        try {
            results = mLocker.search(query);
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
        for (Artist a : results.getArtists()) {
            insertArtist(a);
        }
        for (Album a : results.getAlbums()) {
            insertAlbum(a);
        }
        for (Track t : results.getTracks()) {
            insertTrack(t);
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
        return mDb.query(table, columns, selection, selectionArgs, null, null, null,
                null);
    }

    public class DbSearchResult
    {
        public Cursor mArtists = null;
        public Cursor mAlbums  = null;
        public Cursor mTracks  = null;
    }

    public class DbSearchQuery
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
    
    abstract public class LockerDataCall {
        public abstract Cursor get(String[] columns) throws SQLiteException, IOException, LockerException;
    };
    
    public class GetAlbums extends LockerDataCall
    {
        public Cursor get(String[] columns) throws SQLiteException, IOException, LockerException
        {
            return getAlbumData(columns, null);//
        }
    };
    
    public class GetArtists extends LockerDataCall
    {
        public Cursor get(String[] columns) throws IOException, LockerException
        {
            return getArtistData(columns, null);
        }
    };
    
    abstract public class LockerDataByCall {
        public abstract Cursor get(String[] columns, LockerId id) throws SQLiteException, IOException, LockerException;
    };
    
    public class GetAlbumsByArtist extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException
        {
            return getAlbumDataByArtist(columns, id);
        }
    };
    
    public class GetTracksByAlbum extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException
        {
            return getTrackDataByAlbum(columns, id);
        }
    };
    
    public class GetTracksByArtist extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException
        {
            return getTrackDataByArtist(columns, id);
        }
    };

    static public class IdPolicyException extends RuntimeException
    {
        private static final long serialVersionUID = -5529281068172111226L;

    }
    
    void updateCache(String id, long time, int state, LockerCache.Progress progress) 
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
    
    static public class PreCacheTask extends RefreshTask
    {
        public PreCacheTask(LockerDb db)
        {
            super(db);
        }
        
        private void cacheData(String cache) throws SQLiteException, IOException, LockerException
        {
            if (mDb.mCache.getCacheState(cache) == LockerCache.CacheState.UNCACHED) {
                mDb.mCache.beginCaching(cache, System.currentTimeMillis());
                LockerCache.Progress p = mDb.mCache.getProgress(cache);
                mDb.refreshDispatcher(cache, p, this, null);
                p.mCurrentSet++;
            }
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            Log.w("Mp3Tunes", "Starting PreCache");
            try {
                
                cacheData(LockerCache.CACHES.ARTIST);
                cacheData(LockerCache.CACHES.ALBUM);
                cacheData(LockerCache.CACHES.PLAYLIST);
                Log.w("Mp3Tunes", "PreCache done");
                return true;
            } catch (SQLiteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (LockerException e) {
                e.printStackTrace();
            }
            Log.w("Mp3Tunes", "PreCache Failed");
            return false;
        }
    }
    
    private boolean refreshDispatcher(String cacheId, LockerCache.Progress p, RefreshTask task, String id) throws SQLiteException, IOException, LockerException
    {
        if (cacheId.equals(LockerCache.CACHES.ARTIST)) {
                List<Artist> artists = getArtists(p);
                if (task.isCancelled()) return false;
                return insertArtists(artists);
        } else if (cacheId.equals(LockerCache.CACHES.ALBUM)) {
                List<Album> albums = getAlbums(p);
                if (task.isCancelled()) return false;
                return insertAlbums(albums);
        } else if (cacheId.equals(LockerCache.CACHES.TRACK)) {
                List<Track> tracks = getTracks(p);
                if (task.isCancelled()) return false;
                return insertTracks(tracks);
        } else if (cacheId.equals(LockerCache.CACHES.PLAYLIST)) {
                List<Playlist> playlists = getPlaylists(p);
                if (task.isCancelled()) return false;
                return insertPlaylists(playlists, p);
        } else {
            List<Track> tracks = getTracksForPlaylist(p, id);
            if (task.isCancelled()) return false;
            if (p.mCurrentSet == 0) deleteOldPlaylistTracks(id);
            return insertTracksForPlaylist(tracks, id, p);
        }
    }
    
    private boolean refreshTask(String cacheId, RefreshTask task, String id)
    {
        Log.w("Mp3Tunes", "Starting Refresh");
        try {
            int state = mCache.getCacheState(cacheId);
            if (state != LockerCache.CacheState.CACHED) {
                Log.w("Mp3Tunes", "Not cached yet");
                if (state == LockerCache.CacheState.UNCACHED)
                    mCache.beginCaching(cacheId, System.currentTimeMillis());
                LockerCache.Progress p = mCache.getProgress(cacheId);
                while (refreshDispatcher(cacheId, p, task, id)) {
                    p.mCurrentSet++;
                }
                mCache.finishCaching(cacheId);
            }
            mCache.saveCache(this);
            Log.w("Mp3Tunes", "Refresh Succeeded");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } 
        Log.w("Mp3Tunes", "Refresh Failed");
        return false;
    }
    
    abstract static public class RefreshTask extends AsyncTask <Void, Void, Boolean>
    {
        protected LockerDb mDb;

        public RefreshTask(LockerDb db)
        {
            mDb = db;
        }
        @Override
        protected void onCancelled()
        {
            Log.w("Mp3tunes", "onCancelled called");
            if (mDb.mDb.inTransaction()) {
                Log.w("Mp3tunes", "ending transaction");
                mDb.mDb.endTransaction();
            }
        }
    }
    
    static public class RefreshArtistsTask extends RefreshTask
    {
        public RefreshArtistsTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                Log.w("Mp3Tunes", "Starting RefreshArtists");
                return mDb.refreshTask(LockerCache.CACHES.ARTIST, this, null);
            } catch (Exception e) {
                e.printStackTrace();
                }
            return false;
        }
    }

    static public class RefreshAlbumsTask extends RefreshTask
    {
        public RefreshAlbumsTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                Log.w("Mp3Tunes", "Starting RefreshAlbums");
                return mDb.refreshTask(LockerCache.CACHES.ALBUM, this, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    static public class RefreshPlaylistsTask extends RefreshTask
    {
        public RefreshPlaylistsTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                Log.w("Mp3Tunes", "Starting RefreshAlbums");
                return mDb.refreshTask(LockerCache.CACHES.PLAYLIST, this, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    static public class RefreshTracksTask extends RefreshTask
    {
        public RefreshTracksTask(LockerDb db)
        {
            super(db);
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                Log.w("Mp3Tunes", "Starting RefreshTracks");
                return mDb.refreshTask(LockerCache.CACHES.TRACK, this, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    static public class RefreshPlaylistTracksTask extends RefreshTask
    {
        Id mId;
        
        public RefreshPlaylistTracksTask(LockerDb db, Id id)
        {
            super(db);
            mId = id;
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            try {
                Log.w("Mp3Tunes", "Starting RefreshTracks for playlist");
                if (LockerId.class.isInstance(mId))
                    return mDb.refreshTask(mId.asString(), this, mId.asString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
            
        }
    }
    
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
                    mDb.refreshTracksforAlbum(id.asInt());
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
                mDb.refreshTracksforArtist(id.asInt());
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    public Cursor getCache(String[] projection)
    {
        return mDb.query(DbTables.CACHE, projection, null, null, null, null, null);
    }
}
