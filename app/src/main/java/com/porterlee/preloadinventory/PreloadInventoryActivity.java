package com.porterlee.preloadinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Color;
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
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
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
import static com.porterlee.preloadinventory.MainActivity.MAX_ITEM_HISTORY_INCREASE;

public class PreloadInventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    private static final int selectedLocationBackgroundColor = 0xFF536DFE;
    private static final int selectedLocationTextColor = Color.WHITE;
    private static final int deselectedLocationBackgroundColor = Color.WHITE;
    private static final int deselectedLocationTextColor = Color.BLACK;
    private static final int selectedScannedLocationBackgroundColor = 0xFF009688;
    private static final int selectedScannedLocationTextColor = Color.WHITE;
    private static final int deselectedScannedLocationBackgroundColor = Color.WHITE;
    private static final int deselectedScannedLocationTextColor = 0xFF009688;
    private int maxProgress;
    private SharedPreferences sharedPreferences;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT;
    private SQLiteStatement GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT;
    private SQLiteStatement GET_LOCATION_COUNT_STATEMENT;
    private Vibrator vibrator;
    private File inputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Integer, String> saveTask;
    private AsyncTask<Void, Integer, String> readTask;
    private int maxLocationHistory = MAX_ITEM_HISTORY_INCREASE;
    private int maxItemHistory = MAX_ITEM_HISTORY_INCREASE;
    private ScanResultReceiver resultReciever;
    private int selectedLocation;
    //private long currentPreloadLocationId;
    private int locationCount;
    private int currentNotMisplacedScannedItemCount;
    private int currentScannedAndPreloadedItemCount;
    private int currentPreloadedItemCount;
    private int scannedItemCount;
    private int currentMisplacedScannedItemCount;
    private RecyclerView locationRecyclerView;
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter locationRecyclerAdapter;
    private RecyclerView.Adapter itemRecyclerAdapter;
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (locationRecyclerAdapter.getItemCount() > selectedLocation + 1) {
                setSelectedLocation(selectedLocation + 1);
            } else if (maxItemHistory == locationRecyclerAdapter.getItemCount()) {
                maxItemHistory += MAX_ITEM_HISTORY_INCREASE;
                setSelectedLocation(selectedLocation + 1);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedLocation <= 0) {
                setSelectedLocation(0);
            } else {
                setSelectedLocation(selectedLocation - 1);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || super.onKeyUp(keyCode, event);
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

        archiveDirectory = new File(getFilesDir() + "/" + PreloadInventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        inputFile = new File(INPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        inputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY + "/" + PreloadInventoryDatabase.FILE_NAME);
        //databaseFile = new File(inputFile.getParent() + "/test.db");
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
        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        //db.execSQL("DROP TABLE IF EXISTS " + ScannedItemTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + ScannedLocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedLocationTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + PreloadedItemTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + PreloadedContainerTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedContainerTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + PreloadedLocationTable.NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadedLocationTable.TABLE_CREATION);

        long locationId = readFileIntoPreloadDatabase();

        //LAST_SCANNED_ITEM_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.ID + " = ( SELECT " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " FROM " + ScannedItemTable.NAME + " ORDER BY " + ScannedItemTable.Keys.ID + " DESC LIMIT 1 );");
        //LAST_SCANNED_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedLocationTable.Keys.BARCODE + " FROM " + PreloadedLocationTable.NAME + " WHERE " + PreloadedLocationTable.Keys.ID + " = ( SELECT " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " FROM " + ScannedLocationTable.NAME + " ORDER BY " + ScannedLocationTable.Keys.ID + " DESC LIMIT 1 );");
        GET_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + PreloadInventoryDatabase.BARCODE + " FROM " + PreloadedLocationTable.NAME + " UNION SELECT " + PreloadInventoryDatabase.BARCODE + " FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " < 0 );");
        GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT DISTINCT " + ScannedItemTable.Keys.BARCODE + " FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " IN ( SELECT " + PreloadedItemTable.Keys.ID + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? ) OR " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " IN ( SELECT " + PreloadedContainerTable.Keys.ID + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? ) );");
        GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + ScannedItemTable.Keys.BARCODE + " FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedContainerTable.Keys.BARCODE + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? );");
        GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.LOCATION_ID + " = ?;");
        //GET_SCANNED_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedLocationTable.NAME + ";");
        //GET_PRELOADED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + PreloadedItemTable.NAME + ";");
        GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM ( SELECT " + PreloadedItemTable.Keys.BARCODE + " FROM " + PreloadedItemTable.NAME + " WHERE " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? UNION SELECT " + PreloadedContainerTable.Keys.BARCODE + " FROM " + PreloadedContainerTable.NAME + " WHERE " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " = ? );");
        //GET_SCANNED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + ";");
        GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + " WHERE " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " = ? AND " + ScannedItemTable.Keys.PRELOAD_ITEM_ID +" < 0 AND " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " < 0;");
        GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadedLocationTable.Keys.ID + " FROM " + PreloadedLocationTable.NAME + " WHERE " + PreloadedLocationTable.Keys.BARCODE + " = ? LIMIT 1;");

        /*Cursor cursor = db.rawQuery("SELECT " + PreloadedLocationTable.Keys.ID + " FROM " + PreloadedLocationTable.NAME + " ORDER BY " + PreloadedLocationTable.Keys.ID + " ASC LIMIT 1;", null);
        cursor.moveToFirst();
        if (cursor.getCount() > 0)
            currentPreloadLocationId = cursor.getLong(cursor.getColumnIndex(PreloadInventoryDatabase.ID));
        else
            currentPreloadLocationId = -1;
        cursor.close();*/
        selectedLocation = 0;
        refreshPreloadedLocationCount();

        progressBar = findViewById(R.id.progress_saving);
        maxProgress = progressBar.getMax();

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { /*randomScan();*/ }
        });

        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return ((LocationViewHolder) locationRecyclerView.findViewHolderForAdapterPosition(i)).getId();
            }

            @Override
            public int getItemCount() {
                int count = locationCount;
                count = Math.min(count, maxLocationHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new LocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_location_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                Cursor cursor = db.rawQuery("SELECT " + PreloadInventoryDatabase.ID + ", " + PreloadInventoryDatabase.BARCODE + ", " + PreloadInventoryDatabase.DESCRIPTION + " FROM ( SELECT " + PreloadedLocationTable.Keys.ID + ", " + PreloadedLocationTable.Keys.BARCODE + ", " + PreloadedLocationTable.Keys.DESCRIPTION + ", 1 AS filter FROM " + PreloadedLocationTable.NAME + " UNION SELECT " + ScannedLocationTable.Keys.ID + ", " + ScannedLocationTable.Keys.BARCODE + ", NULL AS " + PreloadInventoryDatabase.DESCRIPTION + ", 0 AS filter FROM " + ScannedLocationTable.NAME + " WHERE " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " = -1 ) ORDER BY filter, " + PreloadInventoryDatabase.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                cursor.moveToFirst();
                long id = cursor.getLong(cursor.getColumnIndex(PreloadInventoryDatabase.ID));
                String barcode = cursor.getString(cursor.getColumnIndex(PreloadInventoryDatabase.BARCODE));
                String description = cursor.getString(cursor.getColumnIndex(PreloadInventoryDatabase.DESCRIPTION));
                cursor.close();
                ((LocationViewHolder) holder).bindViews(id, barcode, description, position == selectedLocation, description != null);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        locationRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && locationRecyclerAdapter.getItemCount() >= maxItemHistory) {
                    maxItemHistory += MAX_ITEM_HISTORY_INCREASE;
                    locationRecyclerAdapter.notifyDataSetChanged();
                }
            }
        });
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator locationRecyclerAnimator = new DefaultItemAnimator();
        locationRecyclerAnimator.setAddDuration(100);
        locationRecyclerAnimator.setChangeDuration(100);
        locationRecyclerAnimator.setMoveDuration(100);
        locationRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(locationRecyclerAnimator);

        refreshCurrentPreloadedItemCount(locationId, true);
        //scannedItemCount = getScannedItemCount();
        refreshCurrentNotMisplacedScannedItemCount(locationId, true);
        refreshCurrentScannedAndPreloadedItemCount(locationId, true);
        refreshCurrentMisplacedScannedItemCount(locationId, true);
        //lastLocationBarcode = getLastLocationBarcode();

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return ((ItemViewHolder) itemRecyclerView.findViewHolderForAdapterPosition(i)).getScannedItemId();
            }

            @Override
            public int getItemCount() {
                int count = currentScannedAndPreloadedItemCount;
                count = Math.min(count, maxItemHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_inventory_item_layout, parent, false));
            }

            private static final String ITEM_QUERY_IN_PRELOADED_LOCATION = "SELECT scanned_item_id, scanned_location_id, preload_location_id, preload_item_id, preload_container_id, barcode, case_number, item_number, packaging, description, tags, date_time FROM ( SELECT " + ScannedItemTable.Keys.ID + " AS scanned_item_id, " + ScannedItemTable.Keys.LOCATION_ID + " AS scanned_location_id, " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " AS preload_item_id, " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " AS preload_container_id, " + ScannedItemTable.Keys.BARCODE + " AS barcode, NULL AS case_number, NULL AS item_number, NULL AS packaging, NULL AS description, " + ScannedItemTable.Keys.TAGS + " AS tags, " + ScannedItemTable.Keys.DATE_TIME + " AS date_time, 0 AS format FROM " + ScannedItemTable.NAME + " WHERE preload_item_id < 0 AND preload_container_id < 0 AND preload_location_id = ? UNION SELECT -1 AS scanned_item_id, -1 AS scanned_location_id, " + PreloadedContainerTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, -1 AS preload_item_id, " + PreloadedContainerTable.Keys.ID + " AS preload_container_id, " + PreloadedContainerTable.Keys.BARCODE + " AS barcode, " + PreloadedContainerTable.Keys.CASE_NUMBER + " AS case_number, NULL AS item_number, NULL AS packaging, " + PreloadedContainerTable.Keys.DESCRIPTION + " AS description, NULL AS tags, NULL AS date_time, 1 AS format FROM " + PreloadedContainerTable.NAME + " WHERE preload_location_id = ? UNION SELECT -1 AS scanned_item_id, -1 AS scanned_location_id, " + PreloadedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + PreloadedItemTable.Keys.ID + " AS preload_item_id, -1 AS preload_container_id, " + PreloadedItemTable.Keys.BARCODE + " AS barcode, " + PreloadedItemTable.Keys.CASE_NUMBER + " AS case_number, " + PreloadedItemTable.Keys.ITEM_NUMBER + " AS item_number, " + PreloadedItemTable.Keys.PACKAGE + " AS packaging, " + PreloadedItemTable.Keys.DESCRIPTION + " AS description, NULL AS tags, NULL AS date_time, 0 AS format FROM " + PreloadedItemTable.NAME + " WHERE preload_location_id = ? ) ORDER BY format, CASE, scanned_item_id DESC, preload_item_id DESC, preload_container_id DESC LIMIT 1 OFFSET ?;";
            private static final String ITEM_QUERY_IN_SCANNED_LOCATION = "SELECT " + ScannedItemTable.Keys.ID + " AS scanned_item_id, " + ScannedItemTable.Keys.LOCATION_ID + " AS scanned_location_id, " + ScannedItemTable.Keys.PRELOAD_LOCATION_ID + " AS preload_location_id, " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " AS preload_item_id, " + ScannedItemTable.Keys.PRELOAD_CONTAINER_ID + " AS preload_container_id, " + ScannedItemTable.Keys.BARCODE + " AS barcode, NULL AS case_number, NULL AS item_number, NULL AS packaging, NULL AS description, " + ScannedItemTable.Keys.TAGS + " AS tags, " + ScannedItemTable.Keys.DATE_TIME + " AS date_time, 0 AS format FROM " + ScannedItemTable.NAME + " WHERE preload_item_id < 0 AND preload_container_id < 0 AND scanned_location_id = ? ORDER BY format, scanned_item_id DESC, preload_item_id DESC, preload_container_id DESC LIMIT 1 OFFSET ?;";

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                long currentLocationId = getCurrentPreloadLocationId();
                Cursor cursor = db.rawQuery(getCurrentIsPreloadLocation() ? ITEM_QUERY_IN_PRELOADED_LOCATION : ITEM_QUERY_IN_SCANNED_LOCATION, getCurrentIsPreloadLocation() ? new String[] {String.valueOf(currentLocationId), String.valueOf(currentLocationId), String.valueOf(currentLocationId), String.valueOf(position)} : new String[] {String.valueOf(currentLocationId), String.valueOf(position)});
                cursor.moveToFirst();
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
                /* this.scannedItemId = scannedItemId;
                 * this.scannedLocationId = scannedLocationId;
                 * this.preloadLocationId = -1;
                 * this.preloadItemId = -1;
                 * this.preloadContainerId = -1;
                 * this.barcode = barcode;
                 * this.caseNumber = null;
                 * this.itemNumber = null;
                 * this.packaging = null;
                 * this.description = null;
                 * this.tags = tags;
                 * this.dateTime = dateTime;
                 */
                cursor.close();
                if (scannedItemId == -1 && scannedLocationId == -1 && preloadLocationId != -1) {
                    if (preloadItemId != -1) {
                        System.out.println("Preloaded Item at index " + position + "-" + barcode);
                        ((ItemViewHolder) holder).bindViewsAsPreloadedItem(preloadLocationId, preloadItemId, barcode, caseNumber, itemNumber, packaging, description);
                    } else if (preloadContainerId != -1) {
                        if (caseNumber == null) {
                            System.out.println("Preloaded Bulk Container at index " + position + "-" + barcode);
                            ((ItemViewHolder) holder).bindViewsAsPreloadedBulkContainer(preloadLocationId, preloadContainerId, barcode, description);
                        } else {
                            System.out.println("Preloaded Case Container at index " + position + "-" + barcode);
                            ((ItemViewHolder) holder).bindViewsAsPreloadedCaseContainer(preloadLocationId, preloadContainerId, barcode, caseNumber, description);
                        }
                    }
                }
                //todo finish
            }

            @Override
            public int getItemViewType(int i) {
                //todo finish
                return 0;
            }
        };
        itemRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && itemRecyclerAdapter.getItemCount() >= maxItemHistory) {
                    //Log.v(TAG, "Scroll state changed to: " + (newState == RecyclerView.SCROLL_STATE_IDLE ? "SCROLL_STATE_IDLE" : (newState == RecyclerView.SCROLL_STATE_DRAGGING ? "SCROLL_STATE_DRAGGING" : "SCROLL_STATE_SETTLING")));

                    maxItemHistory += MAX_ITEM_HISTORY_INCREASE;
                    itemRecyclerAdapter.notifyDataSetChanged();
                }
            }
        });
        itemRecyclerView.setAdapter(itemRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemRecyclerAnimator);
        updateInfo();
    }

    private void setSelectedLocation(int index) {
        if (index == selectedLocation || index < 0 || index >= locationRecyclerAdapter.getItemCount())
            return;

        locationRecyclerAdapter.notifyItemChanged(selectedLocation);
        selectedLocation = index;
        locationRecyclerAdapter.notifyItemChanged(selectedLocation);
        int temp = currentScannedAndPreloadedItemCount;
        refreshCurrentScannedAndPreloadedItemCount(getCurrentPreloadLocationId(), getCurrentIsPreloadLocation());
        if (temp < currentScannedAndPreloadedItemCount) {
            if (temp >= maxItemHistory) {
                itemRecyclerAdapter.notifyItemRangeChanged(0, maxItemHistory);
            } else if (currentScannedAndPreloadedItemCount >= maxItemHistory) {
                itemRecyclerAdapter.notifyItemRangeInserted(temp, maxItemHistory - temp);
                itemRecyclerAdapter.notifyItemRangeChanged(0, temp);
            } else {
                itemRecyclerAdapter.notifyItemRangeInserted(temp, currentScannedAndPreloadedItemCount - temp);
                itemRecyclerAdapter.notifyItemRangeChanged(0, temp);
            }
        } else {
            if (currentScannedAndPreloadedItemCount >= maxItemHistory) {
                itemRecyclerAdapter.notifyItemRangeChanged(0, maxItemHistory);
            } else if (temp >= maxItemHistory) {
                itemRecyclerAdapter.notifyItemRangeRemoved(currentScannedAndPreloadedItemCount, maxItemHistory - currentScannedAndPreloadedItemCount);
                itemRecyclerAdapter.notifyItemRangeChanged(0, currentScannedAndPreloadedItemCount);
            } else {
                itemRecyclerAdapter.notifyItemRangeRemoved(currentScannedAndPreloadedItemCount, temp - currentScannedAndPreloadedItemCount);
                itemRecyclerAdapter.notifyItemRangeChanged(0, currentScannedAndPreloadedItemCount);
            }
        }
        refreshCurrentPreloadedItemCount(getCurrentPreloadLocationId(), getCurrentIsPreloadLocation());
        refreshCurrentNotMisplacedScannedItemCount(getCurrentPreloadLocationId(), getCurrentIsPreloadLocation());
        refreshCurrentMisplacedScannedItemCount(getCurrentPreloadLocationId(), getCurrentIsPreloadLocation());
        updateInfo();
    }

    private long getCurrentPreloadLocationId() {
        return ((LocationViewHolder) locationRecyclerView.findViewHolderForAdapterPosition(selectedLocation)).getId();
    }

    private boolean getCurrentIsPreloadLocation() {
        return ((LocationViewHolder) locationRecyclerView.findViewHolderForAdapterPosition(selectedLocation)).isPreloaded();
    }

    public void refreshCurrentPreloadedItemCount(long locationId, boolean isPreloadedLocation) {
        if (isPreloadedLocation) {
            GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(1, locationId);
            GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(2, locationId);
            currentPreloadedItemCount = (int) GET_PRELOADED_ITEM_COUNT_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            currentPreloadedItemCount = 0;
        }
    }

    private void refreshCurrentMisplacedScannedItemCount(long locationId, boolean isPreloadedLocation) {
        if (isPreloadedLocation) {
            //GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(1, currentPreloadLocationId);
            GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT.bindLong(1, locationId);
            currentMisplacedScannedItemCount = (int) GET_MISPLACED_ITEM_COUNT_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            currentMisplacedScannedItemCount = 0;
        }
    }

    private void refreshCurrentScannedAndPreloadedItemCount(long locationId, boolean isPreloadedLocation) {
        if (isPreloadedLocation) {
            //GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, currentPreloadLocationId);
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(1, locationId);
            //GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, currentPreloadLocationId);
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(2, locationId);
            //GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(3, currentPreloadLocationId);
            GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.bindLong(3, locationId);
            currentScannedAndPreloadedItemCount = (int) GET_SCANNED_AND_PRELOADED_ITEM_COUNT_IN_PRELOADED_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.bindLong(1, locationId);
            currentScannedAndPreloadedItemCount = (int) GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.simpleQueryForLong();
        }
    }

    private void refreshCurrentNotMisplacedScannedItemCount(long locationId, boolean isPreloadedLocation) {
        if (isPreloadedLocation) {
            //GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(1, currentPreloadLocationId);
            GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(1, locationId);
            //GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(2, currentPreloadLocationId);
            GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.bindLong(2, locationId);
            currentNotMisplacedScannedItemCount = (int) GET_SCANNED_ITEM_COUNT_NOT_MISPLACED_IN_LOCATION_STATEMENT.simpleQueryForLong();
        } else {
            GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.bindLong(1, locationId);
            currentScannedAndPreloadedItemCount = (int) GET_ITEM_COUNT_IN_SCANNED_LOCATION_STATEMENT.simpleQueryForLong();
        }
    }

    private void refreshPreloadedLocationCount() {
        locationCount = (int) GET_LOCATION_COUNT_STATEMENT.simpleQueryForLong();
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
        super.onDestroy();

        if (saveTask != null) {
            saveTask.cancel(false);
            saveTask = null;
        }

        if (readTask != null) {
            readTask.cancel(false);
            readTask = null;
        }

        db.close();
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
            case R.id.action_inventory:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
        ((TextView) findViewById(R.id.items_scanned)).setText(getString(R.string.items_scanned_format_string, currentNotMisplacedScannedItemCount, currentPreloadedItemCount));
        ((TextView) findViewById(R.id.items_misplaced)).setText(String.valueOf(currentMisplacedScannedItemCount));
    }

    public long readFileIntoPreloadDatabase() {
        long firstLocationId = 0;
        try {
            LineNumberReader lineReader = new LineNumberReader(new FileReader(PreloadInventoryActivity.INPUT_PATH.getAbsolutePath() + "/" + "data.txt"));
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
                        firstLocationId = currentLocationId;
                        currentLocationBarcode = elements[1];
                        if (currentLocationId == -1) {
                            Log.e(TAG, "");
                            Toast.makeText(this, "Unexpected error at line " + lineReader.getLineNumber() + " of file input: location does not match previously defined location", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, PreloadLocationsActivity.class));
                            finish();
                            lineReader.close();
                            return -1;
                        }
                    } else if (elements.length == 3 && isContainer(elements[1])) {
                        //System.out.println("Bulk-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadContainer(currentLocationId, elements[1], elements[2], null);
                        } else {
                            Log.e(TAG, "");
                            Toast.makeText(this, "Unexpected error at line " + lineReader.getLineNumber() + " of file input: location does not match previously defined location", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, PreloadLocationsActivity.class));
                            finish();
                            lineReader.close();
                            return -1;
                        }
                    } else if (elements.length == 4 && isContainer(elements[1])) {
                        //System.out.println("Case-Container: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', description\'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadContainer(currentLocationId, elements[1], elements[2], elements[3]);
                        } else {
                            Log.e(TAG, "");
                            Toast.makeText(this, "Unexpected error at line " + lineReader.getLineNumber() + " of file input: location does not match previously defined location", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, PreloadLocationsActivity.class));
                            finish();
                            lineReader.close();
                            return -1;
                        }
                    } else if (elements.length == 6 && isItem(elements[1])) {
                        //System.out.println("Item: location = \'" + elements[0] + "\', barcode = \'" + elements[1] + "\', case-number\'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5]);
                        if (elements[0].equals(currentLocationBarcode)) {
                            addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5]);
                        } else {
                            Log.e(TAG, "");
                            Toast.makeText(this, "Unexpected error at line " + lineReader.getLineNumber() + " of file input: location does not match previously defined location", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, PreloadLocationsActivity.class));
                            finish();
                            lineReader.close();
                            return -1;
                        }
                    } else {
                        //System.out.println("error");
                        if (isItem(elements[1]) || isContainer(elements[1]) || isLocation(elements[1])) {
                            Log.e(TAG, "Error at line " + lineReader.getLineNumber() + " in preload file: incorrect format or number of elements");
                            Toast.makeText(this, "Error at line " + lineReader.getLineNumber() + " in preload file: incorrect format", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Error at line " + lineReader.getLineNumber() + " in preload file: barcode \"" + elements[1] + "\" not recognised");
                            Toast.makeText(this, "Error at line " + lineReader.getLineNumber() + " in preload file: barcode \"" + elements[1] + "\" not recognised", Toast.LENGTH_SHORT).show();
                        }
                        startActivity(new Intent(this, PreloadLocationsActivity.class));
                        finish();
                        lineReader.close();
                        return -1;
                    }
                } else {
                    System.out.println("error");
                }
            }

            lineReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return firstLocationId;
    }

    private void addPreloadItem(long locationId, String barcode, String caseNumber, String itemNumber, String packaging, String description) {
        if (saveTask != null || locationId == -1)
            return;

        ContentValues newPreloadItem = new ContentValues();
        newPreloadItem.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, locationId);
        newPreloadItem.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadItem.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);
        newPreloadItem.put(PreloadInventoryDatabase.ITEM_NUMBER, itemNumber);
        newPreloadItem.put(PreloadInventoryDatabase.PACKAGE, packaging);
        newPreloadItem.put(PreloadInventoryDatabase.DESCRIPTION, description);

        if (db.insert(PreloadedItemTable.NAME, null, newPreloadItem) == -1)
            Log.e(TAG, "Error adding container \"" + barcode + "\" to the inventory");
    }

    private void addPreloadContainer(long locationId, @NonNull String barcode, String description, String caseNumber) {
        if (saveTask != null || locationId == -1)
            return;

        ContentValues newPreloadContainer = new ContentValues();
        newPreloadContainer.put(PreloadInventoryDatabase.BARCODE, barcode);
        newPreloadContainer.put(PreloadInventoryDatabase.DESCRIPTION, description);
        newPreloadContainer.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, locationId);
        newPreloadContainer.put(PreloadInventoryDatabase.CASE_NUMBER, caseNumber);

        if (db.insert(PreloadedContainerTable.NAME, null, newPreloadContainer) == -1)
            Log.e(TAG, "Error adding container \"" + barcode + "\" to the inventory");
    }

    private long addPreloadLocation(String barcode, String description) {
        if (saveTask != null) return -1;
        ContentValues newLocation = new ContentValues();
        newLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
        newLocation.put(PreloadInventoryDatabase.DESCRIPTION, description);

        long rowID = db.insert(PreloadedLocationTable.NAME, null, newLocation);

        if (rowID == -1) return -1;

        changedSinceLastArchive = true;
        //Log.v(TAG, "Added location \"" + barcode + "\" to the inventory");
        locationCount++;
        updateInfo();
        return rowID;
    }

    private void addScannedLocation(String barcode) {
        long preloadLocationId;

        GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.bindString(1, barcode);
        try {
            preloadLocationId = GET_PRELOADED_LOCATION_ID_OF_LOCATION_BARCODE_STATEMENT.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            preloadLocationId = -1;
        }

        if (saveTask != null) return;
        ContentValues newScannedLocation = new ContentValues();
        newScannedLocation.put(PreloadInventoryDatabase.BARCODE, barcode);
        newScannedLocation.put(PreloadInventoryDatabase.PRELOAD_LOCATION_ID, preloadLocationId);
        newScannedLocation.put(PreloadInventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        long rowID = db.insert(ScannedLocationTable.NAME, null, newScannedLocation);

        if (rowID == -1) {
            vibrate(300);
            Log.e(TAG, "Error adding location \"" + barcode + "\" to the list");
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the list", Toast.LENGTH_SHORT).show();
            return;
        }

        changedSinceLastArchive = true;
        locationCount++;
        locationRecyclerAdapter.notifyItemInserted(0);

        if (locationRecyclerAdapter.getItemCount() == maxLocationHistory)
            locationRecyclerAdapter.notifyItemRemoved(locationRecyclerAdapter.getItemCount() - 1);

        locationRecyclerAdapter.notifyItemRangeChanged(0, locationRecyclerAdapter.getItemCount());
        locationRecyclerView.scrollToPosition(0);
        setSelectedLocation(0);
        updateInfo();
    }

    private void vibrate(long millis) {
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

        public long getId() { return id; }

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
                public void onClick(View v) { setSelectedLocation(getLayoutPosition()); }
            });
        }

        void bindViews(long id, String barcode, String description, boolean isSelected, boolean isPreloaded) {
            this.id = id;
            this.barcode = barcode;
            this.description = description;
            this.isSelected = isSelected;
            this.isPreloaded = isPreloaded;

            locationDescriptionTextView.setText(description);
            itemView.setBackgroundColor(isPreloaded ? (isSelected ? selectedLocationBackgroundColor : deselectedLocationBackgroundColor) : (isSelected ? selectedScannedLocationBackgroundColor : deselectedScannedLocationBackgroundColor));
            locationDescriptionTextView.setTextColor(isPreloaded ? (isSelected ? selectedLocationTextColor : deselectedLocationTextColor) : (isSelected ? selectedScannedLocationTextColor : deselectedScannedLocationTextColor));
            //todo add colors for non-preloaded barcode
        }
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

        public long getScannedItemId() {
            return scannedItemId;
        }

        public long getScannedLocationId() {
            return scannedLocationId;
        }

        public long getPreloadLocationId() {
            return preloadLocationId;
        }

        public long getPreloadItemId() {
            return preloadItemId;
        }

        public long getPreloadContainerId() {
            return preloadContainerId;
        }

        public String getBarcode() {
            return barcode;
        }

        public String getCaseNumber() {
            return caseNumber;
        }

        public String getItemNumber() {
            return itemNumber;
        }

        public String getPackaging() {
            return packaging;
        }

        public String getDescription() {
            return description;
        }

        public String getDateTime() {
            return dateTime;
        }

        public int getViewType() {
            return viewType;
        }

        ItemViewHolder(final View itemView) {
            super(itemView);
            textView1 = itemView.findViewById(R.id.text_view_1);
            dividerView = itemView.findViewById(R.id.divider_view);
            textView2 = itemView.findViewById(R.id.text_view_2);
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
        }

        void bindViewsAsScannedItem(long scannedItemId, long scannedLocationId, String barcode, String tags, String dateTime) {
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
            //todo finish
        }

        void bindViewsAsPreloadedItem(long preloadLocationId, long preloadItemId, String barcode, String caseNumber, String itemNumber, String packaging, String description) {
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
        }

        void bindViewsAsPreloadedCaseContainer(long preloadLocationId, long preloadContainerId, String barcode, String caseNumber, String description) {
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

        void bindViewsAsPreloadedBulkContainer(long preloadLocationId, long preloadContainerId, String barcode, String description) {
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
            this.dateTime = dateTime;
            viewType = PRELOADED_BULK_CONTAINER;

            textView1.setText(description);
            textView1.setVisibility(View.VISIBLE);
            dividerView.setVisibility(View.VISIBLE);
            textView2.setVisibility(View.INVISIBLE);
            //todo finish
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (saveTask == null) {
                            //preSave();
                            //archiveDatabase();
                            //(savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                            //saveTask = new SaveToFileTask().execute();
                        } else {
                            //saveTask.cancel(false);
                            //postSave();
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

    private void scanBarcode(String barcode) {
        if (saveTask != null) {
            vibrate(300);
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isItem(barcode) && !isContainer(barcode) && !isLocation(barcode)) {
            vibrate(300);
            Toast.makeText(this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLocation(barcode))
            addScannedLocation(barcode);

        //new ScanBarcodeTask().execute(barcode);
        //todo finish
    }
}

