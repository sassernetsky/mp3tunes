package com.mp3tunes.android.player.service;

import java.util.HashMap;

public class PlaybackErrorCodes
{
    private static PacketVideoErrorCodes sErrors = new PacketVideoErrorCodes();
    private static PacketVideoInfoCodes  sInfo   = new PacketVideoInfoCodes();
    
    public static String getError(int code)
    {
        return sErrors.get(code);
    }
    
    public static boolean isFatalError(int error)
    {
        return sErrors.isFatalError(error);
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
        
        public boolean isFatalError(int error)
        {
            switch (error) {
                case -1:  //"Error due to general failure"
                    return true;
                case -2:  //"Error due to cancellation"
                    return false;
                case -3:  //"Error due to no memory being available"
                    return true;
                case -4:  //"Error due to request not being supported"
                    return true;
                case -5:  //"Error due to invalid argument"
                    return true;
                case -6:  //"Error due to invalid resource handle being specified"
                    return true;
                case -7:  //"Error due to resource already exists and another one cannot be created"
                    return true;
                case -8:  //"Error due to resource being busy and request cannot be handled"
                    return true;
                case -9:  //"Error due to resource not ready to accept request"
                    return true;
                case -10: //"Error due to data corruption being detected"
                    return false;
                case -11: //"Error due to request timing out"
                    return true;
                case -12: //"Error due to general overflow"
                    return false;
                case -13: //"Error due to general underflow"
                    return false;
                case -14: //"Error due to resource being in wrong state to handle request"
                    return false;
                case -15: //"Error due to resource not being available"
                    return false;
                case -16: //"Error due to invalid configuration of resource"
                    return false;
                case -17: //"Error due to general error in underlying resource"
                    return false;
                case -18: //"Error due to general data processing"
                    return false;
                case -19: //"Error due to general port processing"
                    return true;
                case -20: //"Error due to lack of authorization to access a resource."
                    return false;
                case -21: //"Error due to the lack of a valid license for the content"
                    return false;
                case -22: //"Error due to the lack of a valid license for the content.  However a preview is available."
                    return false;
                case -23: //"Error due to the download content length larger than the maximum request size"
                    return false;
                case -24: //"Error due to a maximum number of objects in use"
                    return true;
                case -25: //"Return code for low disk space"
                    return true;
                case -26: //"Error due to the requirement of user-id and password input from app for HTTP basic/digest authentication"
                    return false;
                case -27: //"Error: the video container is not valid for progressive playback."
                    return true;
                    default:
                        return false;
            }
        }
    }

    
    private static class PacketVideoInfoCodes {
        private HashMap<Integer, String> mInfo = new HashMap<Integer, String>();
        public PacketVideoInfoCodes()
        {
            mInfo.put(10, "Notification that a port was created");
            mInfo.put(11, "Notification that a port was deleted");
            mInfo.put(12, "Notification that a port was connected");
            mInfo.put(13, "Notification that a port was disconnected");
            mInfo.put(14, "Notification that an overflow occurred (not fatal error)");
            mInfo.put(15, "Notification that an underflow occurred (not fatal error)");
            mInfo.put(16, "Notification that a processing failure occurred (not fatal error)");
            mInfo.put(17, "Notification that end of data stream has been reached");
            mInfo.put(18, "Notification that a data buffer has been created");
            mInfo.put(19, "Notification that buffering of data has started");
            mInfo.put(20, "Notification for data buffering level status");
            mInfo.put(21, "Notification that data buffering has completed");
            mInfo.put(22, "Notification that data is ready for use");
            mInfo.put(23, "Notification for position status");
            mInfo.put(24, "Notification for node state change");
            mInfo.put(25, "Notification that data was discarded during synchronization.");
            mInfo.put(26, "Notification that error handling has started");
            mInfo.put(27, "Notification that error handling has completed");
            mInfo.put(28, "Notification from a remote source");
            mInfo.put(29, "Notification that license acquisition has started.");
            mInfo.put(30, "Notification that download content length is available");
            mInfo.put(31, "Notification that downloaded content reaches the maximum request size, and will be truncated, especially for the case of unavailable content length");
            mInfo.put(32, "Notification that source format is not supported, typically sent during protocol rollover");
            mInfo.put(33, "Notification that a clip transition has occurred while playing a playlist");
            mInfo.put(34, "Notification that content type for download or HTTP streaming is available");
            mInfo.put(35, "Notification that paticular track is disable. This one is on a per track basis.");
            mInfo.put(36, "Notification that unexpected data has been obtained, especially for download, when client receives from server more data than requested in content-length header");
            mInfo.put(37, "Notification that server discnnect happens after download is complete");
            mInfo.put(38, "Notification that new media stream has been started");
            mInfo.put(39, "Notification that node has processed a command with ReportObserver marker info");
            mInfo.put(40, "Notification that meta data is available with source node");
            mInfo.put(41, "Notification that duration is available with source node");
            mInfo.put(42, "Notification that Change Position request not supported");
            mInfo.put(43, "Notification that the content is poorly inter-leaved");
            mInfo.put(44, "Notification that the video track is falling behind");
            mInfo.put(100,"Placeholder for end of range");
        }
        
        public String get(int code)
        {
            return mInfo.get(code);
        }
        
        public boolean isFatalError(int error)
        {
            return false;
        }
    }

    public static String getInfo(int code)
    {
        return sInfo.get(code);
    };
};
