package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.content.ContentResolver;
import android.database.Cursor;

import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.LockerData;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;

public class TrackGetter extends MergeHelper
{
    private static String[] sLocal = new String[] {
        android.provider.BaseColumns._ID,
        android.provider.MediaStore.Audio.Media.TITLE,
        android.provider.MediaStore.Audio.Media.DATA
    };
    
    private static String[] sRemote = new String[] {
        DbKeys.ID,
        DbKeys.ARTIST_NAME,
        DbKeys.PLAY_URL
    };

    
    public TrackGetter(LockerDb db, ContentResolver resolver)
    {
        super(db, resolver);
    }

    @Override
    public LockerData getLocal(LocalId id)
    {
        String[] args = new String[] {id.asString()};
        Cursor c = mCr.query(MediaStore.sTracksUri, sLocal, android.provider.BaseColumns._ID + "=?", args, null);
        return createTrackFromCursor(c, true);
    }

    @Override
    public LockerData getLocal(String name)
    {
        String[] args = new String[] {name};
        Cursor c = mCr.query(MediaStore.sTracksUri, sLocal, android.provider.MediaStore.Audio.Media.TITLE + "=?", args, null);
        return createTrackFromCursor(c, true);
    }
    
    @Override
    public LockerData getRemote(LockerId id) throws IOException, LockerException
    {
        return mDb.getTrack(id);
        //Cursor c = mDb.getTrack(sRemote, DbKeys.ID + "=" + id.asString());
        //return createTrackFromCursor(c, false);
    }

    @Override
    public LockerData getRemote(String name) throws IOException, LockerException
    {
        return mDb.getTrack(name);
        //Cursor c = mDb.getAlbumData(sRemote, DbKeys.TITLE + "=\"" + name + "\"");
        //return createTrackFromCursor(c, false);
    }

    private Track createTrackFromCursor(Cursor c, boolean local)
    {
        if (c.moveToFirst()) {
            Track a = new ConcreteTrack(createId(c.getInt(0), local), c.getString(1), c.getString(2));
            c.close();
            return a;
        }
        c.close();
        return null;
    }
    
}
