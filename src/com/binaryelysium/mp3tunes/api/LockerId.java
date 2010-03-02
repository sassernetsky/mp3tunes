package com.binaryelysium.mp3tunes.api;

public class LockerId implements Id
{
    int    mIntId;
    String mStringId;
    public LockerId(int id) 
    {
      mIntId = id;  
    }
    
    public LockerId(String id) 
    {
      mStringId = id;  
    }
    
    public int asInt()
    {
        if (mStringId == null)
            return mIntId;
        return Integer.parseInt(mStringId);
    }

    public String asString()
    {
        if (mStringId == null)
            return Integer.toString(mIntId);
        return mStringId;
    }
    public Id copyId()
    {
        if (mStringId == null)
            return new LockerId(mIntId);
        return new LockerId(mStringId);
    }

}
