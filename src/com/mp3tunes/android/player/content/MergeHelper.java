package com.mp3tunes.android.player.content;

import java.io.IOException;

import android.content.ContentResolver;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerData;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.LocalId;

abstract public class MergeHelper
{
    protected LockerDb        mDb;
    protected ContentResolver mCr;
    
    protected MergeHelper(LockerDb db, ContentResolver resolver)
    {
        mDb = db;
        mCr = resolver;
    }
    
    abstract public LockerData getLocal(LocalId id);
    abstract public LockerData getLocal(String name);
    abstract public LockerData getRemote(LockerId id) throws IOException, LockerException;
    abstract public LockerData getRemote(String name) throws IOException, LockerException;
    
    public LockerData get(Id id) throws IOException, LockerException
    {
        LocalId localId = getLocalId(id);
        if (localId == null)
            return getRemote((LockerId)id);
        return getLocal(localId);
    }
    
    public LocalId getLocalId(Id id)
    {
        try {
            if (LocalId.class.isInstance(id)) return (LocalId) id;
            LockerData d = getRemote((LockerId)id);
            LockerData local = getLocal(d.getName());
            return (LocalId)local.getId();
        } catch (Exception e) {
            return null;
        }
    }

    public LockerId getLockerId(Id id) throws IOException, LockerException
    {
        LockerData remote;
        try {
            if (LockerId.class.isInstance(id)) return (LockerId) id;
            LockerData d = getLocal((LocalId)id);
            remote = getRemote(d.getName());
            return (LockerId)remote.getId();
        } catch (Exception e) {
            return null;
        }
    }

    public String getName(Id id) throws IOException, LockerException
    {
        if (LockerId.class.isInstance(id))  {
            LockerData a = getRemote((LockerId)id);
            if (a == null) return null;
            return a.getName();
        } else {
            return getLocal((LocalId)id).getName();
        }
    }
    
    protected Id createId(int id, boolean local)
    {
        if (local)
            return new LocalId(id);
        else
            return new LockerId(id);
    }
    
//    public LockerId   getLockerId(Id id);
//    public LocalId    getLocalId(Id id);
//    public String     getName(Id id) throws IOException, LockerException;
}
