package com.mp3tunes.android.player;

import com.binaryelysium.mp3tunes.api.Id;

public class LocalId implements Id
{
    int mId;
    
    public LocalId(int id)
    {
        mId = id;
    }

    public int asInt()
    {
        return mId;
    }

    public String asString()
    {
        return Integer.toString(mId);
    }

    public Id copyId()
    {
        return new LocalId(mId);
    }

}
