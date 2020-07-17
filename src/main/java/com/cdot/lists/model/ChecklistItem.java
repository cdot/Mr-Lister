/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.model;

import com.cdot.lists.Settings;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An item in a Checklist
 */
public class ChecklistItem extends EntryListItem {
    private static final String TAG = "ChecklistItem";

    private final Checklist mList;
    private long mUID;
    private boolean mDone; // has it been checked?

    public ChecklistItem(Checklist checklist, String str, boolean done) {
        mUID = Settings.getUID();
        mList = checklist;
        setText(str);
        mDone = done;
    }

    ChecklistItem(Checklist checklist, ChecklistItem clone) {
        this(checklist, clone.getText(), clone.mDone);
    }

    @Override // implement EntryListItem
    public long getUID() {
        return mUID;
    }

    @Override // EntryListItem
    public EntryList getContainer() {
        return mList;
    }

    @Override // implement EntryListItem
    public void notifyListChanged(boolean save) {
        getContainer().notifyListChanged(save);
    }

    public boolean isDone() {
        return mDone;
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return !mDone;
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem ot) {
        if (!getText().equals(ot.getText()))
            return false;
        return mDone == ((ChecklistItem) ot).mDone;
    }

    // Called on the cache to merge the backing list
    @Override // implement EntryListItem
    public boolean merge(EntryListItem backIt) {
        boolean changed = false;
        if (!getText().equals(backIt.getText())) {
            setText(backIt.getText());
            changed = true;
        }
        ChecklistItem backLit = (ChecklistItem)backIt;
        if (mDone == backLit.mDone) // no changes
            return changed;
        mDone = backLit.mDone;
        return true;
    }

    /**
     * Set the item's done status and trigger a save
     *
     * @param done new done status
     */
    public void setDone(boolean done) {
        mDone = done;
    }

    @Override // implement EntryListItem
    public void fromJSON(JSONObject jo) throws JSONException {
        mUID = jo.getLong("uid");
        setText(jo.getString("name"));
        mDone = false;
        try {
            mDone = jo.getBoolean("done");
        } catch (JSONException ignored) {
        }
    }

    @Override // implement EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        String[] row = r.readNext();
        if (row == null)
            return false;
        setText(row[0]);
        // "false", "0", and "" are read as false. Any other value is read as true
        setDone(row[1].length() == 0 || row[1].matches("[Ff][Aa][Ll][Ss][Ee]|0"));
        return true;
    }

    @Override // implement EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject iob = new JSONObject();
        iob.put("uid", mUID);
        iob.put("name", getText());
        if (mDone)
            iob.put("done", true);
        return iob;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        String[] a = new String[2];
        a[0] = getText();
        a[1] = (mDone ? "TRUE" : "FALSE");
        w.writeNext(a);
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText());
        if (mDone)
            sb.append(" *");
        return sb.toString();
    }
}