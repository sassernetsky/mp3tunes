package com.mp3tunes.android.player.util;

import java.lang.reflect.Method;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Session.LoginException;
import com.mp3tunes.android.player.MP3tunesApplication;
import com.mp3tunes.android.player.activity.Login;

public class RefreshSessionTask extends AsyncTask<Void, Void, Boolean>
{
    Locker         mLocker;
    ContextWrapper mCw;
    Method         mSuccess;
    Method         mFail;
    Object         mReceiver;
    
    public RefreshSessionTask(Context context, Locker locker, Method success, Method fail, Object receiver)
    {
        mCw       = new ContextWrapper(context);
        mLocker   = locker;
        mSuccess  = success;
        mFail     = fail;
        mReceiver = receiver;
    }
    
    public RefreshSessionTask(Context context)
    {
        mCw       = new ContextWrapper(context);
        mLocker   = MP3tunesApplication.getInstance().getLocker();
        mSuccess  = null;
        mFail     = null;
        mReceiver = null;
    }

    public void onPreExecute()
    {
    }
    
    public Boolean doInForground()
    {
        return doInBackground();
    }
    
    @Override
    public Boolean doInBackground(Void... params)
    {
        boolean success = false;
        try {
            SharedPreferences settings = mCw.getSharedPreferences(Login.PREFS, 0);
            String user = settings.getString("mp3tunes_user", "");
            String pass = settings.getString("mp3tunes_pass", "");
            if (!user.equals("") && !pass.equals("")) {
                mLocker.refreshSession(user, pass);
                success = true;
            }
        } catch (LockerException e) {
            success = false;
        } catch (LoginException e) {
            success = false;
        }
        return success;
    }

    @Override
    public void onPostExecute(Boolean result)
    {
        try {
            if (result)
                mSuccess.invoke(mReceiver, (Object[])null);
            else
                mFail.invoke(mReceiver, (Object[])null);
        } catch (Exception e) {
                e.printStackTrace();
        }
    }
}
