package com.binaryelysium.mp3tunes.api;

//The way that we do this needs to be refactored. We should have local and locker versions of
//Track and Album, etc.  The ever present time constraints that we deal with here have up to this
//point prevented us from doing so.
public interface Id
{
    public String asString();
    public int    asInt();
    
    public Id     copyId();
    
    static public class IdPolicyException extends RuntimeException
    {
        private static final long serialVersionUID = -5529281068172111226L;

    }
}
