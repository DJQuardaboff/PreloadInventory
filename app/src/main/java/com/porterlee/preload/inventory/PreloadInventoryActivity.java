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
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.nfc.Tag;
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
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
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

import org.jetbrains.annotations.NotNull;

public class PreloadInventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String ITEM_LIST_QUERY = "SELECT MIN(" + ItemTable.Keys.ID + ") AS _id, MAX(" + ItemTable.Keys.ID + ") AS max_id, MAX(" + ItemTable.Keys.PRELOADED_ITEM_ID + ") AS preloaded_item_id, MAX(" + ItemTable.Keys.SCANNED_LOCATION_ID + ") AS scanned_location_id, MAX(" + ItemTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, " + ItemTable.Keys.BARCODE + " AS barcode, " + ItemTable.Keys.CASE_NUMBER + " AS case_number, " + ItemTable.Keys.ITEM_NUMBER + " AS item_number, " + ItemTable.Keys.PACKAGING + " AS packaging, " + ItemTable.Keys.DESCRIPTION + " AS description, MIN(" + ItemTable.Keys.SOURCE + ") AS source, " + ItemTable.Keys.STATUS + " AS status, " + ItemTable.Keys.ITEM_TYPE + " AS item_type, " + ItemTable.Keys.DATE_TIME + " AS date_time FROM " + ItemTable.NAME + " WHERE preloaded_location_id = ? OR scanned_location_id IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" AND " + LocationTable.Keys.BARCODE + " = ? ) GROUP BY barcode ORDER BY source DESC, _id DESC";
    private static final String LOCATION_LIST_QUERY = "SELECT MIN(" + LocationTable.Keys.ID + ") AS _id, MAX(" + LocationTable.Keys.ID + ") AS max_id, MAX(" + LocationTable.Keys.PRELOADED_LOCATION_ID + ") AS preloaded_location_id, MAX(" + LocationTable.Keys.PROGRESS + ") AS progress, " + LocationTable.Keys.BARCODE + " AS barcode, MAX(" + LocationTable.Keys.DESCRIPTION + ") AS description, MIN(" + LocationTable.Keys.SOURCE + ") AS source, MAX(" + LocationTable.Keys.STATUS + ") AS status, MAX(" + LocationTable.Keys.DATE_TIME + ") AS date_time FROM " + LocationTable.NAME + " WHERE _id NOT NULL GROUP BY barcode ORDER BY source DESC, _id DESC";
    public static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    public static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    private static final int SELECTED_LOCATION_BACKGROUND_COLOR = 0xFF536DFE;
    private static final int SELECTED_LOCATION_TEXT_COLOR = Color.WHITE;
    private static final int DESELECTED_LOCATION_BACKGROUND_COLOR = Color.WHITE;
    private static final int DESELECTED_LOCATION_TEXT_COLOR = Color.BLACK;
    private static final int SELECTED_SCANNED_LOCATION_BACKGROUND_COLOR = 0xFF009688;
    private static final int SELECTED_SCANNED_LOCATION_TEXT_COLOR = Color.WHITE;
    private static final int DESELECTED_SCANNED_LOCATION_BACKGROUND_COLOR = Color.WHITE;
    private static final int DESELECTED_SCANNED_LOCATION_TEXT_COLOR = 0xFF009688;
    private SQLiteStatement GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT;
    private SQLiteStatement GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_SCANNED_LOCATION_COUNT_STATEMENT;
    //private SQLiteStatement GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT;
    //private SQLiteStatement GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT;
    //private SQLiteStatement GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement UPDATE_LOCATION_PROGRESS;
    private SQLiteStatement UPDATE_LOCATION_STATUS;
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
                    return null;

                return new Object[] { isPreloaded ? "preloaded_location" : "non_preloaded_location", barcode, mDatabase.insert(LocationTable.NAME, null, newLocationValues), true };
            } else if (isItem(barcode) || isContainer(barcode)) {
                if (mSelectedLocationBarcode.isEmpty() || (mSelectedLocationId < 0 && mSelectedMaxLocationId < 0 && mSelectedPreloadedLocationId < 0))
                    return new Object[] { "no_location" };

                //boolean refreshList;
                boolean isPreloaded;
                //long scannedLocationId;
                long preloadedItemId = -1;

                //GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
                //scannedLocationId = GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();

                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(2, mSelectedLocationBarcode);
                if (GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0)
                    return new Object[] { "duplicate" };

                try {
                    GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.bindString(1, barcode);
                    preloadedItemId = GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.simpleQueryForLong();
                    isPreloaded = true;
                } catch (SQLiteDoneException e) {
                    isPreloaded = false;
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
                    return null;

                return new Object[] { isItem(barcode) ? (isPreloaded ? "preloaded_item" : "non_preloaded_item") : (isPreloaded ? "preloaded_container" : "non_preloaded_container"), barcode, mDatabase.insert(ItemTable.NAME, null, newItemValues), true };
            } else {
                return new Object[] { "not_recognized" };
            }
        }
    }, null, new WeakAsyncTask.OnPostExecuteListener<Object[]>() {
        @Override
        public void onPostExecute(Object[] resultss) {
            if (resultss == null)
                throw new IllegalArgumentException("Null result from task");

            if (resultss.length < 1)
                throw new IllegalArgumentException("Result array from task not long enough");

            String resultType = (String) resultss[0];

            switch (resultType) {
                case "duplicate":
                    vibrate();
                    Log.w(TAG, "Duplicate barcode scanned");
                    Toast.makeText(PreloadInventoryActivity.this, "Duplicate barcode scanned", Toast.LENGTH_SHORT).show();
                    return;
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

            if (resultss.length < 1)
                throw new IllegalArgumentException("Result array from task not long enough");

            String barcode = (String) resultss[1];
            Long rowId = (Long) resultss[2];
            Boolean refreshList = (Boolean) resultss[3];

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
                case "preloaded_item": case "preloaded_container":
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                    } else {
                        refreshCurrentNotMisplacedScannedItemCount();
                        refreshCurrentPreloadedItemCount();
                        //UPDATE_LOCATION_PROGRESS.bindString(1, String.valueOf(((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount));
                        //UPDATE_LOCATION_PROGRESS.bindLong(1, mSelectedPreloadedLocationId);
                        //if (UPDATE_LOCATION_PROGRESS.executeUpdateDelete() < 1)
                        ContentValues values = new ContentValues(1);
                        values.put(PreloadInventoryDatabase.PROGRESS,((float) mCurrentNotMisplacedScannedItemCount) / mCurrentPreloadedItemCount);
                        if (mDatabase.update(LocationTable.NAME, values, LocationTable.Keys.ID + " = ?", new String[] { String.valueOf(mSelectedPreloadedLocationId) }) < 1)
                            throw new IllegalStateException("No location with id of " + mSelectedPreloadedLocationId);

                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);
                    }
                    break;
                case "non_preloaded_item": case "non_preloaded_container":
                    if (rowId < 0) {
                        vibrate();
                        Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                        throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                    } else {
                        mChangedSinceLastArchive = true;
                        if (refreshList)
                            asyncRefreshItemsScrollToItem(barcode);
                        else
                            asyncScrollToItem(barcode);
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

        mDatabaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY, PreloadInventoryDatabase.FILE_NAME);
        //mDatabaseFile = new File(mInputFile.getParent(), "/test.db");
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

        GET_NOT_MISPLACED_SCANNED_ITEM_COUNT_WITH_PRELOADED_LOCATION_ID_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_ITEM_ID + " IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\" ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_SCANNED_ITEM_COUNT_WITH_SCANNED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SCANNED_LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" )");
        GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOADED + "\"");
        GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? AND " + ItemTable.Keys.PRELOADED_ITEM_ID + " NOT IN ( SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.PRELOADED_LOCATION_ID + " = ? ) AND " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.PRELOADED + "\"");
        //GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ?");
        //GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ID + " FROM ( SELECT " + ScannedLocationTable.Keys.ID + ", " + ScannedLocationTable.Keys.BARCODE + " AS barcode FROM " + ScannedLocationTable.NAME + " WHERE barcode = ? ORDER BY " + ScannedLocationTable.Keys.ID + " ASC LIMIT 1)");
        //GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT MAX(" + LocationTable.Keys.ID + ") as _id FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ? AND " + LocationTable.Keys.SOURCE + " = " + LocationTable.Source.SCANNER + " AND _id NOT NULL GROUP BY " + LocationTable.Keys.BARCODE);
        GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\" AND " + ItemTable.Keys.BARCODE + " = ? AND " + ItemTable.Keys.SCANNED_LOCATION_ID + " IN ( SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\" AND " + LocationTable.Keys.BARCODE + " = ? )");
        GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOADED + "\" AND " + ItemTable.Keys.BARCODE + " = ?");
        GET_SCANNED_ITEM_COUNT_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.SCANNER + "\"");
        GET_SCANNED_LOCATION_COUNT_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.SCANNER + "\"");
        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.SOURCE + " = \"" + ItemTable.Source.PRELOADED + "\" AND " + ItemTable.Keys.BARCODE + " = ?");
        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT = mDatabase.compileStatement("SELECT COUNT(*) FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.SOURCE + " = \"" + LocationTable.Source.PRELOADED + "\" AND " + LocationTable.Keys.BARCODE + " = ?");
        UPDATE_LOCATION_PROGRESS = mDatabase.compileStatement("UPDATE " + LocationTable.NAME + " SET " + PreloadInventoryDatabase.PROGRESS + " = ? WHERE " + LocationTable.Keys.ID + " = ?");
        UPDATE_LOCATION_STATUS = mDatabase.compileStatement("UPDATE " + LocationTable.NAME + " SET " + PreloadInventoryDatabase.STATUS + " = ? WHERE " + LocationTable.Keys.ID + " = ?");

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
            @Override
            public LocationViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) { return new LocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_location_layout, parent, false)); }

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
            @Override
            public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) { return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_item_layout, parent, false)); }

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

        Log.e(TAG, "1");
        mLocationRecyclerView.setSelectedItem(-1);
        initItemLayout();
        asyncRefreshLocations();
        Log.e(TAG, "2");
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
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOADED)) {
            GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
            mCurrentPreloadedItemCount = (int) GET_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            mCurrentPreloadedItemCount = 0;
        }
    }

    private void refreshCurrentMisplacedScannedItemCount() {
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOADED)) {
            GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, mSelectedPreloadedLocationId);
            GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, mSelectedPreloadedLocationId);
            mCurrentMisplacedScannedItemCount = (int) GET_MISPLACED_SCANNED_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            mCurrentMisplacedScannedItemCount = 0;
        }
    }

    private void refreshCurrentNotMisplacedScannedItemCount() {
        if (mSelectedLocationSource.equals(LocationTable.Source.PRELOADED)) {
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
            if (mSelectedLocationSource.equals(LocationTable.Source.PRELOADED)) {
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

            while ((line = lineReader.readLine()) != null) {
                elements = line.split(Pattern.quote("|"));

                if (elements.length > 0) {
                    if (elements.length == 3 && elements[0].equals("LOCATION") && isLocation(elements[1])) {
                        //System.out.println("Location: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                        currentLocationId = addPreloadLocation(elements[1], elements[2]);
                        currentLocationBarcode = elements[1];
                        if (currentLocationId == -1) {
                            lineReader.close();
                            throw new SQLException(String.format(Locale.US, "Error adding location \"%1s\" from line %2d", elements[1], lineReader.getLineNumber()));
                        }
                    } else if (elements.length == 3 && isContainer(elements[1])) {
                        //System.out.println("Bulk-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], ItemTable.ItemType.BULK_CONTAINER) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding bulk-container \"%s\" from line %2d", elements[1], lineReader.getLineNumber()));
                        } else {
                            lineReader.close();
                            throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                        }
                    } else if (elements.length == 4 && isContainer(elements[1])) {
                        //System.out.println("Case-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            if (addPreloadItem(currentLocationId, elements[1], elements[3], "", "", elements[2], ItemTable.ItemType.CASE_CONTAINER) < 0) {
                                Log.e(TAG, "Case-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                                throw new SQLiteException(String.format(Locale.US, "Error adding case-container \"%s\" from line %2d", elements[1], lineReader.getLineNumber()));
                            }
                        } else {
                            lineReader.close();
                            throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                        }
                    } else if (elements.length == 6 && isItem(elements[1])) {
                        //System.out.println("Item: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', case-number\'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                        if (elements[0].equals(currentLocationBarcode)) {
                            if (addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5], ItemTable.ItemType.ITEM) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding item \"%s\" from line %2d", elements[1], lineReader.getLineNumber()));
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

            lineReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private long addPreloadItem(long preloadedLocationId, @NonNull String barcode, @NonNull String caseNumber, @NonNull String itemNumber, @NonNull String packaging, @NonNull String description, @NotNull String itemType) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING) || preloadedLocationId == -1)
            return -1;

        GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, barcode);
        if (GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)
            return -1;

        ContentValues newPreloadItem = new ContentValues();
        newPreloadItem.put(PreloadInventoryDatabase.PRELOADED_ITEM_ID, -1);
        newPreloadItem.put(PreloadInventoryDatabase.SCANNED_LOCATION_ID, -1);
        newPreloadItem.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, preloadedLocationId);
        newPreloadItem.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadItem.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_NUMBER, itemNumber);
        newPreloadItem.put(PreloadInventoryDatabase.PACKAGING, packaging);
        newPreloadItem.put(PreloadInventoryDatabase.DESCRIPTION, description);
        newPreloadItem.put(PreloadInventoryDatabase.SOURCE, ItemTable.Source.PRELOADED);
        newPreloadItem.put(PreloadInventoryDatabase.STATUS, ItemTable.Status.NOT_SCANNED);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_TYPE, itemType);
        newPreloadItem.put(PreloadInventoryDatabase.DATE_TIME, "");

        return mDatabase.insert(ItemTable.NAME, null, newPreloadItem);
    }

    private long addPreloadLocation(@NonNull String barcode, @NonNull String description) {
        if (saveTask.getStatus().equals(AsyncTask.Status.RUNNING))
            return -1;

        GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
        if (GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0)
            return -1;

        ContentValues newLocation = new ContentValues();
        newLocation.put(PreloadInventoryDatabase.PRELOADED_LOCATION_ID, -1);
        newLocation.put(PreloadInventoryDatabase.PROGRESS, 0f);
        newLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
        newLocation.put(PreloadInventoryDatabase.DESCRIPTION, description);
        newLocation.put(PreloadInventoryDatabase.SOURCE, LocationTable.Source.PRELOADED);
        newLocation.put(PreloadInventoryDatabase.STATUS, "");
        newLocation.put(PreloadInventoryDatabase.DATE_TIME, "");

        return mDatabase.insert(LocationTable.NAME, null, newLocation);
    }

    void randomScan() {
    }

    void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot((long) 300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate((long) 300);
        }
    }

    private class LocationViewHolder extends RecyclerView.ViewHolder {
        private TextView locationDescriptionTextView;
        private ProgressBar locationProgressBar;
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


        LocationViewHolder(final View itemView) {
            super(itemView);
            locationDescriptionTextView = itemView.findViewById(R.id.location_description);
            locationProgressBar = itemView.findViewById(R.id.location_progress_bar);
            /*itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scanBarcode(barcode);
                }
            });*/
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

            locationDescriptionTextView.setText(source.equals(LocationTable.Source.PRELOADED) ? description : barcode);
            itemView.setBackgroundColor(source.equals(LocationTable.Source.PRELOADED) ? (isSelected ? SELECTED_LOCATION_BACKGROUND_COLOR : DESELECTED_LOCATION_BACKGROUND_COLOR) : (isSelected ? SELECTED_SCANNED_LOCATION_BACKGROUND_COLOR : DESELECTED_SCANNED_LOCATION_BACKGROUND_COLOR));
            locationDescriptionTextView.setTextColor(source.equals(LocationTable.Source.PRELOADED) ? (isSelected ? SELECTED_LOCATION_TEXT_COLOR : DESELECTED_LOCATION_TEXT_COLOR) : (isSelected ? SELECTED_SCANNED_LOCATION_TEXT_COLOR : DESELECTED_SCANNED_LOCATION_TEXT_COLOR));
            locationDescriptionTextView.setVisibility(View.VISIBLE);
            locationProgressBar.setProgress((int) (progress * locationProgressBar.getMax()));
            locationDescriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            locationDescriptionTextView.setText(String.valueOf("{" + id + "},{" + maxId + "},{" + preloadedLocationId + "},{" + progress + "},{" + barcode + "},{" + description + "},{" + source + "},{" + status + "},{" + dateTime + "}"));
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
                            if (source.equals(ItemTable.Source.PRELOADED)) {
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

            if (source.equals(ItemTable.Source.PRELOADED))
                expandedMenuButton.setVisibility(View.INVISIBLE);
            else
                expandedMenuButton.setVisibility(View.VISIBLE);

            switch (source) {
                case ItemTable.ItemType.ITEM:
                    if (source.equals(ItemTable.Source.PRELOADED)) {
                        textView1.setText(getString(R.string.items_number_format_string, caseNumber, itemNumber));
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setVisibility(View.VISIBLE);
                    } else {
                        textView1.setText(barcode);
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setVisibility(View.INVISIBLE);
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
        /*new AsyncTask<SparseArray<Object>, Void, SparseArray<Object>>() {
            private final String TAG = this.getClass().getSimpleName();

            class ResultKeys {
                static final int ROW_ID = 0;
                static final int RESULT_TYPE = 1;
                static final int BARCODE = 2;
                static final int REFRESH_LIST = 3;
            }

            class ResultValue {
                static final int DUPLICATE = -3;
                static final int NO_LOCATION = -2;
                static final int NOT_RECOGNISED = -1;
                static final int PRELOADED_LOCATION = 1;
                static final int NON_PRELOADED_LOCATION = 2;
                static final int PRELOADED_ITEM = 3;
                static final int NON_PRELOADED_ITEM = 4;
                static final int PRELOADED_CONTAINER = 5;
                static final int NON_PRELOADED_CONTAINER = 6;
            }

            @Override
            protected SparseArray<Object> doInBackground(SparseArray<Object>... voids) {
                if (isLocation(barcode)) {
                    boolean isPreloaded;
                    long locationId;

                    GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                    final boolean refreshLocations = !(GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0);

                    GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                    try {
                        locationId = GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
                        isPreloaded = true;
                    } catch (SQLiteDoneException e) {
                        locationId = -1;
                        isPreloaded = false;
                    }

                    ContentValues newScannedLocation = new ContentValues();
                    newScannedLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
                    newScannedLocation.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, locationId);
                    newScannedLocation.put(PreloadInventoryDatabase.TAGS, "");
                    newScannedLocation.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

                    if (saveTask.getStatus().equals(Status.RUNNING))
                        return null;

                    SparseArray<Object> results = new SparseArray<>(4);
                    results.append(ResultKeys.ROW_ID, mDatabase.insert(ScannedLocationTable.NAME, null, newScannedLocation));
                    results.append(ResultKeys.RESULT_TYPE, isPreloaded ? ResultValue.PRELOADED_LOCATION : ResultValue.NON_PRELOADED_LOCATION);
                    results.append(ResultKeys.BARCODE, barcode);
                    results.append(ResultKeys.REFRESH_LIST, refreshLocations);
                    return results;
                } else if (isItem(barcode) || isContainer(barcode)) {
                    if (mSelectedLocationBarcode == null) {
                        SparseArray<Object> results = new SparseArray<>(1);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.NO_LOCATION);
                        return results;
                    }
                    boolean refreshList;
                    boolean isPreloaded;
                    long scannedLocationId;
                    long preloadedLocationId = mSelectedLocationIsPreloaded ? mSelectedLocationId : -1;
                    long preloadedItemId = -1;
                    long preloadedContainerId = -1;

                    GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.bindString(1, mSelectedLocationBarcode);
                    scannedLocationId = GET_LAST_ID_OF_SCANNED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();

                    GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
                    GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.bindString(2, mSelectedLocationBarcode);
                    if (GET_DUPLICATES_OF_SCANNED_ITEM_WITH_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) {
                        SparseArray<Object> results = new SparseArray<>(1);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.DUPLICATE);
                        return results;
                    }

                    if (isItem(barcode)) {
                        try {
                            GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.bindString(1, barcode);
                            preloadedItemId = GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT.simpleQueryForLong();
                            isPreloaded = true;
                        } catch (SQLiteDoneException e) {
                            isPreloaded = false;
                        }
                    } else {
                        try {
                            GET_PRELOADED_CONTAINER_ID_FROM_BARCODE_STATEMENT.bindString(1, barcode);
                            preloadedContainerId = GET_PRELOADED_CONTAINER_ID_FROM_BARCODE_STATEMENT.simpleQueryForLong();
                            isPreloaded = true;
                        } catch (SQLiteDoneException e) {
                            isPreloaded = false;
                        }
                    }

                    refreshList = !isPreloaded;

                    ContentValues newScannedItem = new ContentValues();
                    newScannedItem.put(PreloadInventoryDatabase.LOCATION_ID, scannedLocationId);
                    newScannedItem.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, preloadedLocationId);
                    newScannedItem.put(PreloadInventoryDatabase.PRELOAD_ITEM_ID, preloadedItemId);
                    newScannedItem.put(PreloadInventoryDatabase.PRELOAD_CONTAINER_ID, preloadedContainerId);
                    newScannedItem.put(PreloadInventoryDatabase.BARCODE, barcode);
                    newScannedItem.put(PreloadInventoryDatabase.TAGS, "");
                    newScannedItem.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

                    if (saveTask.getStatus().equals(Status.RUNNING))
                        return null;

                    SparseArray<Object> results = new SparseArray<>(4);
                    results.append(ResultKeys.ROW_ID, mDatabase.insert(ScannedItemTable.NAME, null, newScannedItem));
                    results.append(ResultKeys.RESULT_TYPE, isItem(barcode) ? (isPreloaded ? ResultValue.PRELOADED_ITEM : ResultValue.NON_PRELOADED_ITEM) : (isPreloaded ? ResultValue.PRELOADED_CONTAINER : ResultValue.NON_PRELOADED_CONTAINER));
                    results.append(ResultKeys.BARCODE, barcode);
                    results.append(ResultKeys.REFRESH_LIST, refreshList);
                    return results;
                } else {
                    SparseArray<Object> results = new SparseArray<>(1);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.NOT_RECOGNISED);
                    return results;
                }
            }

            @Override
            protected void onPostExecute(SparseArray<Object> results) {
                if (results == null) return;
                Integer resultType = (Integer) results.get(ResultKeys.RESULT_TYPE);

                switch (resultType) {
                    case ResultValue.DUPLICATE:
                        vibrate(300);
                        Log.w(TAG, "Duplicate barcode scanned");
                        Toast.makeText(PreloadInventoryActivity.this, "Duplicate barcode scanned", Toast.LENGTH_SHORT).show();
                        return;
                    case ResultValue.NO_LOCATION:
                        vibrate(300);
                        Log.w(TAG, "Barcode scanned before location");
                        Toast.makeText(PreloadInventoryActivity.this, "A location must be scanned first", Toast.LENGTH_SHORT).show();
                        return;
                    case ResultValue.NOT_RECOGNISED:
                        vibrate(300);
                        Log.w(TAG, "Unrecognised barcode scanned");
                        Toast.makeText(PreloadInventoryActivity.this, "Barcode not recognised", Toast.LENGTH_SHORT).show();
                        return;
                }

                String barcode = (String) results.get(ResultKeys.BARCODE);
                Long rowId = (Long) results.get(ResultKeys.ROW_ID);
                Boolean refreshList = (Boolean) results.get(ResultKeys.REFRESH_LIST);

                switch (resultType) {
                    case ResultValue.PRELOADED_LOCATION:
                        if (rowId < 0) {
                            vibrate(300);
                            Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                            throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                        } else
                            asyncScrollToLocation(barcode);
                        break;
                    case ResultValue.NON_PRELOADED_LOCATION:
                        if (rowId < 0) {
                            vibrate(300);
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
                    case ResultValue.PRELOADED_ITEM:
                    case ResultValue.PRELOADED_CONTAINER:
                        if (rowId < 0) {
                            vibrate(300);
                            Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                            throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                        } else
                            asyncScrollToItem(barcode);
                        break;
                    case ResultValue.NON_PRELOADED_ITEM:
                    case ResultValue.NON_PRELOADED_CONTAINER:
                        if (rowId < 0) {
                            vibrate(300);
                            Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                            throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                        } else {
                            if (refreshList)
                                asyncRefreshItemsScrollToItem(barcode);
                            else
                                asyncScrollToItem(barcode);
                        }
                        break;
                }
            }
        }.execute();*/
    }
}