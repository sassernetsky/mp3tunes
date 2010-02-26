package com.mp3tunes.android.player.util;

import com.mp3tunes.android.player.activity.LockerList;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;

public class ReturnToLockerListAction implements OnClickListener, OnCancelListener
{
    Context mContext;
    public ReturnToLockerListAction(Context context)
    {
        mContext = context;
    }
    
    public void onClick(DialogInterface dialog, int which)
    {
        returnToLockerList();
    }

    public void onCancel(DialogInterface dialog)
    {
        returnToLockerList();
    }
    
    public void returnToLockerList()
    {
        Intent intent = new Intent();
        intent.setClass(mContext, LockerList.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(intent);
    }

}
