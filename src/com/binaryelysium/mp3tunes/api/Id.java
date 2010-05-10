package com.binaryelysium.mp3tunes.api;

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
