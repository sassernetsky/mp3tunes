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
        Pair[] pairs = new Pair[c.getCount()];
        
        int index = 0;
        if (c.moveToFirst()) {
            do {
                String val = c.getString(column);
                if (val.startsWith("The ")) val = val.substring(4).trim();
                pairs[index] = new Pair(index, val);
                index++;
            } while(c.moveToNext());
        }
        List<Pair> list = Arrays.asList(pairs);
        Collections.sort(list, new PairComparator());
        int[] indexes = new int[list.size()];
        index = 0;
        for (Pair p :  list) {
            if (p != null) {
                indexes[index] = p.key;
                index++;
            }
        }
        
        return indexes;
    }
    
    

}
