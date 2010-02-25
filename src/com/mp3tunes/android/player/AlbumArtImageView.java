package com.mp3tunes.android.player;

import java.net.URL;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Session.LoginException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class AlbumArtImageView extends ImageView
{
    private String  mFileKey;
    
    private AsyncTask<Void, Void, Boolean> mTask;
    
    public AlbumArtImageView(Context context)
    {
        super(context);
    }
    
    public AlbumArtImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }
    
    public AlbumArtImageView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }
    
    public void getRemoteArtwork(String fileKey)
    {
        mFileKey = fileKey;
        mTask = new GetAlbumArtTask(mFileKey, this).execute();
    }
    
    synchronized public void setDefaultImage(BitmapDrawable bm)
    {
        setImageDrawable(bm);
    }

    
    static class GetAlbumArtTask extends AsyncTask<Void, Void, Boolean>
    {
        String mFileKey;
        Bitmap mImage;
        
        AlbumArtImageView mView;
        
        public GetAlbumArtTask(String key, AlbumArtImageView view)
        {
            mFileKey = key;
            mView    = view;
        }
              
        @Override
        protected Boolean doInBackground(Void... params)
        {
            Locker locker = new Locker();
            try {
                mImage = locker.getAlbumArtFromFileKey(mFileKey);
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        
        @Override
        public void onPostExecute(Boolean result)
        {
            if (result) {
                if (mImage != null)
                    mView.setImage(mImage);
            }
        }
        
    }

    synchronized public void setImage(final Bitmap image)
    {
        post(new Runnable() {
            public void run() {
                setImageDrawable(null);
                BitmapDrawable bd = new BitmapDrawable(image);
                
                setImageDrawable(bd);
            }});
    }
}
