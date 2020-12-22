/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.view;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.cdot.lists.MainActivity;
import com.cdot.lists.R;
import com.cdot.lists.Settings;
import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.model.EntryListItem;

/**
 * Base class of views on list entries. Provides the basic functionality of a sortable text view.
 */
@SuppressLint("ViewConstructor")
public class EntryListItemView extends RelativeLayout implements View.OnClickListener {
    private final String TAG = EntryListItemView.class.getSimpleName();

    // True if this view is of an item being moved
    protected boolean mIsMoving;
    // The item being moved
    protected EntryListItem mItem;
    // The menu resource for this list item
    protected int mMenuResource;
    // Fragment this view belongs to
    protected EntryListFragment mFragment;

    /**
     * Constructor
     *
     * @param item     the item this is a view of
     * @param isMoving whether this is the special case of an item that is being moved
     * @param cxt      the context for the view
     * @param layoutR  R.layout of the view
     * @param menuR    R.menu of the popup menu
     */
    EntryListItemView(EntryListItem item, boolean isMoving, EntryListFragment cxt, int layoutR, int menuR) {
        super(cxt.getActivity());
        inflate(cxt.getActivity(), layoutR, this);
        mFragment = cxt;
        mIsMoving = isMoving;
        mMenuResource = menuR;
        setItem(item);
        if (!isMoving)
            addListeners();
    }

    @Override // implement View.OnLongClickListener
    public void onClick(View view) {
        // override in subclasses
    }

    /**
     * Get the item that this is a view of
     *
     * @return the item
     */
    public EntryListItem getItem() {
        return mItem;
    }

    /**
     * Set the item this is a view of
     *
     * @param item the item we are a view of
     */
    public void setItem(EntryListItem item) {
        mItem = item;
    }

    public MainActivity getMainActivity() {
        return mFragment.getMainActivity();
    }

    public EntryListFragment getFragment() {
        return mFragment;
    }

    @SuppressLint("ClickableViewAccessibility")
    void addListeners() {
        ImageButton butt = findViewById(R.id.move_button);
        butt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d(TAG, "OnTouch " + motionEvent.getAction() + " " + Integer.toHexString(System.identityHashCode(this)));
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
                    getFragment().mMovingItem = mItem;
                return true;
            }
        });
        setOnLongClickListener(view -> {
            showMenu();
            return true;
        });
        setOnClickListener(this);
    }

    /**
     * Called to update the row view when settings have changed
     */
    public void updateView() {
        // Update the item text
        TextView it = findViewById(R.id.item_text);
        String t = mItem.getText();
        it.setText(t);
        setTextFormatting();
        ImageButton mb = findViewById(R.id.move_button);
        mb.setVisibility(mFragment.canManualSort() ? View.VISIBLE : View.GONE);
    }

    /**
     * Format the text according to current status of the item. Base class handles global settings.
     */
    protected void setTextFormatting() {
        TextView it = findViewById(R.id.item_text);
        int padding;
        // Size
        switch (Settings.getInt(Settings.textSizeIndex)) {
            case Settings.TEXT_SIZE_SMALL:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
                padding = 0;
                break;
            default:
            case Settings.TEXT_SIZE_MEDIUM:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
                padding = 5;
                break;
            case Settings.TEXT_SIZE_LARGE:
                it.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
                padding = 10;
                break;
        }
        it.setPadding(0, padding, 0, padding);
    }

    /**
     * Handle a popup menu action. Default is a NOP.
     *
     * @param action the action to handle
     * @return true if the action was handled
     */
    protected boolean onAction(int action) {
        return false;
    }

    /**
     * Show the popup menu for the item
     */
    private void showMenu() {
        PopupMenu popupMenu = new PopupMenu(getContext(), this);
        popupMenu.inflate(mMenuResource);
        popupMenu.setOnMenuItemClickListener(menuItem -> onAction(menuItem.getItemId()));
        popupMenu.show();
    }
}
