package com.porterlee.preloadinventory;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Color;
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
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.porterlee.preloadinventory.PreloadInventoryDatabase.ScannedItemTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.ScannedLocationTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.PreloadedItemTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.PreloadedContainerTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.PreloadedLocationTable;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.preloadinventory.MainActivity.DATE_FORMAT;

public class PreloadInventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String PRELOADED_ITEM_LIST_QUERY = "SELECT _id, scanned_item_id, scanned_location_id, preload_location_id, preload_item_id, preload_container_id, barcode, case_number, item_number, packaging, description, tags, date_time FROM ( SELECT " + ScannedItemTable.Keys.ID + " AS _id, " + ScannedItemTable.Keys.ID + " AS scanned_item_id, " + ScannedItemTable.Keys.LOCATION_ID + " AS scanned_location_id, " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " AS preload_item_id, " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " AS preload_container_id, " + ScannedItemTable.Keys.BARCODE + " AS barcode, NULL AS case_number, NULL AS item_number, NULL AS packaging, NULL AS description, " + ScannedItemTable.Keys.TAGS + " AS tags, " + ScannedItemTable.Keys.DATE_TIME + " AS date_time, 0 AS format FROM " + ScannedItemTable.NAME + " WHERE preload_item_id < 0 AND preload_container_id < 0 AND preload_location_id = ? UNION SELECT " + PreloadedContainerTable.Keys.ID + " AS _id, -1 AS scanned_item_id, -1 AS scanned_location_id, " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, -1 AS preload_item_id, " + PreloadedContainerTable.Keys.ID + " AS preload_container_id, " + PreloadedContainerTable.Keys.BARCODE + " AS barcode, " + PreloadedContainerTable.Keys.CASE_NUMBER + " AS case_number, NULL AS item_number, NULL AS packaging, " + PreloadedContainerTable.Keys.DESCRIPTION + " AS description, NULL AS tags, NULL AS date_time, 1 AS format FROM " + PreloadedContainerTable.NAME + " WHERE preload_location_id = ? UNION SELECT " + PreloadedItemTable.Keys.ID + " AS _id, -1 AS scanned_item_id, -1 AS scanned_location_id, " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + PreloadedItemTable.Keys.ID + " AS preload_item_id, -1 AS preload_container_id, " + PreloadedItemTable.Keys.BARCODE + " AS barcode, " + PreloadedItemTable.Keys.CASE_NUMBER + " AS case_number, " + PreloadedItemTable.Keys.ITEM_NUMBER + " AS item_number, " + PreloadedItemTable.Keys.PACKAGE + " AS packaging, " + PreloadedItemTable.Keys.DESCRIPTION + " AS description, NULL AS tags, NULL AS date_time, 0 AS format FROM " + PreloadedItemTable.NAME + " WHERE preload_location_id = ? ) ORDER BY format, scanned_item_id DESC, preload_item_id DESC, preload_container_id DESC";
    private static final String SCANNED_ITEM_LIST_QUERY = "SELECT " + ScannedItemTable.Keys.ID + " AS _id, " + ScannedItemTable.Keys.ID + " AS scanned_item_id, " + ScannedItemTable.Keys.LOCATION_ID + " AS scanned_location_id, " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " AS preload_item_id, " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " AS preload_container_id, " + ScannedItemTable.Keys.BARCODE + " AS barcode, NULL AS case_number, NULL AS item_number, NULL AS packaging, NULL AS description, " + ScannedItemTable.Keys.TAGS + " AS tags, " + ScannedItemTable.Keys.DATE_TIME + " AS date_time, 0 AS format FROM " + ScannedItemTable.NAME + " WHERE preload_item_id < 0 AND preload_container_id < 0 AND scanned_location_id = ? ORDER BY format, scanned_item_id DESC, preload_item_id DESC, preload_container_id DESC";
    private static final String LOCATION_LIST_QUERY = "SELECT _id, is_preloaded, barcode, description FROM ( SELECT " + PreloadedLocationTable.Keys.ID + " AS _id, " + PreloadedLocationTable.Keys.ID + " AS sort, 1 AS is_preloaded, " + PreloadedLocationTable.Keys.BARCODE + " AS barcode, " + PreloadedLocationTable.Keys.DESCRIPTION + " AS description, 1 AS filter FROM " + PreloadedLocationTable.NAME + " UNION SELECT MAX(" + ScannedLocationTable.Keys.ID + ") AS _id, MIN(" + ScannedLocationTable.Keys.ID + ") AS sort, 0 AS is_preloaded, " + ScannedLocationTable.Keys.BARCODE + " AS barcode, NULL AS description, 0 AS filter FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " < 0 GROUP BY barcode ) WHERE _id NOT NULL ORDER BY filter, sort DESC";
    static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    private static final int SELECTED_LOCATION_BACKGROUND_COLOR = 0xFF536DFE;
    private static final int SELECTED_LOCATION_TEXT_COLOR = Color.WHITE;
    private static final int DESELECTED_LOCATION_BACKGROUND_COLOR = Color.WHITE;
    private static final int deselectedLocationTextColor = Color.BLACK;
    private static final int selectedScannedLocationBackgroundColor = 0xFF009688;
    private static final int selectedScannedLocationTextColor = Color.WHITE;
    private static final int deselectedScannedLocationBackgroundColor = Color.WHITE;
    private static final int deselectedScannedLocationTextColor = 0xFF009688;
    private static final int SAVE_TASK_ID = 1;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_SCANNED_LOCATION_COUNT_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT;
    private SQLiteStatement GET_PRELOADED_CONTAINER_ID_FROM_BARCODE_STATEMENT;
    //private SQLiteStatement GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_LAST_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_DUPLICATES_OF_SCANNED_ITEM_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_LOCATION_COUNT_STATEMENT;
    private SharedPreferences sharedPreferences;
    private Vibrator vibrator;
    private File inputFile;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    //private static AsyncTask<Void, Integer, SparseArray<Object>> saveTask;
    private ScanResultReceiver resultReciever;
    private long selectedLocationId;
    private String selectedLocationBarcode;
    private boolean selectedLocationIsPreloaded;
    private int currentNotMisplacedScannedItemCount;
    private int currentScannedAndPreloadedItemCount;
    private int currentPreloadedItemCount;
    private int currentMisplacedScannedItemCount;
    private SelectableRecyclerView locationRecyclerView;
    private RecyclerView itemRecyclerView;
    private CursorRecyclerViewAdapter<LocationViewHolder> locationRecyclerAdapter;
    private CursorRecyclerViewAdapter<ItemViewHolder> itemRecyclerAdapter;
    private SQLiteDatabase db;
    private IScannerService iScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();
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
        if (iScanner != null) {
            try {
                if (action == KeyEvent.ACTION_DOWN) {
                    iScanner.aDecodeSetTriggerOn(1);
                } else if (action == KeyEvent.ACTION_UP) {
                    iScanner.aDecodeSetTriggerOn(0);
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

        sharedPreferences = getPreferences(MODE_PRIVATE);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        archiveDirectory = new File(getFilesDir().getAbsolutePath(), "/" + PreloadInventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        inputFile = new File(INPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        inputFile.getParentFile().mkdirs();
        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "output.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.getParentFile().mkdirs();
        //databaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY + "/" + PreloadInventoryDatabase.FILE_NAME);
        databaseFile = new File(inputFile.getParent(), "/test.db");
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            try {
                if (databaseFile.renameTo(File.createTempFile("error", ".db", archiveDirectory)))
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
                if (!databaseFile.delete()) {
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
        SparseArray<Object> tempArgs = new SparseArray<>(2);
        tempArgs.put(SaveToFileTask.ArgKeys.DATABASE, db);
        tempArgs.put(SaveToFileTask.ArgKeys.OUTPUT_FILE, outputFile);
        SaveToFileTask.setArgs(tempArgs);
        SaveToFileTask.mActivity = new WeakReference<>(this);
        getLoaderManager().initLoader(SAVE_TASK_ID, null, new LoaderManager.LoaderCallbacks<SparseArray<Object>>() {
            @Override
            public Loader<SparseArray<Object>> onCreateLoader(int id, Bundle args) {
                return new SaveToFileTask(PreloadInventoryActivity.this);
            }

            @Override
            public void onLoadFinished(Loader<SparseArray<Object>> loader, SparseArray<Object> data) {
                if (data == null)
                    throw new IllegalArgumentException("Null result from SaveToFileTask");

                int resultType = (int) data.get(SaveToFileTask.ResultKeys.RESULT_TYPE);
                String message = (String) data.get(SaveToFileTask.ResultKeys.MESSAGE);

                if (savingToast != null) {
                    savingToast.cancel();
                    savingToast = null;
                }

                Toast.makeText(PreloadInventoryActivity.this, message, Toast.LENGTH_SHORT).show();

                if (changedSinceLastArchive)
                    archiveDatabase();

                postSave();
                MediaScannerConnection.scanFile(PreloadInventoryActivity.this, new String[]{outputFile.getAbsolutePath()}, null, null);
            }

            @Override
            public void onLoaderReset(Loader<SparseArray<Object>> loader) {

            }
        });

        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        db.execSQL("DROP TABLE IF EXISTS " + ScannedItemTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedItemTable.TABLE_CREATION);
        db.execSQL("DROP TABLE IF EXISTS " + ScannedLocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedLocationTable.TABLE_CREATION);
        db.execSQL("DROP TABLE IF EXISTS " + PreloadedItemTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedItemTable.TABLE_CREATION);
        db.execSQL("DROP TABLE IF EXISTS " + PreloadedContainerTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedContainerTable.TABLE_CREATION);
        db.execSQL("DROP TABLE IF EXISTS " + PreloadedLocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedLocationTable.TABLE_CREATION);

        try {
            readFileIntoPreloadDatabase();
        } catch (ParseException e) {
            e.printStackTrace();
            startActivity(new Intent(this, PreloadLocationsActivity.class));
            finish();
            Toast.makeText(this, "There was an error parsing the file", Toast.LENGTH_SHORT).show();
        }

        //LAST_SCANNED_ITEM_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.ID + " = ( SELECT " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " FROM " + ScannedItemTable.NAME + " ORDER BY " + ScannedItemTable.Keys.ID + " DESC LIMIT 1 )");
        //LAST_SCANNED_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedLocationTable.Keys.BARCODE + " FROM " + PreloadedLocationTable.NAME + " WHERE " + PreloadedLocationTable.Keys.ID + " = ( SELECT " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " FROM " + ScannedLocationTable.NAME + " ORDER BY " + ScannedLocationTable.Keys.ID + " DESC LIMIT 1 )");
        GET_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + PreloadedLocationTable.Keys.BARCODE + " as barcode FROM " + PreloadedLocationTable.NAME + " GROUP BY barcode UNION SELECT " + ScannedLocationTable.Keys.BARCODE + " as barcode FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " < 0 )");
        GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT DISTINCT " + ScannedItemTable.Keys.BARCODE + " FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " IN ( SELECT " + PreloadedItemTable.Keys.ID + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? ) OR " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " IN ( SELECT " + PreloadedContainerTable.Keys.ID + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? ) )");
        GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + ScannedItemTable.Keys.BARCODE + " FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedContainerTable.Keys.BARCODE + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? )");
        GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.LOCATION_ID + " = ?");
        //GET_SCANNED_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedLocationTable.NAME + "");
        //GET_PRELOADED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + PreloadedItemTable.NAME + "");
        GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedContainerTable.Keys.BARCODE + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? )");
        //GET_SCANNED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME);
        GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? AND " + ScannedItemTable.Keys.PRELOAD_ITEM_ID +" < 0 AND " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " < 0");
        GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedLocationTable.Keys.ID + " FROM " + PreloadedLocationTable.NAME + " WHERE " + PreloadedLocationTable.Keys.BARCODE + " = ?");
        //GET_POSITION_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + PreloadedLocationTable.Keys.ID + " AS id, " + PreloadedLocationTable.Keys.BARCODE + " AS barcode, 1 AS filter FROM " + PreloadedLocationTable.NAME + " UNION SELECT MIN(" + ScannedLocationTable.Keys.ID + ") AS id, " + ScannedLocationTable.Keys.BARCODE + " AS barcode, 0 AS filter FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " = -1 GROUP BY barcode ) WHERE id NOT NULL AND filter < ? OR ( filter = ? AND id > ? )");
        GET_DUPLICATES_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.BARCODE + " = ?");
        //GET_FIRST_ID_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadInventoryDatabase.ID + " FROM ( SELECT " + ScannedLocationTable.Keys.ID + ", " + ScannedLocationTable.Keys.BARCODE + " AS barcode FROM " + ScannedLocationTable.NAME + " WHERE barcode = ? ORDER BY " + ScannedLocationTable.Keys.ID + " ASC LIMIT 1)");
        GET_LAST_ID_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT MAX(" + ScannedLocationTable.Keys.ID + ") as _id FROM " + ScannedLocationTable.NAME + " WHERE barcode = ? AND _id NOT NULL GROUP BY " + ScannedLocationTable.Keys.BARCODE);
        GET_DUPLICATES_OF_SCANNED_ITEM_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.BARCODE + " = ? AND " + ScannedItemTable.Keys.LOCATION_ID + " IN ( SELECT " + ScannedLocationTable.Keys.ID + " FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.BARCODE + " = ? )");
        GET_PRELOADED_ITEM_ID_FROM_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedItemTable.Keys.ID + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.BARCODE + " = ?");
        GET_PRELOADED_CONTAINER_ID_FROM_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedContainerTable.Keys.ID + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.BARCODE + " = ?");
        GET_SCANNED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME);
        GET_SCANNED_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedLocationTable.NAME);

        progressBar = findViewById(R.id.progress_saving);

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { randomScan(); }
        });

        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new CursorRecyclerViewAdapter<LocationViewHolder>(null) {
            @Override
            public LocationViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) { return new LocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_location_layout, parent, false)); }

            @Override
            public void onBindViewHolder(final LocationViewHolder holder, final Cursor cursor) { holder.bindViews(cursor); }
        };
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator locationRecyclerAnimator = new DefaultItemAnimator();
        locationRecyclerAnimator.setAddDuration(100);
        locationRecyclerAnimator.setChangeDuration(100);
        locationRecyclerAnimator.setMoveDuration(100);
        locationRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(locationRecyclerAnimator);

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new CursorRecyclerViewAdapter<ItemViewHolder>(null) {
            @Override
            public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) { return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_item_layout, parent, false)); }

            @Override
            public void onBindViewHolder(final ItemViewHolder holder, final Cursor cursor) { holder.bindViews(cursor); }
        };
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);

        locationRecyclerView.setSelectedItem(-1);
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
                String currentLocationIdString = String.valueOf(selectedLocationId);
                Cursor cursor = db.rawQuery(selectedLocationIsPreloaded ? PRELOADED_ITEM_LIST_QUERY : SCANNED_ITEM_LIST_QUERY, selectedLocationIsPreloaded ? new String[] {currentLocationIdString, currentLocationIdString, currentLocationIdString} : new String[] {currentLocationIdString});
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
                itemRecyclerAdapter.changeCursor(results.first);
                if (results.second >= 0)
                    itemRecyclerView.scrollToPosition(results.second);
            }
        }.execute();
        //todo finish
    }

    private void asyncScrollToItem(final String barcode) {
        final Cursor cursor = itemRecyclerAdapter.getCursor();
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
                    itemRecyclerView.scrollToPosition(position);
            }
        }.execute();
        //todo finish
    }

    private void asyncRefreshLocations() {
        asyncRefreshLocationsScrollToLocation(null);
    }

    private void asyncRefreshLocationsScrollToLocation(final String barcode) {
        new AsyncTask<Void, Void, Pair<Cursor, Integer>>() {
            @Override
            protected Pair<Cursor, Integer> doInBackground(Void... voids) {
                Cursor cursor = db.rawQuery(LOCATION_LIST_QUERY, null);
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
                locationRecyclerAdapter.changeCursor(results.first);
                if (results.second >= 0)
                    locationRecyclerView.scrollToPosition(results.second);
            }
        }.execute();
        //todo finish
    }

    private void asyncScrollToLocation(final String barcode) {
        final Cursor cursor = locationRecyclerAdapter.getCursor();
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
                locationRecyclerView.setSelectedItem(position);
                if (position >= 0)
                    locationRecyclerView.scrollToPosition(position);
            }
        }.execute();
        //todo finish
    }

    private void initItemLayout() {
        asyncRefreshItems();
        refreshCurrentScannedAndPreloadedItemCount();
        refreshCurrentPreloadedItemCount();
        refreshCurrentNotMisplacedScannedItemCount();
        refreshCurrentMisplacedScannedItemCount();
        updateInfo();
    }

    @Override
    public void onUpdate(Integer progress) {

    }

    public void refreshCurrentPreloadedItemCount() {
        if (selectedLocationId < 0) {
            currentPreloadedItemCount = 0;
            return;
        }

        if (selectedLocationIsPreloaded) {
            GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(2, selectedLocationId);
            currentPreloadedItemCount = (int) GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            currentPreloadedItemCount = 0;
        }
    }

    private void refreshCurrentMisplacedScannedItemCount() {
        if (selectedLocationId < 0) {
            currentMisplacedScannedItemCount = 0;
            return;
        }

        if (selectedLocationIsPreloaded) {
            GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            currentMisplacedScannedItemCount = (int) GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            currentMisplacedScannedItemCount = 0;
        }
    }

    private void refreshCurrentScannedAndPreloadedItemCount() {
        if (selectedLocationId < 0) {
            currentScannedAndPreloadedItemCount = 0;
            return;
        }

        if (selectedLocationIsPreloaded) {
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, selectedLocationId);
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(3, selectedLocationId);
            currentScannedAndPreloadedItemCount = (int) GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            currentScannedAndPreloadedItemCount = (int) GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.simpleQueryForLong();
        }
    }

    private void refreshCurrentNotMisplacedScannedItemCount() {
        if (selectedLocationId < 0) {
            currentNotMisplacedScannedItemCount = 0;
            return;
        }

        if (selectedLocationIsPreloaded) {
            GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(2, selectedLocationId);
            currentNotMisplacedScannedItemCount = (int) GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.bindLong(1, selectedLocationId);
            currentNotMisplacedScannedItemCount = (int) GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.simpleQueryForLong();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resultReciever = new ScanResultReceiver();
        IntentFilter resultFilter = new IntentFilter();
        resultFilter.setPriority(0);
        resultFilter.addAction("device.scanner.USERMSG");
        registerReceiver(resultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
        registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));
        loadCurrentScannerOptions();

        if (iScanner != null) {
            try {
                iScanner.aDecodeSetTriggerOn(0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(resultReciever);
        unregisterReceiver(mScanKeyEventReceiver);

        if (iScanner != null) {
            try {
                iScanner.aDecodeSetTriggerOn(0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        locationRecyclerAdapter.getCursor().close();
        itemRecyclerAdapter.getCursor().close();
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
            case R.id.action_remove_all:
                if (GET_SCANNED_ITEM_COUNT_STATEMENT.simpleQueryForLong() > 0 && GET_SCANNED_LOCATION_COUNT_STATEMENT.simpleQueryForLong() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear Inventory");
                    builder.setMessage(
                            "Are you sure you want to clear this inventory?\n" +
                            "\n" +
                            "This will not remove preloaded items and locations"
                    );
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (isSaving())
                                return;

                            changedSinceLastArchive = true;

                            //int deletedCount = db.delete(ItemTable.NAME, "1", null);
                            //db.delete(ItemTable.NAME, null, null);
                            //db.delete(LocationTable.NAME, null, null);

                            db.execSQL("DROP TABLE IF EXISTS " + ScannedItemTable.NAME);
                            db.execSQL("CREATE TABLE " + ScannedItemTable.TABLE_CREATION);

                            db.execSQL("DROP TABLE IF EXISTS " + ScannedLocationTable.NAME);
                            db.execSQL("CREATE TABLE " + ScannedLocationTable.TABLE_CREATION);

                            //if (itemCount + containerCount != deletedCount)
                            //Log.v(TAG, "Detected inconsistencies with number of items while deleting");

                            locationRecyclerView.setSelectedItem(-1);
                            selectedLocationId = -1;
                            selectedLocationBarcode = null;
                            asyncRefreshLocations();
                            updateInfo();
                            Toast.makeText(PreloadInventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_save_to_file:
                if (GET_SCANNED_ITEM_COUNT_STATEMENT.simpleQueryForLong() <= 0 && GET_SCANNED_LOCATION_COUNT_STATEMENT.simpleQueryForLong() <= 0) {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        return true;
                    }
                }

                if (isSaving()) {
                    getSaveTask().cancelLoadInBackground();
                    postSave();
                } else {
                    preSave();
                    archiveDatabase();
                    (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                    initSaveTask().forceLoad();
                }

                return true;
            case R.id.action_cancel_save:
                if (isSaving()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Cancel Save");
                    builder.setMessage("Are you sure you want to stop saving this file?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) { getSaveTask().cancelLoadInBackground(); }
                    });
                    builder.create().show();
                } else {
                    postSave();
                }
                return true;
            case R.id.action_continuous:
                try {
                    if (!item.isChecked()){
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    } else {
                        iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                    }
                    item.setChecked(!item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_locations:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setTitle("Switch mode");
                builder.setMessage("Are you sure you want to switch to preloading locations");
                builder.setNegativeButton("no", null);
                builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
                        finish();
                    }
                });
                builder.create().show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void preSave() {
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(false);
        mOptionsMenu.findItem(R.id.action_continuous).setVisible(false);
        mOptionsMenu.findItem(R.id.action_locations).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(true);
        mOptionsMenu.findItem(R.id.action_continuous).setVisible(true);
        mOptionsMenu.findItem(R.id.action_locations).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void archiveDatabase() {

    }

    private void initScanner() throws RemoteException {
        iScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"));

        if (iScanner != null) {
            iScanner.aDecodeAPIInit();
            //try {
            //Thread.sleep(500);
            //} catch (InterruptedException ignored) { }
            iScanner.aDecodeSetDecodeEnable(1);
            iScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private void loadCurrentScannerOptions() {
        if (mOptionsMenu != null) {
            MenuItem item = mOptionsMenu.findItem(R.id.action_continuous);
            try {
                if (iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
                    iScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    item.setChecked(true);
                } else
                    item.setChecked(iScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
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
        if (locationRecyclerView.getSelectedItem() < 0) {
            scannedItemsTextView.setText("-");
            misplacedItemsTextView.setText("-");
        } else {
            if (selectedLocationIsPreloaded) {
                scannedItemsTextView.setText(getString(R.string.items_scanned_format_string, currentNotMisplacedScannedItemCount, currentPreloadedItemCount));
                misplacedItemsTextView.setText(String.valueOf(currentMisplacedScannedItemCount));
            } else {
                scannedItemsTextView.setText(String.valueOf(currentNotMisplacedScannedItemCount));
                misplacedItemsTextView.setText("-");
            }
        }
    }

    public void readFileIntoPreloadDatabase() throws ParseException {
        try {
            LineNumberReader lineReader = new LineNumberReader(new FileReader(inputFile));
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
                            throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                        }
                    } else if (elements.length == 3 && isContainer(elements[1])) {
                        //System.out.println("Bulk-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadContainer(currentLocationId, elements[1], elements[2], "");
                        } else {
                            lineReader.close();
                            throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                        }
                    } else if (elements.length == 4 && isContainer(elements[1])) {
                        //System.out.println("Case-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadContainer(currentLocationId, elements[1], elements[2], elements[3]);
                        } else {
                            lineReader.close();
                            throw new ParseException("Location does not match previously defined location", lineReader.getLineNumber());
                        }
                    } else if (elements.length == 6 && isItem(elements[1])) {
                        //System.out.println("Item: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', case-number\'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5]);
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
                } else {
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

    private void addPreloadItem(long locationId, @NonNull String barcode, @NonNull String caseNumber, @NonNull String itemNumber, @NonNull String packaging, @NonNull String description) {
        if (isSaving() || locationId == -1)
            return;

        ContentValues newPreloadItem = new ContentValues();
        newPreloadItem.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, locationId);
        newPreloadItem.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadItem.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_NUMBER, itemNumber);
        newPreloadItem.put(PreloadInventoryDatabase.PACKAGE, packaging);
        newPreloadItem.put(PreloadInventoryDatabase.TAGS, "");
        newPreloadItem.put(PreloadInventoryDatabase.DESCRIPTION, description);

        if (db.insert(PreloadedItemTable.NAME, null, newPreloadItem) < 0)
            throw new SQLiteException(String.format("Error adding item \"%s\" to the inventory", barcode));
    }

    private void addPreloadContainer(long locationId, @NonNull String barcode, @NonNull String description, @NonNull String caseNumber) {
        if (isSaving() || locationId == -1)
            return;

        ContentValues newPreloadContainer = new ContentValues();
        newPreloadContainer.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, locationId);
        newPreloadContainer.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadContainer.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);
        newPreloadContainer.put(PreloadInventoryDatabase.TAGS, "");
        newPreloadContainer.put(PreloadInventoryDatabase.DESCRIPTION, description);

        if (db.insert(PreloadedContainerTable.NAME, null, newPreloadContainer) < 0)
            throw new SQLiteException(String.format("Error adding container \"%s\" to the inventory", barcode));
    }

    private long addPreloadLocation(@NonNull String barcode, @NonNull String description) {
        if (isSaving())
            return -1;
        ContentValues newLocation = new ContentValues();
        newLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
        newLocation.put(PreloadInventoryDatabase.TAGS, "");
        newLocation.put(PreloadInventoryDatabase.DESCRIPTION, description);

        long rowID = db.insert(PreloadedLocationTable.NAME, null, newLocation);

        if (rowID == -1) return -1;

        changedSinceLastArchive = true;
        //Log.v(TAG, "Added location \"" + barcode + "\" to the inventory");
        return rowID;
    }

    void randomScan() {
    }

    void vibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    class LocationViewHolder extends RecyclerView.ViewHolder {
        private TextView locationDescriptionTextView;
        private long id = -1;
        private String barcode;
        private String description;
        boolean isSelected = false;
        boolean isPreloaded = false;

        public long getId() {
            return id;
        }

        public String getBarcode() { return barcode; }

        public String getDescription() { return description; }

        public boolean isSelected() {
            return isSelected;
        }

        public boolean isPreloaded() { return isPreloaded; }

        LocationViewHolder(final View itemView) {
            super(itemView);
            locationDescriptionTextView = itemView.findViewById(R.id.location_description);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scanBarcode(barcode);
                }
            });
        }

        void bindViews(Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndex("_id"));
            isPreloaded = cursor.getInt(cursor.getColumnIndex("is_preloaded")) != 0;
            barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            description = cursor.getString(cursor.getColumnIndex("description"));
            isSelected = getAdapterPosition() == locationRecyclerView.getSelectedItem();

            if (isSelected) {
                selectedLocationId = id;
                selectedLocationBarcode = barcode;
                selectedLocationIsPreloaded = isPreloaded;
                initItemLayout();
            }

            locationDescriptionTextView.setText(isPreloaded ? description : barcode);
            itemView.setBackgroundColor(isPreloaded ? (isSelected ? SELECTED_LOCATION_BACKGROUND_COLOR : DESELECTED_LOCATION_BACKGROUND_COLOR) : (isSelected ? selectedScannedLocationBackgroundColor : deselectedScannedLocationBackgroundColor));
            locationDescriptionTextView.setTextColor(isPreloaded ? (isSelected ? SELECTED_LOCATION_TEXT_COLOR : deselectedLocationTextColor) : (isSelected ? selectedScannedLocationTextColor : deselectedScannedLocationTextColor));
            locationDescriptionTextView.setVisibility(View.VISIBLE);
        }
    }

    SaveToFileTask initSaveTask() {
        SparseArray<Object> tempArgs = new SparseArray<>(2);
        tempArgs.put(SaveToFileTask.ArgKeys.DATABASE, db);
        tempArgs.put(SaveToFileTask.ArgKeys.OUTPUT_FILE, outputFile);
        SaveToFileTask.setArgs(tempArgs);
        SaveToFileTask.mActivity = new WeakReference<>(this);
        return getSaveTask();
    }

    boolean isSaving() {
        Loader saveTask = getLoaderManager().getLoader(SAVE_TASK_ID);
        return saveTask != null && saveTask.isStarted();
    }

    SaveToFileTask getSaveTask() {
        return (SaveToFileTask) getLoaderManager().<SparseArray<Object>>getLoader(SAVE_TASK_ID);
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        private static final int PRELOADED_ITEM = 1;
        private static final int PRELOADED_CASE_CONTAINER = 2;
        private static final int PRELOADED_BULK_CONTAINER = 3;
        private static final int SCANNED_ITEM = 4;
        private TextView textView1;
        private View dividerView;
        private TextView textView2;
        private ImageButton expandedMenuButton;
        private long id;
        private long scannedItemId;
        private long scannedLocationId;
        private long preloadLocationId;
        private long preloadItemId;
        private long preloadContainerId;
        private String barcode;
        private String caseNumber;
        private String itemNumber;
        private String packaging;
        private String description;
        private String tags;
        private String dateTime;
        private int viewType;

        public long getId() { return id; }
        public long getScannedItemId() { return scannedItemId; }
        public long getScannedLocationId() { return scannedLocationId; }
        public long getPreloadLocationId() { return preloadLocationId; }
        public long getPreloadItemId() { return preloadItemId; }
        public long getPreloadContainerId() { return preloadContainerId; }
        public String getBarcode() { return barcode; }
        public String getCaseNumber() { return caseNumber; }
        public String getItemNumber() { return itemNumber; }
        public String getPackaging() { return packaging; }
        public String getTags() { return tags; }
        public String getDescription() { return description; }
        public String getDateTime() { return dateTime; }
        public int getViewType() { return viewType; }

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
                            if (getScannedItemId() < 0) {
                                throw new IllegalStateException("A preloaded item's 'Remove item' menu option was clicked. A preloaded item cannot be individually removed by the user");
                            }

                            if (isSaving()) {
                                Toast.makeText(PreloadInventoryActivity.this, "Cannot edit list while saving", Toast.LENGTH_SHORT).show();
                                return true;
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(PreloadInventoryActivity.this);
                            builder.setCancelable(true);
                            builder.setTitle("Remove item");
                            builder.setMessage(String.format("Are you sure you want to remove barcode \"%s\"?", getBarcode()));
                            builder.setNegativeButton("no", null);
                            builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    db.delete(ScannedItemTable.NAME, ScannedItemTable.Keys.ID + " = ?", new String[]{String.valueOf(scannedItemId)});
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
            long scannedItemId = cursor.getLong(cursor.getColumnIndex("scanned_item_id"));
            long scannedLocationId = cursor.getLong(cursor.getColumnIndex("scanned_location_id"));
            long preloadLocationId = cursor.getLong(cursor.getColumnIndex("preload_location_id"));
            long preloadItemId = cursor.getLong(cursor.getColumnIndex("preload_item_id"));
            long preloadContainerId = cursor.getLong(cursor.getColumnIndex("preload_container_id"));
            String barcode = cursor.getString(cursor.getColumnIndex("barcode"));
            String caseNumber = cursor.getString(cursor.getColumnIndex("case_number"));
            String itemNumber = cursor.getString(cursor.getColumnIndex("item_number"));
            String packaging = cursor.getString(cursor.getColumnIndex("packaging"));
            String description = cursor.getString(cursor.getColumnIndex("description"));
            String tags = cursor.getString(cursor.getColumnIndex("tags"));
            String dateTime = cursor.getString(cursor.getColumnIndex("date_time"));

            if (scannedItemId < 0 && scannedLocationId < 0 && preloadLocationId >= 0) {
                expandedMenuButton.setVisibility(View.INVISIBLE);
                if (preloadItemId != -1) {
                    this.scannedItemId = -1;
                    this.scannedLocationId = -1;
                    this.preloadLocationId = preloadLocationId;
                    this.preloadItemId = preloadItemId;
                    this.preloadContainerId = -1;
                    this.barcode = barcode;
                    this.caseNumber = caseNumber;
                    this.itemNumber = itemNumber;
                    this.packaging = packaging;
                    this.description = description;
                    this.tags = tags;
                    this.dateTime = null;
                    viewType = PRELOADED_ITEM;

                    textView1.setText(getString(R.string.items_number_format_string, caseNumber, itemNumber));
                    textView1.setVisibility(View.VISIBLE);
                    dividerView.setVisibility(View.VISIBLE);
                    textView2.setText(packaging);
                    textView2.setVisibility(View.VISIBLE);
                    //todo finish
                } else if (preloadContainerId >= 0) {
                    if (caseNumber == null) {
                        this.scannedItemId = -1;
                        this.scannedLocationId = -1;
                        this.preloadLocationId = preloadLocationId;
                        this.preloadItemId = -1;
                        this.preloadContainerId = preloadContainerId;
                        this.barcode = barcode;
                        this.caseNumber = null;
                        this.itemNumber = null;
                        this.packaging = null;
                        this.description = description;
                        this.tags = tags;
                        this.dateTime = null;
                        viewType = PRELOADED_BULK_CONTAINER;

                        textView1.setText(description);
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setText("-");
                        textView2.setVisibility(View.VISIBLE);
                        //todo finish
                    } else {
                        this.scannedItemId = -1;
                        this.scannedLocationId = -1;
                        this.preloadLocationId = preloadLocationId;
                        this.preloadItemId = -1;
                        this.preloadContainerId = preloadContainerId;
                        this.barcode = barcode;
                        this.caseNumber = caseNumber;
                        this.itemNumber = null;
                        this.packaging = null;
                        this.description = description;
                        this.tags = tags;
                        this.dateTime = null;
                        viewType = PRELOADED_CASE_CONTAINER;

                        textView1.setText(caseNumber);
                        textView1.setVisibility(View.VISIBLE);
                        dividerView.setVisibility(View.VISIBLE);
                        textView2.setText(description);
                        textView2.setVisibility(View.VISIBLE);
                        //todo finish
                    }
                }
            } else {
                this.scannedItemId = scannedItemId;
                this.scannedLocationId = scannedLocationId;
                this.preloadLocationId = -1;
                this.preloadItemId = -1;
                this.preloadContainerId = -1;
                this.barcode = barcode;
                this.caseNumber = null;
                this.itemNumber = null;
                this.packaging = null;
                this.description = null;
                this.tags = tags;
                this.dateTime = dateTime;
                viewType = SCANNED_ITEM;

                textView1.setText(barcode);
                textView1.setVisibility(View.VISIBLE);
                dividerView.setVisibility(View.VISIBLE);
                textView2.setVisibility(View.INVISIBLE);
                expandedMenuButton.setVisibility(View.VISIBLE);
                //todo finish
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (isSaving()) {
                            getSaveTask().cancelLoadInBackground();
                            postSave();
                        } else {
                            preSave();
                            archiveDatabase();
                            (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                            initSaveTask().forceLoad();
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
            if (iScanner != null) {
                try {
                    iScanner.aDecodeGetResult(mDecodeResult);
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
        if (isSaving()) {
            vibrate(300);
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }

        new AsyncTask<SparseArray<Object>, Void, SparseArray<Object>>() {
            private final String TAG = this.getClass().getSimpleName();

            class ParameterKeys {
                static final int ROW_ID = 0;
                static final int RESULT_TYPE = 1;
                static final int BARCODE = 2;
                static final int REFRESH_LIST = 3;
            }

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

                    if (isSaving())
                        return null;

                    SparseArray<Object> results = new SparseArray<>(4);
                    results.append(ResultKeys.ROW_ID, db.insert(ScannedLocationTable.NAME, null, newScannedLocation));
                    results.append(ResultKeys.RESULT_TYPE, isPreloaded ? ResultValue.PRELOADED_LOCATION : ResultValue.NON_PRELOADED_LOCATION);
                    results.append(ResultKeys.BARCODE, barcode);
                    results.append(ResultKeys.REFRESH_LIST, refreshLocations);
                    return results;
                } else if (isItem(barcode) || isContainer(barcode)) {
                    if (selectedLocationBarcode == null) {
                        SparseArray<Object> results = new SparseArray<>(1);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.NO_LOCATION);
                        return results;
                    }
                    boolean refreshList;
                    boolean isPreloaded;
                    long scannedLocationId;
                    long preloadedLocationId = selectedLocationIsPreloaded ? selectedLocationId : -1;
                    long preloadedItemId = -1;
                    long preloadedContainerId = -1;

                    GET_LAST_ID_OF_LOCATION_BARCODE_STATEMENT.bindString(1, selectedLocationBarcode);
                    scannedLocationId = GET_LAST_ID_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();

                    GET_DUPLICATES_OF_SCANNED_ITEM_IN_LOCATION_STATEMENT.bindString(1, barcode);
                    GET_DUPLICATES_OF_SCANNED_ITEM_IN_LOCATION_STATEMENT.bindString(2, selectedLocationBarcode);
                    if (GET_DUPLICATES_OF_SCANNED_ITEM_IN_LOCATION_STATEMENT.simpleQueryForLong() > 0) {
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

                    if (isSaving())
                        return null;

                    SparseArray<Object> results = new SparseArray<>(4);
                    results.append(ResultKeys.ROW_ID, db.insert(ScannedItemTable.NAME, null, newScannedItem));
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
                            changedSinceLastArchive = true;
                            if (refreshList)
                                asyncRefreshLocationsScrollToLocation(barcode);
                            else
                                asyncScrollToLocation(barcode);
                        }
                        break;
                    case ResultValue.PRELOADED_ITEM: case ResultValue.PRELOADED_CONTAINER:
                        if (rowId < 0) {
                            vibrate(300);
                            Log.e(TAG, String.format("Error adding location \"%s\" to the list", barcode));
                            throw new SQLiteException(String.format("Error adding location \"%s\" to the list", barcode));
                        } else
                            asyncScrollToItem(barcode);
                        break;
                    case ResultValue.NON_PRELOADED_ITEM: case ResultValue.NON_PRELOADED_CONTAINER:
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
        }.execute();
        //todo finish
    }

    static class SaveToFileTask extends AsyncTaskLoader<SparseArray<Object>> {
        private static SparseArray mArgs;
        private static WeakReference<PreloadInventoryActivity> mActivity;
        private SQLiteDatabase db;
        private File outputFile;

        public SaveToFileTask(Context context) {
            super(context);
        }

        public static void setArgs(SparseArray<Object> mArgs) {
            SaveToFileTask.mArgs = mArgs;
        }

        public static void setActivity(WeakReference<PreloadInventoryActivity> mActivity) {
            SaveToFileTask.mActivity = mActivity;
        }

        @Override
        public SparseArray<Object> loadInBackground() {
            db = (SQLiteDatabase) mArgs.get(ArgKeys.DATABASE);
            outputFile = (File) mArgs.get(ArgKeys.OUTPUT_FILE);

            Cursor itemCursor = db.rawQuery("SELECT " + ScannedItemTable.Keys.BARCODE + ", " + ScannedItemTable.Keys.LOCATION_ID + ", " + ScannedItemTable.Keys.DATE_TIME + " FROM " + ScannedItemTable.NAME + " ORDER BY " + ScannedItemTable.Keys.ID + " ASC;",null);
            int itemBarcodeIndex = itemCursor.getColumnIndex(PreloadInventoryDatabase.BARCODE);
            int itemLocationIdIndex = itemCursor.getColumnIndex(PreloadInventoryDatabase.LOCATION_ID);
            int itemDateTimeIndex = itemCursor.getColumnIndex(PreloadInventoryDatabase.DATE_TIME);

            Cursor locationCursor = db.rawQuery("SELECT " + ScannedLocationTable.Keys.ID + ", " + ScannedLocationTable.Keys.BARCODE + ", " + ScannedLocationTable.Keys.DATE_TIME + " FROM " + ScannedLocationTable.NAME + " ORDER BY " + ScannedLocationTable.Keys.ID + " ASC;", null);

            int locationIdIndex = locationCursor.getColumnIndex(PreloadInventoryDatabase.ID);
            int locationBarcodeIndex = locationCursor.getColumnIndex(PreloadInventoryDatabase.BARCODE);
            int locationDateTimeIndex = locationCursor.getColumnIndex(PreloadInventoryDatabase.DATE_TIME);

            if (!itemCursor.moveToFirst()) {
                SparseArray<Object> results = new SparseArray<>(2);
                results.append(ResultKeys.RESULT_TYPE, ResultValue.CURSOR_ERROR);
                results.append(ResultKeys.MESSAGE, "There was an error moving item cursor to first position");
                return results;
            }

            if (!locationCursor.moveToFirst()) {
                SparseArray<Object> results = new SparseArray<>(2);
                results.append(ResultKeys.RESULT_TYPE, ResultValue.CURSOR_ERROR);
                results.append(ResultKeys.MESSAGE, "There was an error moving location cursor to first position");
                return results;
            }

            int lineIndex = -1;
            int progress = 0;
            int maxProgress = mActivity.get().progressBar.getMax();

            try {
                //noinspection ResultOfMethodCallIgnored
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("tmp", ".txt", OUTPUT_PATH);
                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);

                lineIndex = 0;
                int tempLocation;
                int itemIndex = 0;
                int totalItemCount = itemCursor.getCount() + 1;
                int currentLocationId = -1;

                //noinspection StringConcatenationMissingWhitespace
                String tempText = BuildConfig.APPLICATION_ID.split("\\.")[2] + "|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n";
                printStream.print(tempText);
                printStream.flush();
                lineIndex++;

                while (!itemCursor.isAfterLast()) {
                    if (isLoadInBackgroundCanceled()) {
                        SparseArray<Object> results = new SparseArray<>(2);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.SAVE_CANCELED);
                        results.append(ResultKeys.MESSAGE, "Save canceled");
                        return results;
                    }

                    final int tempProgress = (int) (((((float) itemIndex) / totalItemCount) / 1.5) * maxProgress);
                    if (progress != tempProgress) {
                        if (mActivity.get() != null) {
                            mActivity.get().runOnUiThread(new Runnable() {
                                @Override
                                public void run() { mActivity.get().progressBar.setProgress(tempProgress); }
                            });
                        }
                        progress = tempProgress;
                    }

                    tempLocation = itemCursor.getInt(itemLocationIdIndex);
                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                SparseArray<Object> results = new SparseArray<>(2);
                                results.append(ResultKeys.RESULT_TYPE, ResultValue.LOCATION_NOT_FOUND);
                                results.append(ResultKeys.MESSAGE, String.format("Location of \"%s\" does not exist", itemCursor.getString(itemBarcodeIndex).trim()));
                                return results;
                            }
                        }

                        printStream.print(locationCursor.getString(locationBarcodeIndex) + "|" + locationCursor.getString(locationDateTimeIndex) + "\r\n");

                        printStream.flush();
                        lineIndex++;
                    }

                    tempText = String.format("%s|%s\r\n", itemCursor.getString(itemBarcodeIndex), itemCursor.getString(itemDateTimeIndex));
                    printStream.print(tempText);

                    printStream.flush();
                    itemCursor.moveToNext();
                    lineIndex++;
                    itemIndex++;
                }

                lineIndex = -1;
                printStream.close();

                itemCursor.moveToFirst();
                locationCursor.moveToFirst();

                BufferedReader br = new BufferedReader(new FileReader(TEMP_OUTPUT_FILE));
                String line;
                currentLocationId = -1;
                itemIndex = 0;

                br.readLine();
                lineIndex = 1;

                while (!itemCursor.isAfterLast()) {
                    if (isLoadInBackgroundCanceled()){
                        SparseArray<Object> results = new SparseArray<>(2);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.SAVE_CANCELED);
                        results.append(ResultKeys.MESSAGE, "Save canceled");
                        return results;
                    }

                    final int tempProgress = (int) (((((float) itemIndex / totalItemCount) / 3) + (2 / 3f)) * maxProgress);
                    if (progress != tempProgress) {
                        if (mActivity.get() != null) {
                            mActivity.get().runOnUiThread(new Runnable() {
                                @Override
                                public void run() { mActivity.get().progressBar.setProgress(tempProgress); }
                            });
                        }
                        progress = tempProgress;
                    }

                    line = br.readLine();
                    tempLocation = itemCursor.getInt(itemLocationIdIndex);

                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                SparseArray<Object> results = new SparseArray<>(2);
                                results.append(ResultKeys.RESULT_TYPE, ResultValue.LOCATION_NOT_FOUND);
                                results.append(ResultKeys.MESSAGE, String.format("Location of \"%s\" does not exist", itemCursor.getString(itemBarcodeIndex).trim()));
                                return results;
                            }
                        }

                        tempText = locationCursor.getString(locationBarcodeIndex) + "|" + locationCursor.getString(locationDateTimeIndex);
                        if (!tempText.equals(line)) {
                            Log.e(TAG, "Error at line " + lineIndex + " of file output\n" +
                                    "\tExpected String: " + tempText + "\n" +
                                    "\tString in file: " + line);

                            SparseArray<Object> results = new SparseArray<>(2);
                            results.append(ResultKeys.RESULT_TYPE, ResultValue.VERIFICATION_FAILED);
                            results.append(ResultKeys.MESSAGE, "There was a problem verifying the output file");
                            return results;

                        }

                        line = br.readLine();
                        lineIndex++;
                    }

                    tempText = itemCursor.getString(itemBarcodeIndex) + "|" + itemCursor.getString(itemDateTimeIndex);
                    if (!tempText.equals(line)) {
                        Log.e(TAG, "Error at line " + lineIndex + " of file output\n" +
                                "Expected String: " + tempText + "\n" +
                                "String in file: " + line);

                        SparseArray<Object> results = new SparseArray<>(2);
                        results.append(ResultKeys.RESULT_TYPE, ResultValue.VERIFICATION_FAILED);
                        results.append(ResultKeys.MESSAGE, "There was a problem verifying the output file");
                        return results;
                    }
                    itemCursor.moveToNext();
                    lineIndex++;
                    itemIndex++;
                }

                lineIndex = -1;

                itemCursor.close();
                locationCursor.close();
                br.close();

                if (outputFile.exists() && !outputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, "Could not delete existing output file");

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.DELETE_FAILED);
                    results.append(ResultKeys.MESSAGE, "Could not delete existing output file");
                    return results;
                }

                if (!TEMP_OUTPUT_FILE.renameTo(outputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, String.format("Could not rename temp file to \"%s\"", outputFile.getName()));

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.RENAME_FAILED);
                    results.append(ResultKeys.MESSAGE, String.format("Could not rename temp file to \"%s\"", outputFile.getName()));
                    return results;
                }
            } catch (FileNotFoundException e){
                if (lineIndex == -1) {
                    Log.e(TAG, "FileNotFoundException occurred outside of while loops: " + e.getMessage());
                    e.printStackTrace();

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.FILE_NOT_FOUND);
                    results.append(ResultKeys.MESSAGE, "FileNotFoundException occurred while saving");
                    return results;
                } else {
                    Log.e(TAG, "FileNotFoundException occurred at line " + lineIndex + " in file while saving: " + e.getMessage());
                    e.printStackTrace();

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.FILE_NOT_FOUND);
                    results.append(ResultKeys.MESSAGE, String.format(Locale.US, "FileNotFoundException occurred at line %d in file while saving", lineIndex));
                    return results;
                }
            } catch (IOException e){
                if (lineIndex == -1) {
                    Log.e(TAG, "IOException occurred outside of while loops: " + e.getMessage());
                    e.printStackTrace();

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.IO_EXCEPTION);
                    results.append(ResultKeys.MESSAGE, "IOException occurred while saving");
                    return results;
                } else {
                    Log.e(TAG, "IOException occurred at line " + lineIndex + " in file while saving: " + e.getMessage());
                    e.printStackTrace();

                    SparseArray<Object> results = new SparseArray<>(2);
                    results.append(ResultKeys.RESULT_TYPE, ResultValue.IO_EXCEPTION);
                    results.append(ResultKeys.MESSAGE, String.format(Locale.US, "IOException occurred at line %d in file while saving", lineIndex));
                    return results;
                }
            }

            SparseArray<Object> results = new SparseArray<>(2);
            results.append(ResultKeys.RESULT_TYPE, ResultValue.SAVED_TO_FILE);
            results.append(ResultKeys.MESSAGE, "Saved to file");
            return results;
        }

        @Override
        public void onCanceled(SparseArray<Object> data) {
            super.onCanceled(data);
        }

        static class ArgKeys {
            static final int DATABASE = 0;
            static final int OUTPUT_FILE = 1;
        }

        static class ResultKeys {
            static final int RESULT_TYPE = 0;
            static final int MESSAGE = 1;
        }

        static class ResultValue {
            static final int IO_EXCEPTION = -8;
            static final int FILE_NOT_FOUND = -7;
            static final int RENAME_FAILED = -6;
            static final int DELETE_FAILED = -5;
            static final int VERIFICATION_FAILED = -4;
            static final int LOCATION_NOT_FOUND = -3;
            static final int CURSOR_ERROR = -2;
            static final int SAVE_CANCELED = -1;
            static final int SAVED_TO_FILE = 1;
        }
    }
}