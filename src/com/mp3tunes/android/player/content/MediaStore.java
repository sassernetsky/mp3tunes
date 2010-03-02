package com.mp3tunes.android.player.content;

import java.io.IOException;
import java.util.Arrays;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.content.LockerDb.GetAlbumsByArtist;
import com.mp3tunes.android.player.util.Timer;

public class MediaStore
{
    private LockerDb        mLockerDb;
    private ContentResolver mCr;
    
    public MediaStore(LockerDb db, ContentResolver cr)
    {
        mLockerDb = db;
        mCr       = cr;
    }
    
    public static final Uri sArtistsUri = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
    public static final Uri sAlbumsUri  = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
    public static final Uri sTracksUri   = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    
    public static final String KEY_LOCAL = "local";
    
    public Cursor getArtistData(String[] columns)throws IOException, LockerException
    {
        String[] joinBy =  new String[] {DbKeys.ARTIST_NAME};
        return getData(columns, joinBy, sArtistsUri, DbKeys.ARTIST_NAME, mLockerDb.new GetArtists());
    }
    
    public Cursor getAlbumData(String[] columns)throws IOException, LockerException
    {
        Timer timer = new Timer("Get Album Data");
        try {
        String[] joinBy = new String[] {DbKeys.ALBUM_NAME};
        return getData(columns, joinBy, sAlbumsUri, DbKeys.ALBUM_NAME, mLockerDb.new GetAlbums());
        } finally {
            timer.push();
        }
    }
    
    public Cursor getAlbumDataByArtist(String[] columns, Id id) throws IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.ALBUM_NAME};
        return getDataById(columns, joinBy, sAlbumsUri, DbKeys.ALBUM_NAME, id, 
                           android.provider.MediaStore.Audio.Media.ARTIST, new ArtistGetter(mLockerDb, mCr),
                           mLockerDb.new GetAlbumsByArtist());
    }

    public Cursor getTrackDataByAlbum(String[] columns, Id id) throws SQLiteException, IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.TITLE};
        return getDataById(columns, joinBy, sTracksUri, DbKeys.TITLE, id, 
                           android.provider.MediaStore.Audio.Media.ALBUM, new AlbumGetter(mLockerDb, mCr),
                           mLockerDb.new GetTracksByAlbum());
    }

    public Cursor getTrackDataByArtist(String[] columns, Id id) throws IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.TITLE};
        return getDataById(columns, joinBy, sTracksUri, DbKeys.TITLE, id, 
                android.provider.MediaStore.Audio.Media.ARTIST, new ArtistGetter(mLockerDb, mCr),
                mLockerDb.new GetTracksByArtist());
    }
    
    public Cursor getTrackDataByPlaylist(String[] columns, LockerId lockerId) throws SQLiteException, IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.TITLE};
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);
        
        //no matter what columns the caller requests we need the Artist, Album, and Title to be returned
        //so we make sure they are in the projection here
        cols = ensureColInColumns(cols, DbKeys.TITLE);
        cols = ensureColInColumns(cols, DbKeys.ALBUM_NAME); 
        cols = ensureColInColumns(cols, DbKeys.ARTIST_NAME);
        
        Cursor locker = mLockerDb.getTrackDataByPlaylist(cols, lockerId);
        
        int titleIndex  = locker.getColumnIndexOrThrow(DbKeys.TITLE);
        int artistIndex = locker.getColumnIndexOrThrow(DbKeys.ARTIST_NAME);
        int albumIndex  = locker.getColumnIndexOrThrow(DbKeys.ALBUM_NAME);
        
        String   where = android.provider.MediaStore.Audio.Media.ARTIST + "=? AND " +
                         android.provider.MediaStore.Audio.Media.ALBUM  + "=? AND " +
                         android.provider.MediaStore.Audio.Media.TITLE  + "=?";
        String[] whereArgs = new String[3];
        
        MatrixCursor output = new MatrixCursor(columns);
        int len = columns.length - 1;
        if (locker.moveToFirst()) {
            do {
                MatrixCursor.RowBuilder builder = output.newRow();
                
                whereArgs[0] = locker.getString(artistIndex);
                whereArgs[1] = locker.getString(albumIndex);
                whereArgs[2] = locker.getString(titleIndex);
                Cursor cursor = mCr.query(sTracksUri, projection, where, whereArgs, null);
                if (cursor.moveToFirst()) {
                    buildRow(builder, cursor, len, 1);
                } else {
                    buildRow(builder, locker, len, 0);
                }
            } while (locker.moveToNext());
        }
        return output;
    }
    
    public Track getTrack(Id id)
    {
        try {
            return (Track)(new TrackGetter(mLockerDb, mCr).get(id));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LockerException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private String[] lockerDbToMediaStoreColumns(String[] from)
    {
        String[] output = new String[from.length];
        for (int i = 0; i < from.length; i++){
            output[i] = lockerDbToMediaStoreKey(from[i]);
        }
        return output;
    }
    
    private String lockerDbToMediaStoreKey(String from)
    {
        if (from.equals(DbKeys.ARTIST_NAME))
            return android.provider.MediaStore.Audio.Media.ARTIST;
        else if (from.equals(DbKeys.ALBUM_COUNT))
            return android.provider.MediaStore.Audio.ArtistColumns.NUMBER_OF_ALBUMS;
        else if (from.equals(DbKeys.ID))
            return android.provider.BaseColumns._ID;
        else if (from.equals(DbKeys.ALBUM_NAME))
            return android.provider.MediaStore.Audio.Media.ALBUM;
        return from;
    }
    
    private Cursor merge(Cursor locker, Cursor store, String[] columns, String[] joinOn)
    {
        Timer timer = new Timer("Merge took");
        try { 
        MatrixCursor output;
        if (locker == null) {
            output = addColumnToCursor(store, columns, 1);
        } else if (store  == null) {
            output = addColumnToCursor(locker, columns, 0);
        } else {
            output = new MatrixCursor(columns);
            CursorJoiner joiner = new CursorJoiner(locker, joinOn, store, lockerDbToMediaStoreColumns(joinOn));
  
            int len = columns.length - 1;
            for (CursorJoiner.Result joinerResult : joiner) {
                MatrixCursor.RowBuilder builder = output.newRow();
                switch (joinerResult) {
                    case LEFT:
                        buildRow(builder, locker, len, 0);
                        break;
                    case RIGHT:
                        buildRow(builder, store, len, 1);
                        break;
                    case BOTH: 
                        buildRow(builder, store, len, 1);
                        break;
                }
            }
            locker.close();
            store.close();
        }
        return output;
        } finally {
            timer.push();
        }
    }
    
    private String[] rmLocalCol(String[] array)
    {
        if (array[array.length - 1].equals(KEY_LOCAL)) {
            String[] a = new String[array.length - 1];
            System.arraycopy(array, 0, a, 0, array.length - 1);
            return a;
        }
        return array;
    }
    
    private String[] ensureColInColumns(String[] array, String key) 
    {
        String[] sorted = new String[array.length];
        System.arraycopy(array, 0, sorted, 0, array.length);
        Arrays.sort(sorted);
        if (Arrays.binarySearch(sorted, key) < 0) {
            String[] old = array;
            array = new String[old.length + 1];
            System.arraycopy(old, 0, array, 0, old.length);
            array[array.length - 1] = key;
        }
        return array;
    }
    
    private MatrixCursor addColumnToCursor(Cursor c, String[] columns, int local)
    {
        MatrixCursor output = new MatrixCursor(columns);
        if (c == null) return output;
        int len = columns.length - 1;
        if (c.moveToFirst()) {
            do {
                MatrixCursor.RowBuilder builder = output.newRow();
                buildRow(builder, c, len, local);
            } while (c.moveToNext());
        }
        c.close();
        return output;
    }
    
    private void buildRow(MatrixCursor.RowBuilder builder, Cursor c, int len, int local)
    {
        for (int i = 0; i < len; i++) {
            builder.add(c.getString(i));
        }
        builder.add(local);
    }

    private Cursor getData(String[] columns, String[] joinBy, Uri storeUri, String order, LockerDb.LockerDataCall call) throws SQLiteException, IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);

        Cursor locker = call.get(cols);
        Cursor store = mCr.query(storeUri, projection, null, null, 
                              "lower(" + lockerDbToMediaStoreKey(order) + ")");
        return merge(locker, store, columns, joinBy);
    }
    
    private Cursor getDataById(String[] columns, String[] joinBy, Uri storeUri, String order, Id id, String nameKey, MergeHelper helper, LockerDb.LockerDataByCall call) throws IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);
        
        LocalId  localId  = helper.getLocalId(id);
        LockerId lockerId = helper.getLockerId(id);
        String   name     = helper.getName(id);
        
        
        Log.w("Mp3Tunes", "Name: " + name);
        Cursor locker = null;
        Cursor store  = null;
        if (localId != null) {
            Log.w("Mp3Tunes", "Have Local data");
            store = mCr.query(storeUri, projection, nameKey + "=\"" + name + "\"", null, 
                              "lower(" + lockerDbToMediaStoreKey(order) + ")");
        }
        if (lockerId != null) {
            Log.w("Mp3Tunes", "Have Remote data");
            locker = call.get(cols, lockerId);
        }
        return merge(locker, store, columns, joinBy);
    }

}
