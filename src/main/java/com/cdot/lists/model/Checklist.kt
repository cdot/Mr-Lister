/*
 * Copyright © 2020 C-Dot Consultants
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cdot.lists.model

import android.util.Log
import com.cdot.lists.Lister
import com.opencsv.CSVReader
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
class Checklist : EntryList {
    /**
     * Constructor
     */
    constructor()

    /**
     * Constructor
     */
    constructor(name: String?) {
        text = name
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job    the JSON object
     * @throws JSONException if something goes wrong
     */
    constructor(job: JSONObject) {
        fromJSON(job)
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy   list to clone
     */
    constructor(copy: Checklist) : super(copy) {
        for (item in copy.data) addChild(ChecklistItem(item as ChecklistItem))
    }

    // EntryList
    override val flagNames: Set<String>
        get() = super.flagNames.plus(moveCheckedItemsToEnd).plus(autoDeleteChecked)


    // implement EntryListItem
    override val isMoveable: Boolean
        get() = true

    override val itemsAreMoveable: Boolean
        get() = !getFlag(moveCheckedItemsToEnd)

    @Throws(JSONException::class)
    override fun fromJSON(jo: JSONObject) {
        clear()
        super.fromJSON(jo)
        text = jo.getString("name")
        val items = jo.getJSONArray("items")
        for (i in 0 until items.length()) {
            val ci = ChecklistItem(null as String?)
            ci.fromJSON(items.getJSONObject(i))
            addChild(ci)
        }
    }

    // CSV lists continue of a set of rows, each with the list name in the first column,
    // the item name in the second column, and the done status in the third column
    @Throws(Exception::class)  // EntryListItem
    override fun fromCSV(r: CSVReader): Boolean {
        if (r.peek() == null) return false
        if (text == null || text.equals(r.peek()[0])) {
            // recognised header row
            if (text == null) text = r.peek()[0]
            while (r.peek() != null && r.peek()[0] == text) {
                val ci = ChecklistItem()
                if (!ci.fromCSV(r)) break
                addChild(ci)
            }
        }
        return true
    }

    // EntryListItem
    override fun toJSON(): JSONObject {
        val job = super.toJSON()
        try {
            job.put("name", text)
            val items = JSONArray()
            for (item in data) {
                items.put(item.toJSON())
            }
            job.put("items", items)
        } catch (je: JSONException) {
            Log.e(TAG, Lister.stringifyException(je))
        }
        return job
    }

    /**
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    val checkedCount: Int
        get() {
            var i = 0
            for (item in data) {
                if (item.getFlag(ChecklistItem.isDone)) i++
            }
            return i
        }

    /**
     * Make a global change to the "checked" status of all items in the list
     *
     * @param check true to set items as checked, false to set as unchecked
     */
    fun checkAll(check: Boolean): Boolean {
        var changed = false
        for (item in data) {
            val ci = item as ChecklistItem
            if (ci.getFlag(ChecklistItem.isDone) != check) {
                if (check) ci.setFlag(ChecklistItem.isDone) else ci.clearFlag(ChecklistItem.isDone)
                changed = true
            }
        }
        return changed
    }

    /**
     * Delete all the checked items in the list
     *
     * @return number of items deleted
     */
    fun deleteAllChecked(): Int {
        val kill = ArrayList<ChecklistItem>()
        for (it in data) {
            if (it.getFlag(ChecklistItem.isDone)) kill.add(it as ChecklistItem)
        }
        newUndoSet()
        for (dead in kill) {
            remove(dead, true)
        }
        return kill.size
    }

    companion object {
        private val TAG = Checklist::class.java.simpleName
        const val moveCheckedItemsToEnd = "movend"
        const val autoDeleteChecked = "autodel"
    }
}