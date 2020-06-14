/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Stack;

/**
 * base class of things that can be serialised to a JSON representation and saved.
 */
abstract class EntryList implements JSONable, EntryListItem {
    private final String TAG = "Serialisable";
    ArrayAdapter mArrayAdapter;

    // The list that contains this list.
    private EntryList mParent;

    private Context mContext;

    protected ArrayList<EntryListItem> mUnsorted = new ArrayList<>();

    // Is this list being displayed sorted?
    protected boolean mShowSorted = false;
    protected ArrayList<EntryListItem> mSorted = new ArrayList<>();

    transient EntryListItem mMovingItem = null;

    private class Remove {
        int index;
        EntryListItem item;

        Remove(int ix, EntryListItem it) {
            index = ix;
            item = it;
        }
    }

    Stack<ArrayList<Remove>> mRemoves;

    EntryList(EntryList parent, Context cxt) {
        mParent = parent;
        mContext = cxt;
        mRemoves = new Stack<>();
    }

    /**
     * Construct by reading content from a Uri
     *
     * @param uri the URI to read from
     * @param cxt the context, used to access the ContentResolver. Generally the application context.
     * @throws Exception if there's a problem reading or decoding
     */
    EntryList(Uri uri, EntryList parent, Context cxt) throws Exception {
        this(parent, cxt);
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File((uri.getPath())));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = cxt.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
        }
        fromStream(stream);
    }

    public EntryList getContainer() {
        return mParent;
    }

    Context getContext() {
        return mContext;
    }

    /**
     * Get an array giving to the current sort order of the items in the list. Used only for
     * display.
     *
     * @return a sorted array of items
     */
    protected ArrayList<EntryListItem> getSorted() {
        return (mShowSorted ? mSorted : mUnsorted);
    }

    /**
     * Save the list, subclasses override if the operation is supported.
     */
    void save(Context cxt) {
    }

    /**
     * Get the current list size
     *
     * @return size
     */
    int size() {
        return mUnsorted.size();
    }

    /**
     * Add a new item to the end of the list
     *
     * @param item the item to add
     * @return the index of the added item
     */
    void add(EntryListItem item) {
        mUnsorted.add(item);
        reSort();
    }

    /**
     * Get the entry at the given index
     *
     * @param i index of the list to remove
     */
    EntryListItem get(int i) {
        if (i >= mUnsorted.size())
            return null;
        return mUnsorted.get(i);
    }

    /**
     * Put a new item at a specified position in the list
     *
     * @param item the item to add
     * @param i    the index of the added item
     */
    void put(int i, EntryListItem item) {
        mUnsorted.add(i, item);
        reSort();
    }

    void clear() {
        mUnsorted.clear();
        mSorted.clear();
    }

    /**
     * Remove the given item from the list
     *
     * @param item item to remove
     */
    void remove(EntryListItem item, boolean undo) {
        Log.d(TAG, "remove");
        if (undo) {
            if (mRemoves.size() == 0)
                mRemoves.push(new ArrayList<Remove>());
            mRemoves.peek().add(new Remove(mUnsorted.indexOf(item), item));
        }
        mUnsorted.remove(item);
        mSorted.remove(item);
    }

    /**
     * Call to start a new undo set. An undo will undo all the delete operations in the most
     * recent undo set.
     */
    void newUndoSet() {
        mRemoves.push(new ArrayList<Remove>());
    }

    /**
     * Undo the last remove. All operations with the same undo tag will be undone.
     *
     * @return the number of items restored
     */
    int undoRemove() {
        if (mRemoves.size() == 0)
            return 0;
        ArrayList<Remove> items = mRemoves.pop();
        if (items.size() == 0)
            return 0;
        for (Remove it : items)
            mUnsorted.add(it.index, it.item);
        reSort();
        return items.size();
    }

    /**
     * Find an item in the list by text string
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return index of matched item or -1 if not found
     */
    int find(String str, boolean matchCase) {
        int i = -1;
        for (EntryListItem next : mUnsorted) {
            i++;
            if (next.getText().equalsIgnoreCase(str))
                return i;
        }
        if (matchCase)
            return -1;
        i = -1;
        for (EntryListItem next : mUnsorted) {
            i++;
            if (next.getText().toLowerCase().contains(str.toLowerCase()))
                return i;
        }
        return -1;
    }

    /**
     * Get the index of the item in the list, or -1 if it's not there
     * c.f. find(), sortedIndexOf
     *
     * @param ci
     * @return the index of the item in the list, or -1 if it's not there
     */
    int indexOf(EntryListItem ci) {
        return mUnsorted.indexOf(ci);
    }

    /**
     * Get the index of the item in the sorted list, or -1 if it's not there
     * c.f. find(), indexOf
     *
     * @param ci
     * @return the index of the item in the list, or -1 if it's not there
     */
    int sortedIndexOf(EntryListItem ci) {
        return mSorted.indexOf(ci);
    }

    /**
     * Move the item to a new position in the list
     *
     * @param bit item to move
     * @param i   position to move it to, position in the unsorted list!
     * @return true if the item moved
     */
    boolean moveItemToPosition(EntryListItem item, int i) {
        Log.d(TAG, "M" + i);
        if (i >= 0 && i < mUnsorted.size()) {
            remove(item, false);
            put(i, item);
            return true;
        }
        return false;
    }

    /**
     * Set the item in the list that is currently being moved
     *
     * @param item the item being moved
     */
    void setMovingItem(EntryListItem item) {
        if (mMovingItem == null || item == null)
            mMovingItem = item;
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Inform the UI that the list changed and needs refreshing.
     *
     * @param doSave true to save the list
     */
    public void notifyListChanged(boolean doSave) {
        if (doSave)
            save(getContext());
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * After an edit to the list, re-sort the UI representation
     */
    protected void reSort() {
        mSorted = (ArrayList<EntryListItem>) mUnsorted.clone();
        Collections.sort(mSorted, new Comparator<EntryListItem>() {
            public int compare(EntryListItem item, EntryListItem item2) {
                return item.getText().compareToIgnoreCase(item2.getText());
            }
        });
    }

    /**
     * Launch a thread to perform an asynchronous save to a URI. If there's an error, it will
     * be reported in a Toast on the UI thread.
     *
     * @param uri the URI to save to
     */
    void saveToUri(final Uri uri) {
        // Launch a thread to do this save, so we don't block the ui thread
        Log.d(TAG, "Saving to " + uri);
        final byte[] data;
        try {
            String s = toJSON().toString(1);
            data = s.getBytes();
        } catch (JSONException je) {
            throw new Error("JSON exception " + je.getMessage());
        }
        new Thread(new Runnable() {
            public void run() {
                OutputStream stream;
                try {
                    String scheme = uri.getScheme();
                    if (Objects.equals(scheme, ContentResolver.SCHEME_FILE)) {
                        String path = uri.getPath();
                        stream = new FileOutputStream(new File(path));
                    } else if (Objects.equals(scheme, ContentResolver.SCHEME_CONTENT))
                        stream = mContext.getContentResolver().openOutputStream(uri);
                    else
                        throw new IOException("Unknown uri scheme: " + uri.getScheme());
                    if (stream == null)
                        throw new IOException("Stream open failed");
                    stream.write(data);
                    stream.close();
                    Log.d(TAG, "Saved to " + uri);
                } catch (IOException ioe) {
                    final String mess = ioe.getMessage();
                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(mContext, "Exception while saving to Uri " + mess, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Load the object from JSON read from the stream
     *
     * @param stream source of the JSON
     * @throws Exception IOException or JSONException
     */
    void fromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        JSONObject job;
        try {
            job = new JSONObject(sb.toString());
        } catch (JSONException je) {
            // Old format, see if we can handle it as an array of items
            job = new JSONObject();
            job.put("items", new JSONArray(sb.toString()));
        }
        fromJSON(job);
    }

    @Override // implements JSONable
    public void fromJSON(JSONObject job) throws JSONException {
        try {
            mShowSorted = job.getBoolean("sort");
        } catch (JSONException je) {
            mShowSorted = Settings.getBool(Settings.forceAlphaSort);
        }
    }

    @Override // implements JSONable
    public JSONObject toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        JSONArray its = new JSONArray();
        for (EntryListItem cl : mUnsorted)
            its.put(cl.toJSON());
        job.put("items", its);
        job.put("sort", mShowSorted);
        return job;
    }
}