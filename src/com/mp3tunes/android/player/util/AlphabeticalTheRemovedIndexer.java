package com.mp3tunes.android.player.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.database.Cursor;
import android.util.Log;

import com.mp3tunes.android.player.util.ReindexingCursorWrapper.CursorIndexer;

public class AlphabeticalTheRemovedIndexer implements CursorIndexer
{   
    public int[] get(Cursor c, int column)
    {
        List<Pair> list = getListOfPairs(c, column);
        
        
        Collections.sort(list, new PairComparator());
        
        return Pair.createIndicies(list);
    }
    
    List<Pair> getListOfPairs(Cursor c, int column)
    {
        Pair[] pairs = new Pair[c.getCount()];
        
        int index = 0;
        if (c.moveToFirst()) {
            do {
                String val = c.getString(column);
                
                val = removeThe(val);
                pairs[index] = new Pair(index, val);
                index++;
            } while(c.moveToNext());
        }
        return Arrays.asList(pairs);
    }
    
    String removeThe(String val)
    {
        if (val.startsWith("The ")) 
            return val.substring(4).trim();
        return val;
    }
}
