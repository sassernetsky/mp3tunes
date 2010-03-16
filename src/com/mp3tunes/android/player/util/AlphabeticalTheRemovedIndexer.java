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

    private class Pair
    {
        Pair(int key, String value)
        {
            this.key   = key;
            this.value = value;
        }
        
        int key;
        String value;
    }
    
    private class PairComparator implements Comparator<Pair>
    {

        public int compare(Pair first, Pair second)
        {
            if (first == null && second == null) return 0;
            if (first == null) return -1;
            if (second == null) return -1;
            return first.value.compareToIgnoreCase(second.value);
        }
        
    }
    
    public int[] get(Cursor c, int column)
    {
        List<Pair> list = getListOfPairs(c, column);
        
        
        Collections.sort(list, new PairComparator());
        
        return createIndicies(list);
    }

    int[] createIndicies(List<Pair> list)
    {
        int[] indexes = new int[list.size()];
        int index = 0;
        for (Pair p :  list) {
            if (p != null) {
                indexes[index] = p.key;
                index++;
            }
        }
        
        return indexes;
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
