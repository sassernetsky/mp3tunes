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
    
    synchronized public Session getSession()
    {
            LockerContext c = sRetriever.get();
            return c.mSession;
    }
    
    synchronized public String getSessionId() throws InvalidSessionException
    {
        LockerContext c = sRetriever.get();
        Session s = c.mSession;
        try {
            return s.getSessionId();
        } catch (NullPointerException e) {
            throw new InvalidSessionException();
        }
    }
    
    synchronized public void setSession(Session session)
    {
        LockerContext c = sRetriever.get();
        c.mSession = session;
    }
    
    synchronized public String getPartnerToken()
    {
        LockerContext c = sRetriever.get();
        return c.mPartnerToken;
    }
    
    synchronized public void setPartnerToken(String token)
    {
        LockerContext c = sRetriever.get();
        c.mPartnerToken = token;
    }
    
    synchronized public String getUserName()
    {
        return sRetriever.getUserName();
    }
    
    synchronized public String getPassword()
    {
        return sRetriever.getPassword();
    }
    
    synchronized public static LockerContext instance()
    {
        return sRetriever.get();
    }
    
    public LockerContext()
    {
    }
    
    synchronized public static void setContextRetriever(ContextRetriever retriever)
    {
        sRetriever = retriever;
    }
    
    
    //We provide the getUserName and getPassword methods so that we do not
    //force our security policy on the library user. 
    public interface ContextRetriever
    {
        public LockerContext get();
        public String getUserName();
        public String getPassword();
    }
    
    static private class Retriever implements ContextRetriever 
    {
        private static LockerContext sContext = new LockerContext();
        public LockerContext get()
        {
            return sContext;
        }
        public String getPassword()
        {
            return null;
        }
        public String getUserName()
        {
            return null;
        }
        
    }
}
