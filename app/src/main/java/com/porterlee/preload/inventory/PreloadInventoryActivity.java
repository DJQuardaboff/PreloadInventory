package com.porterlee.preload.inventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.StaleDataException;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.porterlee.preload.BuildConfig;
import com.porterlee.preload.CursorRecyclerViewAdapter;
import com.porterlee.preload.Manifest;
import com.porterlee.preload.R;
import com.porterlee.preload.SelectableRecyclerView;
import com.porterlee.preload.WeakAsyncTask;
import com.porterlee.preload.location.PreloadLocationsActivity;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.preload.MainActivity.DATE_FORMAT;

import com.porterlee.preload.inventory.PreloadInventoryDatabase.ItemTable;
import com.porterlee.preload.inventory.PreloadInventoryDatabase.LocationTable;
import com.porterlee.preload.location.PreloadLocationsDatabase;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class PreloadInventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    @Language("RoomSql")
    private static final String ITEM_LIST_QUERY = "SELECT MIN(" + ItemTable.Keys.ID + ") AS _id, MAX(" + ItemTable.Keys.ID + ") AS max_id, MAX(" + ItemTable.Keys.PRELOADED_ITEM_ID + ") AS preloaded_item_id, MAX(" + ItemTable.Keys.SCANNED_LOCATION_ID + ") AS scanned_location_id, MAX(" + ItemTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, " + ItemTable.Keys.BARCODE + " AS barcode, MAX(" + ItemTable.Keys.CASE_NUMBER + ") AS case_number, MAX(" + ItemTable.Keys.ITEM_NUMBER + ") AS item_number, MAX(" + ItemTable.Keys.PACKAGING + ") AS packaging, MAX(" + ItemTable.Keys.DESCRIPTION + ") AS description, MIN(" + ItemTable.Keys.SOURCE + ") AS source, MAX(" + ItemTable.Keys.STATUS + ") AS status, MAX(" + ItemTable.Keys.ITEM_TYPE + ") AS item_type, MAX(" + ItemTable.Keys.DATE_TIME + ") AS date_time FROM " + ItemTable.NAME + " WHERE MAX(preloaded_location_id, scanned_location_id) IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? ) GROUP BY barcode ORDER BY source, _id";
    @Language("RoomSql")
    private static final String LOCATION_LIST_QUERY = "SELECT MIN(" + LocationTable.Keys.ID + ") AS _id, MAX(" + LocationTable.Keys.ID + ") AS max_id, MAX(" + LocationTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, MAX(" + LocationTable.Keys.PROGRESS + ") AS progress, " + LocationTable.Keys.BARCODE + " AS barcode, MAX(" + LocationTable.Keys.DESCRIPTION + ") AS description, MIN(" + LocationTable.Keys.SOURCE + ") AS source, MAX(" + LocationTable.Keys.STATUS + ") AS status, MAX(" + LocationTable.Keys.DATE_TIME + ") AS date_time FROM " + LocationTable.NAME + " WHERE _id NOT NULL GROUP BY barcode ORDER BY source, _id";
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "output.txt");
    public static final File INPUT_FILE = new File(EXTERNAL_PATH, "data.txt");
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    private SQLiteStatement GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT;
    private SQLiteStatement GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_SCANNED_LOCATION_COUNT_STATEMENT;
    //private SQLiteStatement GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT;
    //private SQLiteStatement GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT;
    //private SQLiteStatement GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    //private SQLiteStatement GET_STATUS_OF_PRELOADED_LOCATION_STATEMENT;
    private String previousPrefix = "";
    private String previousPostfix = "";
    private SharedPreferences mSharedPreferences;
    private Vibrator mVibrator;
    private File mDatabaseFile;
    private File mArchiveDirectory;
    private boolean mChangedSinceLastArchive;
    private MaterialProgressBar mProgressBar;
    private Menu mOptionsMenu;
    private long mSelectedLocationId = -1;
    private long mSelectedMaxLocationId = -1;
    private long mSelectedPreloadedLocationId = -1;
    private float mSelectedLocationProgress = 0f;
    private String mSelectedLocationBarcode = "";
    private String mSelectedLocationSource = "";
    private String mSelectedLocationStatus = "";
    private int mCurrentNotMisplacedScannedItemCount;
    private int mCurrentPreloadedItemCount;
    private int mCurrentMisplacedScannedItemCount;
    private int mCurrentScannedItemCount;
    private SelectableRecyclerView mLocationRecyclerView;
    private SelectableRecyclerView mItemRecyclerView;
    private WeakAsyncTask<Void, Float, Pair<String, String>> saveTask;
    private CursorRecyclerViewAdapter<LocationViewHolder> mLocationRecyclerAdapter;
    private CursorRecyclerViewAdapter<ItemViewHolder> mItemRecyclerAdapter;
    private volatile SQLiteDatabase mDatabase;
    private ScanResultReceiver mResultReciever;
    private IScannerService mScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();

    private final WeakAsyncTask.AsyncTaskListeners<Void, Float, Pair<String, String>> saveTaskListeners = new WeakAsyncTask.AsyncTaskListeners<>(null, new WeakAsyncTask.OnDoInBackgroundListener<Void, Float, Pair<String, String>>() {
        private static final int MAX_UPDATES = 100;

        @Override
        public Pair<String, String> onDoInBackground(Void... params) {
            Cursor itemCursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " AS barcode, " + ItemTable.Keys.SCANNED_LOCATION_ID + " AS scanned_location_id, " + ItemTable.Keys.DATE_TIME + " AS date_time FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\" ORDER BY _id ASC", null);
            int itemBarcodeIndex = itemCursor.getColumnIndex("barcode");
            int itemLocationIdIndex = itemCursor.getColumnIndex("scanned_location_id");
            int itemDateTimeIndex = itemCursor.getColumnIndex("date_time");

            Cursor locationCursor = mDatabase.rawQuery("SELECT " + LocationTable.Keys.ID + " AS _id, " + LocationTable.Keys.BARCODE + " AS barcode, " + LocationTable.Keys.DATE_TIME + " AS date_time FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" ORDER BY " + LocationTable.Keys.ID + " ASC", null);
            int locationIdIndex = locationCursor.getColumnIndex("_id");
            int locationBarcodeIndex = locationCursor.getColumnIndex("barcode");
            int locationDateTimeIndex = locationCursor.getColumnIndex("date_time");

            if (!itemCursor.moveToFirst()) {
                itemCursor.close();
                locationCursor.close();
                return new Pair<>("CursorError", "There was an error moving item cursor to first position");
            }

            if (!locationCursor.moveToFirst()) {
                itemCursor.close();
                locationCursor.close();
                return new Pair<>("CursorError", "There was an error moving location cursor to first position");
            }

            try {
                //noinspection ResultOfMethodCallIgnored
                EXTERNAL_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("tmp", ".txt", EXTERNAL_PATH);
                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);

                int updateNum = 0;
                int tempLocation;
                int itemIndex = 0;
                int totalItemCount = itemCursor.getCount() + 1;
                int currentLocationId = -1;

                //noinspection StringConcatenationMissingWhitespace
                String tempText = BuildConfig.APPLICATION_ID.split(Pattern.quote("."))[2] + ".inventory|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n";
                printStream.print(tempText);
                printStream.flush();

                while (!itemCursor.isAfterLast()) {
                    if (isCancelled()) {
                        itemCursor.close();
                        locationCursor.close();
                        return new Pair<>("SaveCancelled", "Save cancelled");
                    }

                    final float tempProgress = ((float) itemIndex) / totalItemCount;
                    if (tempProgress * MAX_UPDATES > updateNum) {
                        publishProgress(tempProgress);
                        updateNum++;
                    }

                    tempLocation = itemCursor.getInt(itemLocationIdIndex);
                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                itemCursor.close();
                                locationCursor.close();
                                return new Pair<>("LocationNotFound", String.format("Location of \"%s\" does not exist", itemCursor.getString(itemBarcodeIndex).trim()));
                            }
                        }

                        printStream.printf("\"%1s\"|\"%2s\"\r\n", locationCursor.getString(locationBarcodeIndex).replace("\"", "\"\""), locationCursor.getString(locationDateTimeIndex).replace("\"", "\"\""));
                        printStream.flush();
                    }

                    printStream.printf("\"%1s\"|\"%2s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), itemCursor.getString(itemDateTimeIndex).replace("\"", "\"\""));
                    printStream.flush();

                    itemCursor.moveToNext();
                    itemIndex++;
                }

                printStream.close();

                itemCursor.close();
                locationCursor.close();

                if (!OUTPUT_FILE.delete() && OUTPUT_FILE.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not delete existing output file");

                    itemCursor.close();
                    locationCursor.close();
                    return new Pair<>("DeleteFailed", "Could not delete existing output file");
                }

                refreshExternalPath();
                //MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[]{ EXTERNAL_PATH.getAbsolutePath() }, null, null);

                if (!TEMP_OUTPUT_FILE.renameTo(OUTPUT_FILE) && TEMP_OUTPUT_FILE.exists() && !OUTPUT_FILE.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, String.format("Could not rename temp file to \"%s\"", OUTPUT_FILE.getName()));

                    itemCursor.close();
                    locationCursor.close();
                    return new Pair<>("RenameFailed", String.format("Could not rename temp file to \"%s\"", OUTPUT_FILE.getName()));
                }

                refreshExternalPath();
                //MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[]{ OUTPUT_FILE.getParent() }, null, null);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "FileNotFoundException occurred while saving: " + e.getMessage());
                e.printStackTrace();

                itemCursor.close();
                locationCursor.close();
                return new Pair<>("FileNotFound", "FileNotFoundException occurred while saving");
            } catch (IOException e) {
                Log.w(TAG, "IOException occurred while saving: " + e.getMessage());
                e.printStackTrace();

                itemCursor.close();
                locationCursor.close();
                return new Pair<>("IOException", "IOException occurred while saving");
            } finally {
                itemCursor.close();
                locationCursor.close();
            }

            itemCursor.close();
            locationCursor.close();
            return new Pair<>("SavedToFile", "Saved to file");
        }
    }, new WeakAsyncTask.OnProgressUpdateListener<Float>() {
        @Override
        public void onProgressUpdate(Float... progress) {
            if (progress != null && progress.length > 0 && progress[0] != null)
                mProgressBar.setProgress((int) (progress[0] * mProgressBar.getMax()));
        }
    }, new WeakAsyncTask.OnPostExecuteListener<Pair<String, String>>() {
        @Override
        public void onPostExecute(Pair<String, String> result) {
            if (result == null)
                throw new IllegalArgumentException("Null result from SaveToFileTask");

            Toast.makeText(PreloadInventoryActivity.this, result.second, Toast.LENGTH_SHORT).show();

            if (mChangedSinceLastArchive)
                archiveDatabase();

            postSave();
        }
    }, new WeakAsyncTask.OnCancelledListener<Pair<String, String>>() {
        @Override
        public void onCancelled(Pair<String, String> result) {
            if (result == null)
                throw new IllegalArgumentException("Null result from SaveToFileTask");

            Toast.makeText(PreloadInventoryActivity.this, result.second, Toast.LENGTH_SHORT).show();

            postSave();
        }
    });

    private final WeakAsyncTask.AsyncTaskListeners<String, Void, Object[]> scanBarcodeTaskListeners = new WeakAsyncTask.AsyncTaskListeners<>(null, new WeakAsyncTask.OnDoInBackgroundListener<String, Void, Object[]>() {
        @Override
        public Object[] onDoInBackground(String... params) {
            if (params == null || params.length < 1)
                throw new NullPointerException("This task must be initialized with a barcode");

            final String barcode = params[0];

            if (isLocation(barcode)) {
                boolean isPreloaded;
                long locationId;

                //GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                //final boolean refreshList = !(GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0);

                GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                try {
                    locationId = GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
                    isPreloaded = true;
                } catch (SQLiteDoneException e) {
                    locationId = -1;
                    isPreloaded = false;
                }

                ContentValues newLocationValues = new ContentValues();
                newLocationValues.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, locationId);
                newLocationValues.put(PreloadInventoryDatabase.PROGRESS, 0);
                newLocationValues.put(PreloadInventoryDatabase.BARCODE, barcode);
                newLocationValues.put(PreloadInventoryDatabase.DESCRIPTION, "");
                newLocationValues.put(PreloadInventoryDatabase.SOURCE, LocationTable.Source.SCANNER);
                newLocationValues.put(PreloadInventoryDatabase.STATUS, "");
                newLocationValues.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

                if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
                    return new Object[] { "saving", barcode };

                return new Object[] { isPreloaded ? "preloaded_location" : "non_preloaded_location", barcode, isPreloaded ? mDatabase.insert(LocationTable.NAME, null, newLocationValues) : -1, true };
            } else if (isItem(barcode) || isContainer(barcode)) {
                if (mSelectedLocationBarcode.isEmpty() || (mSelectedLocationId < 0 && mSelectedMaxLocationId < 0 && mSelectedPreloadedLocationId < 0))
                    return new Object[] { "no_location" };

                //boolean refreshList;
                boolean isPreloaded;
                boolean isMisplaced = false;
                //long scannedLocationId;
                long preloadedItemId = -1;

                ContentValues newItemValues = new ContentValues();
                newItemValues.put(PreloadInventoryDatabase.SCANNED_LOCATION_ID, mSelectedMaxLocationId);
                newItemValues.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, mSelectedPreloadedLocationId);
                newItemValues.put(PreloadInventoryDatabase.BARCODE, barcode);
                newItemValues.put(PreloadInventoryDatabase.SOURCE, ItemTable.Source.SCANNER);
                newItemValues.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

                //GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
                //scannedLocationId = GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();

                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(2, mSelectedLocationBarcode);
                if (GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0)
                    return new Object[] { "duplicate", barcode };

                GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.bindString(2, barcode);
                if (GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.simpleQueryForLong() < 1) {
                    isMisplaced = true;

                    Cursor cursor = mDatabase.rawQuery("SELECT " + ItemTable.Keys.CASE_NUMBER + " AS case_number, " + ItemTable.Keys.ITEM_NUMBER + " AS item_number, " + ItemTable.Keys.PACKAGING + " AS packaging, " + ItemTable.Keys.DESCRIPTION + " AS description, " + ItemTable.Keys.ITEM_TYPE + " AS item_type FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = ? AND " + ItemTable.Keys.BARCODE + " = ?", new String[] { ItemTable.Source.PRELOAD, barcode });
                    cursor.moveToFirst();

                    if (cursor.getCount() > 0) {
                        int caseNumberIndex = cursor.getColumnIndex("case_number");
                        int itemNumberIndex = cursor.getColumnIndex("item_number");
                        int packagingIndex = cursor.getColumnIndex("packaging");
                        int descriptionIndex = cursor.getColumnIndex("description");
                        int itemTypeIndex = cursor.getColumnIndex("item_type");

                        newItemValues.put(PreloadInventoryDatabase.CASE_NUMBER, cursor.getString(caseNumberIndex));
                        newItemValues.put(PreloadInventoryDatabase.ITEM_NUMBER, cursor.getString(itemNumberIndex));
                        newItemValues.put(PreloadInventoryDatabase.PACKAGING, cursor.getString(packagingIndex));
                        newItemValues.put(PreloadInventoryDatabase.DESCRIPTION, cursor.getString(descriptionIndex));
                        newItemValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.MISPLACED);
                        newItemValues.put(PreloadInventoryDatabase.ITEM_TYPE, cursor.getString(itemTypeIndex));
                    } else {
                        newItemValues.put(PreloadInventoryDatabase.CASE_NUMBER, "");
                        newItemValues.put(PreloadInventoryDatabase.ITEM_NUMBER, "");
                        newItemValues.put(PreloadInventoryDatabase.PACKAGING, "");
                        newItemValues.put(PreloadInventoryDatabase.DESCRIPTION, "");
                        newItemValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.SCANNED);
                        newItemValues.put(PreloadInventoryDatabase.ITEM_TYPE, "");
                    }

                    cursor.close();
                } else {
                    newItemValues.put(PreloadInventoryDatabase.CASE_NUMBER, "");
                    newItemValues.put(PreloadInventoryDatabase.ITEM_NUMBER, "");
                    newItemValues.put(PreloadInventoryDatabase.PACKAGING, "");
                    newItemValues.put(PreloadInventoryDatabase.DESCRIPTION, "");
                    newItemValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.SCANNED);
                    newItemValues.put(PreloadInventoryDatabase.ITEM_TYPE, "");
                }

                try {
                    GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.bindString(1, barcode);
                    preloadedItemId = GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.simpleQueryForLong();
                    isPreloaded = true;
                } catch (SQLiteDoneException e) {
                    isPreloaded = false;
                }

                newItemValues.put(PreloadInventoryDatabase.PRELOADED_ITEM_ID, preloadedItemId);

                //refreshList = !isPreloaded;

                if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
                    return new Object[] { "saving", barcode };

                long rowId = mDatabase.insert(ItemTable.NAME, null, newItemValues);

                if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
                    if (isPreloaded && !isMisplaced) {
                        /*refreshCurrentNotMisplacedScannedItemCount();
                        refreshCurrentPreloadedItemCount();

                        final ContentValues progressValues = new ContentValues(1);
                        progressValues.put(PreloadInventoryDatabase.PROGRESS, ((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount);

                        if (mDatabase.update(LocationTable.NAME, progressValues, LocationTable.Keys.ID + " = ?", new String[]{ String.valueOf(mSelectedPreloadedLocationId) }) < 1)
                            throw new IllegalStateException("No location with id of " + mSelectedPreloadedLocationId);

                        PreloadInventoryActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mSelectedLocationViewHolder.setProgress(progressValues.getAsFloat(PreloadInventoryDatabase.PROGRESS));
                            }
                        });
                        */
                        ContentValues statusValues = new ContentValues(1);
                        statusValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.SCANNED);

                        if (mDatabase.update(ItemTable.NAME, statusValues, ItemTable.Keys.ID + " = ? AND " + ItemTable.Keys.SOURCE + " = ?", new String[]{ String.valueOf(preloadedItemId), ItemTable.Source.PRELOAD }) < 1)
                            throw new IllegalStateException("No preloaded item with id of " + preloadedItemId);
                    }/* else if (isPreloaded) {
                        final ContentValues statusValues = new ContentValues(1);
                        statusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.ERROR);

                        if (mDatabase.update(LocationTable.NAME, statusValues, LocationTable.Keys.ID + " = ?", new String[]{ String.valueOf(mSelectedPreloadedLocationId) }) < 1)
                            throw new IllegalStateException("No location with id of " + mSelectedPreloadedLocationId);

                        PreloadInventoryActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mSelectedLocationViewHolder.setStatus(statusValues.getAsString(PreloadInventoryDatabase.STATUS));
                            }
                        });
                    } else {
                        final ContentValues statusValues = new ContentValues(1);
                        statusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.WARNING);

                        if (mDatabase.update(LocationTable.NAME, statusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.STATUS + " != ?", new String[]{ String.valueOf(mSelectedPreloadedLocationId), LocationTable.Status.ERROR }) < 1)
                            throw new SQLiteException();
                    }*/
                }

                return new Object[] { isPreloaded ? isMisplaced ? "misplaced_item" : "preloaded_item" : "non_preloaded_item", barcode, rowId, true };
            } else {
                return new Object[] { "not_recognized", barcode };
            }
        }
    }, null, new WeakAsyncTask.OnPostExecuteListener<Object[]>() {
        @Override
        public void onPostExecute(Object[] results) {
            if (results == null)
                throw new IllegalArgumentException("Null result from task");

            if (results.length < 1)
                throw new IllegalArgumentException("Result array from task is empty");

            String resultType = (String) results[0];

            switch (resultType) {
                case "no_location":
                    vibrate();
                    Log.i(TAG, "Barcode scanned before location");
                    Toast.makeText(PreloadInventoryActivity.this, "A location must be scanned first", Toast.LENGTH_SHORT).show();
                    return;
            }

            if (results.length < 2)
                throw new IllegalArgumentException(String.format("Result array from task not long enough for a result of \"%s\"", resultType));

            String barcode = (String) results[1];

            switch (resultType) {
                case "duplicate":
                    asyncScrollToItem(barcode);
                    vibrate();
                    Log.i(TAG, String.format("Duplicate barcode scanned: \"%s\"", barcode));
                    Toast.makeText(PreloadInventoryActivity.this, "Duplicate barcode scanned", Toast.LENGTH_SHORT).show();
                    return;
                case "saving":
                    asyncScrollToItem(barcode);
                    vibrate();
                    Log.i(TAG, "Cannot scan barcode while saving");
                    Toast.makeText(PreloadInventoryActivity.this, "Cannot scan barcode while saving", Toast.LENGTH_SHORT).show();
                    return;
                case "not_recognized":
                    vibrate();
                    Log.i(TAG, String.format("Unrecognised barcode scanned: \"%s\"", barcode));
                    Toast.makeText(PreloadInventoryActivity.this, "Barcode not recognised", Toast.LENGTH_SHORT).show();
                    return;
            }

            if (results.length < 4)
                throw new IllegalArgumentException(String.format("Result array from task not long enough for a result of \"%s\"", resultType));

            Long rowId = (Long) results[2];
            Boolean refreshList = (Boolean) results[3];

            switch (resultType) {
                case "preloaded_location":
                    if (rowId < 0) {
                        vibrate();
                        Log.w(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshLocationsScrollToLocation(barcode);
                        else
                            asyncScrollToLocation(barcode);
                    }
                    break;
                case "non_preloaded_location":
                    /*if (rowId < 0) {
                        vibrate();
                        Log.w(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshLocationsScrollToLocation(barcode);
                        else
                            asyncScrollToLocation(barcode);
                    }*/

                    vibrate();
                    Toast.makeText(PreloadInventoryActivity.this, "Non-preloaded item scanned", Toast.LENGTH_SHORT).show();
                    break;
                case "preloaded_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.w(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        //asyncRefreshLocations();
                        asyncRefreshItemsScrollToItem(barcode);
                        /*if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);*/
                    }
                    break;
                case "non_preloaded_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.w(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        vibrate();
                        Toast.makeText(PreloadInventoryActivity.this, "Non-preloaded item scanned", Toast.LENGTH_SHORT).show();
                        mChangedSinceLastArchive = true;
                        //asyncRefreshLocations();
                        asyncRefreshItemsScrollToItem(barcode);
                        /*if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);*/
                    }
                    break;
                case "misplaced_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.w(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        vibrate();
                        Toast.makeText(PreloadInventoryActivity.this, "Misplaced item scanned", Toast.LENGTH_SHORT).show();
                        mChangedSinceLastArchive = true;
                        //asyncRefreshLocations();
                        asyncRefreshItemsScrollToItem(barcode);
                        /*if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);*/
                    }
                    break;
            }
        }
    }, new WeakAsyncTask.OnCancelledListener<Object[]>() {
        @Override
        public void onCancelled(Object[] result) {
            Log.w(TAG + ".ScanBarcodeTask", "This task should not be cancelled");
        }
    });

    private final WeakAsyncTask.AsyncTaskListeners<Void, Void, String> readFileTaskListeners = new WeakAsyncTask.AsyncTaskListeners<>(new WeakAsyncTask.OnPreExecuteListener() {
        @Override
        public void onPreExecute() {
            mProgressBar.setIndeterminate(true);
        }
    }, new WeakAsyncTask.OnDoInBackgroundListener<Void, Void, String>() {
        @Override
        public String onDoInBackground(Void... voids) {
            LineNumberReader lineReader = null;
            try {
                final long startRead = System.currentTimeMillis();

                int tries = 0;

                while (lineReader == null && startRead + 10000 > System.currentTimeMillis() ) {
                    tries++;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {

                    }
                    try {
                        lineReader = new LineNumberReader(new FileReader(INPUT_FILE));
                    } catch (FileNotFoundException ignored) {

                    }
                }
                Log.e(TAG, String.valueOf(tries));

                String line;
                String[] elements;
                long currentLocationId = -1;

                mDatabase.beginTransaction();

                while ((line = lineReader.readLine()) != null) {
                    elements = line.split("((?<!\\|)(\\|)(?!\\|))");

                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = elements[i].replaceAll("(^\")|(\"$)", "").replace("\"\"", "\"").replace("||", "|");
                    }

                    if (elements.length > 0) {
                        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, elements[1]);
                        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                        if (isLocation(elements[1]) ? !(GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) : !(GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)) {
                            if (elements.length == 3 && elements[0].equals("L")) {
                                //System.out.println("Location: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                                currentLocationId = addPreloadLocation(elements[1], elements[2]);
                                if (currentLocationId == -1)
                                    throw new SQLException(String.format(Locale.US, "Error adding location \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                            } else if (elements.length == 4 && elements[0].equals("C")) {
                                //System.out.println("Case-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                                if (addPreloadItem(currentLocationId, elements[1], elements[3], "", "", elements[2], ItemTable.ItemType.CASE_CONTAINER) < 0)
                                    throw new SQLiteException(String.format(Locale.US, "Error adding case-container \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                            } else if (elements.length == 3 && elements[0].equals("B")) {
                                //System.out.println("Bulk-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                                if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], ItemTable.ItemType.BULK_CONTAINER) < 0)
                                    throw new SQLiteException(String.format(Locale.US, "Error adding bulk-container \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                            } else if (elements.length == 6 && elements[0].equals("I")) {
                                //System.out.println("Item: barcode = \'" + elements[1] + "\', case-number = \'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                                if (addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5], ItemTable.ItemType.ITEM) < 0)
                                    throw new SQLiteException(String.format(Locale.US, "Error adding item \"%1s\" from line %2d", elements[1], lineReader.getLineNumber()));
                            } else {
                                if (elements.length < 2)
                                    throw new ParseException("Expected at least 2 elements in line", lineReader.getLineNumber());
                                else if (isItem(elements[1]) || isContainer(elements[1]) || isLocation(elements[1]))
                                    throw new ParseException("Incorrect format or number of elements", lineReader.getLineNumber());
                                else
                                    throw new ParseException(String.format("Barcode \"%s\" not recognised", elements[1]), lineReader.getLineNumber());
                            }
                        }
                    } else if (lineReader.getLineNumber() < 2)
                        throw new ParseException("Blank file", lineReader.getLineNumber());

                /*
                GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, elements[1]);
                GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                if (!(GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) && !(GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)) {
                    if (elements.length > 0) {
                        if (elements.length == 3 && elements[0].equals("L") && isLocation(elements[1])) {
                            //System.out.println("Location: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            currentLocationId = addPreloadLocation(elements[1], elements[2]);
                            currentLocationBarcode = elements[1];
                            if (currentLocationId == -1) {
                                lineReader.close();
                                throw new SQLException(String.format(Locale.US, "Error adding location \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                            }
                        } else if (elements.length == 3 && isContainer(elements[1])) {
                            //System.out.println("Bulk-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\'");
                            if (elements[0].equals(currentLocationBarcode)) {
                                if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], ItemTable.ItemType.BULK_CONTAINER) < 0) {
                                    lineReader.close();
                                    throw new SQLiteException(String.format(Locale.US, "Error adding bulk-container \"%s\" from line%2d", elements[1], lineReader.getLineNumber()));
                                }
                            } else {
                                lineReader.close();
                                throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                            }
                        } else if (elements.length == 4 && isContainer(elements[1])) {
                            //System.out.println("Case-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                            if (elements[0].equals(currentLocationBarcode)) {
                                if (addPreloadItem(currentLocationId, elements[1], elements[3], "", "", elements[2], ItemTable.ItemType.CASE_CONTAINER) < 0) {
                                    lineReader.close();
                                    throw new SQLiteException(String.format(Locale.US, "Error adding case-container \"%s\" from line%2d", elements[1], lineReader.getLineNumber()));
                                }
                            } else {
                                lineReader.close();
                                throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                            }
                        } else if (elements.length == 6 && isItem(elements[1])) {
                            //System.out.println("Item: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', case-number\'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                            if (elements[0].equals(currentLocationBarcode)) {
                                if (addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5], ItemTable.ItemType.ITEM) < 0) {
                                    lineReader.close();
                                    throw new SQLiteException(String.format(Locale.US, "Error adding item \"%s\" from line %2d", elements[1], lineReader.getLineNumber()));
                                }
                            } else {
                                lineReader.close();
                                throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                            }
                        } else {
                            lineReader.close();
                            if (elements.length < 2)
                                throw new ParseException("Expected at least 2 elements in line", lineReader.getLineNumber());
                            else if (isItem(elements[1]) || isContainer(elements[1]) || isLocation(elements[1]))
                                throw new ParseException("Incorrect format or number of elements", lineReader.getLineNumber());
                            else
                                throw new ParseException(String.format("Barcode \"%s\" not recognised", elements[1]), lineReader.getLineNumber());
                        }
                    } else if (lineReader.getLineNumber() < 2) {
                        lineReader.close();
                        throw new ParseException("Blank file", lineReader.getLineNumber());
                    }
                }*/
                }

                mDatabase.setTransactionSuccessful();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return ParseException.class.getSimpleName();
            } catch (IOException e) {
                e.printStackTrace();
                return ParseException.class.getSimpleName();
            } catch (ParseException e) {
                e.printStackTrace();
                return ParseException.class.getSimpleName();
            } finally {
                if (lineReader != null) {
                    try {
                        lineReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (mDatabase.inTransaction())
                    mDatabase.endTransaction();
            }
            return null;
        }
    }, new WeakAsyncTask.OnProgressUpdateListener<Void>() {
        @Override
        public void onProgressUpdate(Void... progress) {

        }
    }, new WeakAsyncTask.OnPostExecuteListener<String>() {
        @Override
        public void onPostExecute(String result) {
            mProgressBar.setIndeterminate(false);

            if (result != null && result.equals(ParseException.class.getSimpleName())) {
                startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
                finish();
                Toast.makeText(PreloadInventoryActivity.this, "There was an error parsing the file", Toast.LENGTH_SHORT).show();
            }

            asyncRefreshItems();
            asyncRefreshLocations();
        }
    }, new WeakAsyncTask.OnCancelledListener<String>() {
        @Override
        public void onCancelled(String result) {

        }
    });

    private BroadcastReceiver mScanKeyEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
                KeyEvent event = intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);
                switch (event.getKeyCode()) {
                    case ScanConst.KEYCODE_SCAN_FRONT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_LEFT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_RIGHT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_REAR:
                        onScanKeyEvent(event.getAction());
                        break;
                }
            }
        }
    };

    private void onScanKeyEvent(int action) {
        if (mScanner != null) {
            try {
                if (action == KeyEvent.ACTION_DOWN) {
                    mScanner.aDecodeSetTriggerOn(1);
                } else if (action == KeyEvent.ACTION_UP) {
                    mScanner.aDecodeSetTriggerOn(0);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preload_inventory_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%1s v%2s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        mSharedPreferences = getSharedPreferences("preload_preferences", MODE_PRIVATE);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mVibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        mArchiveDirectory = new File(getFilesDir().getAbsolutePath(), PreloadInventoryDatabase.ARCHIVE_DIRECTORY);
        if (!mArchiveDirectory.mkdirs() && !mArchiveDirectory.exists())
            Log.w(TAG, "Archive directory does not exist and could not be created, this may cause a problem");

        if (!EXTERNAL_PATH.mkdirs() && !EXTERNAL_PATH.exists())
            Log.w(TAG, "External directory does not exist and could not be created, this may cause a problem");

        mDatabaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY, PreloadInventoryDatabase.FILE_NAME);
        //mDatabaseFile = new File(INPUT_FILE.getParent(), "/test.db");
        if (!mDatabaseFile.getParentFile().mkdirs() && !mDatabaseFile.exists())
            Log.w(TAG, "Output directory does not exist and could not be created, this may cause a problem");

        mDatabaseFile.renameTo(new File(EXTERNAL_PATH, "test.db"));

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            try {
                if (mDatabaseFile.renameTo(File.createTempFile("error", ".db", mArchiveDirectory)))
                    Toast.makeText(this, "There was an error loading the inventory file. It has been archived", Toast.LENGTH_SHORT).show();
                else
                    databaseLoadingError();
            } catch (IOException e1) {
                e1.printStackTrace();
                databaseLoadingError();
            }
        }
    }

    private void databaseLoadingError() {
        AlertDialog.Builder builder = new AlertDialog.Builder(PreloadInventoryActivity.this);
        builder.setCancelable(false);
        builder.setTitle("Database Load Error");
        builder.setMessage(
                "There was an error loading the inventory file and it could not be archived.\n" +
                "\n" +
                "Would you like to delete the it?\n" +
                "\n" +
                "Answering no will close the app."
        );
        builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { finish(); }
        });
        builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!mDatabaseFile.delete() && mDatabaseFile.exists()) {
                    Toast.makeText(PreloadInventoryActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Toast.makeText(PreloadInventoryActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                initialize();
            }
        });
        builder.create().show();
    }

    private void initialize() throws SQLiteCantOpenDatabaseException {
        saveTask = new WeakAsyncTask<>(saveTaskListeners);

        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile, null);

        ItemTable.create(mDatabase);
        LocationTable.create(mDatabase);

        GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_ITEM_ID + " IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SCANNED_LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" )");
        GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\"");
        GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.PRELOADED_ITEM_ID + " > -1 AND " + ItemTable.Keys.PRELOADED_ITEM_ID + " NOT IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.PRELOAD + "\"");
        //GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ?");
        //GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ID + " FROM ( SELECT " + ScannedLocationTable.Keys.ID + ", " + ScannedLocationTable.Keys.BARCODE + " AS barcode FROM " + ScannedLocationTable.NAME + " WHERE barcode = ? ORDER BY " + ScannedLocationTable.Keys.ID + " ASC LIMIT 1)");
        //GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT MAX(" + LocationTable.Keys.ID + ") as _id FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = " + LocationTable.Source.SCANNER + " AND _id NOT NULL GROUP BY " + LocationTable.Keys.BARCODE);
        GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\" AND " + ItemTable.Keys.BARCODE + " = ? AND " + ItemTable.Keys.SCANNED_LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" AND " + LocationTable.Keys.BARCODE + " = ? )");
        GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" AND " + ItemTable.Keys.BARCODE + " = ?");
        GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" AND " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.BARCODE + " = ?");
        GET_SCANNED_ITEM_COUNT_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_SCANNED_LOCATION_COUNT_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\"");
        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" AND " + ItemTable.Keys.BARCODE + " = ?");
        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.PRELOAD + "\" AND " + LocationTable.Keys.BARCODE + " = ?");
        //GET_STATUS_OF_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT " + LocationTable.Keys.STATUS + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.PRELOAD + "\" AND " + LocationTable.Keys.ID + " = ?");
        GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM ( SELECT MIN(" + ItemTable.Keys.SOURCE + ") AS min_source FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.STATUS + " != ? GROUP BY " + ItemTable.Keys.BARCODE + " ) WHERE min_source = ?");

        mProgressBar = findViewById(R.id.progress_saving);

        /*this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { randomScan(); }
        });*/

        mLocationRecyclerView = findViewById(R.id.location_list_view);
        mLocationRecyclerView.setHasFixedSize(true);
        mLocationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mLocationRecyclerAdapter = new CursorRecyclerViewAdapter<LocationViewHolder>(null) {
            static final int PRELOADED = 1;
            static final int SCANNED = 2;

            @NonNull
            @Override
            public LocationViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
                switch (viewType) {
                    case PRELOADED:
                        return new PreloadedLocationViewHolder(parent);
                    case SCANNED:
                        return new ScannedLocationViewHolder(parent);
                    default:
                        throw new IllegalStateException("Invalid viewType");
                }
            }

            @Override
            public void onBindViewHolder(final LocationViewHolder holder, final Cursor cursor) { holder.bindViews(cursor); }

            @Override
            public int getItemViewType(int position) {
                getCursor().moveToPosition(position);
                if (getCursor().getString(getCursor().getColumnIndex("source")).equals(LocationTable.Source.PRELOAD))
                    return PRELOADED;
                else
                    return SCANNED;
            }
        };
        mLocationRecyclerView.setAdapter(mLocationRecyclerAdapter);
        final RecyclerView.ItemAnimator locationRecyclerAnimator = new DefaultItemAnimator();
        locationRecyclerAnimator.setAddDuration(100);
        locationRecyclerAnimator.setChangeDuration(100);
        locationRecyclerAnimator.setMoveDuration(100);
        locationRecyclerAnimator.setRemoveDuration(100);
        mLocationRecyclerView.setItemAnimator(locationRecyclerAnimator);

        mItemRecyclerView = findViewById(R.id.item_list_view);
        mItemRecyclerView.setHasFixedSize(true);
        mItemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mItemRecyclerAdapter = new CursorRecyclerViewAdapter<ItemViewHolder>(null) {
            @NonNull
            @Override
            public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_item_layout, parent, false)); }

            @Override
            public void onBindViewHolder(final ItemViewHolder holder, final Cursor cursor) { holder.bindViews(cursor); }
        };
        mItemRecyclerView.setAdapter(mItemRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        mItemRecyclerView.setItemAnimator(itemRecyclerAnimator);

        mLocationRecyclerView.setSelectedItem(-1);
        mItemRecyclerView.setSelectedItem(-1);

        if (!mSharedPreferences.getBoolean("ongoing_inventory", false)) {
            mDatabase.delete(ItemTable.NAME, null, null);
            mDatabase.delete(LocationTable.NAME, null, null);

            readFileIntoPreloadDatabase();

            mSharedPreferences.edit().putBoolean("ongoing_inventory", true).apply();
            if (!INPUT_FILE.renameTo(new File(INPUT_FILE.getParent(), "data.old")) && INPUT_FILE.exists())
                throw new RuntimeException("Could not move input file to app data directory");
        }

        asyncRefreshItems();
        asyncRefreshLocations();
    }

    private void asyncRefreshItems() {
        asyncRefreshItemsScrollToItem(null);
    }

    private void asyncRefreshItemsScrollToItem(final String barcode) {
        new AsyncTask<Void, Void, Pair<Cursor, Integer>>() {
            @Override
            protected Pair<Cursor, Integer> doInBackground(Void... voids) {
                Cursor cursor = mDatabase.rawQuery(ITEM_LIST_QUERY, new String[] { mSelectedLocationBarcode });
                int position = -1;
                if (barcode != null && cursor.moveToFirst()) {
                    int barcodeColumnIndex = cursor.getColumnIndex("barcode");
                    for (int i = 0; i < cursor.getCount(); i++) {
                        cursor.moveToPosition(i);
                        if (barcode.equals(cursor.getString(barcodeColumnIndex)))
                            position = i;
                    }
                }

                return new Pair<>(cursor, position);
            }

            @Override
            protected void onPostExecute(Pair<Cursor, Integer> results) {
                mItemRecyclerAdapter.changeCursor(results.first);
                if (results.second >= 0) {
                    mItemRecyclerView.setSelectedItem(results.second);
                    mItemRecyclerView.scrollToPosition(results.second);
                }
                refreshItemInfo();
            }
        }.execute();
    }

    private void asyncScrollToItem(final String barcode) {
        final Cursor cursor = mItemRecyclerAdapter.getCursor();
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                try {
                    if (barcode != null && cursor.moveToFirst()) {
                        int position = -1;
                        int barcodeColumnIndex = cursor.getColumnIndex("barcode");
                        for (int i = 0; i < cursor.getCount(); i++) {
                            cursor.moveToPosition(i);
                            if (!cursor.isClosed() && barcode.equals(cursor.getString(barcodeColumnIndex)))
                                position = i;
                        }
                        return position;
                    } else
                        return -1;
                } catch (IllegalStateException e) {
                    return -1;
                } catch (StaleDataException e) {
                    return -1;
                }
            }

            @Override
            protected void onPostExecute(Integer position) {
                if (position >= 0) {
                    mItemRecyclerView.setSelectedItem(position);
                    mItemRecyclerView.scrollToPosition(position);
                }
            }
        }.execute();
    }

    private void asyncRefreshLocations() {
        asyncRefreshLocationsScrollToLocation(null);
    }

    private void asyncRefreshLocationsScrollToLocation(final String barcode) {
        new AsyncTask<Void, Void, Pair<Cursor, Integer>>() {
            @Override
            protected Pair<Cursor, Integer> doInBackground(Void... voids) {
                Cursor cursor = mDatabase.rawQuery(LOCATION_LIST_QUERY, null);
                int position = -1;

                if (barcode != null && cursor.moveToFirst()) {
                    int barcodeColumnIndex = cursor.getColumnIndex("barcode");
                    for (int i = 0; i < cursor.getCount(); i++) {
                        cursor.moveToPosition(i);
                        if (barcode.equals(cursor.getString(barcodeColumnIndex)))
                            position = i;
                    }
                }

                return new Pair<>(cursor, position);
            }

            @Override
            protected void onPostExecute(Pair<Cursor, Integer> results) {
                mLocationRecyclerAdapter.changeCursor(results.first);
                if (results.second >= 0) {
                    if (mLocationRecyclerView.getSelectedItem() != results.second)
                        mItemRecyclerView.setSelectedItem(-1);
                    mLocationRecyclerView.setSelectedItem(results.second);
                    mLocationRecyclerView.scrollToPosition(results.second);
                }
            }
        }.execute();
    }

    private void asyncScrollToLocation(final String barcode) {
        final Cursor cursor = mLocationRecyclerAdapter.getCursor();
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                try {
                    if (barcode != null && cursor.moveToFirst()) {
                        int position = -1;
                        int barcodeColumnIndex = cursor.getColumnIndex("barcode");
                        for (int i = 0; i < cursor.getCount(); i++) {
                            cursor.moveToPosition(i);
                            if (barcode.equals(cursor.getString(barcodeColumnIndex)))
                                position = i;
                        }
                        return position;
                    } else
                        return -1;
                } catch (IllegalStateException e) {
                    return -1;
                } catch (StaleDataException e) {
                    return -1;
                }
            }

            @Override
            protected void onPostExecute(Integer position) {
                mLocationRecyclerView.setSelectedItem(position);
                if (position >= 0)
                    mLocationRecyclerView.scrollToPosition(position);
            }
        }.execute();
    }

    private void refreshExternalPath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(EXTERNAL_PATH);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } else {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(EXTERNAL_PATH)));
        }
    }

    private void refreshItemInfo() {
        refreshCurrentPreloadedItemCount();
        refreshCurrentNotMisplacedScannedItemCount();
        refreshCurrentMisplacedScannedItemCount();
        refreshCurrentScannedItemCount();
        updateInfo();

        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
            boolean refreshLocations = false;

            if (mCurrentMisplacedScannedItemCount > 0 && mCurrentScannedItemCount > 0) {
                if (!mSelectedLocationStatus.equals(LocationTable.Status.WARNING_ERROR)) {
                    ContentValues locationStatusValues = new ContentValues(1);
                    locationStatusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.WARNING_ERROR);

                    if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                        throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                    refreshLocations = true;
                }
            } else if (mCurrentMisplacedScannedItemCount > 0) {
                if (!mSelectedLocationStatus.equals(LocationTable.Status.ERROR)) {
                    ContentValues locationStatusValues = new ContentValues(1);
                    locationStatusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.ERROR);

                    if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                        throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                    refreshLocations = true;
                }
            } else if (mCurrentScannedItemCount > 0) {
                if (!mSelectedLocationStatus.equals(LocationTable.Status.WARNING)) {
                    ContentValues locationStatusValues = new ContentValues(1);
                    locationStatusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.WARNING);

                    if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                        throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                    refreshLocations = true;
                }
            } else {
                if (!mSelectedLocationStatus.equals("")) {
                    ContentValues locationStatusValues = new ContentValues(1);
                    locationStatusValues.put(PreloadInventoryDatabase.STATUS, "");

                    if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                        throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                    refreshLocations = true;
                }
            }

            ContentValues locationProgressValues = new ContentValues(1);
            locationProgressValues.put(PreloadInventoryDatabase.PROGRESS, mCurrentPreloadedItemCount > 0 ? ((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount : 0);

            if (mSelectedLocationProgress != locationProgressValues.getAsFloat(PreloadInventoryDatabase.PROGRESS)) {
                if (mDatabase.update(LocationTable.NAME, locationProgressValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                    throw new SQLiteException("Could not update progress of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                refreshLocations = true;
            }

            if (refreshLocations)
                asyncRefreshLocations();
        }
    }

    public void refreshCurrentPreloadedItemCount() {
        switch (mSelectedLocationSource) {
            case LocationTable.Source.PRELOAD:
                GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                mCurrentPreloadedItemCount = (int) GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
                break;
            default:
                mCurrentPreloadedItemCount = 0;
                break;
        }
    }

    private void refreshCurrentMisplacedScannedItemCount() {
        switch (mSelectedLocationSource) {
            case LocationTable.Source.PRELOAD:
                GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, mSelectedPreloadedLocationId);
                mCurrentMisplacedScannedItemCount = (int) GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
                break;
            default:
                mCurrentMisplacedScannedItemCount = 0;
                break;
        }
    }

    private void refreshCurrentNotMisplacedScannedItemCount() {
        switch (mSelectedLocationSource) {
            case LocationTable.Source.PRELOAD:
                GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                mCurrentNotMisplacedScannedItemCount = (int) GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT.simpleQueryForLong();
                break;
            default:
                mCurrentNotMisplacedScannedItemCount = 0;
                break;
        }
    }

    private void refreshCurrentScannedItemCount() {
        switch (mSelectedLocationSource) {
            case LocationTable.Source.PRELOAD:
                GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindString(2, ItemTable.Status.MISPLACED);
                GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindString(3, ItemTable.Source.SCANNER);
                mCurrentScannedItemCount = (int) GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
                break;
            case LocationTable.Source.SCANNER:
                GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
                mCurrentScannedItemCount = (int) GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
                break;
            default:
                mCurrentScannedItemCount = 0;
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResultReciever = new ScanResultReceiver();
        IntentFilter resultFilter = new IntentFilter();
        resultFilter.setPriority(0);
        resultFilter.addAction("device.scanner.USERMSG");
        registerReceiver(mResultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
        registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));
        loadCurrentScannerOptions();

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                previousPrefix = mScanner.aDecodeGetPrefix();
                previousPostfix = mScanner.aDecodeGetPostfix();
                mScanner.aDecodeSetPrefix("");
                mScanner.aDecodeSetPostfix("");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mResultReciever);
        unregisterReceiver(mScanKeyEventReceiver);

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(previousPrefix);
                mScanner.aDecodeSetPostfix(previousPostfix);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (saveTask != null && saveTask.getStatus().equals(AsyncTask.Status.RUNNING) && !saveTask.isCancelled())
            saveTask.cancel(false);
        if (mDatabase != null && mDatabase.isOpen())
            mDatabase.close();
        if (mLocationRecyclerAdapter != null && mLocationRecyclerAdapter.getCursor() != null)
            mLocationRecyclerAdapter.getCursor().close();
        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.getCursor().close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_inventory_menu, menu);
        loadCurrentScannerOptions();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save_to_file:
                if (GET_SCANNED_ITEM_COUNT_STATEMENT.simpleQueryForLong() > 0 && GET_SCANNED_LOCATION_COUNT_STATEMENT.simpleQueryForLong() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                            return true;
                        }
                    }

                    if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                        saveTask.cancel(false);
                        postSave();
                    } else {
                        preSave();
                        archiveDatabase();
                        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
                        //initSaveTask().execute();
                        if (saveTask.getStatus().equals(AsyncTask.Status.PENDING))
                            saveTask.execute();
                        else
                            (saveTask = new WeakAsyncTask<>(saveTaskListeners)).execute();
                    }
                } else
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();

                return true;
            case R.id.action_cancel_save:
                if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Cancel Save");
                    builder.setMessage("Are you sure you want to stop saving this file?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) { saveTask.cancel(false); }
                    });
                    builder.create().show();
                } else
                    postSave();

                return true;
            case R.id.action_reset_inventory:
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Reset Inventory");
                    builder.setMessage(
                            "Are you sure you want to reset inventory?\n" +
                            "\n" +
                            "This will remove all items and locations"
                    );
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
                                return;

                            //mDatabase.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
                            //mDatabase.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

                            mDatabase.close();
                            //noinspection ResultOfMethodCallIgnored
                            if (!mDatabaseFile.delete())
                                throw new RuntimeException("Database file could not be deleted");

                            File locationsDatabaseFile = new File(getFilesDir() + "/" + PreloadLocationsDatabase.DIRECTORY, PreloadLocationsDatabase.FILE_NAME);

                            //noinspection ResultOfMethodCallIgnored
                            if (locationsDatabaseFile.exists() && !locationsDatabaseFile.delete())
                                throw new RuntimeException("Locations database file could not be deleted");

                            //noinspection ResultOfMethodCallIgnored
                            if (!INPUT_FILE.delete() && INPUT_FILE.exists())
                                throw new RuntimeException("Input file could not be deleted");

                            //noinspection ResultOfMethodCallIgnored
                            if (!OUTPUT_FILE.delete() && OUTPUT_FILE.exists())
                                throw new RuntimeException("Output file could not be deleted");

                            MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[] { OUTPUT_FILE.getParent() },  null, null);

                            mSharedPreferences.edit().putBoolean("ongoing_inventory", false).apply();

                            startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
                            finish();

                            Toast.makeText(PreloadInventoryActivity.this, "Inventory reset", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                }

                return true;
            /*case R.id.action_clear_scanned_items:
                if (GET_SCANNED_ITEM_COUNT_STATEMENT.simpleQueryForLong() > 0 && GET_SCANNED_LOCATION_COUNT_STATEMENT.simpleQueryForLong() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear Scanned Items");
                    builder.setMessage(
                            "Are you sure you want to clear all scanned items?\n" +
                            "\n" +
                            "This will not remove any preloaded items or locations"
                    );
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
                                return;

                            mChangedSinceLastArchive = true;

                            mDatabase.execSQL("DROP TABLE " + ItemTable.NAME);
                            mDatabase.execSQL("CREATE TABLE " + ItemTable.NAME);

                            mDatabase.execSQL("DROP TABLE " + LocationTable.NAME);
                            mDatabase.execSQL("CREATE TABLE " + LocationTable.NAME);

                            mLocationRecyclerView.setSelectedItem(-1);
                            mSelectedLocationViewHolder = null;
                            mSelectedLocationSource = "";
                            mSelectedLocationStatus = "";
                            mSelectedPreloadedLocationId = -1;
                            mSelectedLocationId = -1;
                            mSelectedMaxLocationId = -1;
                            mSelectedLocationBarcode = "";
                            asyncRefreshLocations();
                            updateInfo();

                            startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
                            finish();

                            Toast.makeText(PreloadInventoryActivity.this, "Inventory reset", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();

                return true;*/
            case R.id.action_continuous:
                try {
                    if (!item.isChecked())
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    else
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);

                    item.setChecked(!item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void preSave() {
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_reset_inventory).setVisible(false);
        //mOptionsMenu.findItem(R.id.action_clear_scanned_items).setVisible(false);
        mOptionsMenu.findItem(R.id.action_continuous).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        mProgressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_reset_inventory).setVisible(true);
        //mOptionsMenu.findItem(R.id.action_clear_scanned_items).setVisible(true);
        mOptionsMenu.findItem(R.id.action_continuous).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void archiveDatabase() {

    }

    private void initScanner() throws RemoteException {
        mScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"));

        if (mScanner != null) {
            mScanner.aDecodeAPIInit();
            //try {
            //Thread.sleep(500);
            //} catch (InterruptedException ignored) { }
            mScanner.aDecodeSetDecodeEnable(1);
            mScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private void loadCurrentScannerOptions() {
        if (mOptionsMenu != null) {
            MenuItem item = mOptionsMenu.findItem(R.id.action_continuous);
            try {
                if (mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
                    mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    item.setChecked(true);
                } else
                    item.setChecked(mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
            } catch (NullPointerException e) {
                e.printStackTrace();
                item.setVisible(false);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    static CharSequence formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    static boolean isItem(@NonNull String barcode) {
        return barcode.startsWith("e1") || barcode.startsWith("E");// || barcode.startsWith("t") || barcode.startsWith("T");
    }

    static boolean isContainer(@NonNull String barcode) {
        return barcode.startsWith("m1") || barcode.startsWith("M");// || barcode.startsWith("a") || barcode.startsWith("A");
    }

    static boolean isLocation(@NonNull String barcode) {
        return barcode.startsWith("V");// || barcode.startsWith("L5");
    }

    private void updateInfo() {
        TextView scannedItemsTextView = findViewById(R.id.items_scanned_text_view);
        TextView misplacedItemsTextView = findViewById(R.id.misplaced_items_text_view);
        if (mLocationRecyclerView.getSelectedItem() < 0) {
            scannedItemsTextView.setText("-");
            misplacedItemsTextView.setText("-");
        } else {
            if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
                scannedItemsTextView.setText(getString(R.string.items_scanned_format_string, mCurrentNotMisplacedScannedItemCount, mCurrentPreloadedItemCount));
                misplacedItemsTextView.setText(String.valueOf(mCurrentMisplacedScannedItemCount + mCurrentScannedItemCount));
            } else {
                scannedItemsTextView.setText(String.valueOf(mCurrentScannedItemCount));
                misplacedItemsTextView.setText("-");
            }
        }
    }

    public void readFileIntoPreloadDatabase() {
        //new WeakAsyncTask<>(readFileTaskListeners).execute();
        LineNumberReader lineReader = null;
        try {

            lineReader = new LineNumberReader(new FileReader(INPUT_FILE));
            String line;
            String[] elements;
            long currentLocationId = -1;

            mDatabase.beginTransaction();

            while ((line = lineReader.readLine()) != null) {
                elements = line.split("((?<!\\|)(\\|)(?!\\|))");

                for (int i = 0; i < elements.length; i++) {
                    elements[i] = elements[i].replaceAll("(^\")|(\"$)", "").replace("\"\"", "\"").replace("||", "|");
                }

                if (elements.length > 0) {
                    GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, elements[1]);
                    GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                    if (isLocation(elements[1]) ? !(GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) : !(GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)) {
                        if (elements.length == 3 && elements[0].equals("L")) {
                            //System.out.println("Location: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            currentLocationId = addPreloadLocation(elements[1], elements[2]);
                            if (currentLocationId == -1)
                                throw new SQLException(String.format(Locale.US, "Error adding location \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                        } else if (elements.length == 4 && elements[0].equals("C")) {
                            //System.out.println("Case-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], elements[3], "", "", elements[2], ItemTable.ItemType.CASE_CONTAINER) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding case-container \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                        } else if (elements.length == 3 && elements[0].equals("B")) {
                            //System.out.println("Bulk-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], ItemTable.ItemType.BULK_CONTAINER) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding bulk-container \"%1s\" from line%2d", elements[1], lineReader.getLineNumber()));
                        } else if (elements.length == 6 && elements[0].equals("I")) {
                            //System.out.println("Item: barcode = \'" + elements[1] + "\', case-number = \'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                            if (addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5], ItemTable.ItemType.ITEM) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding item \"%1s\" from line %2d", elements[1], lineReader.getLineNumber()));
                        } else {
                            if (elements.length < 2)
                                throw new ParseException("Expected at least 2 elements in line", lineReader.getLineNumber());
                            else if (isItem(elements[1]) || isContainer(elements[1]) || isLocation(elements[1]))
                                throw new ParseException("Incorrect format or number of elements", lineReader.getLineNumber());
                            else
                                throw new ParseException(String.format("Barcode \"%s\" not recognised", elements[1]), lineReader.getLineNumber());
                        }
                    }
                } else if (lineReader.getLineNumber() < 2)
                    throw new ParseException("Blank file", lineReader.getLineNumber());
            }

            mDatabase.setTransactionSuccessful();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
            startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
            finish();
            Toast.makeText(PreloadInventoryActivity.this, "There was an error parsing the file", Toast.LENGTH_SHORT).show();
        } finally {
            if (lineReader != null) {
                try {
                    lineReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (mDatabase.inTransaction())
                mDatabase.endTransaction();
        }
    }

    private long addPreloadItem(long preloadedLocationId, @NonNull String barcode, @NonNull String caseNumber, @NonNull String itemNumber, @NonNull String packaging, @NonNull String description, @NotNull String itemType) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING) || preloadedLocationId == -1)
            return -1;

        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, barcode);
        if (GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0) {
            return -1;
        }

        ContentValues newPreloadItem = new ContentValues();
        newPreloadItem.put(PreloadInventoryDatabase.PRELOADED_ITEM_ID, -1);
        newPreloadItem.put(PreloadInventoryDatabase.SCANNED_LOCATION_ID, -1);
        newPreloadItem.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, preloadedLocationId);
        newPreloadItem.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadItem.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_NUMBER, itemNumber);
        newPreloadItem.put(PreloadInventoryDatabase.PACKAGING, packaging);
        newPreloadItem.put(PreloadInventoryDatabase.DESCRIPTION, description);
        newPreloadItem.put(PreloadInventoryDatabase.SOURCE, ItemTable.Source.PRELOAD);
        newPreloadItem.put(PreloadInventoryDatabase.STATUS, "");
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_TYPE, itemType);
        newPreloadItem.put(PreloadInventoryDatabase.DATE_TIME, "");

        return mDatabase.insert(ItemTable.NAME, null, newPreloadItem);
    }

    private long addPreloadLocation(@NonNull String barcode, @NonNull String description) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
            return -1;

        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
        if (GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) {
            return -1;
        }

        ContentValues newLocation = new ContentValues();
        newLocation.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, -1);
        newLocation.put(PreloadInventoryDatabase.PROGRESS, 0f);
        newLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
        newLocation.put(PreloadInventoryDatabase.DESCRIPTION, description);
        newLocation.put(PreloadInventoryDatabase.SOURCE, LocationTable.Source.PRELOAD);
        newLocation.put(PreloadInventoryDatabase.STATUS, "");
        newLocation.put(PreloadInventoryDatabase.DATE_TIME, "");

        return mDatabase.insert(LocationTable.NAME, null, newLocation);
    }

    /*void randomScan() {
        if (mCurrentPreloadedItemCount < 1)
            return;

        Cursor cursor = mItemRecyclerAdapter.getCursor();
        int barcodeIndex = cursor.getColumnIndex(PreloadInventoryDatabase.BARCODE);
        int sourceIndex = cursor.getColumnIndex(PreloadInventoryDatabase.SOURCE);
        int statusIndex = cursor.getColumnIndex(PreloadInventoryDatabase.STATUS);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            if (cursor.getString(sourceIndex).equals(ItemTable.Source.PRELOAD)) {
                if (!cursor.getString(statusIndex).equals(ItemTable.Status.SCANNED)) {
                    scanBarcode(cursor.getString(barcodeIndex));
                    break;
                }
            }
            cursor.moveToNext();
        }
    }*/

    void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot((long) 300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate((long) 300);
        }
    }

    private abstract class LocationViewHolder extends RecyclerView.ViewHolder {

        public LocationViewHolder(final View itemView) {
            super(itemView);
        }

        abstract void bindViews(final Cursor cursor);
    }

    private class PreloadedLocationViewHolder extends LocationViewHolder {
        private TextView locationTextView;
        private MaterialProgressBar locationProgressBar;
        //private ImageView locationWarningSymbol;
        private ImageView locationErrorSymbol;
        private long id = -1;
        private long maxId = -1;
        private long preloadedLocationId = -1;
        private float progress = 0f;
        private String barcode = "";
        private String description = "";
        private String status = "";
        private String dateTime = "";
        boolean isSelected = false;

        PreloadedLocationViewHolder(final ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_location_layout, parent, false));
            locationTextView = itemView.findViewById(R.id.location_text_view);
            locationProgressBar = itemView.findViewById(R.id.location_progress_bar);
            //locationWarningSymbol = itemView.findViewById(R.id.location_warning_symbol);
            locationErrorSymbol = itemView.findViewById(R.id.location_error_symbol);
            itemView.setClickable(true);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scanBarcode(barcode);
                }
            });
        }

        @Override
        void bindViews(final Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            maxId = cursor.getLong(cursor.getColumnIndex("max_id"));
            preloadedLocationId = cursor.getLong(cursor.getColumnIndex("preloaded_location_id"));
            progress = cursor.getFloat(cursor.getColumnIndex("progress"));
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            description = cursor.getString(cursor.getColumnIndex("description"));
            status = cursor.getString(cursor.getColumnIndex("status"));
            dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == mLocationRecyclerView.getSelectedItem();

            if (isSelected) {
                if (mSelectedLocationId != id)
                    asyncRefreshItems();

                mSelectedLocationId = id;
                mSelectedMaxLocationId = maxId;
                mSelectedPreloadedLocationId = preloadedLocationId;
                mSelectedLocationProgress = progress;
                mSelectedLocationBarcode = barcode;
                mSelectedLocationSource = LocationTable.Source.PRELOAD;
                mSelectedLocationStatus = status;

                itemView.setSelected(true);
                locationProgressBar.setProgressDrawable(ContextCompat.getDrawable(PreloadInventoryActivity.this, R.drawable.preloaded_selected_location_progress_bar));
            } else {
                itemView.setSelected(false);
                locationProgressBar.setProgressDrawable(ContextCompat.getDrawable(PreloadInventoryActivity.this, R.drawable.preloaded_deselected_location_progress_bar));
            }

            switch (status) {
                case LocationTable.Status.WARNING_ERROR:
                    //locationWarningSymbol.setVisibility(View.VISIBLE);
                    locationErrorSymbol.setVisibility(View.VISIBLE);
                    break;
                case LocationTable.Status.WARNING:
                    //locationWarningSymbol.setVisibility(View.VISIBLE);
                    //locationErrorSymbol.setVisibility(View.GONE);
                    locationErrorSymbol.setVisibility(View.VISIBLE);
                    break;
                case LocationTable.Status.ERROR:
                    //locationWarningSymbol.setVisibility(View.GONE);
                    locationErrorSymbol.setVisibility(View.VISIBLE);
                    break;
                default:
                    //locationWarningSymbol.setVisibility(View.GONE);
                    locationErrorSymbol.setVisibility(View.GONE);
                    break;
            }

            locationProgressBar.setProgress(Math.round(progress * locationProgressBar.getMax()));
            locationTextView.setText(description);
        }
    }

    private class ScannedLocationViewHolder extends LocationViewHolder {
        private TextView locationTextView;
        private View locationBackground;
        private long id = -1;
        private long maxId = -1;
        private String barcode = "";
        private String dateTime = "";
        boolean isSelected = false;

        ScannedLocationViewHolder(final ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.scanned_location_layout, parent, false));
            locationTextView = itemView.findViewById(R.id.location_text_view);
            locationBackground = itemView.findViewById(R.id.location_background);
            //itemView.setClickable(true);
            /*itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scanBarcode(barcode);
                }
            });*/
        }

        @Override
        void bindViews(Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            maxId = cursor.getLong(cursor.getColumnIndex("max_id"));
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == mLocationRecyclerView.getSelectedItem();

            if (isSelected) {
                if (mSelectedLocationId != id)
                    asyncRefreshItems();

                mSelectedLocationId = id;
                mSelectedMaxLocationId = maxId;
                mSelectedPreloadedLocationId = -1;
                mSelectedLocationProgress = 0f;
                mSelectedLocationBarcode = barcode;
                mSelectedLocationSource = LocationTable.Source.SCANNER;
                mSelectedLocationStatus = "";

                itemView.setSelected(true);
                locationTextView.setTypeface(null, Typeface.BOLD);
                locationBackground.setBackgroundResource(R.drawable.scanned_selected_location_background);
            } else {
                itemView.setSelected(false);
                locationTextView.setTypeface(null, Typeface.NORMAL);
                locationBackground.setBackgroundResource(R.drawable.scanned_deselected_location_background);
            }

            locationTextView.setText(barcode);
        }
    }

    private class ItemViewHolder extends RecyclerView.ViewHolder {
        private TextView textView1;
        private View dividerView;
        private TextView textView2;
        private ImageButton expandedMenuButton;
        private long id = -1;
        private long maxId = -1;
        private long preloadItemId = -1;
        private long scannedLocationId = -1;
        private long preloadLocationId = -1;
        private String barcode = "";
        private String caseNumber = "";
        private String itemNumber = "";
        private String packaging = "";
        private String description = "";
        private String source = "";
        private String status = "";
        private String itemType = "";
        private String dateTime = "";
        private boolean isSelected = false;

        ItemViewHolder(final View itemView) {
            super(itemView);
            textView1 = itemView.findViewById(R.id.text_view_1);
            dividerView = itemView.findViewById(R.id.divider_view);
            textView2 = itemView.findViewById(R.id.text_view_2);
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final PopupMenu popup = new PopupMenu(PreloadInventoryActivity.this, expandedMenuButton);
                    popup.getMenuInflater().inflate(R.menu.popup_menu_item, popup.getMenu());
                    final MenuItem item = popup.getMenu().findItem(R.id.remove_item);
                    item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            if (source.equals(ItemTable.Source.PRELOAD) && !status.equals(ItemTable.Status.SCANNED) && !status.equals(ItemTable.Status.MISPLACED)) {
                                throw new IllegalStateException("A not-scanned preloaded item's 'Remove item' menu option was clicked. Preloaded items cannot be individually removed by the user");
                            }

                            if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                                Toast.makeText(PreloadInventoryActivity.this, "Cannot edit list while saving", Toast.LENGTH_SHORT).show();
                                return true;
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(PreloadInventoryActivity.this);
                            builder.setCancelable(true);
                            builder.setTitle("Remove item");
                            if (source.equals(ItemTable.Source.PRELOAD)) {
                                switch (itemType) {
                                    case ItemTable.ItemType.ITEM:
                                        builder.setMessage(String.format("Are you sure you want to remove item \"%s\"?", getString(R.string.items_number_format_string, caseNumber, itemNumber)));
                                        break;
                                    case ItemTable.ItemType.BULK_CONTAINER:
                                        builder.setMessage(String.format("Are you sure you want to remove bulk-container \"%s\"?", description));
                                        break;
                                    case ItemTable.ItemType.CASE_CONTAINER:
                                        builder.setMessage(String.format("Are you sure you want to remove item \"%s\"?", caseNumber));
                                        break;
                                }
                            } else {
                                builder.setMessage(String.format("Are you sure you want to remove item \"%s\"?", barcode));
                            }
                            builder.setNegativeButton("no", null);
                            builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (mDatabase.delete(ItemTable.NAME, ItemTable.Keys.ID + " = ? AND " + ItemTable.Keys.SOURCE + " != ?", new String[]{ String.valueOf(maxId), ItemTable.Source.PRELOAD }) < 1)
                                        throw new SQLiteException("Could not delete scanned item: No scanned item found with an id of " + maxId);

                                    /*refreshCurrentMisplacedScannedItemCount();
                                    refreshCurrentNotMisplacedScannedItemCount();
                                    refreshCurrentPreloadedItemCount();

                                    GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                                    long scannedItemCount = GET_SCANNED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
                                    if (mCurrentMisplacedScannedItemCount > 0) {
                                        Log.e(TAG, "ERROR");
                                        ContentValues locationStatusValues = new ContentValues(1);
                                        locationStatusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.ERROR);

                                        if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                                            throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                                        mSelectedLocationViewHolder.setStatus(locationStatusValues.getAsString(PreloadInventoryDatabase.STATUS));
                                    } else if (scannedItemCount > 0) {
                                        Log.e(TAG, "WARNING");
                                        ContentValues locationStatusValues = new ContentValues(1);
                                        locationStatusValues.put(PreloadInventoryDatabase.STATUS, LocationTable.Status.WARNING);

                                        if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                                            throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                                        mSelectedLocationViewHolder.setStatus(locationStatusValues.getAsString(PreloadInventoryDatabase.STATUS));
                                    } else {
                                        Log.e(TAG, "OK");
                                        ContentValues locationStatusValues = new ContentValues(1);
                                        locationStatusValues.put(PreloadInventoryDatabase.STATUS, "");

                                        if (mDatabase.update(LocationTable.NAME, locationStatusValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                                            throw new SQLiteException("Could not update status of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                                        mSelectedLocationViewHolder.setStatus(locationStatusValues.getAsString(PreloadInventoryDatabase.STATUS));
                                    }*/

                                    if (source.equals(ItemTable.Source.PRELOAD)) {
                                        ContentValues itemStatusValues = new ContentValues(1);
                                        itemStatusValues.put(PreloadInventoryDatabase.STATUS, "");

                                        if (mDatabase.update(ItemTable.NAME, itemStatusValues, ItemTable.Keys.ID + " = ? AND " + ItemTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(id), ItemTable.Source.PRELOAD}) < 1)
                                            throw new SQLiteException("Could not update status of preloaded item: No preloaded item found with an id of " + maxId);

                                        /*ContentValues locationProgressValues = new ContentValues(1);
                                        locationProgressValues.put(PreloadInventoryDatabase.PROGRESS, ((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount);

                                        if (mDatabase.update(LocationTable.NAME, locationProgressValues, LocationTable.Keys.ID + " = ? AND " + LocationTable.Keys.SOURCE + " = ?", new String[]{String.valueOf(mSelectedPreloadedLocationId), LocationTable.Source.PRELOAD}) < 1)
                                            throw new SQLiteException("Could not update progress of preloaded location: No preloaded location found with an id of " + mSelectedPreloadedLocationId);

                                        mSelectedLocationViewHolder.setProgress(locationProgressValues.getAsFloat(PreloadInventoryDatabase.PROGRESS));
                                        asyncRefreshLocations();*/
                                    }
                                    asyncRefreshItems();
                                }
                            });
                            builder.create().show();

                            return true;
                        }
                    });
                    popup.show();
                }
            });
            /*itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Dialog infoDialog = ;
                }
            });*/
        }

        void bindViews(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndex("_id"));
            this.maxId = cursor.getLong(cursor.getColumnIndex("max_id"));
            this.preloadItemId = cursor.getLong(cursor.getColumnIndex("preloaded_item_id"));
            this.scannedLocationId = cursor.getLong(cursor.getColumnIndex("scanned_location_id"));
            this.preloadLocationId = cursor.getLong(cursor.getColumnIndex("preloaded_location_id"));
            this.barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            this.caseNumber = cursor.getString(cursor.getColumnIndex("case_number"));
            this.itemNumber = cursor.getString(cursor.getColumnIndex("item_number"));
            this.packaging = cursor.getString(cursor.getColumnIndex("packaging"));
            this.description = cursor.getString(cursor.getColumnIndex("description"));
            this.source = cursor.getString(cursor.getColumnIndex("source"));
            this.status = cursor.getString(cursor.getColumnIndex("status"));
            this.itemType = cursor.getString(cursor.getColumnIndex("item_type"));
            this.dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == mItemRecyclerView.getSelectedItem();

            if (isSelected) {
                textView1.setTypeface(null, Typeface.BOLD);
                textView2.setTypeface(null, Typeface.BOLD);
            } else {
                textView1.setTypeface(null, Typeface.NORMAL);
                textView2.setTypeface(null, Typeface.NORMAL);
            }

            if (source.equals(ItemTable.Source.PRELOAD) && !status.equals(ItemTable.Status.SCANNED) && !status.equals(ItemTable.Status.MISPLACED))
                expandedMenuButton.setVisibility(View.GONE);
            else
                expandedMenuButton.setVisibility(View.VISIBLE);

            if (source.equals(ItemTable.Source.PRELOAD) || status.equals(ItemTable.Status.MISPLACED)) {
                switch (itemType) {
                    case ItemTable.ItemType.ITEM:
                        textView1.setText(getString(R.string.items_number_format_string, caseNumber, itemNumber));
                        textView1.setVisibility(View.VISIBLE);
                        textView2.setText(packaging);
                        textView2.setVisibility(View.VISIBLE);
                        break;
                    case ItemTable.ItemType.BULK_CONTAINER:
                        textView1.setText(description);
                        textView2.setVisibility(View.INVISIBLE);
                        break;
                    case ItemTable.ItemType.CASE_CONTAINER:
                        textView1.setText(caseNumber);
                        textView2.setText(description);
                        textView2.setVisibility(View.VISIBLE);
                        break;
                }
            } else {
                textView1.setText(barcode);
                textView2.setVisibility(View.INVISIBLE);
            }

            if (preloadLocationId < 0) { // if it's from a scanned location
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.not_misplaced_preloaded_item_color, null));
            } else if (status.equals(ItemTable.Status.MISPLACED)) { // if it's misplaced
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.misplaced_scanned_preloaded_item_color, null));
            } else if (source.equals(ItemTable.Source.PRELOAD)) { // if it's not misplaced
                if (status.equals(ItemTable.Status.SCANNED)) { // if it's scanned
                    itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.not_misplaced_scanned_preloaded_item_color, null));
                } else {
                    itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.not_misplaced_preloaded_item_color, null));
                }
            } else {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.scanned_item_color, null));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (!saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                            preSave();
                            archiveDatabase();
                            Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
                            //initSaveTask().execute();
                            if (saveTask.getStatus().equals(AsyncTask.Status.PENDING))
                                saveTask.execute();
                            else
                                (saveTask = new WeakAsyncTask<>(saveTaskListeners)).execute();
                        }
                    }
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScanner != null) {
                try {
                    mScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;

                    if (barcode.equals("")) {
                        Toast.makeText(PreloadInventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                    } else if (!barcode.equals("SCAN AGAIN")) {
                        scanBarcode(barcode);
                    }
                    //System.out.println("symName: " + mDecodeResult.symName);
                    //System.out.println("decodeValue: " + mDecodeResult.decodeValue);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void scanBarcode(final String barcode) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
            vibrate();
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }

        new WeakAsyncTask<>(scanBarcodeTaskListeners).execute(barcode);
    }
}