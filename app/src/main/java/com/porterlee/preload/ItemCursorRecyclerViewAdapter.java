package com.porterlee.preload;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;

import com.porterlee.preload.inventory.PreloadInventoryDatabase;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class ItemCursorRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    private static final String TAG = ItemCursorRecyclerViewAdapter.class.getCanonicalName();
    private volatile Cursor mCursor;
    private boolean mDataValid;
    private int mRowIdColumn;
    private DataSetObserver mDataSetObserver;
    private ArrayList<Utils.Holder<Integer>> mHolderList = new ArrayList<>();
    private HashMap<Utils.Holder, Integer> mHolderToPreloadedCursorIndexMap = new HashMap<>();
    private HashMap<String, Utils.Holder<Integer>> mBarcodeToHolderMap = new HashMap<>();
    private ArrayList<String> mDuplicateBarcodes = new ArrayList<>();
    private int mMisplacedCount = 0;
    private int mNotPreloadedCount = 0;
    private int mDuplicateCount = 0;
    private int mPreloadedScannedCount = 0;
    private int mPreloadedTotalCount = 0;

    public ItemCursorRecyclerViewAdapter(Cursor cursor) {
        mCursor = cursor;
        mDataValid = cursor != null;
        mRowIdColumn = mDataValid ? mCursor.getColumnIndex("_id") : -1;
        mDataSetObserver = new NotifyingDataSetObserver();
        if (mDataValid) {
            remapCursor();
        }
        if (mCursor != null) {
            mCursor.registerDataSetObserver(mDataSetObserver);
        }
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public int getItemCount() {
        if (mDataValid && mCursor != null) {
            return mHolderList.size();
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        if (mDataValid && mCursor != null && mCursor.moveToPosition(position)) {
            return mCursor.getLong(mRowIdColumn);
        }
        return 0;
    }

    @Override
    public void setHasStableIds(boolean hasStableIds) {
        super.setHasStableIds(true);
    }

    public abstract void onBindViewHolder(VH viewHolder, Cursor cursor);

    @Override
    public void onBindViewHolder(@NonNull VH viewHolder, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(mHolderList.get(position).get())) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        onBindViewHolder(viewHolder, mCursor);
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     */
    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     */
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }
        final Cursor oldCursor = mCursor;
        if (oldCursor != null && mDataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mCursor = newCursor;

        mHolderList.clear();
        mHolderToPreloadedCursorIndexMap.clear();
        mBarcodeToHolderMap.clear();
        mDuplicateBarcodes.clear();
        mMisplacedCount = 0;
        mNotPreloadedCount = 0;
        mDuplicateCount = 0;
        mPreloadedScannedCount = 0;
        mPreloadedTotalCount = 0;

        if (mCursor != null) {
            if (mDataSetObserver != null) {
                mCursor.registerDataSetObserver(mDataSetObserver);
            }
            mRowIdColumn = newCursor.getColumnIndexOrThrow("_id");
            mDataValid = true;
            remapCursor();
            notifyDataSetChanged();
        } else {
            mRowIdColumn = -1;
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
        return oldCursor;
    }

    private void remapCursor() {
        SparseArray<Utils.Holder<Integer>> idToHolderMap = new SparseArray<>();
        ArrayList<Utils.Holder> preloadedHolders = new ArrayList<>();
        final int preloadedItemIdColumnIndex = mCursor.getColumnIndex("preloaded_item_id");
        final int barcodeColumnIndex = mCursor.getColumnIndex("barcode");
        final int sourceColumnIndex = mCursor.getColumnIndex("source");
        final int statusColumnIndex = mCursor.getColumnIndex("status");

        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            if (PreloadInventoryDatabase.ItemTable.Source.PRELOAD.equals(mCursor.getString(sourceColumnIndex))) { // has preloaded source (preloaded item)
                Utils.Holder<Integer> current = new Utils.Holder<>(mCursor.getPosition());
                mHolderList.add(current);
                mHolderToPreloadedCursorIndexMap.put(current, current.get());
                mBarcodeToHolderMap.put(mCursor.getString(barcodeColumnIndex), current);
                idToHolderMap.append(mCursor.getInt(mRowIdColumn), current);
                preloadedHolders.add(current);
                mPreloadedTotalCount++;
            } else if (PreloadInventoryDatabase.ItemTable.Source.SCANNER.equals(mCursor.getString(sourceColumnIndex))) { // has scanner source (scanned item)
                if (PreloadInventoryDatabase.ItemTable.Status.MISPLACED.equals(mCursor.getString(statusColumnIndex))) { // has misplaced status (misplaced item)
                    Utils.Holder<Integer> current = new Utils.Holder<>(mCursor.getPosition());
                    mHolderList.add(0, current);
                    mBarcodeToHolderMap.put(mCursor.getString(barcodeColumnIndex), current);
                    idToHolderMap.append(mCursor.getInt(mRowIdColumn), current);
                    mMisplacedCount++;
                } else { // not misplaced
                    final Utils.Holder<Integer> preloadItemHolder = idToHolderMap.get(mCursor.getInt(preloadedItemIdColumnIndex), null);
                    if (preloadItemHolder == null) { // has no preloaded data (non-preloaded item)
                        Utils.Holder<Integer> current = new Utils.Holder<>(mCursor.getPosition());
                        mHolderList.add(0, current);
                        mBarcodeToHolderMap.put(mCursor.getString(barcodeColumnIndex), current);
                        idToHolderMap.append(mCursor.getInt(mRowIdColumn), current);
                        mNotPreloadedCount++;
                    } else {
                        if (preloadedHolders.contains(preloadItemHolder)) { // preloaded data is still there (scanned preloaded item)
                            preloadItemHolder.set(mCursor.getPosition());
                            preloadedHolders.remove(preloadItemHolder);
                            mPreloadedScannedCount++;
                        } else { // preloaded data was replaced by a scanned version already (duplicate scanned preloaded item)
                            Utils.Holder<Integer> current = new Utils.Holder<>(mCursor.getPosition());
                            mHolderList.add(mHolderList.indexOf(preloadItemHolder), current);
                            mHolderToPreloadedCursorIndexMap.put(current, mHolderToPreloadedCursorIndexMap.get(preloadItemHolder));
                            mDuplicateBarcodes.add(mCursor.getString(barcodeColumnIndex));
                            mDuplicateCount++;
                        }
                    }
                }
            } else {
                throw new IllegalStateException("unknown barcode source");
            }
        }
    }

    public boolean getIsDuplicate(String barcode) {
        return mDuplicateBarcodes.contains(barcode);
    }

    public int getMisplacedCount() {
        return mMisplacedCount;
    }

    public int getNotPreloadedCount() {
        return mNotPreloadedCount;
    }

    public int getDuplicateCount() {
        return mDuplicateCount;
    }

    public int getPreloadedScannedCount() {
        return mPreloadedScannedCount;
    }

    public int getPreloadedTotalCount() {
        return mPreloadedTotalCount;
    }

    public int getPreloadedDataIndex(int index) {
        Integer dataIndex = mHolderToPreloadedCursorIndexMap.get(mHolderList.get(index));
        return dataIndex != null ? dataIndex : -1;
    }

    public int getIndexOfBarcode(String barcode) {
        return mHolderList.indexOf(mBarcodeToHolderMap.get(barcode));
    }

    private class NotifyingDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            super.onChanged();
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
    }
}