package com.mp3tunes.android.player.service;

import com.binaryelysium.mp3tunes.api.Locker;
import com.mp3tunes.android.player.activity.Login;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.AsyncTask;


public class RefreshSessionTask extends AsyncTask<Void, Void, Boolean>
{
    Locker         mLocker;
    ContextWrapper mCw;
    
    public RefreshSessionTask(Context context, Locker locker)
    {
        mCw     = new ContextWrapper(context);
        mLocker = locker;
    }

    public void onPreExecute()
    {
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
        } catch (Exception e) {
            success = false;
        }
        return success;
    }

    @Override
    public void onPostExecute(Boolean result)
    {
        
    }
    
}
