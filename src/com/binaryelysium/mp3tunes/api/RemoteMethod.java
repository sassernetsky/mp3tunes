package com.binaryelysium.mp3tunes.api;

import java.util.LinkedList;
import java.util.List;

public class RemoteMethod
{
    public static final String API_GENERAL = "http://ws.mp3tunes.com/api/v1/";
    public static final String API_STORAGE = "http://ws.mp3tunes.com/api/v1/";
    public static final String API_LOGIN   = "https://shop.mp3tunes.com/api/v1/";
    public static final String XML_OUTPUT  = "output=xml";
    
    public static final class METHODS {
        public static final String LOGIN                  = "login";
        public static final String LOGOUT                 = "logout";
        public static final String ACCOUNT_DATA           = "accountData";
        public static final String CREATE_ACCOUNT         = "createAccount";
        public static final String LAST_UPDATE            = "lastUpdate";
        public static final String LOCKER_DATA            = "lockerData";
        public static final String LOCKER_SEARCH          = "lockerSearch";
        public static final String LOCKER_FILE_EXISTS     = "lockerFileExists";
        public static final String LOCKER_STATS           = "lockerStats";
        public static final String PLAYLIST_ADD           = "playlistAdd";
        public static final String PLAYLIST_DELETE        = "playlistDelete";
        public static final String PLAYLIST_EDIT          = "playlistEdit";
        public static final String PLAYLIST_TRACK_ADD     = "playlistTrackAdd";
        public static final String PLAYLIST_TRACK_DELETE  = "playlistTrackDelete";
        public static final String PLAYLIST_TRACK_REORDER = "playlistTrackReorder";
        public static final String ALBUM_ART_GET          = "albumArtGet";
        public static final String LOCKER_DELETE          = "lockerDelete";
        public static final String LOCKER_GET             = "lockerGet";
        public static final String LOCKER_PUT             = "lockerPut";
        public static final String LOCKER_PLAY            = "lockerPlay";
    }
    
    private String mCall;
    

    public String getCall()
    {
        return mCall;
    }
    
    private RemoteMethod(String call)
    {
        mCall = call;
    }

    public class Builder {
        private String mMethod;
        private List<String> params = new LinkedList<String>();
    
        public Builder(String method) 
        {
            mMethod = method;
        }
        
        public void addParam(String key, String val)
        {
            params.add("&" + key + "=" + val);
        }
        
        public void addParam(String key)
        {
            params.add("&" + key);
        }
        
        public RemoteMethod create()
        {
            String site;
            if (mMethod == METHODS.LOGIN || mMethod == METHODS.LOGOUT)
                site = API_LOGIN;
            else 
                site = API_GENERAL;
            
            StringBuilder builder = new StringBuilder(site).append(mMethod).append("?")
                                        .append(XML_OUTPUT).append("&partner_token=")
                                        .append(Locker.getPartnerToken());
            
            for (String val : params) {
                builder.append(val);
            }
            
            return new RemoteMethod(builder.toString());
        }
    }
}
