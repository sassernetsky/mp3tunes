package com.mp3tunes.android.player.util;

import java.io.File;  

import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.service.Logger;

import android.content.Context;
import android.os.Environment;  
import android.os.StatFs;  

public class StorageInfo
{
    String mCacheDir;
    long   mMaxCacheSize;
    long   mMinFreeStorageSize;
    
    public StorageInfo(Context context)
    {
        mCacheDir           = Music.getMP3tunesCacheDir();
        mMaxCacheSize       = Music.getMaxCacheSize(context);
        mMinFreeStorageSize = Music.getMinFreeStorageSize();
    }
    
    public boolean needCacheSpace()
    {
        long currentCacheSize = size(new File(mCacheDir));
        long availible        = MemoryStatus.getAvailableExternalMemorySize();
        Logger.log("Cache Size: " + currentCacheSize + " availible: " + availible);
        return ((currentCacheSize > mMaxCacheSize && mMaxCacheSize != -1) || availible < mMinFreeStorageSize);
            
    }
    
    static public long size(File file) 
    {
        if (file.isFile())
          return file.length();
        File[] files = file.listFiles();
        long size = 0;
        if (files != null) {
          for (int i = 0; i < files.length; i++)
            size += size(files[i]);
        }
        return size;
    }
    
    static public class MemoryStatus {  
      
        static final int ERROR = -1;  
          
        static public boolean externalMemoryAvailable() {  
            return android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);  
        }  
          
        static public long getAvailableInternalMemorySize() {  
            File path = Environment.getDataDirectory();  
            StatFs stat = new StatFs(path.getPath());  
            long blockSize = stat.getBlockSize();  
            long availableBlocks = stat.getAvailableBlocks();  
            return availableBlocks * blockSize;  
        }  
          
        static public long getTotalInternalMemorySize() {  
            File path = Environment.getDataDirectory();  
            StatFs stat = new StatFs(path.getPath());  
            long blockSize = stat.getBlockSize();  
            long totalBlocks = stat.getBlockCount();  
            return totalBlocks * blockSize;  
        }  
          
        static public long getAvailableExternalMemorySize() {  
            if(externalMemoryAvailable()) {  
                File path = Environment.getExternalStorageDirectory();  
                StatFs stat = new StatFs(path.getPath());  
                long blockSize = stat.getBlockSize();  
                long availableBlocks = stat.getAvailableBlocks();  
                return availableBlocks * blockSize;  
            } else {  
                return ERROR;  
            }  
        }  
          
        static public long getTotalExternalMemorySize() {  
            if(externalMemoryAvailable()) {  
                File path = Environment.getExternalStorageDirectory();  
                StatFs stat = new StatFs(path.getPath());  
                long blockSize = stat.getBlockSize();  
                long totalBlocks = stat.getBlockCount();  
                return totalBlocks * blockSize;  
            } else {  
                return ERROR;  
            }  
        }  
          
        static public String formatSize(long size) {  
            String suffix = null;  
          
            if (size >= 1024) {  
                suffix = "KiB";  
                size /= 1024;  
                if (size >= 1024) {  
                    suffix = "MiB";  
                    size /= 1024;  
                }  
            }  
          
            StringBuilder resultBuffer = new StringBuilder(Long.toString(size));  
          
            int commaOffset = resultBuffer.length() - 3;  
            while (commaOffset > 0) {  
                resultBuffer.insert(commaOffset, ',');  
                commaOffset -= 3;  
            }  
          
            if (suffix != null)  
                resultBuffer.append(suffix);  
            return resultBuffer.toString();  
        }  
    }  
}
