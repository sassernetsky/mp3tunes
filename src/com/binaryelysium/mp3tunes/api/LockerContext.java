package com.binaryelysium.mp3tunes.api;


//This guy holds the state of the locker
//things like the current session that we are using.
//our partner id that sort of stuff. The default implementation
//is a singleton but you can create another policy by setting a
//new ContextRetriever 
public class LockerContext
{
    private Session mSession;
    private String  mPartnerToken;
    private static ContextRetriever sRetriever = new Retriever();
    
    public Session getSession()
    {
        return sRetriever.get().mSession;
    }
    
    public String getSessionId()
    {
        return sRetriever.get().mSession.getSessionId();
    }
    
    public void setSession(Session session)
    {
        sRetriever.get().mSession = session;
    }
    
    public String getPartnerToken()
    {
        return sRetriever.get().mPartnerToken;
    }
    
    public void setPartnerToken(String token)
    {
        sRetriever.get().mPartnerToken = token;
    }
    
    public static LockerContext instance()
    {
        return sRetriever.get();
    }
    
    public LockerContext()
    {
    }
    
    public static void setContextRetriever(ContextRetriever retriever)
    {
        sRetriever = retriever;
    }
    
    public interface ContextRetriever
    {
        public LockerContext get();
        
    }
    
    static private class Retriever implements ContextRetriever 
    {
        private static LockerContext sContext = new LockerContext();
        public LockerContext get()
        {
            return sContext;
        }
        
    }
}
