package com.mp3tunes.android.player.service;


import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

public class Bitrate
{
    private static class MyTelephonyManager {
        private final static int NETWORK_TYPE_1xRTT  = 7;
        private final static int NETWORK_TYPE_CDMA   = 4;
        private final static int NETWORK_TYPE_EVDO_0 = 5;
        private final static int NETWORK_TYPE_EVDO_A = 6;
        private final static int NETWORK_TYPE_HSDPA  = 8;
        private final static int NETWORK_TYPE_HSPA   = 10;
        private final static int NETWORK_TYPE_HSUPA  = 9;
        
    }
    
    public static int getBitrate(Service service, Context context)
    {
        try {
        int bitrate = Integer.valueOf(PreferenceManager
                .getDefaultSharedPreferences(service).getString("bitrate", "-1"));

        if (bitrate == -1) {
            // Resources resources = getResources();
            // int[] vals = resources.getIntArray(R.array.rate_values);
            // TODO Figure out why the above code does not work
            int[] vals = new int[] { -1, 24000, 56000, 96000, 128000, 192000 };

            ContextWrapper      cw = new ContextWrapper(context);
            ConnectivityManager cm = (ConnectivityManager)cw.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = cm.getActiveNetworkInfo();
            int type = network.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                bitrate = vals[4]; // 5 = 128000
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager tm = (TelephonyManager) cw.getSystemService(Context.TELEPHONY_SERVICE);

                int nType = tm.getNetworkType();
                switch (nType) {
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_1xRTT:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_CDMA:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_EVDO_0:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_EVDO_A:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_HSDPA:
                        return vals[3]; // 3 = 96000
                    case MyTelephonyManager.NETWORK_TYPE_HSPA:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_HSUPA:
                        return vals[3]; // 3 = 96000
                    default:
                        Logger.log("Network Type: " + Integer.toString(nType));
                        bitrate = 0;
                }
            }
        }
        return bitrate;
        } catch (Exception e) {
            return 0;
        }
    }
}
