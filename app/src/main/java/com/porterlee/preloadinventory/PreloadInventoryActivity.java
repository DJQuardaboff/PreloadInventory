package com.porterlee.preloadinventory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import com.porterlee.preloadinventory.PreloadInventoryDatabase.ScannedItemTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.ScannedLocationTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.PreloadItemTable;
import com.porterlee.preloadinventory.PreloadInventoryDatabase.PreloadLocationTable;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.preloadinventory.MainActivity.MAX_ITEM_HISTORY_INCREASE;

public class PreloadInventoryActivity extends AppCompatActivity {
    static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadInventoryDatabase.DIRECTORY);
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    private int maxProgress;
    private SharedPreferences sharedPreferences;
    private SQLiteStatement LAST_SCANNED_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement LAST_SCANNED_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement GET_SCANNED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_SCANNED_LOCATION_COUNT_STATEMENT;
    private SQLiteStatement GET_PRELOADED_ITEM_COUNT_STATEMENT;
    private SQLiteStatement GET_PRELOADED_LOCATION_COUNT_STATEMENT;
    private Vibrator vibrator;
    private File inputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Integer, String> saveTask;
    private int maxItemHistory = MAX_ITEM_HISTORY_INCREASE;
    private ScanResultReceiver resultReciever;
    private int preloadedLocationCount = 0;
    private String lastLocationBarcode = "-";
    private RecyclerView locationRecyclerView;
    private RecyclerView.Adapter locationRecyclerAdapter;
    private SQLiteDatabase db;
    private IScannerService iScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();
    //todo finish

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

        archiveDirectory = new File(getFilesDir() + "/" + PreloadInventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        inputFile = new File(INPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        inputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + PreloadInventoryDatabase.DIRECTORY + "/" + PreloadInventoryDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            try {
                if (databaseFile.renameTo(File.createTempFile("error", ".db", archiveDirectory))) {
                    Toast.makeText(this, "There was an error loading the inventory file. It has been archived", Toast.LENGTH_SHORT).show();
                } else {
                    databaseLoadingError();
                }
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
        builder.setMessage("There was an error loading the inventory file and it could not be archived.\n\nWould you like to delete the it?\n\nAnswering no will close the app.");
        builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
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

        //db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

        //db.execSQL("DROP TABLE IF EXISTS " + ScannedItemTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + ScannedLocationTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScannedLocationTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + PreloadItemTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadItemTable.TABLE_CREATION);
        //db.execSQL("DROP TABLE IF EXISTS " + PreloadLocationTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PreloadLocationTable.TABLE_CREATION);


        //LAST_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + LocationTable.Keys.BARCODE + " FROM " + PreloadLocationsDatabase.LocationTable.NAME + " ORDER BY " + PreloadLocationsDatabase.LocationTable.Keys.ID + " DESC LIMIT 1;");
        LAST_SCANNED_ITEM_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadItemTable.Keys.BARCODE + " FROM " + PreloadItemTable.NAME + " WHERE " + PreloadItemTable.Keys.ID + " = ( SELECT " + ScannedItemTable.Keys.PRELOAD_ITEM_ID + " FROM " + ScannedItemTable.NAME + " ORDER BY " + ScannedItemTable.Keys.ID + " DESC LIMIT 1 );");
        LAST_SCANNED_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + PreloadLocationTable.Keys.BARCODE + " FROM " + PreloadLocationTable.NAME + " WHERE " + PreloadLocationTable.Keys.ID + " = ( SELECT " + ScannedLocationTable.Keys.PRELOAD_LOCATION_ID + " FROM " + ScannedLocationTable.NAME + " ORDER BY " + ScannedLocationTable.Keys.ID + " DESC LIMIT 1 );");
        GET_SCANNED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedItemTable.NAME + ";");
        GET_SCANNED_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + ScannedLocationTable.NAME + ";");
        GET_PRELOADED_ITEM_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + PreloadItemTable.NAME + ";");
        GET_PRELOADED_LOCATION_COUNT_STATEMENT = db.compileStatement("SELECT COUNT(*) FROM " + PreloadLocationTable.NAME + ";");

        preloadedLocationCount = getPreloadedLocationCount();
        //lastLocationBarcode = getLastLocationBarcode();

        progressBar = findViewById(R.id.progress_saving);
        maxProgress = progressBar.getMax();

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });

        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                Cursor cursor = db.rawQuery("SELECT " + PreloadLocationsDatabase.LocationTable.Keys.ID + " FROM " + PreloadLocationsDatabase.LocationTable.NAME + " ORDER BY " + PreloadLocationsDatabase.LocationTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(i)});
                cursor.moveToFirst();
                long id = cursor.getLong(cursor.getColumnIndex(PreloadLocationsDatabase.ID));
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                int count = preloadedLocationCount;
                count = Math.min(count, maxItemHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                final PreloadLocationsActivity.PreloadLocationViewHolder preloadLocationViewHolder = new PreloadLocationsActivity.PreloadLocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_locations_item_layout, parent, false));
                preloadLocationViewHolder.expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(PreloadInventoryActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                if (saveTask != null) {
                                    Toast.makeText(PreloadInventoryActivity.this, "Cannot edit list while saving", Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(PreloadInventoryActivity.this);
                                builder.setCancelable(true);
                                builder.setTitle("Remove location");
                                builder.setMessage("Are you sure you want to remove location \"" + preloadLocationViewHolder.getBarcode() + "\"?");
                                builder.setNegativeButton("no", null);
                                builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d(TAG, "Removing location at position " + preloadLocationViewHolder.getAdapterPosition() + " with barcode " + preloadLocationViewHolder.getBarcode());
                                        removeLocation(preloadLocationViewHolder);
                                    }
                                });
                                builder.create().show();

                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                return preloadLocationViewHolder;
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                Cursor cursor = db.rawQuery("SELECT " + PreloadLocationsDatabase.LocationTable.Keys.ID + ", " + PreloadLocationsDatabase.LocationTable.Keys.BARCODE + " FROM " + PreloadLocationsDatabase.LocationTable.NAME + " ORDER BY " + PreloadLocationsDatabase.LocationTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                cursor.moveToFirst();

                final long locationId = cursor.getInt(cursor.getColumnIndex(PreloadLocationsDatabase.ID));
                final String locationBarcode = cursor.getString(cursor.getColumnIndex(PreloadLocationsDatabase.BARCODE));

                cursor.close();

                ((PreloadLocationsActivity.PreloadLocationViewHolder) holder).bindViews(locationId, locationBarcode);
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
                    //Log.v(TAG, "Scroll state changed to: " + (newState == RecyclerView.SCROLL_STATE_IDLE ? "SCROLL_STATE_IDLE" : (newState == RecyclerView.SCROLL_STATE_DRAGGING ? "SCROLL_STATE_DRAGGING" : "SCROLL_STATE_SETTLING")));

                    maxItemHistory += MAX_ITEM_HISTORY_INCREASE;
                    locationRecyclerAdapter.notifyDataSetChanged();
                }
            }
        });
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(itemRecyclerAnimator);
        updateInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_locations_menu, menu);
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
            //} catch (InterruptedException e) { }
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

    class PreloadItemViewHolder extends RecyclerView.ViewHolder {
        //todo finish

        PreloadItemViewHolder(final View itemView) {
            super(itemView);
            //todo finish
        }

        void bindViews(Cursor cursor) {
            //todo finish
        }
    }
}

