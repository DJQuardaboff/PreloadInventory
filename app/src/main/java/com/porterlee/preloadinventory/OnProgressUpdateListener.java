package com.porterlee.preloadinventory;

import android.support.annotation.FloatRange;

public interface OnProgressUpdateListener {
    void onProgressUpdate(@FloatRange(from=0.0,to=1.0) float progress);
}
