package com.cdot.lists;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cdot.lists.databinding.MainActivityBinding;
import com.cdot.lists.fragment.ChecklistFragment;
import com.cdot.lists.fragment.ChecklistsFragment;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.Checklists;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    // AppCompatActivity is a subclass of androidx.fragment.app.FragmentActivity
    private static final String TAG = "MainActivity";

    public static final int REQUEST_CHANGE_STORE = 1;
    public static final int REQUEST_CREATE_STORE = 2;
    public static final int REQUEST_IMPORT_LIST = 3;

    public Checklists mLists;
    private Settings mSettings;

    public Settings getSettings() {
        return mSettings;
    }

    @Override // FragmentActivity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivityBinding binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mLists = new Checklists();
        mSettings = new Settings(this);

        loadLists();

        Fragment f = new ChecklistsFragment(mLists);

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.fragment, f, TAG).commit();
    }

    @Override // FragmentActivity
    public void onAttachedToWindow() {
        if (mSettings.getBool(Settings.alwaysShow)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    @Override // FragmentActivity
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - loading lists");
        loadLists();
        if (mLists == null || mLists.size() == 0)
            Toast.makeText(this, R.string.no_lists, Toast.LENGTH_LONG).show();
    }

    /**
     * Get the fragment at the top of the back stack
     *
     * @return a fragment
     */
    public EntryListFragment getFragment() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            int fid = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1).getId();
            return (EntryListFragment) fm.findFragmentById(fid);
        } else
            return (EntryListFragment) fm.findFragmentByTag(TAG);
    }

    /**
     * Dispatch motion event to current fragment
     *
     * @param motionEvent
     * @return
     */
    @Override // FragmentActivity
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        // dispatch to current fragment
        EntryListFragment elf = getFragment();
        if (elf != null && elf.dispatchTouchEvent(motionEvent))
            return true;
        return super.dispatchTouchEvent(motionEvent);
    }


    @Override // FragmentActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        if (resultCode != Activity.RESULT_OK || resultData == null)
            return;

        if (requestCode == REQUEST_IMPORT_LIST)
            handleImport(resultData.getData());
        else if (requestCode == REQUEST_CHANGE_STORE || requestCode == REQUEST_CREATE_STORE)
            handleChangeStore(resultData);
    }

    /**
     * Save the checklists. Saves to the cache first, and then the backing store (if configured)
     */
    void saveLists() {
        // Save to the cache, then refresh the backing store. That way the
        // cache will always be older than the backing store if the backing store save
        // succeeds
        Log.d(TAG, "Saving to cache");
        try {
            FileOutputStream stream = openFileOutput(Settings.cacheFile, Context.MODE_PRIVATE);
            String s = mLists.toJSON().toString(1);
            stream.write(s.getBytes());
            stream.close();
        } catch (Exception e) {
            // Would really like to toast this
            Log.d(TAG, "Exception saving to cache: " + e);
        }
        final Uri uri = mSettings.getUri("backingStore");
        if (uri == null)
            return;
        mLists.saveToUri(uri, this);
    }

    /**
     * Load the list of checklists. The backing store has the master list, the local cache
     * is merged with it, replacing the lists on the backing store if the last save date on the
     * cache is more recent.
     */
    Thread mLoadThread = null;

    void handleImport(Uri uri) {
        try {
            if (uri == null)
                return;
            InputStream stream;
            if (Objects.equals(uri.getScheme(), "file")) {
                stream = new FileInputStream(new File((uri.getPath())));
            } else if (Objects.equals(uri.getScheme(), "content")) {
                stream = getContentResolver().openInputStream(uri);
            } else {
                throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
            }
            Checklist newList = new Checklist(mLists, "Unknown");
            newList.fromStream(stream);

            mLists.add(newList);
            mLists.notifyListChanged(true);
            Log.d(TAG, "imported list: " + newList.getText());
            Toast.makeText(this, getString(R.string.import_report, newList.getText()), Toast.LENGTH_LONG).show();
            pushFragment(new ChecklistFragment(newList));
        } catch (Exception e) {
            Log.d(TAG, "import failed " + e.getMessage());
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void importList() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_LIST);
    }

    public void loadLists() {
        if (mLoadThread != null && mLoadThread.isAlive()) {
            mLoadThread.interrupt();
            mLoadThread = null;
        }

        mLists.clear();

        // First load the cache, then asynchronously load the backing store
        try {
            FileInputStream fis = openFileInput(Settings.cacheFile);
            mLists.fromStream(fis);
        } catch (Exception e) {
            Log.d(TAG, "Exception loading from cache " + Settings.cacheFile + ": " + e);
        }
        Log.d(TAG, mLists.size() + " lists loaded from cache");
        //removeDuplicates();
        mLists.notifyListChanged(false);

        final Uri uri = getSettings().getUri("backingStore");
        if (uri == null)
            return;
        mLoadThread = new Thread(() -> {
            Log.d(TAG, "Starting load thread");
            try {
                Checklists backing = new Checklists();
                InputStream stream;
                if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                    stream = new FileInputStream(new File((uri.getPath())));
                } else if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
                    stream = getContentResolver().openInputStream(uri);
                } else {
                    throw new IOException("Failed to load lists. Unknown uri scheme: " + uri.getScheme());
                }
                if (mLoadThread.isInterrupted())
                    return;
                backing.fromStream(stream);
                Log.d(TAG, backing.size() + " lists loaded from backing store");
                mLists.clear(); // kill the cache
                boolean save = mLists.merge(backing);
                //removeDuplicates();
                new Handler(Looper.getMainLooper()).post(() -> mLists.notifyListChanged(save));
            } catch (final SecurityException se) {
                // openInputStream denied, reroute through picker to re-establish permissions
                Log.d(TAG, "Security Exception loading backing store " + se);
                // In a thread, have to use the UI thread to request access
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.access_denied);
                        builder.setMessage(R.string.reconfirm_backing_store);
                        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            // Callers must include CATEGORY_OPENABLE in the Intent to obtain URIs that can be opened with ContentResolver#openFileDescriptor(Uri, String)
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            // Indicate the permissions should be persistable across device reboots
                            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            if (Build.VERSION.SDK_INT >= 26)
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                            intent.setType("application/json");
                            startActivityForResult(intent, REQUEST_CHANGE_STORE);
                            // onActivityResult will re-load the list in response to a successful
                            // REQUEST_CHANGE_STORE
                        });
                        builder.show();
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Exception loading backing store: " + e);
            }
        });
        mLoadThread.start();
    }

    private void handleChangeStore(Intent resultData) {
        Uri newURI = resultData.getData();
        if (newURI == null)
            return;

        Uri oldURI = getSettings().getUri(Settings.backingStore);
        if (!newURI.equals(oldURI)) {
            getSettings().setUri(Settings.backingStore, newURI);

            // Persist granted access across reboots
            int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(newURI, takeFlags);
        }
    }

    /**
     * Hide the current fragment, pushing it onto the stack, then open a new fragment. A neat
     * alternative to dialogs.
     *
     * @param fragment the fragment to switch to
     */
    public void pushFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ftx = fm.beginTransaction();
        // Hide, but don't close, the open fragment (which will always be the tree view)
        ftx.hide(fm.findFragmentById(R.id.fragment));
        ftx.add(R.id.fragment, fragment, fragment.getClass().getName());
        ftx.addToBackStack(null);
        ftx.commit();
    }
}