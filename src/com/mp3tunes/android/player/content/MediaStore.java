package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;

public class MediaStore
{
    private LockerDb        mLockerDb;
    private ContentResolver mCr;
    
    public MediaStore(LockerDb db, ContentResolver cr)
    {
        mLockerDb = db;
        mCr       = cr;
    }
    
    private static final Uri sArtistsUri = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
    private static final Uri sAlbumsUri  = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;

    public static final String KEY_LOCAL = "local";
    
    public Cursor getArtistData(String[] columns)throws IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        Cursor locker = mLockerDb.getArtistData(cols);
        
        String[] projection = lockerDbToMediaStoreColumns(cols);
        Cursor store = mCr.query(sArtistsUri, projection, null, null, 
                                 "lower(" + lockerDbToMediaStoreKey(DbKeys.ARTIST_NAME) + ")");
        
        return merge(locker, store, columns, new String[] {DbKeys.ARTIST_NAME});
    }
    
    public Cursor getAlbumData(String[] columns, Id id)throws IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);
        String[] joinBy = new String[] {DbKeys.ALBUM_NAME};
        
        Cursor locker = null;
        Cursor store  = null;
        if (id == null) {
            locker = mLockerDb.getAlbumData(cols, null);
            store = mCr.query(sAlbumsUri, projection, null, null, 
                              "lower(" + lockerDbToMediaStoreKey(DbKeys.ALBUM_NAME) + ")");
        } else {
            Log.w("Mp3Tunes", "geting album data for artist id: " + id.asString());
            if (LockerId.class.isInstance(id)) {
                Log.w("Mp3Tunes", "Id is from Locker");
                locker = mLockerDb.getAlbumData(cols, id.asString());
                Artist artist = mLockerDb.getArtistById(id);
                Log.w("Mp3Tunes", "Artist name: " + artist.getName());
                store = mCr.query(sAlbumsUri, projection,
                        android.provider.MediaStore.Audio.Media.ARTIST + "=\"" + artist.getName() + "\"", null, 
                        "lower(" + lockerDbToMediaStoreKey(DbKeys.ALBUM_NAME) + ")");
            } else {
                Log.w("Mp3Tunes", "Id is from Store");
                Artist localArtist = getArtistFromLocal(id);
                if (localArtist == null) return null;
                
                Log.w("Mp3Tunes", "Artist name: " + localArtist.getName());
                Artist remoteArtist = mLockerDb.getArtistByName(localArtist.getName());
                if (remoteArtist != null) {
                    Log.w("Mp3Tunes", "Artist is local and remote");
                    locker = mLockerDb.getAlbumData(cols, id.asString());
                }
                store = mCr.query(sAlbumsUri, projection,
                        android.provider.MediaStore.Audio.Media.ARTIST + "=\"" + localArtist.getName() + "\"", null, 
                        "lower(" + lockerDbToMediaStoreKey(DbKeys.ALBUM_NAME) + ")");
            }
        }
        return merge(locker, store, columns, joinBy);
    }
    
    public Track getTrack(Id id)
    {
        if (LockerId.class.isInstance(id)) {
            return mLockerDb.getTrack((LockerId)id);
        } else if (LocalId.class.isInstance(id)) {
            throw new RuntimeException();
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
                    case BOTH:  
                        buildRow(builder, store, len, 1);
                        break;
                }
            }
        }
        return output;
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
    
    private Artist getArtistFromLocal(Id id)
    {
        String[] projection = new String[] {
            android.provider.BaseColumns._ID,
            android.provider.MediaStore.Audio.Media.ARTIST
        };
        Cursor c = mCr.query(sArtistsUri, projection, android.provider.BaseColumns._ID + "=" + id.asInt(), null, null);
        if (c.moveToFirst()) {
            Artist a = new Artist(id, c.getString(1));
            return a;
        }
        return null;
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
        return output;
    }
    
    private void buildRow(MatrixCursor.RowBuilder builder, Cursor c, int len, int local)
    {
        for (int i = 0; i < len; i++) {
            builder.add(c.getString(i));
        }
        builder.add(local);
    }
}
