package com.porterlee.preload.location;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.porterlee.plcscanners.AbstractScanner;
import com.porterlee.plcscanners.Utils;
import com.porterlee.preload.BarcodeType;
import com.porterlee.preload.BuildConfig;
import com.porterlee.preload.DividerItemDecoration;
import com.porterlee.preload.MainActivity;
import com.porterlee.preload.R;
import com.porterlee.preload.inventory.PreloadInventoryActivity;
import com.porterlee.preload.location.PreloadLocationsDatabase.LocationTable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Pattern;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class PreloadLocationsActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final File EXTERNAL_PATH;
    static {
        File temp = new File(Environment.getExternalStorageDirectory(), PreloadLocationsDatabase.DIRECTORY);
        try {
            temp = temp.getCanonicalFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        EXTERNAL_PATH = temp;
    }
    private static final String TAG = PreloadLocationsActivity.class.getSimpleName();
    private int maxProgress;
    //private FileObserver mFileObserver;
    private SQLiteStatement LAST_LOCATION_BARCODE_STATEMENT;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Integer, String> saveTask;
    private int locationCount = 0;
    private String lastLocationBarcode = "-";
    private RecyclerView locationRecyclerView;
    private RecyclerView.Adapter locationRecyclerAdapter;
    private SQLiteDatabase db;

    private final AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener = new AbstractScanner.OnBarcodeScannedListener() {
        @Override
        public void onBarcodeScanned(String barcode) {
            Log.e(TAG, "PreloadLocationActivity.onBarcodeScanned()");
            if (BarcodeType.Item.isOfType(barcode) || BarcodeType.Container.isOfType(barcode)) {
                AbstractScanner.onScanComplete(false);
                toastShort("Cannot accept items in preload location mode");
                return;
            }

            if (!BarcodeType.Location.isOfType(barcode)) {
                AbstractScanner.onScanComplete(false);
                toastShort("Barcode \"" + barcode + "\" not recognised");
                return;
            }

            if (saveTask != null) {
                AbstractScanner.onScanComplete(false);
                toastShort("Cannot scan while saving");
                return;
            }

            Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.BARCODE + " = ?;", new String[] {String.valueOf(barcode)});

            if (cursor.getCount() > 0) {
                AbstractScanner.onScanComplete(false);
                toastShort("Location was already scanned");
                cursor.close();
                return;
            }

            AbstractScanner.onScanComplete(true);
            cursor.close();
            addLocation(barcode);
        }
    };

    private AbstractScanner getScanner() {
        return AbstractScanner.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AbstractScanner.setActivity(this);

        if (!getScanner().init()) {
            finish();
            toastLong("Scanner failed to initialize");
            return;
        }

        setContentView(R.layout.preload_locations_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%s v%s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        archiveDirectory = new File(getFilesDir() + "/" + PreloadLocationsDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        outputFile = new File(EXTERNAL_PATH.getAbsolutePath(), "data.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.getParentFile().mkdirs();
        databaseFile = new File(getFilesDir() + "/" + PreloadLocationsDatabase.DIRECTORY, PreloadLocationsDatabase.FILE_NAME);
        //databaseFile = new File(EXTERNAL_PATH, PreloadLocationsDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.getParentFile().mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            try {
                if (databaseFile.renameTo(File.createTempFile("error", ".db", archiveDirectory))) {
                    toastShort("There was an error loading the list file. It has been archived");
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
        getScanner().setIsEnabled(false);
        new AlertDialog.Builder(PreloadLocationsActivity.this)
                .setCancelable(false)
                .setTitle("Database Load Error")
                .setMessage(
                        "There was an error loading the list file and it could not be archived.\n" +
                        "\n" +
                        "Would you like to delete the it?\n" +
                        "\n" +
                        "Answering no will close the app."
                ).setNegativeButton(R.string.action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!databaseFile.delete()) {
                            toastShort("The file could not be deleted");
                            finish();
                            return;
                        }
                        toastShort("The file was deleted");
                        initialize();
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        getScanner().setIsEnabled(true);
                    }
                }).create().show();
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

        /*this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });*/
        /*
        mFileObserver = new FileObserver(PreloadInventoryActivity.EXTERNAL_PATH.getAbsolutePath()) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if ((event & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0 && PreloadInventoryActivity.INPUT_FILE.exists()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            askToInventory();
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshFileMenuOption();
                    }
                });
            }
        };
        */
        locationRecyclerView = findViewById(R.id.location_list_view);
        locationRecyclerView.setHasFixedSize(true);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public int getItemCount() {
                return locationCount;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new PreloadLocationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.preload_locations_item_layout, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
                Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                cursor.moveToFirst();

                final long locationId = cursor.getInt(cursor.getColumnIndex(PreloadLocationsDatabase.ID));
                final String locationBarcode = cursor.getString(cursor.getColumnIndex(PreloadLocationsDatabase.BARCODE));

                cursor.close();

                ((PreloadLocationViewHolder) holder).bindViews(locationId, locationBarcode);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }
        };
        locationRecyclerView.setAdapter(locationRecyclerAdapter);
        final RecyclerView.ItemAnimator itemRecyclerAnimator = new DefaultItemAnimator();
        itemRecyclerAnimator.setAddDuration(100);
        itemRecyclerAnimator.setChangeDuration(100);
        itemRecyclerAnimator.setMoveDuration(100);
        itemRecyclerAnimator.setRemoveDuration(100);
        locationRecyclerView.setItemAnimator(itemRecyclerAnimator);
        locationRecyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider, DividerItemDecoration.VERTICAL_LIST));
        updateInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getScanner().onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //mFileObserver.startWatching();
        getScanner().onResume();
        AbstractScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
    }

    @Override
    protected void onPause() {
        getScanner().onPause();
        //mFileObserver.stopWatching();
        super.onPause();
    }

    @Override
    protected void onStop() {
        getScanner().onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        getScanner().onDestroy();
        if (saveTask != null) {
            saveTask.cancel(false);
            saveTask = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_locations_menu, menu);
        return super.onCreateOptionsMenu(menu) | getScanner().onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        refreshFileMenuOption();
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save_to_file:
                if (locationRecyclerAdapter.getItemCount() <= 0) {
                    toastShort("There are no locations in this list");
                    return true;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        //Toast.makeText(this, "Write external storage permission is required for this", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        return true;
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
                    getScanner().setIsEnabled(false);
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setTitle("Cancel Save")
                            .setMessage("Are you sure you want to stop saving this file?")
                            .setNegativeButton(R.string.action_no, null)
                            .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (saveTask != null && !saveTask.isCancelled()) {
                                        saveTask.cancel(false);
                                    }
                                }
                            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    getScanner().setIsEnabled(true);
                                }
                            }).create().show();
                } else {
                    postSave();
                }
                return true;
            case R.id.action_clear_list:
                if (locationRecyclerAdapter.getItemCount() > 0) {
                    getScanner().setIsEnabled(false);
                    new AlertDialog.Builder(this)
                            .setCancelable(true)
                            .setTitle("Clear List")
                            .setMessage("Are you sure you want to clear this list?")
                            .setNegativeButton("no", null)
                            .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (saveTask != null) {
                                        return;
                                    }

                                    changedSinceLastArchive = true;
                                    db.delete(LocationTable.NAME, null, null);

                                    locationCount = 0;
                                    lastLocationBarcode = "-";

                                    locationRecyclerAdapter.notifyDataSetChanged();
                                    locationRecyclerAdapter.notifyItemRangeRemoved(0, locationRecyclerAdapter.getItemCount());
                                    updateInfo();
                                    toastShort("List cleared");
                                }
                            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    getScanner().setIsEnabled(true);
                                }
                            }).create().show();
                } else {
                    toastShort("There are no locations in this list");
                }
                return true;
            /*case R.id.action_continuous:
                try {
                    if (!item.isChecked()){
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    } else {
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                    }
                    item.setChecked(!item.isChecked());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;*/
            case R.id.action_start_inventory:
                askToInventory();
                return true;
            default:
                return super.onOptionsItemSelected(item) | getScanner().onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu) || getScanner().onPrepareOptionsMenu(menu);
    }

    private void toastShort(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toastLong(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshFileMenuOption() {
        if (mOptionsMenu != null)
            mOptionsMenu.findItem(R.id.action_start_inventory).setEnabled(PreloadInventoryActivity.INPUT_FILE.exists());
    }

    public void askToInventory() {
        getScanner().setIsEnabled(false);
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("New Inventory")
                .setMessage(
                        "Would you like to start a new inventory with this data?\n" +
                        "\n" +
                        "This will overwrite any inventory previously started."
                ).setNegativeButton(R.string.action_no, null)
                .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (PreloadInventoryActivity.INPUT_FILE.exists()) {
                            startActivity(new Intent(PreloadLocationsActivity.this, PreloadInventoryActivity.class));
                            finish();
                        } else {
                            toastShort("File no longer exists");
                        }
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        getScanner().setIsEnabled(true);
                    }
                }).create().show();
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
        mOptionsMenu.findItem(R.id.action_clear_list).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        saveTask = null;
        //progressBar.setVisibility(View.GONE);
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.action_cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_clear_list).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }
    /*
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
    */
    private void addLocation(@NonNull String barcode) {
        if (saveTask != null) return;

        ContentValues newItem = new ContentValues();
        newItem.put(PreloadLocationsDatabase.BARCODE, barcode);
        newItem.put(PreloadLocationsDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (db.insert(LocationTable.NAME, null, newItem) == -1) {
            Log.e(TAG, "Error adding location \"" + barcode + "\" to the list");
            toastShort("Error adding location \"" + barcode + "\" to the list");
            Utils.vibrate(getApplicationContext());
            return;
        }

        changedSinceLastArchive = true;

        //Log.v(TAG, "Added item \"" + barcode + "\" to the inventory");

        locationCount++;
        lastLocationBarcode = barcode;
        locationRecyclerAdapter.notifyItemInserted(0);

        locationRecyclerAdapter.notifyItemRangeChanged(0, locationRecyclerAdapter.getItemCount());
        locationRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    private void removeLocation(@NonNull PreloadLocationViewHolder holder) {
        if (saveTask != null) return;

        if (db.delete(LocationTable.NAME, PreloadLocationsDatabase.ID + " = ?;", new String[] { String.valueOf(holder.getId()) }) > 0) {

            locationCount--;

            if (holder.getAdapterPosition() == 0) {
                lastLocationBarcode = getLastLocationBarcode();
            }

            changedSinceLastArchive = true;

            locationRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            locationRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, locationRecyclerAdapter.getItemCount() - holder.getAdapterPosition());

            updateInfo();
        } else {
            Utils.vibrate(getApplicationContext());
            Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(LocationTable.Keys.BARCODE));
            }

            cursor.close();
            Log.e(TAG, "Error removing location \"" + barcode +"\" from the list");
            toastShort("Error removing location \"" + barcode +"\" from the list");
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
        return DateFormat.format(MainActivity.DATE_FORMAT, millis).toString();
    }

    class PreloadLocationViewHolder extends RecyclerView.ViewHolder {
        TextView locationTextView;
        private ImageButton expandedMenuButton;
        private String barcode;
        private long id = -1;


        public long getId() {
            return id;
        }

        PreloadLocationViewHolder(final View itemView) {
            super(itemView);
            locationTextView = itemView.findViewById(R.id.location_text_view);
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupMenu popup = new PopupMenu(PreloadLocationsActivity.this, view);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.popup_menu_location, popup.getMenu());
                    popup.getMenu().findItem(R.id.remove_location).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (saveTask != null) {
                                toastShort("Cannot edit list while saving");
                                return true;
                            }
                            getScanner().setIsEnabled(false);
                            new AlertDialog.Builder(PreloadLocationsActivity.this)
                                    .setCancelable(true)
                                    .setTitle("Remove location")
                                    .setMessage("Are you sure you want to remove location \"" + barcode + "\"?")
                                    .setNegativeButton(R.string.action_no, null)
                                    .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            removeLocation(PreloadLocationViewHolder.this);
                                        }
                                    }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                        @Override
                                        public void onDismiss(DialogInterface dialog) {
                                            getScanner().setIsEnabled(true);
                                        }
                                    }).create().show();
                            return true;
                        }
                    });
                    popup.show();
                }
            });
        }

        void bindViews(long id, String barcode) {
            this.id = id;
            this.barcode = barcode;

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
            int progress = 0;
            int tempProgress;

            try {
                //noinspection ResultOfMethodCallIgnored
                EXTERNAL_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("data", ".txt", EXTERNAL_PATH);
                //Log.v(TAG, "Temp output file: " + TEMP_OUTPUT_FILE.getAbsolutePath());
                int totalLocationCount = locationCursor.getCount() + 1;
                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);
                int lineIndex = 0;

                //
                //noinspection StringConcatenationMissingWhitespace
                String tempText = BuildConfig.APPLICATION_ID.split(Pattern.quote("."))[2] + ".location|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n";
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

                    printStream.printf("\"%1s\"|\"%2s\"\r\n", locationCursor.getString(locationBarcodeIndex).replace("\"","\"\""), locationCursor.getString(locationDateTimeIndex).replace("\"","\"\""));
                    printStream.flush();
                    locationCursor.moveToNext();
                    lineIndex++;
                }

                printStream.close();
                locationCursor.close();
                locationCursor.close();

                if (outputFile.exists() && !outputFile.delete()) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not delete existing output file");
                    return "Could not delete existing output file";
                }

                refreshExternalPath();
                //MediaScannerConnection.scanFile(PreloadLocationsActivity.this, new String[]{ outputFile.getParent() }, null, null);

                if (!TEMP_OUTPUT_FILE.renameTo(outputFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    TEMP_OUTPUT_FILE.delete();
                    Log.w(TAG, "Could not rename temp file to \"" + outputFile.getName() + "\"");
                    return "Could not rename temp file to \"" + outputFile.getName() + "\"";
                } else {
                    if (!PreloadInventoryActivity.OUTPUT_FILE.delete() && PreloadInventoryActivity.OUTPUT_FILE.exists()) {
                        Log.w(TAG, "Could not delete inventory file");
                        return "Could not delete inventory file";
                    }
                }

                refreshExternalPath();
                //MediaScannerConnection.scanFile(PreloadLocationsActivity.this, new String[]{ outputFile.getParent() }, null, null);
            } catch (FileNotFoundException e){//IOException e) {
                Log.e(TAG, "FileNotFoundException occurred while saving: " + e.getMessage());
                e.printStackTrace();
                locationCursor.close();
                locationCursor.close();
                return "FileNotFoundException occurred while saving";
            } catch (IOException e){//IOException e) {
                Log.e(TAG, "IOException occurred while saving: " + e.getMessage());
                e.printStackTrace();
                locationCursor.close();
                locationCursor.close();
                return "IOException occurred while saving";
            } finally {
                locationCursor.close();
                locationCursor.close();
            }

            locationCursor.close();
            locationCursor.close();
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

            toastShort(result);
            if (changedSinceLastArchive)
                archiveDatabase();
            postSave();
        }

        @Override
        protected void onCancelled(String result) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            toastShort(result);
            postSave();
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 1) {
                        if (saveTask == null) {
                            preSave();
                            archiveDatabase();
                            (savingToast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT)).show();
                            saveTask = new SaveToFileTask().execute();
                        } else {
                            saveTask.cancel(false);
                            postSave();
                        }
                    }
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
