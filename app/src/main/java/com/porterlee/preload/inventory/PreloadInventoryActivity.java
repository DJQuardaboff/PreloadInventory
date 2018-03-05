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
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
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
import android.widget.Button;
import android.widget.ImageButton;
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
import java.util.Random;
import java.util.regex.Pattern;

import com.danilomendes.progressbar.InvertedTextProgressbar;
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

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class PreloadInventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    @Language("RoomSql")
    private static final String ITEM_LIST_QUERY = "SELECT MIN(" + ItemTable.Keys.ID + ") AS _id, MAX(" + ItemTable.Keys.ID + ") AS max_id, MAX(" + ItemTable.Keys.PRELOADED_ITEM_ID + ") AS preloaded_item_id, MAX(" + ItemTable.Keys.SCANNED_LOCATION_ID + ") AS scanned_location_id, MAX(" + ItemTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, " + ItemTable.Keys.BARCODE + " AS barcode, MAX(" + ItemTable.Keys.CASE_NUMBER + ") AS case_number, MAX(" + ItemTable.Keys.ITEM_NUMBER + ") AS item_number, MAX(" + ItemTable.Keys.PACKAGING + ") AS packaging, MAX(" + ItemTable.Keys.DESCRIPTION + ") AS description, MIN(" + ItemTable.Keys.SOURCE + ") AS source, MAX(" + ItemTable.Keys.STATUS + ") AS status, " + ItemTable.Keys.ITEM_TYPE + " AS item_type, MAX(" + ItemTable.Keys.DATE_TIME + ") AS date_time FROM " + ItemTable.NAME + " WHERE preloaded_location_id = ? OR scanned_location_id IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" AND " + LocationTable.Keys.BARCODE + " = ? ) GROUP BY barcode ORDER BY source, _id";
    @Language("RoomSql")
    private static final String LOCATION_LIST_QUERY = "SELECT MIN(" + LocationTable.Keys.ID + ") AS _id, MAX(" + LocationTable.Keys.ID + ") AS max_id, MAX(" + LocationTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, MAX(" + LocationTable.Keys.PROGRESS + ") AS progress, " + LocationTable.Keys.BARCODE + " AS barcode, MAX(" + LocationTable.Keys.DESCRIPTION + ") AS description, MIN(" + LocationTable.Keys.SOURCE + ") AS source, MAX(" + LocationTable.Keys.STATUS + ") AS status, MAX(" + LocationTable.Keys.DATE_TIME + ") AS date_time FROM " + LocationTable.NAME + " WHERE _id NOT NULL GROUP BY barcode ORDER BY source, _id";
    public static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    public static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
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
    private SharedPreferences mSharedPreferences;
    private Vibrator mVibrator;
    private File mInputFile;
    private File mOutputFile;
    private File mDatabaseFile;
    private File mArchiveDirectory;
    private boolean mChangedSinceLastArchive;
    private MaterialProgressBar mProgressBar;
    private Menu mOptionsMenu;
    private long mSelectedLocationId = -1;
    private long mSelectedMaxLocationId = -1;
    private long mSelectedPreloadedLocationId = -1;
    private String mSelectedLocationBarcode = "";
    private String mSelectedLocationSource = "";
    private String mSelectedLocationStatus = "";
    private int mCurrentNotMisplacedScannedItemCount;
    private int mCurrentPreloadedItemCount;
    private int mCurrentMisplacedScannedItemCount;
    private SelectableRecyclerView mLocationRecyclerView;
    private RecyclerView mItemRecyclerView;
    private WeakAsyncTask<Void, Float, Pair<String, String>> saveTask;
    private CursorRecyclerViewAdapter<LocationViewHolder> mLocationRecyclerAdapter;
    private CursorRecyclerViewAdapter<ItemViewHolder> mItemRecyclerAdapter;
    private volatile SQLiteDatabase mDatabase;
    private ScanResultReceiver mResultReciever;
    private IScannerService mScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();

    private final WeakAsyncTask.AsyncTaskListeners<Void, Float, Pair<String, String>> saveTaskListeners = new WeakAsyncTask.AsyncTaskListeners<>(null, new WeakAsyncTask.OnDoInBackgroundListener<Void, Float, Pair<String, String>>() {
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
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("tmp", ".txt", OUTPUT_PATH);
                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);

                int updateNum = 0;
                int tempLocation;
                int itemIndex = 0;
                int totalItemCount = itemCursor.getCount() + 1;
                int currentLocationId = -1;

                //noinspection StringConcatenationMissingWhitespace
                String tempText = BuildConfig.APPLICATION_ID.split(Pattern.quote("."))[2] + "|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n";
                printStream.print(tempText);
                printStream.flush();

                while (!itemCursor.isAfterLast()) {
                    if (isCancelled()) {
                        itemCursor.close();
                        locationCursor.close();
                        return new Pair<>("SaveCancelled", "Save canceled");
                    }

                    final float tempProgress = ((float) itemIndex) / totalItemCount;
                    if (tempProgress * mProgressBar.getMax() > updateNum) {
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

                        printStream.printf("%s|%s\r\n", locationCursor.getString(locationBarcodeIndex), locationCursor.getString(locationDateTimeIndex));
                        printStream.flush();
                    }

                    printStream.printf("%s|%s\r\n", itemCursor.getString(itemBarcodeIndex), itemCursor.getString(itemDateTimeIndex));
                    printStream.flush();

                    itemCursor.moveToNext();
                    itemIndex++;
                }

                printStream.close();

                itemCursor.close();
                locationCursor.close();

                if (mOutputFile.exists() && !mOutputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, "Could not delete existing output file");

                    itemCursor.close();
                    locationCursor.close();
                    return new Pair<>("DeleteFailed", "Could not delete existing output file");
                }

                MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[] { mOutputFile.getParent() },  null, null);

                if (!TEMP_OUTPUT_FILE.renameTo(mOutputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, String.format("Could not rename temp file to \"%s\"", mOutputFile.getName()));

                    itemCursor.close();
                    locationCursor.close();
                    return new Pair<>("RenameFailed", String.format("Could not rename temp file to \"%s\"", mOutputFile.getName()));
                }

                MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[] { mOutputFile.getParent() },  null, null);
            } catch (FileNotFoundException e){
                Log.e(TAG, "FileNotFoundException occurred while saving: " + e.getMessage());
                e.printStackTrace();

                itemCursor.close();
                locationCursor.close();
                return new Pair<>("FileNotFound", "FileNotFoundException occurred while saving");
            } catch (IOException e){
                Log.e(TAG, "IOException occurred while saving: " + e.getMessage());
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

            //MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[] { mOutputFile.getParent() },  null, null);

            postSave();
        }
    }, new WeakAsyncTask.OnCancelledListener<Pair<String, String>>() {
        @Override
        public void onCancelled(Pair<String, String> result) {
            if (saveTaskListeners.getOnPostExecuteListener() != null)
                saveTaskListeners.getOnPostExecuteListener().onPostExecute(result);
        }
    });

    private final WeakAsyncTask.AsyncTaskListeners<String, Void, Object[]> scanBarcodeTaskListeners = new WeakAsyncTask.AsyncTaskListeners<>(null, new WeakAsyncTask.OnDoInBackgroundListener<String, Void, Object[]>() {
        @Override
        public Object[] onDoInBackground(String... params) {
            if (params == null || params.length < 1)
                throw new NullPointerException("This task must be initialized with a barcode");

            String barcode = params[0];

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

                return new Object[] { isPreloaded ? "preloaded_location" : "non_preloaded_location", barcode, mDatabase.insert(LocationTable.NAME, null, newLocationValues), true };
            } else if (isItem(barcode) || isContainer(barcode)) {
                if (mSelectedLocationBarcode.isEmpty() || (mSelectedLocationId < 0 && mSelectedMaxLocationId < 0 && mSelectedPreloadedLocationId < 0))
                    return new Object[] { "no_location" };

                //boolean refreshList;
                boolean isPreloaded;
                boolean isMisplaced = false;
                //long scannedLocationId;
                long preloadedItemId = -1;

                //GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
                //scannedLocationId = GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();

                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(2, mSelectedLocationBarcode);
                if (GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0)
                    return new Object[] { "duplicate", barcode };

                GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
                GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.bindString(2, barcode);
                if (GET_PRELOADED_ITEM_COUNT_FROM_PRELOADED_LOCATION_ID_AND_BARCODE_STATEMENT.simpleQueryForLong() < 1)
                    isMisplaced = true;

                try {
                    GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.bindString(1, barcode);
                    preloadedItemId = GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.simpleQueryForLong();
                    isPreloaded = true;
                } catch (SQLiteDoneException e) {
                    isPreloaded = false;
                }

                if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD) && isPreloaded && !isMisplaced) {
                    refreshCurrentNotMisplacedScannedItemCount();
                    refreshCurrentPreloadedItemCount();

                    ContentValues progressValues = new ContentValues(1);
                    progressValues.put(PreloadInventoryDatabase.PROGRESS, ((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount);

                    if (mDatabase.update(LocationTable.NAME, progressValues, LocationTable.Keys.ID + " = ?", new String[] { String.valueOf(mSelectedPreloadedLocationId) }) < 1)
                        throw new IllegalStateException("No location with id of " + mSelectedPreloadedLocationId);

                    ContentValues statusValues = new ContentValues(1);
                    statusValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.SCANNED);

                    if (mDatabase.update(ItemTable.NAME, statusValues, ItemTable.Keys.ID + " = ? AND " + ItemTable.Keys.SOURCE + " = ?", new String[]{ String.valueOf(preloadedItemId), ItemTable.Source.PRELOAD }) < 1)
                        throw new IllegalStateException("No preloaded item with id of " + preloadedItemId);

                }

                //refreshList = !isPreloaded;

                ContentValues newItemValues = new ContentValues();
                newItemValues.put(PreloadInventoryDatabase.PRELOADED_ITEM_ID, preloadedItemId);
                newItemValues.put(PreloadInventoryDatabase.SCANNED_LOCATION_ID, mSelectedMaxLocationId);
                newItemValues.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, mSelectedPreloadedLocationId);
                newItemValues.put(PreloadInventoryDatabase.BARCODE, barcode);
                newItemValues.put(PreloadInventoryDatabase.CASE_NUMBER, "");
                newItemValues.put(PreloadInventoryDatabase.ITEM_NUMBER, "");
                newItemValues.put(PreloadInventoryDatabase.PACKAGING, "");
                newItemValues.put(PreloadInventoryDatabase.DESCRIPTION, "");
                newItemValues.put(PreloadInventoryDatabase.SOURCE, ItemTable.Source.SCANNER);
                newItemValues.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.SCANNED);
                newItemValues.put(PreloadInventoryDatabase.ITEM_TYPE, ItemTable.ItemType.ITEM);
                newItemValues.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

                if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
                    return new Object[] { "saving", barcode };

                return new Object[] { isPreloaded ? isMisplaced ? "misplaced_item" : "preloaded_item" : "non_preloaded_item", barcode, mDatabase.insert(ItemTable.NAME, null, newItemValues), true };
            } else {
                return new Object[] { "not_recognized" };
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
                    Log.w(TAG, "Barcode scanned before location");
                    Toast.makeText(PreloadInventoryActivity.this, "A location must be scanned first", Toast.LENGTH_SHORT).show();
                    return;
                case "not_recognized":
                    vibrate();
                    Log.w(TAG, "Unrecognised barcode scanned");
                    Toast.makeText(PreloadInventoryActivity.this, "Barcode not recognised", Toast.LENGTH_SHORT).show();
                    return;
            }

            if (results.length < 2)
                throw new IllegalArgumentException(String.format("Result array from task not long enough for a result of \"%s\"", resultType));

            String barcode = (String) results[1];

            switch (resultType) {
                case "duplicate":
                    asyncScrollToItem(barcode);
                    vibrate();
                    Log.w(TAG, "Duplicate barcode scanned");
                    Toast.makeText(PreloadInventoryActivity.this, "Duplicate barcode scanned", Toast.LENGTH_SHORT).show();
                    return;
                case "saving":
                    asyncScrollToItem(barcode);
                    vibrate();
                    Log.w(TAG, "Cannot scan barcode while saving");
                    Toast.makeText(PreloadInventoryActivity.this, "Cannot scan barcode while saving", Toast.LENGTH_SHORT).show();
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
                        Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
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
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshLocationsScrollToLocation(barcode);
                        else
                            asyncScrollToLocation(barcode);
                    }
                    break;
                case "preloaded_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);

                        Log.e(TAG, "preloaded item scanned");
                        asyncRefreshLocations();
                    }
                    break;
                case "non_preloaded_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);

                        Log.e(TAG, "non-preloaded item scanned");
                    }
                    break;
                case "misplaced_item":
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding item \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding item \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);

                        Log.e(TAG, "misplaced item scanned");
                        //todo finish
                    }
                    break;
            }
            updateInfo();
        }
    }, new WeakAsyncTask.OnCancelledListener<Object[]>() {
        @Override
        public void onCancelled(Object[] result) {
            Log.w(TAG + ".ScanBarcodeTask", "This task should not be cancelled");
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

        mSharedPreferences = getPreferences(MODE_PRIVATE);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mVibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        mArchiveDirectory = new File(getFilesDir().getAbsolutePath(), PreloadInventoryDatabase.ARCHIVE_DIRECTORY);
        if (!mArchiveDirectory.exists() && !mArchiveDirectory.mkdirs())
            Log.w(TAG, "Archive directory does not exist and could not be created, this may cause a problem");

        mInputFile = new File(INPUT_PATH.getAbsolutePath(), "data.txt");
        if (!mInputFile.exists() && !INPUT_PATH.mkdirs())
            Log.w(TAG, "Input directory does not exist and could not be created, this may cause a problem");

        mOutputFile = new File(OUTPUT_PATH.getAbsolutePath(), "output.txt");
        if (!mOutputFile.exists() && !OUTPUT_PATH.mkdirs())
            Log.w(TAG, "Output directory does not exist and could not be created, this may cause a problem");

        //mDatabaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY, PreloadInventoryDatabase.FILE_NAME);
        mDatabaseFile = new File(mInputFile.getParent(), "/test.db");
        if (!mDatabaseFile.exists() && !mDatabaseFile.getParentFile().mkdirs())
            Log.w(TAG, "Output directory does not exist and could not be created, this may cause a problem");

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
                if (!mDatabaseFile.delete()) {
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

        mDatabase.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
        mDatabase.execSQL("CREATE TABLE IF NOT EXISTS " + ItemTable.TABLE_CREATION);
        mDatabase.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);
        mDatabase.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_ITEM_ID + " IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\" ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SCANNED_LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" )");
        GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOAD + "\"");
        GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.PRELOADED_ITEM_ID + " NOT IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
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

        try {
            readFileIntoPreloadDatabase();
        } catch (ParseException e) {
            e.printStackTrace();
            startActivity(new Intent(this, PreloadLocationsActivity.class));
            finish();
            Toast.makeText(this, "There was an error parsing the file", Toast.LENGTH_SHORT).show();
        }

        mProgressBar = findViewById(R.id.progress_saving);

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { randomScan(); }
        });

        mLocationRecyclerView = findViewById(R.id.location_list_view);
        mLocationRecyclerView.setHasFixedSize(true);
        mLocationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mLocationRecyclerAdapter = new CursorRecyclerViewAdapter<LocationViewHolder>(null) {
            @NonNull
            @Override
            public LocationViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) { return new LocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_location_layout, parent, false)); }

            @Override
            public void onBindViewHolder(final LocationViewHolder holder, final Cursor cursor) { holder.bindViews(cursor); }
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
        initItemLayout();
        asyncRefreshLocations();
    }

    private void asyncRefreshItems() {
        asyncRefreshItemsScrollToItem(null);
    }

    private void asyncRefreshItemsScrollToItem(final String barcode) {
        new AsyncTask<Void, Void, Pair<Cursor, Integer>>() {
            @Override
            protected Pair<Cursor, Integer> doInBackground(Void... voids) {
                Cursor cursor = mDatabase.rawQuery(ITEM_LIST_QUERY, new String[] { String.valueOf(mSelectedPreloadedLocationId), mSelectedLocationBarcode });
                //todo change database
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
                if (results.second >= 0)
                    mItemRecyclerView.scrollToPosition(results.second);
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
                if (position >= 0)
                    mItemRecyclerView.scrollToPosition(position);
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
                mLocationRecyclerView.setSelectedItem(results.second);
                if (results.second >= 0)
                    mLocationRecyclerView.scrollToPosition(results.second);
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

    private void initItemLayout() {
        asyncRefreshItems();
        refreshCurrentPreloadedItemCount();
        refreshCurrentNotMisplacedScannedItemCount();
        refreshCurrentMisplacedScannedItemCount();
        updateInfo();
    }

    public void refreshCurrentPreloadedItemCount() {
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
            GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
            mCurrentPreloadedItemCount = (int) GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            mCurrentPreloadedItemCount = 0;
        }
    }

    private void refreshCurrentMisplacedScannedItemCount() {
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
            GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
            GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, mSelectedPreloadedLocationId);
            mCurrentMisplacedScannedItemCount = (int) GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            mCurrentMisplacedScannedItemCount = 0;
        }
    }

    private void refreshCurrentNotMisplacedScannedItemCount() {
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
            GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
            mCurrentNotMisplacedScannedItemCount = (int) GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT.simpleQueryForLong();
        } else if (mSelectedLocationSource.equals(LocationTable.Source.SCANNER)) {
            GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
            mCurrentNotMisplacedScannedItemCount = (int) GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
        } else {
            mCurrentNotMisplacedScannedItemCount = 0;
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
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        mDatabase.close();
        mLocationRecyclerAdapter.getCursor().close();
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

                            mChangedSinceLastArchive = true;

                            //int deletedCount = mDatabase.delete(ItemTable.NAME, "1", null);
                            //mDatabase.delete(ItemTable.NAME, null, null);
                            //mDatabase.delete(LocationTable.NAME, null, null);

                            mDatabase.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);

                            mDatabase.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

                            //if (itemCount + containerCount != deletedCount)
                            //Log.v(TAG, "Detected inconsistencies with number of items while deleting");

                            /*mLocationRecyclerView.setSelectedItem(-1);
                            mSelectedLocationSource = "";
                            mSelectedLocationStatus = "";
                            mSelectedPreloadedLocationId = -1;
                            mSelectedScannedLocationId = -1;
                            mSelectedLocationBarcode = "";
                            asyncRefreshLocations();
                            updateInfo();*/

                            startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
                            finish();

                            Toast.makeText(PreloadInventoryActivity.this, "Inventory reset", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                }

                return true;
            case R.id.action_clear_scanned_items:
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

                return true;
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
        mOptionsMenu.findItem(R.id.action_clear_scanned_items).setVisible(false);
        mOptionsMenu.findItem(R.id.action_continuous).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        mProgressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_reset_inventory).setVisible(true);
        mOptionsMenu.findItem(R.id.action_clear_scanned_items).setVisible(true);
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
        //Log.v(TAG, "Updating info");
        TextView scannedItemsTextView = findViewById(R.id.items_scanned);
        TextView misplacedItemsTextView = findViewById(R.id.misplaced_items_text_view);
        if (mLocationRecyclerView.getSelectedItem() < 0) {
            scannedItemsTextView.setText("-");
            misplacedItemsTextView.setText("-");
        } else {
            if (mSelectedLocationSource.equals(LocationTable.Source.PRELOAD)) {
                scannedItemsTextView.setText(getString(R.string.items_scanned_format_string, mCurrentNotMisplacedScannedItemCount, mCurrentPreloadedItemCount));
                misplacedItemsTextView.setText(String.valueOf(mCurrentMisplacedScannedItemCount));
            } else {
                scannedItemsTextView.setText(String.valueOf(mCurrentNotMisplacedScannedItemCount));
                misplacedItemsTextView.setText("-");
            }
        }
    }

    public void readFileIntoPreloadDatabase() throws ParseException {
        try {
            LineNumberReader lineReader = new LineNumberReader(new FileReader(mInputFile));
            String line;
            String[] elements;
            long currentLocationId = -1;
            String currentLocationBarcode = "";

            mDatabase.beginTransaction();

            while ((line = lineReader.readLine()) != null) {
                elements = line.split(Pattern.quote("|"));

                Log.e(TAG, "\"" + elements[1] + "\"");
                GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                Log.e(TAG, "duplicates1: " + GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong());

                Log.e(TAG, "\"" + elements[1] + "\"");
                GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                long temp = GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong();
                Log.e(TAG, "duplicates2: " + temp);

                GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                if (!(GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)) {
                    if (elements.length > 0) {
                        if (elements.length == 3 && elements[0].equals("LOCATION") && isLocation(elements[1])) {
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
                }
            }

            mDatabase.setTransactionSuccessful();

            lineReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mDatabase.inTransaction())
                mDatabase.endTransaction();
        }
    }

    private long addPreloadItem(long preloadedLocationId, @NonNull String barcode, @NonNull String caseNumber, @NonNull String itemNumber, @NonNull String packaging, @NonNull String description, @NotNull String itemType) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING) || preloadedLocationId == -1)
            return -1;

        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, barcode);
        long temp = GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong();
        if (temp > 0) {
            Log.e(TAG, "duplicates: " + temp);
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
        newPreloadItem.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.NOT_SCANNED);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_TYPE, itemType);
        newPreloadItem.put(PreloadInventoryDatabase.DATE_TIME, "");

        return mDatabase.insert(ItemTable.NAME, null, newPreloadItem);
    }

    private long addPreloadLocation(@NonNull String barcode, @NonNull String description) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
            return -1;

        Log.e(TAG, "\"" + barcode + "\"");

        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
        long temp = GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
        Log.e(TAG, "duplicates3: " + temp);
        if (temp > 0) {
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

    void randomScan() {
        if (mCurrentPreloadedItemCount < 1)
            return;

        int position = new Random().nextInt(mCurrentPreloadedItemCount);
        Cursor cursor = mItemRecyclerAdapter.getCursor();
        int barcodeIndex = cursor.getColumnIndex(PreloadInventoryDatabase.BARCODE);
        int sourceIndex = cursor.getColumnIndex(PreloadInventoryDatabase.SOURCE);
        cursor.moveToFirst();
        int index = 0;
        while (!cursor.isAfterLast()) {
            if (cursor.getString(sourceIndex).equals(ItemTable.Source.PRELOAD)) {
                if (index == position)
                    scanBarcode(cursor.getString(barcodeIndex));
                index++;
            }
            cursor.moveToNext();
        }
    }

    void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot((long) 300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate((long) 300);
        }
    }

    private class LocationViewHolder extends RecyclerView.ViewHolder {
        //private TextView locationDescriptionTextView;
        private InvertedTextProgressbar locationProgressBar;
        private long id = -1;
        private long maxId = -1;
        private long preloadedLocationId = -1;
        private float progress = 0f;
        private String barcode = "";
        private String description = "";
        private String source = "";
        private String status = "";
        private String dateTime = "";
        boolean isSelected = false;

        InvertedTextProgressbar getProgressBar() {
            return locationProgressBar;
        }

        LocationViewHolder(final View itemView) {
            super(itemView);
            //locationDescriptionTextView = itemView.findViewById(R.id.location_description);
            locationProgressBar = itemView.findViewById(R.id.location_progress_bar);
            itemView.setClickable(true);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scanBarcode(barcode);
                }
            });
        }

        void bindViews(Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            maxId = cursor.getLong(cursor.getColumnIndex("max_id"));
            preloadedLocationId = cursor.getLong(cursor.getColumnIndex("preloaded_location_id"));
            progress = cursor.getFloat(cursor.getColumnIndex("progress"));
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            description = cursor.getString(cursor.getColumnIndex("description"));
            source = cursor.getString(cursor.getColumnIndex("source"));
            status = cursor.getString(cursor.getColumnIndex("status"));
            dateTime = cursor.getString(cursor.getColumnIndex("date_time"));
            isSelected = getAdapterPosition() == mLocationRecyclerView.getSelectedItem();

            if (isSelected) {
                mSelectedLocationId = id;
                mSelectedMaxLocationId = maxId;
                mSelectedPreloadedLocationId = preloadedLocationId;
                mSelectedLocationBarcode = barcode;
                mSelectedLocationSource = source;
                mSelectedLocationStatus = status;
                initItemLayout();
            }

            //itemView.setBackgroundColor(source.equals(LocationTable.Source.PRELOAD) ? (isSelected ? SELECTED_PRELOADED_LOCATION_BACKGROUND_COLOR : DESELECTED_PRELOADED_LOCATION_BACKGROUND_COLOR) : (isSelected ? SELECTED_SCANNED_LOCATION_BACKGROUND_COLOR : DESELECTED_SCANNED_LOCATION_BACKGROUND_COLOR));
            itemView.setBackgroundColor(source.equals(LocationTable.Source.PRELOAD) ? (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_preloaded_location_background_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_preloaded_location_background_color, null)) : (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_scanned_location_background_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_scanned_location_background_color, null)));

            //locationDescriptionTextView.setText(source.equals(LocationTable.Source.PRELOAD) ? description : barcode);
            //locationDescriptionTextView.setTextColor(source.equals(LocationTable.Source.PRELOAD) ? (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_preloaded_location_text_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_preloaded_location_text_color, null)) : (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_scanned_location_text_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_scanned_location_text_color, null)));
            locationProgressBar.setText(source.equals(LocationTable.Source.PRELOAD) ? description : barcode);
            locationProgressBar.getTextPaint().setColor(source.equals(LocationTable.Source.PRELOAD) ? (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_preloaded_location_text_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_preloaded_location_text_color, null)) : (isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_scanned_location_text_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_scanned_location_text_color, null)));
            locationProgressBar.getTextInvertedPaint().setColor(isSelected ? ResourcesCompat.getColor(getResources(), R.color.selected_preloaded_location_inverted_text_color, null) : ResourcesCompat.getColor(getResources(), R.color.deselected_preloaded_location_inverted_text_color, null));
            locationProgressBar.setImageResource(isSelected ? R.drawable.preloaded_location_progress_bar_selected : R.drawable.preloaded_location_progress_bar_deselected);

            Log.e(TAG, String.valueOf((int) (progress * locationProgressBar.getMaxProgress())));
            locationProgressBar.setProgress((int) (progress * locationProgressBar.getMaxProgress()));
        }
    }

    private class ItemViewHolder extends RecyclerView.ViewHolder {
        private TextView textView1;
        private View dividerView;
        private TextView textView2;
        private ImageButton expandedMenuButton;
        private long id = -1;
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
                    popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());
                    final MenuItem item = popup.getMenu().findItem(R.id.remove_item);
                    item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            if (source.equals(ItemTable.Source.PRELOAD)) {
                                throw new IllegalStateException("A preloaded item's 'Remove item' menu option was clicked. Preloaded items cannot be individually removed by the user");
                            }

                            if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                                Toast.makeText(PreloadInventoryActivity.this, "Cannot edit list while saving", Toast.LENGTH_SHORT).show();
                                return true;
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(PreloadInventoryActivity.this);
                            builder.setCancelable(true);
                            builder.setTitle("Remove item");
                            builder.setMessage(String.format("Are you sure you want to remove barcode \"%s\"?", barcode));
                            builder.setNegativeButton("no", null);
                            builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mDatabase.delete(ItemTable.NAME, ItemTable.Keys.ID + " = ?", new String[]{String.valueOf(id)});
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
        }

        void bindViews(Cursor cursor) {
            this.id = cursor.getLong(cursor.getColumnIndex("_id"));
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

            if (source.equals(ItemTable.Source.PRELOAD))
                expandedMenuButton.setVisibility(View.INVISIBLE);
            else
                expandedMenuButton.setVisibility(View.VISIBLE);

            switch (itemType) {
                case ItemTable.ItemType.ITEM:
                    if (source.equals(ItemTable.Source.PRELOAD)) {
                        textView1.setText(getString(R.string.items_number_format_string, caseNumber, itemNumber));
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setText(packaging);
                        textView2.setVisibility(View.VISIBLE);
                    } else {
                        textView1.setText(barcode);
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setText("-");
                        textView2.setVisibility(View.VISIBLE);
                    }
                    break;
                case ItemTable.ItemType.BULK_CONTAINER:
                    textView1.setText(description);
                    textView1.setVisibility(View.VISIBLE);
                    dividerView.setVisibility(View.VISIBLE);
                    textView2.setText("-");
                    textView2.setVisibility(View.VISIBLE);
                    break;
                case ItemTable.ItemType.CASE_CONTAINER:
                    textView1.setText(caseNumber);
                    textView1.setVisibility(View.VISIBLE);
                    dividerView.setVisibility(View.VISIBLE);
                    textView2.setText(description);
                    textView2.setVisibility(View.VISIBLE);
                    break;
            }

            if (source.equals(ItemTable.Source.PRELOAD)) {
                if (preloadLocationId == mSelectedPreloadedLocationId) { // if it's not misplaced

                } else { // if it's misplaced

                }
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
                    if (barcode.equals(">><<")) {
                        Toast.makeText(PreloadInventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                    } else if (barcode.startsWith(">>") && barcode.endsWith("<<")) {
                        barcode = barcode.substring(2, barcode.length() - 2);
                        if (barcode.equals("SCAN AGAIN")) return;
                        scanBarcode(barcode);
                    } else if (!barcode.equals("SCAN AGAIN")){
                        Toast.makeText(PreloadInventoryActivity.this, "Barcode prefix and suffix may not be set", Toast.LENGTH_SHORT).show();
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