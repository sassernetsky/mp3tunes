package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerData;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.content.MediaStore;

public class ArtistGetter extends MergeHelper
{   
    private static String[] sLocal = new String[] {
        android.provider.BaseColumns._ID,
        android.provider.MediaStore.Audio.Media.ARTIST
    };
    
    private static String[] sRemote = new String[] {
        DbKeys.ID,
        DbKeys.ARTIST_NAME
    };
    
    
    public ArtistGetter(LockerDb db, ContentResolver resolver)
    {
        super(db, resolver);
    }

    @Override
    public LockerData getLocal(LocalId id)
    {        
        Cursor c = mCr.query(MediaStore.sArtistsUri, sLocal, android.provider.BaseColumns._ID + "=" + id.asInt(), null, null);
        return createArtistFromCursor(c, true);
    }
    
    @Override
    public LockerData getLocal(String name)
    {
        String[] args = new String[] {name};
        Cursor c = mCr.query(MediaStore.sArtistsUri, sLocal, android.provider.MediaStore.Audio.Media.ARTIST + "=?", args, null);
        return createArtistFromCursor(c, true);
    }
    
    @Override
    public LockerData getRemote(LockerId id) throws IOException, LockerException
    {
        Cursor c = mDb.getArtistData(sRemote, DbKeys.ID + "=" + id.asString());
        return createArtistFromCursor(c, false);
    }
    
    @Override
    public LockerData getRemote(String name) throws IOException, LockerException
    {
        Cursor c = mDb.getArtistData(sRemote, DbKeys.ARTIST_NAME + "=" + DatabaseUtils.sqlEscapeString(name));
        return createArtistFromCursor(c, false);
    }

    private Artist createArtistFromCursor(Cursor c, boolean local)
    {
        if (c.moveToFirst()) {
            Artist a = new Artist(createId(c.getInt(0), local), c.getString(1));
            Log.w("Mp3Tunes", "Created artist: " + a.getName());
            c.close();
            return a;
        }
        c.close();
        return null;
    }
    
    
}
