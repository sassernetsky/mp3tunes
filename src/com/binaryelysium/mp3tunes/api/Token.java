package com.binaryelysium.mp3tunes.api;

public class Token
{
    private String mToken;
    private int mCount;
    
    public Token( String token, int count )
    {
        mToken = token;
        mCount = count;
    }
    
    public String getToken()
    {
        return mToken;
    }

    
    public int getCount()
    {
        return mCount;
    }

    
    public void setToken( String token )
    {
        mToken = token;
    }

    
    public void setCount( int count )
    {
        mCount = count;
    }

}
