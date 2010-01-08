package com.mp3tunes.android.player.service;

import java.util.HashMap;

public class PlaybackErrorCodes
{
    private static PacketVideoErrorCodes sErrors = new PacketVideoErrorCodes();
    
    public static String getError(int code)
    {
        return sErrors.get(code);
    }
    
    
    private static class PacketVideoErrorCodes {
        private HashMap<Integer, String> mErrors = new HashMap<Integer, String>();
        public PacketVideoErrorCodes()
        {
            mErrors.put(-1, "Error due to general failure");
            mErrors.put(-2, "Error due to cancellation");
            mErrors.put(-3, "Error due to no memory being available");
            mErrors.put(-4, "Error due to request not being supported");
            mErrors.put(-5, "Error due to invalid argument");
            mErrors.put(-6, "Error due to invalid resource handle being specified");
            mErrors.put(-7, "Error due to resource already exists and another one cannot be created");
            mErrors.put(-8, "Error due to resource being busy and request cannot be handled");
            mErrors.put(-9, "Error due to resource not ready to accept request");
            mErrors.put(-10, "Error due to data corruption being detected");
            mErrors.put(-11, "Error due to request timing out");
            mErrors.put(-12, "Error due to general overflow");
            mErrors.put(-13, "Error due to general underflow");
            mErrors.put(-14, "Error due to resource being in wrong state to handle request");
            mErrors.put(-15, "Error due to resource not being available");
            mErrors.put(-16, "Error due to invalid configuration of resource");
            mErrors.put(-17, "Error due to general error in underlying resource");
            mErrors.put(-18, "Error due to general data processing");
            mErrors.put(-19, "Error due to general port processing");
            mErrors.put(-20, "Error due to lack of authorization to access a resource.");
            mErrors.put(-21, "Error due to the lack of a valid license for the content");
            mErrors.put(-22, "Error due to the lack of a valid license for the content.  However a preview is available.");
            mErrors.put(-23, "Error due to the download content length larger than the maximum request size");
            mErrors.put(-24, "Error due to a maximum number of objects in use");
            mErrors.put(-25, "Return code for low disk space");
            mErrors.put(-26, "Error due to the requirement of user-id and password input from app for HTTP basic/digest authentication");
            mErrors.put(-27, "Error: the video container is not valid for progressive playback.");
        }
        
        public String get(int code)
        {
            return mErrors.get(code);
        }
    };
};
