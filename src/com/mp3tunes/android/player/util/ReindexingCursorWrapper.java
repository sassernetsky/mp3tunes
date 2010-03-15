package com.mp3tunes.android.player.util;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

public class ReindexingCursorWrapper extends CursorWrapper
{
    Cursor mCursor;
    int[]  mIndex;
    int    mPos;
    
    interface CursorIndexer
    {
        public int [] get(Cursor c, int column);
    }
    
    public ReindexingCursorWrapper(Cursor cursor, CursorIndexer indexer, int orderByColumn)
    {
        super(cursor);
        mCursor = cursor;
        Timer timer = new Timer("Indexing cursor");
        mIndex  = indexer.get(cursor, orderByColumn);
        timer.push();
        mPos    = -1;
    }

    public void close()
    {
        super.close();
    }

    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer)
    {
        super.copyStringToBuffer(columnIndex, buffer);
    }

    public void deactivate()
    {
        super.deactivate();
    }

    public byte[] getBlob(int columnIndex)
    {
        return super.getBlob(columnIndex);
    }

    public int getColumnCount()
    {
        return super.getColumnCount();
    }

    public int getColumnIndex(String columnName)
    {
        return super.getColumnIndex(columnName);
    }

    public int getColumnIndexOrThrow(String columnName)
            throws IllegalArgumentException
    {
        return super.getColumnIndexOrThrow(columnName);
    }

    public String getColumnName(int columnIndex)
    {
        return super.getColumnName(columnIndex);
    }

    public String[] getColumnNames()
    {
        return super.getColumnNames();
    }

    public int getCount()
    {
        return super.getCount();
    }

    public double getDouble(int columnIndex)
    {
        return super.getDouble(columnIndex);
    }

    public Bundle getExtras()
    {
        return super.getExtras();
    }

    public float getFloat(int columnIndex)
    {
        return super.getFloat(columnIndex);
    }

    public int getInt(int columnIndex)
    {
        return super.getInt(columnIndex);
    }

    public long getLong(int columnIndex)
    {
        return super.getLong(columnIndex);
    }

    public int getPosition()
    {
        return mPos;
    }

    public short getShort(int columnIndex)
    {
        return super.getShort(columnIndex);
    }

    public String getString(int columnIndex)
    {
        return super.getString(columnIndex);
    }

    public boolean getWantsAllOnMoveCalls()
    {
        return super.getWantsAllOnMoveCalls();
    }

    public boolean isAfterLast()
    {
        return mPos == mIndex.length;
    }

    public boolean isBeforeFirst()
    {
        return mPos == -1;
    }

    public boolean isClosed()
    {
        return super.isClosed();
    }

    public boolean isFirst()
    {
        return mPos == 0;
    }

    public boolean isLast()
    {
        return mPos == (mIndex.length - 1);
    }

    public boolean isNull(int columnIndex)
    {
        return super.isNull(columnIndex);
    }

    public boolean move(int offset)
    {
        int pos = mPos + offset;
        if (pos >= mIndex.length) {
            mPos =  mIndex.length;
            return false;
        } else if (pos < 0) {
            mPos = -1;
            return false;
        }
        mPos = pos;
        return super.moveToPosition(mIndex[mPos]);
    }

    public boolean moveToFirst()
    {
        mPos = 0;
        if (super.moveToPosition(mIndex[mPos])) 
            return true;
        mPos = -1;
        return false;
    }

    public boolean moveToLast()
    {
        mPos = mIndex.length - 1;
        if (super.moveToPosition(mIndex[mPos])) 
            return true;
        mPos = -1;
        return false;
    }

    public boolean moveToNext()
    {
        mPos++;
        if (mPos > mIndex.length) return false;
        return super.moveToPosition(mIndex[mPos]);
    }

    public boolean moveToPosition(int position)
    {
        mPos = position;
        return super.moveToPosition(mIndex[mPos]);
    }

    public boolean moveToPrevious()
    {
        mPos--;
        if (mPos < 0) return false;
        return super.moveToPosition(mIndex[mPos]);
    }

    public void registerContentObserver(ContentObserver observer)
    {
        super.registerContentObserver(observer);
    }

    public void registerDataSetObserver(DataSetObserver observer)
    {
        super.registerDataSetObserver(observer);
    }

    public boolean requery()
    {
        return super.requery();
    }

    public Bundle respond(Bundle extras)
    {
        return super.respond(extras);
    }

    public void setNotificationUri(ContentResolver cr, Uri uri)
    {
        super.setNotificationUri(cr, uri);
    }

    public void unregisterContentObserver(ContentObserver observer)
    {
        super.unregisterContentObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer)
    {
        super.unregisterDataSetObserver(observer);
    }

}
