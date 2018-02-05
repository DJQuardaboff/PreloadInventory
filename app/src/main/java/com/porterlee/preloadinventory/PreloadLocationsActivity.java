package com.porterlee.preloadinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
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

import com.porterlee.preloadinventory.PreloadLocationsDatabase.LocationTable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.preloadinventory.MainActivity.DATE_FORMAT;
import static com.porterlee.preloadinventory.MainActivity.DUPLICATE_BARCODE_TAG;
import static com.porterlee.preloadinventory.MainActivity.FILE_NAME_KEY;
import static com.porterlee.preloadinventory.MainActivity.MAX_ITEM_HISTORY_INCREASE;

public class PreloadLocationsActivity extends AppCompatActivity {
    static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadLocationsDatabase.DIRECTORY);
    private static final String TAG = PreloadLocationsActivity.class.getSimpleName();
    private int maxProgress;
    private SharedPreferences sharedPreferences;
    private SQLiteStatement LAST_LOCATION_BARCODE_STATEMENT;
    private Vibrator vibrator;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Integer, String> saveTask;
    private int maxItemHistory = MAX_ITEM_HISTORY_INCREASE;
    private ScanResultReceiver resultReciever;
    private int locationCount = 0;
    private String lastLocationBarcode = "-";
    private RecyclerView locationRecyclerView;
    private RecyclerView.Adapter locationRecyclerAdapter;
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
        setContentView(R.layout.preload_locations_layout);

        sharedPreferences = getPreferences(MODE_PRIVATE);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        archiveDirectory = new File(getFilesDir() + "/" + PreloadLocationsDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + PreloadLocationsDatabase.DIRECTORY + "/" + PreloadLocationsDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            try {
                if (databaseFile.renameTo(File.createTempFile("error", ".db", archiveDirectory))) {
                    Toast.makeText(this, "There was an error loading the database. It has been archived", Toast.LENGTH_SHORT).show();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(PreloadLocationsActivity.this);
        builder.setCancelable(false);
        builder.setTitle("Delete Database");
        builder.setMessage("There was an error loading the last list and it could not be archived.\n\nWould you like to delete the it?\n\nAnswering no will return you to the previous screen.");
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
                    Toast.makeText(PreloadLocationsActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Toast.makeText(PreloadLocationsActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                initialize();
            }
        });

        builder.create().show();
    }

    private void initialize() throws SQLiteCantOpenDatabaseException {
        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        //db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        LAST_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");

        locationCount = getLocationCount();
        lastLocationBarcode = getLastLocationBarcode();

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
                Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(i)});
                cursor.moveToFirst();
                long id = cursor.getLong(cursor.getColumnIndex(PreloadLocationsDatabase.ID));
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                int count = locationCount;
                count = Math.min(count, maxItemHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                final PreloadLocationViewHolder preloadLocationViewHolder = new PreloadLocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_locations_item_layout, parent, false));
                preloadLocationViewHolder.expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(PreloadLocationsActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.popup_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                if (saveTask != null) {
                                    Toast.makeText(PreloadLocationsActivity.this, "Cannot edit list while saving", Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(PreloadLocationsActivity.this);
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
                Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DESCRIPTION + ", " + LocationTable.Keys.TAGS + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                cursor.moveToFirst();

                final long locationId = cursor.getInt(cursor.getColumnIndex(PreloadLocationsDatabase.ID));
                final String locationBarcode = cursor.getString(cursor.getColumnIndex(PreloadLocationsDatabase.BARCODE));
                final String locationDescription = cursor.getString(cursor.getColumnIndex(PreloadLocationsDatabase.DESCRIPTION));
                final String locationTags = cursor.getString(cursor.getColumnIndex(PreloadLocationsDatabase.TAGS));

                cursor.close();

                ((PreloadLocationViewHolder) holder).bindViews(locationId, locationBarcode, locationDescription, locationTags);
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_locations_menu, menu);
        loadCurrentScannerOptions();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                if (locationRecyclerAdapter.getItemCount() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear List");
                    builder.setMessage("Are you sure you want to clear this list?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask != null) {
                                return;
                            }

                            changedSinceLastArchive = true;

                            int deletedCount = db.delete(LocationTable.NAME, "1", null);

                            if (locationCount != deletedCount) {
                                Log.v(TAG, "Detected inconsistencies with number of locations while deleting");
                                //Toast.makeText(PreloadLocationsActivity.this, "Detected inconsistencies with number of locations while deleting", Toast.LENGTH_SHORT).show();
                            }

                            locationCount = 0;
                            lastLocationBarcode = "-";

                            locationRecyclerAdapter.notifyDataSetChanged();
                            locationRecyclerAdapter.notifyItemRangeRemoved(0, locationRecyclerAdapter.getItemCount());
                            updateInfo();
                            Toast.makeText(PreloadLocationsActivity.this, "List cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else
                    Toast.makeText(this, "There are no locations in this list", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save_to_file:
                if (locationRecyclerAdapter.getItemCount() <= 0) {
                    Toast.makeText(this, "There are no locations in this list", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                }

                if (saveTask == null) {
                    preSave();
                    archiveDatabase();
                    (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                    saveTask = new SaveToFileTask().execute();
                } else {
                    saveTask.cancel(false);
                    postSave();
                }

                return true;
            case R.id.action_cancel_save:
                if (saveTask != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Cancel Save");
                    builder.setMessage("Are you sure you want to stop saving this file?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask != null && !saveTask.isCancelled())
                                saveTask.cancel(false);
                        }
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

    public int getLocationCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    private void updateInfo() {
        //Log.v(TAG, "Updating info");
        ((TextView) findViewById(R.id.last_scan)).setText(lastLocationBarcode);
        ((TextView) findViewById(R.id.total_locations)).setText(String.valueOf(locationCount));
    }

    private void preSave() {
        progressBar.setProgress(0);
        //progressBar.setVisibility(View.VISIBLE);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        saveTask = null;
        //progressBar.setVisibility(View.GONE);
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private static final String alphaNumeric = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private void randomScan() {
        Random r = new Random();
        String barcode = "V";

        for (int i = r.nextInt(5) + 5; i > 0; i--)
            barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))));

        if (isLocation(barcode))
            barcode = barcode.toUpperCase();

        scanBarcode(barcode);
    }

    private void scanBarcode(String barcode) {
        if (isItem(barcode) || isContainer(barcode)) {
            vibrate(300);
            Toast.makeText(this, "Cannot accept items in preload location mode", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isLocation(barcode)) {
            vibrate(300);
            Toast.makeText(this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
            return;
        }

        String tags = "";
        if (saveTask != null) {
            vibrate(300);
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }

        Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ?;", new String[] {String.valueOf(barcode)});

        if (cursor.getCount() > 0) {
            cursor.close();
            vibrate(300);
            Toast.makeText(this, "Location was already scanned", Toast.LENGTH_SHORT).show();
            tags = tags.concat(DUPLICATE_BARCODE_TAG);
            return;
        }

        cursor.close();
        addLocation(barcode, tags);
    }

    private void vibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    private void addLocation(@NonNull String barcode, @NonNull String tags) {
        if (saveTask != null) return;

        ContentValues newItem = new ContentValues();
        newItem.put(PreloadLocationsDatabase.BARCODE, barcode);
        newItem.put(PreloadLocationsDatabase.TAGS, tags);
        newItem.put(PreloadLocationsDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (db.insert(LocationTable.NAME, null, newItem) == -1) {
            Log.e(TAG, "Error adding item \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding item \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            vibrate(300);
            return;
        }

        changedSinceLastArchive = true;

        //Log.v(TAG, "Added item \"" + barcode + "\" to the inventory");

        locationCount++;
        lastLocationBarcode = barcode;
        locationRecyclerAdapter.notifyItemInserted(0);

        if (locationRecyclerAdapter.getItemCount() == maxItemHistory)
            locationRecyclerAdapter.notifyItemRemoved(locationRecyclerAdapter.getItemCount() - 1);

        locationRecyclerAdapter.notifyItemRangeChanged(0, locationRecyclerAdapter.getItemCount());
        locationRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    private void removeLocation(@NonNull PreloadLocationViewHolder holder) {
        if (saveTask != null) return;

        if (db.delete(LocationTable.NAME, PreloadLocationsDatabase.ID + " = ?;", new String[] {String.valueOf(holder.getId())}) > 0) {

            locationCount--;

            if (holder.getAdapterPosition() == 0) {
                lastLocationBarcode = getLastLocationBarcode();
            }

            changedSinceLastArchive = true;

            locationRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            locationRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, locationRecyclerAdapter.getItemCount() - holder.getAdapterPosition());

            updateInfo();
        } else {
            vibrate(300);
            Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(LocationTable.Keys.BARCODE));
            }

            cursor.close();
            Log.e(TAG, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(PreloadLocationsActivity.this, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    private String getLastLocationBarcode() {
        /*Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;", null);
        String barcode = "-";

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
        }

        cursor.close();
        return barcode;*/
        try {
            return LAST_LOCATION_BARCODE_STATEMENT.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            return "-";
        }
    }

    private CharSequence formatDate(long millis) {
        return DateFormat.format(DATE_FORMAT, millis).toString();
    }

    private boolean isItem(@NonNull String barcode) {
        return barcode.startsWith("e1") || barcode.startsWith("E");// || barcode.startsWith("t") || barcode.startsWith("T");
    }

    private boolean isContainer(@NonNull String barcode) {
        return barcode.startsWith("m1") || barcode.startsWith("M");// || barcode.startsWith("a") || barcode.startsWith("A");
    }

    private boolean isLocation(@NonNull String barcode) {
        return barcode.startsWith("V");// || barcode.startsWith("L5");
    }

    class PreloadLocationViewHolder extends RecyclerView.ViewHolder {
        TextView locationTextView;
        private ColorStateList locationBarcodeTextViewDefaultColor;
        private ImageButton expandedMenuButton;
        private String barcode;
        private String description;
        private String tags;

        public long getId() {
            return id;
        }

        private long id = -1;

        public String getBarcode() {
            return barcode;
        }

        public String getDescription() {
            return description;
        }

        public String getTags() {
            return tags;
        }

        PreloadLocationViewHolder(final View itemView) {
            super(itemView);
            locationTextView = itemView.findViewById(R.id.location_text_view);
            locationBarcodeTextViewDefaultColor = locationTextView.getTextColors();
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
        }

        void bindViews(long id, String barcode, String description, String tags) {
            this.id = id;
            this.barcode = barcode;
            this.description = description;
            this.tags = tags;

            locationTextView.setText(barcode);
        }
    }

    private void archiveDatabase() {
        //noinspection ResultOfMethodCallIgnored
        //archiveDirectory.mkdirs();
    }

    class SaveToFileTask extends AsyncTask<Void, Integer, String> {
        protected String doInBackground(Void... voids) {
            Cursor locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " ASC;",null);

            locationCursor.moveToFirst();
            int locationBarcodeIndex = locationCursor.getColumnIndex(PreloadLocationsDatabase.BARCODE);
            int locationDateTimeIndex = locationCursor.getColumnIndex(PreloadLocationsDatabase.DATE_TIME);

            //Log.v(TAG, "Saving to file");

            int lineIndex = -1;
            int progress = 0;
            int tempProgress;

            try {
                //noinspection ResultOfMethodCallIgnored
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("data", ".txt", OUTPUT_PATH);
                Log.v(TAG, "Temp output file: " + TEMP_OUTPUT_FILE.getAbsolutePath());
                int totalLocationCount = locationCursor.getCount() + 1;
                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);
                lineIndex = 0;

                //
                String tempText = BuildConfig.APPLICATION_ID + "|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n";
                printStream.print(tempText);
                printStream.flush();
                lineIndex++;
                //

                while (!locationCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    tempProgress = (int) (((((float) lineIndex) / totalLocationCount) / 1.5) * maxProgress);
                    if (progress != tempProgress) {
                        publishProgress(tempProgress);
                        progress = tempProgress;
                    }

                    tempText = locationCursor.getString(locationBarcodeIndex) + "|" + locationCursor.getString(locationDateTimeIndex);
                    printStream.println(tempText);
                    printStream.flush();
                    locationCursor.moveToNext();
                    lineIndex++;
                }

                lineIndex = -1;
                printStream.close();
                locationCursor.moveToFirst();
                BufferedReader br = new BufferedReader(new FileReader(TEMP_OUTPUT_FILE));
                String line;
                lineIndex = 0;

                //
                br.readLine();
                lineIndex++;
                //

                while (!locationCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    tempProgress = (int) (((((float) lineIndex / totalLocationCount) / 3) + (2 / 3f)) * maxProgress);
                    if (progress != tempProgress) {
                        publishProgress(tempProgress);
                        progress = tempProgress;
                    }

                    line = br.readLine();
                    tempText = locationCursor.getString(locationCursor.getColumnIndex(PreloadLocationsDatabase.BARCODE)) + "|" + locationCursor.getString(locationCursor.getColumnIndex(PreloadLocationsDatabase.DATE_TIME));

                    if (!tempText.equals(line)) {
                        Log.e(TAG, "Error at line " + lineIndex + " of file output\n" +
                                "Expected String: " + tempText + "\n" +
                                "String in file: " + line);
                        return "There was a problem verifying the output file";
                    }

                    //Log.v(TAG, itemText);
                    locationCursor.moveToNext();
                    lineIndex++;
                }

                lineIndex = -1;

                locationCursor.close();
                locationCursor.close();
                br.close();

                if (outputFile.exists() && !outputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, "Could not delete existing output file");
                    return "Could not delete existing output file";
                }

                if (!TEMP_OUTPUT_FILE.renameTo(outputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.e(TAG, "Could not rename temp file to \"" + outputFile.getName() + "\"");
                    return "Could not rename temp file to \"" + outputFile.getName() + "\"";
                }
            } catch (FileNotFoundException e){//IOException e) {
                if (lineIndex == -1) {
                    Log.e(TAG, "FileNotFoundException occurred outside of while loops: " + e.getMessage());
                    e.printStackTrace();
                    return "IOException occurred while saving";
                } else {
                    Log.e(TAG, "FileNotFoundException occurred at line " + lineIndex + " in file while saving: " + e.getMessage());
                    e.printStackTrace();
                    return "IOException occurred at line " + lineIndex + " in file while saving";
                }
            } catch (IOException e){//IOException e) {
                if (lineIndex == -1) {
                    Log.e(TAG, "IOException occurred outside of while loops: " + e.getMessage());
                    e.printStackTrace();
                    return "IOException occurred while saving";
                } else {
                    Log.e(TAG, "IOException occurred at line " + lineIndex + " in file while saving: " + e.getMessage());
                    e.printStackTrace();
                    return "IOException occurred at line " + lineIndex + " in file while saving";
                }
            }

            Log.v(TAG, "Saved to: " + outputFile.getAbsolutePath());
            return "Saved to file";
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress(progress[0],true);
                progressBar.animate();
            } else*/ {
                progressBar.setProgress(progress[0]);
            }
        }

        protected void onPostExecute(String result) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(PreloadLocationsActivity.this, result, Toast.LENGTH_SHORT).show();
            if (changedSinceLastArchive)
                archiveDatabase();
            postSave();
            MediaScannerConnection.scanFile(PreloadLocationsActivity.this, new String[]{outputFile.getAbsolutePath()}, null, null);
        }

        @Override
        protected void onCancelled(String s) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(PreloadLocationsActivity.this, s, Toast.LENGTH_SHORT).show();
            postSave();
        }
    }

    private class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (iScanner != null) {
                try {
                    iScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;
                    if (barcode.equals(">><<")) {
                        Toast.makeText(PreloadLocationsActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                    } else if ((barcode.startsWith(">>")) && (barcode.endsWith("<<"))) {
                        barcode = barcode.substring(2, barcode.length() - 2);
                        if (barcode.equals("SCAN AGAIN")) return;
                        scanBarcode(barcode);
                    } else if (!barcode.equals("SCAN AGAIN")){
                        Toast.makeText(PreloadLocationsActivity.this, "Malformed barcode: " + barcode, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(PreloadLocationsActivity.this, "Barcode prefix and suffix might not be set", Toast.LENGTH_SHORT).show();
                    }
                    //System.out.println("symName: " + mDecodeResult.symName);
                    //System.out.println("decodeValue: " + mDecodeResult.decodeValue);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
