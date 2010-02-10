/***************************************************************************
 *   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
 *   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
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
package com.mp3tunes.android.player.activity;

import java.util.ArrayList;

import com.mp3tunes.android.player.ListAdapter;
import com.mp3tunes.android.player.ListEntry;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.service.GuiNotifier;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Primary activity that encapsulates browsing the locker
 * 
 * @author ramblurr
 * 
 */
public class LockerList extends ListActivity
{
    
    //Static finals
    static class Option {
        Option(int one, int two)
        {
            str  = one;
            icon = two;
        }
        public final int str;
        public final int icon;
    };
    private static final Option[] mMainOptions = {
        new Option(R.string.artists, R.drawable.artist_icon),
        new Option(R.string.albums, R.drawable.album_icon),
        new Option(R.string.playlists, R.drawable.playlist_icon),
        new Option(R.string.radio, R.drawable.playlist_icon),
        new Option(R.string.search, R.drawable.search_icon)
    };
    
    // sense of the animation when changing menu
    private static final int TRANSLATION_LEFT  = 0;
    private static final int TRANSLATION_RIGHT = 1;
    private static final int ABOUT_DIALOG      = 0;

    private IntentFilter mIntentFilter;
    private Dialog  mAboutDialog;

    @Override
    public void onCreate(Bundle state)
    {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.lockerlist);

        // this prevents the background image from flickering when the
        // animations run
        getListView().setAnimationCacheEnabled( false );
        
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_ERROR );
        mIntentFilter.addAction(GuiNotifier.META_CHANGED );

        createAlertDialog();
        showMainMenu( TRANSLATION_LEFT );
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();    
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mStatusListener);
        
    }
    
    @Override
    public void onResume() {
        registerReceiver( mStatusListener, mIntentFilter );
        super.onResume();
    }
    
    @Override
    public void onStop() 
    {
        if (mAboutDialog.isShowing()) dismissDialog(ABOUT_DIALOG);
        super.onStop();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog d = null;
        if(id == ABOUT_DIALOG){
            createAlertDialog();
            d = mAboutDialog;
        }
        return d;
    }

    
    private BroadcastReceiver mStatusListener = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            String action = intent.getAction();
            if ( action.equals(GuiNotifier.PLAYBACK_ERROR)) {

            } else if( action.equals(GuiNotifier.META_CHANGED)){
                //Update now playing buttons after the service is re-bound
            }
        }
    };

    /** Creates the menu items */
    public boolean onCreateOptionsMenu( Menu menu )
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu( Menu menu )
    {
        menu.findItem(R.id.menu_opt_player).setVisible( Music.isMusicPlaying() );
        return super.onPrepareOptionsMenu( menu );
    }

    /** Handles menu clicks */
    public boolean onOptionsItemSelected( MenuItem item )
    {
        Intent intent;
        switch ( item.getItemId() )
        {
        case R.id.menu_opt_logout:
            clearData();
            logout();
            return true;
        case R.id.menu_opt_settings:
            intent = new Intent(LockerList.this, Preferences.class);
            startActivity(intent);
            return true;
        case R.id.menu_opt_about:
            showDialog(ABOUT_DIALOG);
            return true;
        case R.id.menu_opt_player:
            intent = new Intent("com.mp3tunes.android.player.PLAYER");
            startActivity(intent);
            return true;
        }
        return false;
    }

    /** displays the main menu */
    private void showMainMenu( int sense )
    {
        ArrayList<ListEntry> entries = new ArrayList<ListEntry>();
        int listArrow = R.drawable.list_arrow;

        for (Option o : mMainOptions) {
            entries.add(new ListEntry(getString(o.str), o.icon, getString(o.str), listArrow));    
        }
        
        ListAdapter adapter = new ListAdapter(LockerList.this);
        adapter.setSourceIconified(entries);
        setListAdapter(adapter);
        
        if (sense == TRANSLATION_LEFT)
            getListView().startAnimation(AnimationUtils.loadAnimation(this, R.anim.ltrtranslation));
        else if (sense == TRANSLATION_RIGHT)
            getListView().startAnimation(AnimationUtils.loadAnimation(this, R.anim.rtltranslation));
    }

    private void showSubMenu( int sense , int pos)
    {
        // which menu option has been selected
        Intent intent = new Intent(Intent.ACTION_PICK);
        switch (mMainOptions[pos].str)
        {
        case R.string.artists:
            intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/artist");
            break; 
        case R.string.albums:
            intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/album");
            break;
        case R.string.tracks:
            intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
            break;
        case R.string.search:
            onSearchRequested();
            return;
        case R.string.radio:
            intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/radio");
            break;
        case R.string.playlists:
            intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/playlist");
            break;
        default:
            return;
        }
        startActivity(intent);
    }
    
    @Override
    protected void onListItemClick( ListView l, View vu, int position, long id )
    {
        showSubMenu(TRANSLATION_LEFT, position);
    }


    /**
     * Clears all session data (and the cache), and sends the user back to the
     * login screen.
     */
    private void logout()
    {
        Intent intent = new Intent( LockerList.this, Login.class );
        startActivity( intent );
        finish();
    }

    private void clearData()
    {
        SharedPreferences settings = getSharedPreferences( Login.PREFS, 0 );
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("auto_login", false);
        editor.commit();
        Music.getDb(this).clearDB();
        try {
            Music.sService.stop();
        } catch (RemoteException e) {
        } catch (Exception e) {
        }
    }
    
    private void createAlertDialog()
    {
        AlertDialog.Builder builder;
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.about_dialog, (ViewGroup)findViewById(R.id.layout_about_root));

        
        ((TextView)layout.findViewById(R.id.about_player_name_view)).setText(R.string.about_player_name);
        ((TextView)layout.findViewById(R.id.about_bugs_link_view)).setText(R.string.about_bugs_link);
        ((TextView)layout.findViewById(R.id.about_code_link_view)).setText(R.string.about_code_link);
        TextView text = (TextView)layout.findViewById(R.id.about_text_view);
        
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo("com.mp3tunes.android.player", 0).versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        String t = getString(R.string.about_text);
        t = String.format(t, version);
        text.setText(t);

        builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        mAboutDialog = builder.create();
        mAboutDialog.setCancelable(true);
        mAboutDialog.setCanceledOnTouchOutside(true);
    }
}

