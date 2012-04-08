/*   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shellware.CarHome;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

/**
 * Retrieves and organizes media to play. Before being used, you must call {@link #prepare()},
 * which will retrieve all of the music on the user's device (by performing a query on a content
 * resolver). After that, it's ready to retrieve a random song, with its title and URI, upon
 * request.
 */
public class MusicRetriever {
    final String TAG = "MusicRetriever";

    ContentResolver mContentResolver;

    private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    
    // the items (songs) we have queried
    List<Item> mItems = new ArrayList<Item>();

    Random mRandom = new Random();

    public MusicRetriever(ContentResolver cr) {
        mContentResolver = cr;
    }

    /**
     * Loads music data. This method may take long, so be sure to call it asynchronously without
     * blocking the main thread.
     */
    public void prepare() {
        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.i(TAG, "Querying media...");
        Log.i(TAG, "URI: " + uri.toString());

        // Perform a query on the content resolver. The URI we're passing specifies that we
        // want to query for all audio media on external storage (e.g. SD card)
        Cursor cur = mContentResolver.query(uri, null, null, null, null);
        Log.i(TAG, "Query finished. " + (cur == null ? "Returned NULL." : "Returned a cursor."));

        if (cur == null) {
            // Query failed...
            Log.e(TAG, "Failed to retrieve music: cursor is null :-(");
            return;
        }
        if (!cur.moveToFirst()) {
            // Nothing to query. There is no music on the device. How boring.
            Log.e(TAG, "Failed to move cursor to first row (no query results).");
            return;
        }

        Log.i(TAG, "Listing...");

        // retrieve the indices of the columns where the ID and title of the song are
        int titleColumn = cur.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
        int artistColumn = cur.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST);
        int albumIdColumn = cur.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM_ID);
        int idColumn = cur.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
        int durationColumn = cur.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION);

        Log.i(TAG, "Title column index: " + String.valueOf(titleColumn));
        Log.i(TAG, "ID column index: " + String.valueOf(titleColumn));
        Log.i(TAG, "Artist column index: " + String.valueOf(artistColumn));

        long lastAlbumId = 0;
        Bitmap artwork = null;
        int artworkSize = 0;
        
        // add each song to mItems
        do {
        	Long albumId = cur.getLong(albumIdColumn);
        	
        	if (albumId != lastAlbumId) {        		
	            Uri artUri = ContentUris.withAppendedId(sArtworkUri, albumId);
	            ContentResolver res = this.getContentResolver();
	            InputStream in = null;
	            
	            artworkSize = 0;
	            
	    		try {
	    			in = res.openInputStream(artUri);
	    	        artwork = BitmapFactory.decodeStream(in);
	    	        artworkSize = artwork.getByteCount();
	    	        lastAlbumId = albumId;
	    		} catch (FileNotFoundException e) {
	    			artworkSize = 0;
	    			lastAlbumId = 0;
	    		}
        	}
        	
        	Log.i(TAG, "ID: " + cur.getString(idColumn) + " Album: " + cur.getString(albumIdColumn) + " Art: " + artworkSize);
            mItems.add(new Item(cur.getLong(idColumn), 
            					cur.getString(titleColumn), 
            					cur.getString(artistColumn),
            					cur.getInt(durationColumn),
            					artworkSize == 0 ? null : artwork));
            
        } while (cur.moveToNext());

        Log.i(TAG, "Done querying media. MusicRetriever is ready.");
    }

    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    /** Returns a random Item. If there are no items available, returns null. */
    public Item getRandomItem() {
        if (mItems.size() <= 0) return null;
        return mItems.get(mRandom.nextInt(mItems.size()));
    }

    public class Item {
        private long id;
        private String title;
        private String artist;
        private int duration;
        private Bitmap albumArt;

        public Item(long id, String title, String artist, int duration, Bitmap albumArt) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.albumArt = albumArt;
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public int getDuration() { return duration; }
        public Bitmap getAlbumArt() { return albumArt; }
        
        public Uri getURI() {
            return ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
    }
}
