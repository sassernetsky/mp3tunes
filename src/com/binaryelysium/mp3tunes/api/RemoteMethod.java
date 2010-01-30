package com.binaryelysium.mp3tunes.api;

import java.util.LinkedList;
import java.util.List;

public class RemoteMethod
{
    public static final String API_GENERAL = "http://ws.mp3tunes.com/api/v1/";
    public static final String API_STORAGE = "http://ws.mp3tunes.com/api/v1/";
    public static final String API_LOGIN   = "https://shop.mp3tunes.com/api/v1/";
    public static final String API_CONTENT = "http://content.mp3tunes.com/storage/";
    public static final String JSON_OUTPUT = "output=json";
    
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

    public static class Builder {
        private String mMethod;
        private String mFileKey;
        private List<String> params = new LinkedList<String>();
    
        public Builder(String method) 
        {
            mMethod = method;
        }
        
        public Builder addParam(String key, String val)
        {
            params.add("&" + key + "=" + val);
            return this;
        }
        
        public Builder addParam(String key)
        {
            params.add("&" + key);
            return this;
        }
        
        public Builder addFileKey(String key)
        {
            if (callNeedsFileKey(mMethod)) {
                mFileKey = key;
            }
            return this;
        }
        
        public RemoteMethod create()
        {
            String site = getSiteForCall(mMethod);
            
            StringBuilder builder = new StringBuilder(site).append(mMethod);
            
            if (callNeedsFileKey(mMethod) && mFileKey != null)
                builder.append("/").append(mFileKey);
            
            builder.append("?");
            builder.append("partner_token=").append(LockerContext.instance().getPartnerToken());
            
            if (parseResponse(mMethod))
                builder.append("&").append(JSON_OUTPUT);
            
            if (mMethod != METHODS.LOGIN && mMethod != METHODS.LOGOUT)
                builder.append("&sid=").append(LockerContext.instance().getSessionId());
            
            for (String val : params) {
                builder.append(val);
            }
            
            return new RemoteMethod(builder.toString());
        }
        
        //TODO It is probably better to do this without the huge conditional
        private static String getSiteForCall(String call)
        {
            if (call == METHODS.LOGIN || call == METHODS.LOGOUT)
                return API_LOGIN;
            else if (call == METHODS.LOCKER_PLAY || call == METHODS.LOCKER_GET || 
                     call == METHODS.LOCKER_PUT  || call == METHODS.LOCKER_DELETE)
                return API_CONTENT; 
            else 
                return API_GENERAL;
        }
        
        private static boolean callNeedsFileKey(String call)
        {
            if (call == METHODS.LOCKER_PLAY   || call == METHODS.LOCKER_GET || 
                call == METHODS.LOCKER_DELETE || call == METHODS.LOCKER_PUT)
                return true;
            return false;
        }
        
        private static boolean parseResponse(String call)
        {
            if (call != METHODS.LOCKER_PLAY && call != METHODS.LOCKER_GET)
                return true;
            return false;
        }
    }
}
