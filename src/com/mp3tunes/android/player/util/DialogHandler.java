package com.mp3tunes.android.player.util;

import java.lang.ref.WeakReference;
import java.util.Map;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public class DialogHandler
{
    private WeakReference<Activity> mActivity;
    private String[]                mMap;
    private DialogHandlerImpl       mImpl;
    
    static boolean sIsLogging = true;
    
    private void log(String func, String message)
    {
        if (sIsLogging)
            Log.w("Mp3Tunes DialogHandler." + func, message);
    }
    
    public DialogHandler(Activity activity, String[] values) 
    {
        mActivity = new WeakReference<Activity>(activity);
        mMap      = values;
        
        //FIXME: this is deprecated in favor of SDK_INT but SDK_INT does not
        //exist in  API level 3 which is what we are testing for
        String sdk = Build.VERSION.SDK;
        log("DialogHandler", "SDK version " +  sdk);
        if (sdk.equals("3"))
            mImpl = new CupcakeDialogHandler();
        else
            mImpl = new DefaultDialogHandler();
    }
    
    public void   dismiss(int dialog)
    {
        mImpl.dismiss(dialog);
    }
    
    public void show(int dialog)
    {
        log("show", "Dialog id: " + dialog);
        mImpl.show(dialog);
    }
    
    public void   dismissAll()
    {
        mImpl.dismissAll();
    }
    
    public Dialog createDialog(int id)
    {
        return mImpl.createDialog(id);
    }
    
    public void   prepareDialog(int id)
    {
        mImpl.prepareDialog(id);
    }
    
    interface DialogHandlerImpl {
        public void   dismiss(int dialog);
        public void   show(int dialog);
        public void   dismissAll();
        public Dialog createDialog(int id);
        public void   prepareDialog(int id);
    }
    
    
    private class CupcakeDialogHandler implements DialogHandlerImpl
    {
        private ProgressDialog       mStatusDialog;
        private int                  mDialogValue;
        
        public void dismissAll()
        {
            try {
                log("CupcakeDialogHandler.dismissAll", "Removing dialog");
                mActivity.get().removeDialog(mDialogValue);
                mStatusDialog = null;
                mDialogValue  = 0;
                log("CupcakeDialogHandler.dismissAll", "dialog removed");
            } catch (Exception e) {
                
            }
        }

        public Dialog createDialog(int id)
        {
            if (mStatusDialog == null) {
                log("CupcakeDialogHandler.createDialog", "creating new dialog");
                mStatusDialog = new ProgressDialog(mActivity.get());
                log("CupcakeDialogHandler.createDialog", "dialog created");
                setMessage(id);
                mStatusDialog.setTitle("");
                mStatusDialog.setIndeterminate(true);
                mStatusDialog.setCancelable(true);
                mDialogValue = id;
                log("CupcakeDialogHandler.createDialog", "dialog setup");
            }
            return mStatusDialog;
        }

        public void dismiss(int dialog)
        {
            if (mStatusDialog != null) dialog = mDialogValue;
            try {
                mActivity.get().dismissDialog(dialog);
            } catch (Exception e) {}
        }

        public void show(int dialog)
        {
            if (mStatusDialog != null) {
                setMessage(dialog);
                mActivity.get().showDialog(mDialogValue);
            } else {
                log("CupcakeDialogHandler.show", "Null Status dialog");
                mDialogValue = dialog;
                mActivity.get().showDialog(dialog);
                log("CupcakeDialogHandler.show", "dialog shown");
            }
        }
        
        private void setMessage(int id)
        {
            log("CupcakeDialogHandler.prepareDialog", "preparing");
            String text = mMap[id];
            if (text == null) {
                text = "Unknown State: " + id; 
            }
            mStatusDialog.setMessage(text);
            log("CupcakeDialogHandler.prepareDialog", "message set");
        }
        
        public void   prepareDialog(int id)
        {
        }
    }
    
    private class MyProgressDialog extends ProgressDialog
    {
        public MyProgressDialog(Context context)
        {
            super(context);
        }

        @Override
        public void onRestoreInstanceState(Bundle state)
        {
            super.onRestoreInstanceState(state);
        }
    }
    
    private class DefaultDialogHandler implements DialogHandlerImpl
    {
        private MyProgressDialog       mStatusDialog;
        
        public void dismissAll()
        {
            for (int i = 0; i < mMap.length; i++) {
                dismiss(i);
            }
        }

        public Dialog createDialog(int id)
        {
            if (mStatusDialog == null) {
                mStatusDialog = new MyProgressDialog(mActivity.get());
                mStatusDialog.setTitle("");
                mStatusDialog.setIndeterminate(true);
                mStatusDialog.setCancelable(true);
            }
            return mStatusDialog;
        }

        public void dismiss(int dialog)
        {
            try {
                mActivity.get().dismissDialog(dialog);
            } catch (Exception e2) {}
        }

        public void show(int dialog)
        {
            mActivity.get().showDialog(dialog);
        }
        
        public void   prepareDialog(int id)
        {
            String text = mMap[id];
            if (text == null) {
                text = "Unknown State: " + id; 
            }
            mStatusDialog.setMessage(text);
        }
    }
}
