/***************************************************************************
 *   Copyright 2008 Casey Link <unnamedrambler@gmail.com>                  *
 *   Copyright 2005-2009 Last.fm Ltd.                                      *
 *   Portions contributed by Casey Link, Lukasz Wisniewski,                *
 *   Mike Jennings, and Michael Novak Jr.                                  *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/
package com.mp3tunes.android.player;

import java.util.WeakHashMap;

import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerContext;
import com.binaryelysium.mp3tunes.api.LockerContext.ContextRetriever;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;


public class MP3tunesApplication extends Application
{

	private WeakHashMap<String, Object> map; // used to store global instance specific data
    private static MP3tunesApplication instance;
    
    public static final String LAST_UPDATE  = "LastUpdate";
    private final String LOCKER_CONTEXT_KEY = "mp3tunes_locker_context";
    private final String LOCKER_KEY         = "mp3tunes_locker";
    
    public static MP3tunesApplication getInstance()
    {

        return instance;
    }

    public void onCreate()
    {

        super.onCreate();
        instance = this;

        this.map = new WeakHashMap<String, Object>();
        map.put(LOCKER_CONTEXT_KEY, new LockerContext());
        LockerContext.setContextRetriever(new Retriever());
        LockerContext.instance().setPartnerToken(PrivateAPIKey.KEY);
    }    


    
    public void onTerminate()
    {
        // clean up application global
        this.map.clear();
        this.map = null;

        instance = null;
        super.onTerminate();
    }
    
    
    /**
     * Shows an error dialog to the user.
     * @param ctx
     * @param title
     * @param description
     */
    public void presentError(Context ctx, String title, String description) {
        //AlertDialog.Builder d = new AlertDialog.Builder(ctx);
        //d.setTitle(title);
        //d.setMessage(description);
        //d.setIcon(android.R.drawable.ic_dialog_alert);
        //d.setNeutralButton("OK",
        //        new DialogInterface.OnClickListener() {
        //            public void onClick(DialogInterface dialog, int whichButton)
        //            {
        //            }
        //        });
        //d.show();
    }    
    
    public void setLocker(Locker l)
    {
        map.put(LOCKER_KEY, l);
    }
    
    public Locker getLocker()
    {
        Locker l = (Locker)map.get(LOCKER_KEY);
        
        return l;
    }
    
    private class Retriever implements ContextRetriever 
    {
        public LockerContext get()
        {
            LockerContext l = (LockerContext)map.get(LOCKER_CONTEXT_KEY);
            return l;
        }
        
    }
}
