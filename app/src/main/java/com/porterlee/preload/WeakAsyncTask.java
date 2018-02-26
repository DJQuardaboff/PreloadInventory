package com.porterlee.preload;

import android.os.AsyncTask;

import java.lang.ref.WeakReference;

public class WeakAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    private volatile WeakReference<AsyncTaskListeners<Params, Progress, Result>> mListeners;

    public WeakAsyncTask(AsyncTaskListeners<Params, Progress, Result> listeners) {
        listeners.mOnDoInBackgroundListener.task = this;
        mListeners = new WeakReference<>(listeners);
    }

    @Override
    protected void onPreExecute() {
        if (mListeners.get().mOnPreExecuteListener != null)
            mListeners.get().mOnPreExecuteListener.onPreExecute();
    }

    @Override
    protected Result doInBackground(Params[] params) {
        if (mListeners.get().mOnDoInBackgroundListener != null)
            return mListeners.get().mOnDoInBackgroundListener.onDoInBackground(params);
        return null;
    }

    @Override
    protected void onProgressUpdate(Progress[] progress) {
        if (mListeners.get().mOnProgressUpdateListener != null)
            mListeners.get().mOnProgressUpdateListener.onProgressUpdate(progress);
    }

    @Override
    protected void onPostExecute(Result result) {
        if (mListeners.get().mOnPostExecuteListener != null)
            mListeners.get().mOnPostExecuteListener.onPostExecute(result);
    }

    @Override
    protected void onCancelled(Result result) {
        if (mListeners.get().mOnCancelledListener != null)
            mListeners.get().mOnCancelledListener.onCancelled(result);
    }

    public static abstract class OnPreExecuteListener {
        public abstract void onPreExecute();
    }

    public static abstract class OnDoInBackgroundListener<Params, Progress, Result> {
        private volatile WeakAsyncTask<Params, Progress, Result> task;

        public abstract Result onDoInBackground(Params[] params);

        @SafeVarargs
        protected final void publishProgress(Progress... progress) {
            if (task != null)
                task.publishProgress(progress);
            else
                throw new NullPointerException("Task to call publishProgress() on is null");
        }

        protected final boolean isCancelled() {
            if (task != null)
                return task.isCancelled();
            else
                throw new NullPointerException("Task to call isCancelled() on is null");
        }
    }

    public static abstract class OnProgressUpdateListener<Progress> {
        public abstract void onProgressUpdate(Progress[] progress);
    }

    public static abstract class OnPostExecuteListener<Result> {
        public abstract void onPostExecute(Result result);
    }

    public static abstract class OnCancelledListener<Result> {
        public abstract void onCancelled(Result result);
    }

    public static class AsyncTaskListeners<Params, Progress, Result> {
        private OnPreExecuteListener mOnPreExecuteListener;
        private OnDoInBackgroundListener<Params, Progress, Result> mOnDoInBackgroundListener;
        private OnProgressUpdateListener<Progress> mOnProgressUpdateListener;
        private OnPostExecuteListener<Result> mOnPostExecuteListener;
        private OnCancelledListener<Result> mOnCancelledListener;

        public AsyncTaskListeners(OnPreExecuteListener onPreExecuteListener, OnDoInBackgroundListener<Params, Progress, Result> onDoInBackgroundListener, OnProgressUpdateListener<Progress> onProgressUpdateListener, OnPostExecuteListener<Result> onPostExecuteListener, OnCancelledListener<Result> onCancelledListener) {
            setOnPreExecuteListener(onPreExecuteListener);
            setOnDoInBackgroundListener(onDoInBackgroundListener);
            setOnProgressUpdateListener(onProgressUpdateListener);
            setOnPostExecuteListener(onPostExecuteListener);
            setOnCancelledListener(onCancelledListener);
        }

        public void setOnPreExecuteListener(OnPreExecuteListener onPreExecuteListener) {
            mOnPreExecuteListener = onPreExecuteListener;
        }

        public OnPreExecuteListener getOnPreExecuteListener() {
            return mOnPreExecuteListener;
        }

        public void setOnDoInBackgroundListener(OnDoInBackgroundListener<Params, Progress, Result> onDoInBackgroundListener) {
            mOnDoInBackgroundListener = onDoInBackgroundListener;
        }

        public OnDoInBackgroundListener<Params, Progress, Result> getOnDoInBackgroundListener() {
            return mOnDoInBackgroundListener;
        }

        public void setOnProgressUpdateListener(OnProgressUpdateListener<Progress> onProgressUpdateListener) {
            mOnProgressUpdateListener = onProgressUpdateListener;
        }

        public OnProgressUpdateListener<Progress> getOnProgressUpdateListener() {
            return mOnProgressUpdateListener;
        }

        public void  setOnPostExecuteListener(OnPostExecuteListener<Result> onPostExecuteListener) {
            mOnPostExecuteListener = onPostExecuteListener;
        }

        public OnPostExecuteListener<Result> getOnPostExecuteListener() {
            return mOnPostExecuteListener;
        }

        public void setOnCancelledListener(OnCancelledListener<Result> onCancelledListener) {
            mOnCancelledListener = onCancelledListener;
        }

        public OnCancelledListener<Result> getOnCancelledListener() {
            return mOnCancelledListener;
        }
    }
}
