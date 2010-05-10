package com.mp3tunes.android.player.util;

import com.mp3tunes.android.player.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DialogUtils
{
    static public AlertDialog buildDialogFromLayout(Activity a, int layoutId, int layoutRootId, 
            int textViewId, int textId) 
    {
        AlertDialog.Builder builder;
        LayoutInflater inflater = (LayoutInflater) a.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(layoutId, (ViewGroup)a.findViewById(layoutRootId));

        TextView text = (TextView) layout.findViewById(textViewId);
        text.setText(textId);

        builder = new AlertDialog.Builder(a);
        builder.setView(layout);
        return builder.create();
    }
    
    static public AlertDialog buildNetworkProblemDialog(Context context, int messageId, DialogInterface.OnClickListener listener)
    {
        return new AlertDialog.Builder(context)
                                  .setTitle(R.string.network_problem)
                                  .setCancelable(false)
                                  .setPositiveButton(R.string.ok_text, listener)
                                  .setMessage(context.getString(messageId))
                                  .create();
    }
}
