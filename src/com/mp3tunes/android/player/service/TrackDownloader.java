package com.mp3tunes.android.player.service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.HttpClientCaller.Progress;
import com.binaryelysium.util.FileUtils;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.service.CachedTrack.AlreadyDownloadedException;
import com.mp3tunes.android.player.service.PlaybackService.MyOnErrorListener;
import com.mp3tunes.android.player.util.Pair;
import com.mp3tunes.android.player.util.StorageInfo;
import com.mp3tunes.android.player.util.Timer;

public class TrackDownloader
{
    int                                 mNextJobId = 1;
    PriorityBlockingQueue<Job>          mQueue;
    boolean                             mDestroying;
    HttpClientCaller.Progress           mProgressCallback;
    private Job                         mJob;
    private OutputStreamResponseHandler mOutputHandler;
    private Object                      mChangingTrackLock;
    private Context                     mContext;
    private Timer                       mTimer;
    private MediaScannerConnection      mScanner;
    private MyOnErrorListener           mErrorListener;
    
    private final int POLL = 0;
    
    static final public class Priority
    {
        static final int NOWPLAYING   = 400;
        static final int NEXTTRACK    = 300;
        static final int FUTURETRACK  = 200;
        static final int SKIPPEDTRACK = 100;
        static final int FORSTORAGE   = 0;
    }
    public TrackDownloader(Context context, Object lock, MyOnErrorListener errorListener)
    {
        mNextJobId         = 0;
        mQueue             = new PriorityBlockingQueue<Job>(10, new JobComparator());
        mDestroying        = false;
        mChangingTrackLock = lock;
        mErrorListener     = errorListener;
        mContext           = context;
        mHandler.handleMessage(mHandler.obtainMessage(POLL));
        mScanner = new MediaScannerConnection(mContext, mClient);
        mScanner.connect();
    }
    
    //returns the Job Id of the track we are going to download
    public Pair<Integer, CachedTrack> downloadTrack(Track track,  int priority, String format, int bitrate)
    {
        try {
            //First we try to create a CachedTrack for the Track
            CachedTrack cached = new CachedTrack(track, format, bitrate);
            
            //If the track isn't finished then create a job to download it
            if (cached.getStatus() != CachedTrack.Status.finished) {
                Job job    = new Job(priority, cached, cached.getPlayUrl());
                addJob(job);
                return new Pair<Integer, CachedTrack>(job.id, cached);
            }
            
            //If the track is already finished then no job is needed
            return new Pair<Integer, CachedTrack>(null, cached);
        } catch (AlreadyDownloadedException e) {
            //We have a file in the temporary location for this track. So, we check to see if there is a job
            //to download it already
            String path = e.getPath();
            Logger.log("downloadTrack(): already have file at: " + path);
            Job job = getJobByPath(path);
            if (job != null) {
                //We have a job so we want to adjust the priority and return it
                Logger.log("downloadTrack(): Already have job adjusting priority");
                job.priority = priority;
                return new Pair<Integer, CachedTrack>(job.id, job.track);
            }
            
            //Since there was no current job for this temporary track we assume that it is a failed download.
            //In this case we delete it and create a new one
            
            File file = new File(path);
            if (file.delete()) return downloadTrack(track, priority, format, bitrate);
            return new Pair<Integer, CachedTrack>(job.id, job.track);
        }
    }

    //Note: It is not safe to stream files downloaded with this call until the job that is completed
//    public Pair<Integer, CachedTrack> downloadTrack(Track track) throws AlreadyDownloadedException
//    {
//        try {
//            CachedTrack cached = new CachedTrack(track, null, 0);
//            Job job    = new Job(Priority.FORSTORAGE, cached, cached.getUrl());
//            addJob(job);
//            return new Pair<Integer, CachedTrack>(job.id, cached);
//        } catch (AlreadyDownloadedException e) {
//            //we already have a file. First we see if there is a job running for it
//            String path = e.getPath();
//            Job job = getJobByPath(path);
//            if (job != null) {
//                job.priority = Priority.FORSTORAGE;
//                return new Pair<Integer, CachedTrack>(job.id, job.track);
//            }
//        
//            //There is no job running this means that 
//            CachedTrack cached = new CachedTrack(track, path, HttpServer.pathToUrl(path), null, 0);
//            cached.setStatus(CachedTrack.Status.finished);
//            return new Pair<Integer, CachedTrack>(null, cached);
//        }
//    }
    
    private Job getJobByPath(String path)
    {
        synchronized (mQueue) {
            for (Job job : mQueue) {
                if (job.track.getPath().equals(path)) 
                    return job;
            }
            if (mJob != null && mJob.track.getPath().equals(path))
                return mJob;
        }
        return null;
    }
    
    //This is kind of a weird function.  Its goal is to reset the priority of all
    //of the items in the queue to less than a certain value
    public void setMaxPriority(int priority)
    {
        synchronized (mQueue) {
            do {
                Job job = mQueue.poll();
                if (job == null) break;
                if (job.priority > Priority.SKIPPEDTRACK) {
                    job.priority = Priority.SKIPPEDTRACK;
                    mQueue.add(job);
                } else {
                    mQueue.add(job);
                    break;
                }
            } while (true);
        }
    }
    
    public void resetPriority(Integer id, int priority)
    {
        synchronized (mQueue) {           
            //Logger.log("resetPriority(): called with id: " + id.intValue() + " priority: " + priority + " num jobs: " + mQueue.size());
            for (Job job : mQueue) {
                //Logger.log("resetPriority(): mQueue job " + job.id + " downloads: " + job.track.getTitle());
                if (id.equals(job.id)) {
                    mQueue.remove(job);
                    //Logger.log("Changing priority of: " + job.track.getTitle() + " from: " + job.priority + " to: " + priority);
                    job.priority = priority;
                    mQueue.add(job);
                    break;
                }
            }
        }
    }
    
    private void addJob(Job job)
    {
        synchronized (job.track) {
            job.track.setStatus(CachedTrack.Status.queued);
            mQueue.add(job);     
            Logger.log("addJob(): job count: " + mQueue.size());
        }
    }

    public class Job
    {
        int              id;
        int              priority;
        CachedTrack      track;
        String           url;
        FileOutputStream stream;
        Boolean          cancelled;
        

        
        
        public Job(int priority, CachedTrack track, String url) 
        {
            this.id        = mNextJobId;
            this.priority  = priority;
            this.track     = track;
            this.url       = url;
            this.cancelled = false;
            try {
                Logger.log("Creating stream for file: " + track.getPath());
                this.stream   = new FileOutputStream(track.getPath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                assert(false);
                //This should be an impossible situation.  We literally just successfully created this file a few microseconds ago.
                //If it is no longer there then what can we do?
            }

            mNextJobId++;
        }

    }
    
    public class JobComparator implements Comparator<Job>
    {

        public int compare(Job first, Job second)
        {
            if (first.priority > second.priority) return -1;
            if (first.priority < second.priority) return  1;
            return 0;
        }
        
    }
    
    
    private final Handler mHandler = new Handler() {
        
        private int mCount = 0;
        
        private MediaScannerConnection scanner;
        
        
        private void pollNext(long delay) 
        {
            Message msg = mHandler.obtainMessage(POLL);
            mHandler.removeMessages(POLL);
            mHandler.sendMessageDelayed(msg, delay);
        }

        
        void fail(String message)
        {
            logJobState("Failed download of");
            mJob.track.setErrorMessage(message);
            mJob.track.setStatus(CachedTrack.Status.failed);
            mErrorListener.onTrackDownloadFailed(mJob.track);
        }
        
        void succeed()
        {
            logJobState("Successful download of");
            mJob.track.setStatus(CachedTrack.Status.finished);
        }
        
        void logJobState(String message)
        {
            String title  = "'" + mJob.track.getTitle() + "'";
            String artist = "'" + mJob.track.getArtistName() + "'";
            String url    = "'" + mJob.url + "'";
            Logger.log("TrackDownloader: " + message + ": Title: " + title + " Artist: " + artist + " url: " + url + " Priority: " + mJob.priority);
        }
        
        boolean setupNextJob()
        {
            synchronized (mChangingTrackLock) {
                synchronized (mQueue) {
                try {
                    if (mJob.track.getStatus() == CachedTrack.Status.failed &&
                        mJob.priority          != Priority.NOWPLAYING       &&
                        mJob.cancelled         != true) {
                            mJob.stream.close();
                            mJob.stream = new FileOutputStream(mJob.track.getPath());
                            mJob.track.setStatus(CachedTrack.Status.downloading);
                            mQueue.add(mJob);
                    }
                } catch (Exception e) {}
                    mJob = mQueue.poll();
                }
                if (mJob == null) return false;
                mJob.track.setStatus(CachedTrack.Status.downloading);
                mOutputHandler = new OutputStreamResponseHandler(mJob);
                logJobState("Begining download of");
                
                //check to make sure we have cache space    // 
                freeCacheSpace();
            }
            return true;
        }
        
        void handleSocketException(String message)
        {
            try {
                if (mOutputHandler.mReturn) {
                    succeed();
                } else {
                    fail(message);
                }
            } catch (Exception e) {
                Logger.log(e);
                fail(message);
            }
        }
        
        void performDownload() throws IOException
        {
            if (mTimer != null) {
                mTimer.push();
                mTimer = null;
            }
            if (!HttpClientCaller.getInstance().callStream(mJob.url, mOutputHandler, null)) {
                throw new IOException();
            }
            succeed();
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == POLL) {
                Thread t = new Thread() {
                    public void run() {
                        while (!mDestroying) {
                            if (!setupNextJob()) break;
                            try {
                                performDownload();
                            } catch (SocketTimeoutException e) {
                                handleSocketException("network timeout error");
                            } catch (SocketException e) {
                                handleSocketException("broken network connection error");
                            } catch (IOException e) {
                                e.printStackTrace();
                                fail(e.getMessage());
                            }
                            if (mJob.track.cacheTrack() && mScanner.isConnected()) {
                                mScanner.scanFile(mJob.track.getPath(), null);
                            }
                        }
                        mHandler.post(mPoll);
                    }
                };
                t.start();
            }
        }
        Runnable mPoll = new Runnable() {
            public void run()
            {
                pollNext(250);
            }
        };
        
//        public String copyFileToCorrectExtension(String contentType, String file)
//        {
//            String extension = null;
//            if (contentType.equals("audio/mpeg")) {
//                extension += ".mp3";
//            } else if (contentType.equals("audio/mp4")) {
//                extension += ".mp4";
//            } else if (contentType.equals("audio/ogg")) {
//                extension += ".ogg";
//            } else if (contentType.equals("audio/vorbis")) {
//                extension += ".ogg";
//            } else if (contentType.equals("application/ogg")) {
//                extension += ".ogg";
//            } else if (contentType.equals("audio/x-ms-wma")) {
//                extension += ".wma";
//            } else if (contentType.equals("video/quicktime")) {
//                Log.w("Mp3Tunes", "inserting a video file video/quicktime");
//                extension += ".mp4";
//            } else if (contentType.equals("video/mp4")) {
//                Log.w("Mp3Tunes", "inserting a video file video/mp4");
//                extension += ".mp4";
//            } else if (contentType.equals("video/x-ms-wmv")) {
//                Log.w("Mp3Tunes", "inserting a video file video/x-ms-wmv");
//                extension += ".wmv";
//            }
//            if (extension == null) return null;
//            String newFile = file.replace(".tmp", extension);
//            try {
//                FileUtils.copyFile(file, newFile);
//            } catch (IOException e) {
//                return null;
//            }
//            return newFile;
//        }
    };
    
    static private class OutputStreamResponseHandler extends HttpClientCaller.CancellableResponseHandler  
    {
        Job          mJob;
        String       mContentType;
        Object       mLock   = new Object();
        Boolean      mReturn = null;
        
        OutputStreamResponseHandler(Job job)
        {
            super();
            mJob = job;
        }
        
        public String getContentType()
        {
            return mContentType;
        }
        
        public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException 
        {
            if (mReturn != null) return mReturn;
            mReturn = false;
            
            for (Header h :response.getAllHeaders()) {
                if (h.getName().equals("Content-Type")) mContentType = h.getValue();
            }
            
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Long length = entity.getContentLength();
                InputStream input = entity.getContent();
                byte[] buffer = new byte[4096];
                int size  = 0;
                int total = 0;
                while (true) {
                    if (mJob.cancelled) {
                        Logger.log("Cancelling job");
                        abort();
                        return false;
                    }
                    try {
                        size = input.read(buffer);
                    } catch (SocketTimeoutException e) {
                        synchronized (mLock) {
                            //Sometimes our content length is somewhat off.  We want to report success
                            //in this case
                            if ((length - total) < 10000) {
                                Logger.log("Socket timed out close to the end.  Trying to handle");
                                mJob.track.mProgress.run(total, total);
                                mReturn = true;
                                return false;
                            }
                            Logger.log("Socket timed out in the middle of file. Got: " + total + " bytes. Expected: " + length + " bytes");
                            mReturn = false;
                            return false;   
                        }
                    }
                    if (size == -1) break;
                    mJob.stream.write(buffer, 0, size);
                    total += size;
                    mJob.track.mProgress.run(total, length);
                }
                return true;
            } else {
                return false;
            }
        }
    }
    
    MediaScannerConnection.MediaScannerConnectionClient mClient = 
        new MediaScannerConnection.MediaScannerConnectionClient()
    {

        public void onMediaScannerConnected()
        {
            Logger.log("Connected to MediaScanner");
        }

        public void onScanCompleted(String path, Uri uri)
        {
            if (uri != null) {
                Logger.log("Scan Successful: uri: " + uri.toString());
            } else {
                Log.w("Mp3Tunes", "Scan Failed");
            }
        }
        
    };
    
    public void clear()
    {
        if (mJob != null)
            mJob.cancelled = true;
        if (mQueue != null)
            mQueue.clear();
    }
    
    private void freeCacheSpace()
    {
        StorageInfo info = new StorageInfo(mContext);
        while (info.needCacheSpace()) {
            freeSomeCacheSpace();
        }
    }
    
    private void freeSomeCacheSpace()
    {        
        File file = getOldestFile(Music.getMP3tunesCacheDir(), null);
        if (file == null) {
            Logger.log("Empty cache dir. Nothing to delete");
            return;
        }
        Logger.log("deleting: " + file.getAbsolutePath());
        file.delete();
    }
    
    private File getOldestFile(String dir, FileFilter filter) 
    {
        File fl = new File(dir);
        if (filter == null) {
            filter = new FileFilter() {                  
                public boolean accept(File file) {
                    return file.isFile();
                }
            };
        }
        File[] files = fl.listFiles(filter);
        
        long lastMod = Long.MAX_VALUE;
        File choice = null;
        for (File file : files) {
                if (file.lastModified() < lastMod) {
                        choice = file;
                        lastMod = file.lastModified();
                }
        }
        return choice;
    }

    public void cancelOldDownload(CachedTrack track)
    {
        synchronized (mQueue) {
            //Nothing to cancel
            if (mJob == null) {
                Logger.log("cancelOldDownload(): Not cancelling: no track downloading");
                return;
            }
            
            //we are already downloading the track
            if (track.getFileKey().equals(mJob.track.getFileKey())) {
                Logger.log("cancelOldDownload(): Not cancelling: downloading: " + mJob.track.getFileKey() + " need to download: " + track.getFileKey());
                return;
            }
        
            //the track is already downloaded
            if (track.getStatus() == CachedTrack.Status.finished) {
                Logger.log("cancelOldDownload(): Not cancelling: " + track.getFileKey() + " already downloaded");
                return;
            }
        
            Logger.log("cancelOldDownload(): Cancelling  download of: " + mJob.track.getTitle() + " in favor of: " + track.getFileKey());
            mTimer = new Timer("Begining cancel");
            mJob.cancelled = true;
        }
    }
}
