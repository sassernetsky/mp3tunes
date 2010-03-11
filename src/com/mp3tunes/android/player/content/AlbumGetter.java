package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.DatabaseUtils;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.LockerData;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.LocalId;

public class AlbumGetter extends MergeHelper
{
    private static String[] sLocal = new String[] {
        android.provider.BaseColumns._ID,
        android.provider.MediaStore.Audio.Media.ALBUM
    };
    
    private static String[] sRemote = new String[] {
        DbKeys.ID,
        DbKeys.ALBUM_NAME
    };
    
    
    public AlbumGetter(LockerDb db, ContentResolver resolver)
    {
        super(db, resolver);
    }

    @Override
    public LockerData getLocal(LocalId id)
    {
        Cursor c = mCr.query(MediaStore.sAlbumsUri, sLocal, android.provider.BaseColumns._ID + "=" + id.asInt(), null, null);
        return createAlbumFromCursor(c, true);
    }

    @Override
    public LockerData getLocal(String name)
    {
        String[] args = new String[] {name};
        Cursor c = mCr.query(MediaStore.sAlbumsUri, sLocal, android.provider.MediaStore.Audio.Media.ALBUM + "=?", args, null);
        return createAlbumFromCursor(c, true);
    }
    
    @Override
    public LockerData getRemote(LockerId id) throws IOException, LockerException
    {
        Cursor c = mDb.getAlbumData(sRemote, DbKeys.ID + "=" + id.asString());
        return createAlbumFromCursor(c, false);
    }

    @Override
    public LockerData getRemote(String name) throws IOException, LockerException
    {
        Cursor c = mDb.getAlbumData(sRemote, DbKeys.ALBUM_NAME + "=" + DatabaseUtils.sqlEscapeString(name));
        return createAlbumFromCursor(c, false);
    }

    private Album createAlbumFromCursor(Cursor c, boolean local)
    {
        if (c.moveToFirst()) {
            Album a = new Album(createId(c.getInt(0), local), c.getString(1));
            c.close();
            return a;
        }
        c.close();
        return null;
    }
}
