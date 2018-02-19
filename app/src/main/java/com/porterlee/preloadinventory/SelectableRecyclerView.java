package com.porterlee.preloadinventory;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

public class SelectableRecyclerView extends RecyclerView {
    private int selectedLocation;

    public SelectableRecyclerView(Context context) {
        super(context);
    }

    public SelectableRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectableRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean setSelectedLocation(int index) {
        if (index >= getAdapter().getItemCount() || index == selectedLocation)
            return false;

        getAdapter().notifyItemChanged(selectedLocation);
        selectedLocation = index;
        getAdapter().notifyItemChanged(selectedLocation);
        return true;
    }

    public int getSelectedLocation() {
        return selectedLocation;
    }
}
