package com.porterlee.mobileinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
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
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
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
import java.io.PrintStream;
import java.util.Random;

import com.porterlee.mobileinventory.InventoryDatabase.ItemTable;
import com.porterlee.mobileinventory.InventoryDatabase.LocationTable;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScannerService;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class InventoryActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), "Download");
    private static final String IS_LIKE_ITEM_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'e1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'E%\'";
    private static final String IS_LIKE_CONTAINER_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'m1%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'M%\'";
    //private static final String IS_LIKE_LOCATION_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'V%\' OR " + ItemTable.Keys.BARCODE + " LIKE \'L5%\'";
    //private static final String IS_LIKE_PROCESS_CLAUSE = ItemTable.Keys.BARCODE + " LIKE \'L3%\'";
    private static final String DUPLICATE_BARCODE_TAG = "D";
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = InventoryActivity.class.getSimpleName();
    private static final int MAX_ITEM_HISTORY_INCREASE = 25;
    private static final int errorColor = Color.RED;
    private SQLiteStatement LAST_ITEM_BARCODE_STATEMENT;
    private SQLiteStatement LAST_LOCATION_BARCODE_STATEMENT;
    private SQLiteStatement LAST_LOCATION_ID_STATEMENT;
    private Vibrator vibrator;
    private File outputFile;
    private File databaseFile;
    private File archiveDirectory;
    private boolean changedSinceLastArchive = true;
    private Toast savingToast;
    private int[] autosizeInventoryItemTextSizes;
    private int[] autosizeInventoryLocationTextSizes;
    private MaterialProgressBar progressBar;
    private Menu mOptionsMenu;
    private AsyncTask<Void, Integer, String> saveTask;
    private int maxItemHistory = MAX_ITEM_HISTORY_INCREASE;
    private ScanResultReceiver resultReciever;
    private int itemCount = 0;
    private int containerCount = 0;
    private long lastLocationId = -1;
    private String lastLocationBarcode = "-";
    private String lastItemBarcode = "-";
    //private int locationCount;
    //private int processCount;
    private RecyclerView itemRecyclerView;
    private RecyclerView.Adapter itemRecyclerAdapter;
    private SQLiteDatabase db;
    private IScannerService iScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inventory_layout);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        Resources resources = getResources();
        autosizeInventoryItemTextSizes = resources.getIntArray(R.array.autosize_inventory_item_text_sizes);
        autosizeInventoryLocationTextSizes = resources.getIntArray(R.array.autosize_inventory_location_text_sizes);

        archiveDirectory = new File(getFilesDir() + "/" + InventoryDatabase.ARCHIVE_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        archiveDirectory.mkdirs();
        outputFile = new File(OUTPUT_PATH.getAbsolutePath(), "invinfo.txt");
        //noinspection ResultOfMethodCallIgnored
        outputFile.mkdirs();
        databaseFile = new File(getFilesDir() + "/" + InventoryDatabase.DIRECTORY + "/" + InventoryDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            try {
                //System.out.println(databaseFile.exists());
                if (databaseFile.renameTo(File.createTempFile("error", ".db", new File(databaseFile.getParent() + "/" + InventoryDatabase.ARCHIVE_DIRECTORY))))
                    Toast.makeText(this, "There was an error loading the database. It has been archived", Toast.LENGTH_SHORT).show();
            } catch (IOException e1) {

                e1.printStackTrace();
                AlertDialog.Builder builder = new AlertDialog.Builder(InventoryActivity.this);
                builder.setCancelable(true);
                builder.setTitle("Database Error");
                builder.setMessage("There was an error loading the last inventory and it could not be archived.\n\nWould you like to delete the it?\n\nAnswering no will return you to the previous screen.");
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
                            Toast.makeText(InventoryActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        Toast.makeText(InventoryActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                        initialize();
                        //db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
                    }
                });
                builder.create().show();
            }
        }
    }

    private void initialize() throws SQLiteCantOpenDatabaseException{
        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        //db.execSQL("DROP TABLE IF EXISTS " + ItemTable.NAME);
        //db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

        db.execSQL("CREATE TABLE IF NOT EXISTS " + ItemTable.TABLE_CREATION);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        LAST_ITEM_BARCODE_STATEMENT = db.compileStatement("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1;");
        LAST_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");
        LAST_LOCATION_ID_STATEMENT = db.compileStatement("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");

        itemCount = getItemCount();
        containerCount = getContainerCount();
        lastLocationId = getLastLocationId();
        lastLocationBarcode = getLastLocationBarcode();
        lastItemBarcode = getLastItemBarcode();

        progressBar = findViewById(R.id.progress_saving);

        this.<Button>findViewById(R.id.random_scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                randomScan();
            }
        });

        itemRecyclerView = findViewById(R.id.item_list_view);
        itemRecyclerView.setHasFixedSize(true);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemRecyclerAdapter = new RecyclerView.Adapter() {
            @Override
            public long getItemId(int i) {
                Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.ID + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(i)});
                cursor.moveToFirst();
                long id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
                cursor.close();
                return id;
            }

            @Override
            public int getItemCount() {
                int count = itemCount + containerCount;
                count = Math.min(count, maxItemHistory);
                return count;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                final InventoryItemViewHolder inventoryItemViewHolder = new InventoryItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.inventory_item_layout, parent, false));
                inventoryItemViewHolder.expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popup = new PopupMenu(InventoryActivity.this, view);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.inventory_item_popup_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.remove_item).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                if (saveTask != null) {
                                    Toast.makeText(InventoryActivity.this, "Cannot edit inventory while saving", Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(InventoryActivity.this);
                                builder.setCancelable(true);
                                builder.setTitle("Remove " + (isItem(inventoryItemViewHolder.getItemBarcode()) ? "Item" : "Container"));
                                builder.setMessage("Are you sure you want to remove this item?");
                                builder.setNegativeButton("no", null);
                                builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d(TAG, "Removing " + (isItem(inventoryItemViewHolder.getItemBarcode()) ? "item" : "container") + " at position " + inventoryItemViewHolder.getAdapterPosition() + " with barcode " + inventoryItemViewHolder.getItemBarcode());

                                        if (isContainer(inventoryItemViewHolder.getItemBarcode()))
                                            removeBarcodeContainer(inventoryItemViewHolder);

                                        if (isItem(inventoryItemViewHolder.getItemBarcode()))
                                            removeBarcodeItem(inventoryItemViewHolder);
                                    }
                                });
                                builder.create().show();

                                return true;
                            }
                        });
                        popup.show();
                    }
                });
                return inventoryItemViewHolder;
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
                Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.ID + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.DESCRIPTION + ", " + ItemTable.Keys.TAGS + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1 OFFSET ?;", new String[] {String.valueOf(position)});
                cursor.moveToFirst();

                final long itemId = cursor.getInt(cursor.getColumnIndex(InventoryDatabase.ID));
                final long itemLocationId = cursor.getInt(cursor.getColumnIndex(InventoryDatabase.LOCATION_ID));
                final String itemBarcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
                final String itemDescription = cursor.getString(cursor.getColumnIndex(InventoryDatabase.DESCRIPTION));
                final String itemTags = cursor.getString(cursor.getColumnIndex(InventoryDatabase.TAGS));

                cursor.close();

                cursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DESCRIPTION + ", " + LocationTable.Keys.TAGS + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ?;", new String[] {String.valueOf(itemLocationId)});
                cursor.moveToFirst();

                final String locationBarcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
                final String locationDescription = cursor.getString(cursor.getColumnIndex(InventoryDatabase.DESCRIPTION));
                final String locationTags = cursor.getString(cursor.getColumnIndex(InventoryDatabase.TAGS));

                cursor.close();

                ((InventoryItemViewHolder) holder).bindViews(itemId, itemBarcode, itemDescription, itemTags, locationBarcode, locationDescription, locationTags);

            }

            @Override
            public int getItemViewType(int i) {
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

        //for (int i = 0; i < 10000; i++)
        //randomScan();

        itemRecyclerAdapter.notifyDataSetChanged();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(resultReciever);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inventory_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove_all:
                if (itemRecyclerAdapter.getItemCount() > 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Clear Inventory");
                    builder.setMessage("Are you sure you want to clear this inventory?");
                    builder.setNegativeButton("no", null);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveTask != null) {
                                return;
                            }

                            changedSinceLastArchive = true;

                            int deletedCount = db.delete(ItemTable.NAME, "1", null);
                            db.delete(LocationTable.NAME, null, null);

                            if (itemCount + containerCount != deletedCount)
                                Toast.makeText(InventoryActivity.this, "Detected inconsistencies with number of items while deleting", Toast.LENGTH_SHORT).show();

                            itemCount = 0;
                            containerCount = 0;
                            lastLocationId = -1;
                            lastLocationBarcode = "-";
                            lastItemBarcode = "-";

                            itemRecyclerAdapter.notifyDataSetChanged();
                            itemRecyclerAdapter.notifyItemRangeRemoved(0, itemRecyclerAdapter.getItemCount());
                            updateInfo();
                            Toast.makeText(InventoryActivity.this, "Inventory cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.create().show();
                } else
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save_to_file:
                if (itemRecyclerAdapter.getItemCount() <= 0) {
                    Toast.makeText(this, "There are no items in this inventory", Toast.LENGTH_SHORT).show();
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
            case R.id.action_preload:
                //startActivity(MainActivity.getPreloadIntent(this));
                //startActivity(new Intent(this, PreloadLocationsActivity.class));
                //finish();
                //Toast.makeText(this, "Preload mode is not ready yet", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.cancel_save:
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
            //} catch (InterruptedException e) {
            //}
            iScanner.aDecodeSetDecodeEnable(1);
            iScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private int getItemCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_ITEM_CLAUSE + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    private int getContainerCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + IS_LIKE_CONTAINER_CLAUSE + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }

    /*public int getLocationCount() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + LocationTable.NAME + ";", null);
        cursor.moveToFirst();
        int count = cursor.getInt(cursor.getColumnIndex(cursor.getColumnNames()[0]));
        cursor.close();
        return count;
    }*/

    private void updateInfo() {
        //Log.v(TAG, "Updating info");
        ((TextView) findViewById(R.id.current_location)).setText(lastLocationBarcode);
        ((TextView) findViewById(R.id.last_scan)).setText(lastItemBarcode);
        ((TextView) findViewById(R.id.total_items)).setText(String.valueOf(itemCount));
        ((TextView) findViewById(R.id.total_containers)).setText(String.valueOf(containerCount));
    }

    private void preSave() {
        progressBar.setProgress(0);
        //progressBar.setVisibility(View.VISIBLE);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(false);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(true);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(false);
        mOptionsMenu.findItem(R.id.action_preload).setVisible(false);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private void postSave() {
        saveTask = null;
        //progressBar.setVisibility(View.GONE);
        progressBar.setProgress(0);
        mOptionsMenu.findItem(R.id.action_save_to_file).setVisible(true);
        mOptionsMenu.findItem(R.id.cancel_save).setVisible(false);
        mOptionsMenu.findItem(R.id.action_remove_all).setVisible(true);
        mOptionsMenu.findItem(R.id.action_preload).setVisible(true);
        onPrepareOptionsMenu(mOptionsMenu);
    }

    private static final String alphaNumeric = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private void randomScan() {
        Random r = new Random();
        int temp = r.nextInt(10);
        String barcode = (lastLocationId == -1 | temp < 2) ? "V" : (temp < 3 ? "m1" : (temp < 4 ? "M" : (temp < 7 ? "e1" : "E")));

        for (int i = r.nextInt(5) + 5; i > 0; i--)
            barcode = barcode.concat(String.valueOf(alphaNumeric.charAt(r.nextInt(alphaNumeric.length()))).toUpperCase());

        if (isLocation(barcode))
            barcode = barcode.toUpperCase();

        scanBarcode(barcode);
    }

    private void scanBarcode(String barcode) {
        String tags = "";
        if (saveTask != null) {
            vibrate(300);
            Toast.makeText(this, "Cannot scan while saving", Toast.LENGTH_SHORT).show();
            return;
        }
        //noinspection SqlResolve
        Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.BARCODE + " = ?;", new String[] {String.valueOf(barcode)});

        if (cursor.getCount() > 0) {
            cursor.close();
            vibrate(300);
            Toast.makeText(this, "Duplicate " + (isItem(barcode) ? "item" : "container") + " scanned", Toast.LENGTH_SHORT).show();
            tags = tags.concat(DUPLICATE_BARCODE_TAG);
        }

        cursor.close();

        if (isItem(barcode)) {
            addBarcodeItem(barcode, tags);
        } else if (isContainer(barcode)) {
            addBarcodeContainer(barcode, tags);
        } else if (isLocation(barcode)) {
            addBarcodeLocation(barcode, tags);
        } else {
            vibrate(300);
            Toast.makeText(this, "Barcode \"" + barcode + "\" not recognised", Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }

    private void addBarcodeItem(@NonNull String barcode, @NonNull String tags) {
        if (saveTask != null) return;
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newItem = new ContentValues();
        newItem.put(InventoryDatabase.BARCODE, barcode);
        newItem.put(InventoryDatabase.TAGS, tags);
        newItem.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newItem.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (db.insert(ItemTable.NAME, null, newItem) == -1) {
            vibrate(300);
            Log.e(TAG, "Error adding item \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding item \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        changedSinceLastArchive = true;

        //Log.v(TAG, "Added item \"" + barcode + "\" to the inventory");

        itemCount++;
        lastItemBarcode = barcode;
        itemRecyclerAdapter.notifyItemInserted(0);

        if (itemRecyclerAdapter.getItemCount() == maxItemHistory)
            itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);

        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    private void removeBarcodeItem(@NonNull InventoryItemViewHolder holder) {
        if (saveTask != null) return;
        //System.out.println("remove item " + index);
        if (db.delete(ItemTable.NAME, InventoryDatabase.ID + " = ?;", new String[] {String.valueOf(holder.getId())}) > 0) {
            //Log.v(TAG, "Removed item \"" + holder.getItemBarcode() + "\" from the inventory");
            itemCount--;

            if (holder.getAdapterPosition() == 0) {
                lastItemBarcode = getLastItemBarcode();
            }

            changedSinceLastArchive = true;

            itemRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            itemRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, itemRecyclerAdapter.getItemCount() - holder.getAdapterPosition());

            updateInfo();
        } else {
            vibrate(300);
            Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.e(TAG, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing item " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    private void addBarcodeContainer(@NonNull String barcode, @NonNull String tags) {
        if (saveTask != null) return;
        if (lastLocationId == -1) {
            Toast.makeText(this, "A location has not been scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues newContainer = new ContentValues();
        newContainer.put(InventoryDatabase.BARCODE, barcode);
        newContainer.put(InventoryDatabase.TAGS, tags);
        newContainer.put(InventoryDatabase.LOCATION_ID, lastLocationId);
        newContainer.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        if (db.insert(ItemTable.NAME, null, newContainer) == -1) {
            vibrate(300);
            Log.e(TAG, "Error adding container \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding container \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        changedSinceLastArchive = true;

        //Log.v(TAG, "Added container \"" + barcode + "\" to the inventory");

        containerCount++;
        lastItemBarcode = barcode;
        itemRecyclerAdapter.notifyItemInserted(0);

        if (itemRecyclerAdapter.getItemCount() == maxItemHistory)
            itemRecyclerAdapter.notifyItemRemoved(itemRecyclerAdapter.getItemCount() - 1);

        itemRecyclerAdapter.notifyItemRangeChanged(0, itemRecyclerAdapter.getItemCount());
        itemRecyclerView.scrollToPosition(0);

        updateInfo();
    }

    private void removeBarcodeContainer(@NonNull InventoryItemViewHolder holder) {
        if (saveTask != null) return;
        if (db.delete(ItemTable.NAME, InventoryDatabase.ID + " = " + holder.getId(), null) > 0) {
            //Log.v(TAG, "Removed container \"" + holder.getItemBarcode() + "\" from the inventory");

            containerCount--;

            if (holder.getAdapterPosition() == 0) {
                lastItemBarcode = getLastItemBarcode();
            }

            changedSinceLastArchive = true;

            itemRecyclerAdapter.notifyItemRemoved(holder.getAdapterPosition());
            itemRecyclerAdapter.notifyItemRangeChanged(holder.getAdapterPosition() + 1, itemRecyclerAdapter.getItemCount() - holder.getAdapterPosition());

            updateInfo();
        } else {
            vibrate(300);
            Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + ItemTable.Keys.ID + " = ?;", new String[] {String.valueOf(holder.getId())});
            String barcode = "";

            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                barcode = cursor.getString(cursor.getColumnIndex(ItemTable.Keys.BARCODE));
            }

            cursor.close();
            Log.e(TAG, "Error removing container " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory");
            Toast.makeText(InventoryActivity.this, "Error removing container " + (barcode.equals("") ? "#" + holder.getAdapterPosition() : "\"" + barcode +"\", #" + holder.getAdapterPosition() ) + " from the inventory", Toast.LENGTH_SHORT).show();
        }
    }

    private void addBarcodeLocation(@NonNull String barcode, String tags) {
        if (saveTask != null) return;
        ContentValues newLocation = new ContentValues();
        newLocation.put(InventoryDatabase.BARCODE, barcode);
        newLocation.put(InventoryDatabase.TAGS, tags);
        newLocation.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

        long rowID = db.insert(LocationTable.NAME, null, newLocation);

        if (rowID == -1) {
            vibrate(300);
            Log.e(TAG, "Error adding location \"" + barcode + "\" to the inventory");
            Toast.makeText(this, "Error adding location \"" + barcode + "\" to the inventory", Toast.LENGTH_SHORT).show();
            return;
        }

        changedSinceLastArchive = true;

        //Log.v(TAG, "Added location \"" + barcode + "\" to the inventory");

        //locationCount++;
        lastLocationId = rowID;
        lastLocationBarcode = barcode;

        updateInfo();
    }

    private long getLastLocationId() {
        /*Cursor cursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;", null);
        long id = -1;

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
        }

        cursor.close();
        return id;*/
        try {
            return LAST_LOCATION_ID_STATEMENT.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            return -1;
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

    private String getLastItemBarcode() {
        /*Cursor cursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " DESC LIMIT 1;", null);
        String barcode = "-";

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
        }

        cursor.close();
        return barcode;*/
        try {
            return LAST_ITEM_BARCODE_STATEMENT.simpleQueryForString();
        } catch (SQLiteDoneException e) {
            return "-";
        }
    }

    /*public void takePhoto(int index) {
        HashMap<String, Object> itemForPhoto = items.get(index);
    }*/

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

    /*public boolean isProcess(@NonNull String barcode) {
        return barcode.startsWith("L3");
    }*/

    class InventoryItemViewHolder extends RecyclerView.ViewHolder {
        //private MaterialProgressBar progressBarWaiting;
        private TextView itemBarcodeTextView;
        private ColorStateList itemBarcodeTextViewDefaultColor;
        private TextView itemLocationTextView;
        private View itemDividerView;
        private ImageButton expandedMenuButton;
        private long id = -1;
        private String itemBarcode;
        private String itemDescription;
        //private String itemTags;
        private String locationBarcode;
        private String locationDescription;
        //private String locationTags;

        InventoryItemViewHolder(final View itemView) {
            super(itemView);
            //progressBarWaiting = itemView.findViewById(R.id.progressbar_waiting);
            itemBarcodeTextView = itemView.findViewById(R.id.barcode_text_view);
            TextViewCompat.setAutoSizeTextTypeUniformWithPresetSizes(itemBarcodeTextView, autosizeInventoryItemTextSizes, TypedValue.COMPLEX_UNIT_SP);
            itemBarcodeTextViewDefaultColor = itemBarcodeTextView.getTextColors();
            itemLocationTextView = itemView.findViewById(R.id.location_text_view);
            TextViewCompat.setAutoSizeTextTypeUniformWithPresetSizes(itemLocationTextView, autosizeInventoryLocationTextSizes, TypedValue.COMPLEX_UNIT_SP);
            itemDividerView = itemView.findViewById(R.id.divider_view);
            expandedMenuButton = itemView.findViewById(R.id.menu_button);
        }

        long getId() {
            return id;
        }

        /*MaterialProgressBar getProgressBarWaiting() {
            return progressBarWaiting;
        }

        View getItemDividerView() {
            return itemDividerView;
        }

        ImageButton getExpandedMenuButton() {
            return expandedMenuButton;
        }*/

        TextView getItemBarcodeTextView() {
            return itemBarcodeTextView;
        }

        TextView getItemLocationTextView() {
            return itemLocationTextView;
        }

        String getItemBarcode() {
            return itemBarcode;
        }

        String getItemDescription() {
            return itemDescription;
        }

        String getLocationBarcode() {
            return locationBarcode;
        }

        String getLocationDescription() {
            return locationDescription;
        }

        void bindViews(long id, @NonNull String itemBarcode, String itemDescription, @NonNull String itemTags, @NonNull String locationBarcode, String locationDescription, @NonNull String locationTags) {
            this.id = id;
            this.itemBarcode = itemBarcode;
            this.itemDescription = itemDescription;
            //this.itemTags = itemTags;
            this.locationBarcode = locationBarcode;
            this.locationDescription = locationDescription;
            //this.locationTags = locationTags;

            /*if (ready)*/ {
                //progressLoading.setVisibility(View.GONE);

                itemBarcodeTextView.setText(itemBarcode);
                itemBarcodeTextView.setVisibility(View.VISIBLE);

                if (itemTags.contains(DUPLICATE_BARCODE_TAG))
                    itemBarcodeTextView.setTextColor(errorColor);
                else
                    itemBarcodeTextView.setTextColor(itemBarcodeTextViewDefaultColor);

                itemDividerView.setVisibility(View.VISIBLE);

                itemLocationTextView.setText(locationBarcode);
                itemLocationTextView.setVisibility(View.VISIBLE);

                expandedMenuButton.setVisibility(View.VISIBLE);
            } /*else {
                progressLoading.setVisibility(View.VISIBLE);
                itemBarcodeTextView.setVisibility(View.GONE);
                itemLocationTextView.setVisibility(View.GONE);
                expandedMenuButton.setVisibility(View.GONE);
            }*/
        }
    }

    private class SaveToFileTask extends AsyncTask<Void, Integer, String> {
        protected String doInBackground(Void... voids) {
            //if (cursors.length < 2)
                //return "Incorrect number of arguments passed to save thread";

            Cursor itemCursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.DATE_TIME + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " ASC;",null);
            Cursor locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " ASC;", null);

            //Cursor itemCursor = cursors[0];
            //Cursor locationCursor = cursors[1];

            //HashMap<String, Object> tempHashmap;

            //ArrayList<HashMap<String, Object>> itemHashmaps = new ArrayList<>();
            itemCursor.moveToFirst();
            int itemBarcodeIndex = itemCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int itemLocationIdIndex = itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID);
            int itemDateTimeIndex = itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            /*while (!itemCursor.isAfterLast()) {
                tempHashmap = new HashMap<>();
                tempHashmap.put(InventoryDatabase.BARCODE, itemCursor.getString(itemBarcodeIndex));
                tempHashmap.put(InventoryDatabase.LOCATION_ID, itemCursor.getInt(itemLocationIdIndex));
                tempHashmap.put(InventoryDatabase.DATE_TIME, itemCursor.getString(itemDateTimeIndex));
                itemHashmaps.add(tempHashmap);
                itemCursor.moveToNext();
            }*/

            //itemCursor.close();

            //ArrayList<HashMap<String, Object>> locationHashmaps = new ArrayList<>();
            locationCursor.moveToFirst();
            int locationIdIndex = locationCursor.getColumnIndex(InventoryDatabase.ID);
            int locationBarcodeIndex = locationCursor.getColumnIndex(InventoryDatabase.BARCODE);
            int locationDateTimeIndex = locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME);

            /*while (!locationCursor.isAfterLast()) {
                tempHashmap = new HashMap<>();
                tempHashmap.put(InventoryDatabase.ID, locationCursor.getInt(locationIdIndex));
                tempHashmap.put(InventoryDatabase.BARCODE, locationCursor.getString(locationBarcodeIndex));
                tempHashmap.put(InventoryDatabase.DATE_TIME, locationCursor.getString(locationDateTimeIndex));
                locationHashmaps.add(tempHashmap);
                locationCursor.moveToNext();
            }*/

            //locationCursor.close();

            //Log.v(TAG, "Saving to file");
            int lineIndex = -1;
            int progress = 0;
            int tempProgress;
            int maxProgress = progressBar.getMax();

            try {
                //noinspection ResultOfMethodCallIgnored
                OUTPUT_PATH.mkdirs();
                final File TEMP_OUTPUT_FILE = File.createTempFile("invinfo", ".txt", OUTPUT_PATH);
                Log.v(TAG, "Temp output file: " + TEMP_OUTPUT_FILE.getAbsolutePath());

                //Cursor itemCursor = db.rawQuery("SELECT " + ItemTable.Keys.BARCODE + ", " + ItemTable.Keys.LOCATION_ID + ", " + ItemTable.Keys.DATE_TIME + " FROM " + ItemTable.NAME + " ORDER BY " + ItemTable.Keys.ID + " ASC;",null);
                //itemCursor.moveToFirst();
                //int itemBarcodeIndex = itemCursor.getColumnIndex(InventoryDatabase.BARCODE);
                //int itemLocationIdIndex = itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID);
                //int itemDateTimeIndex = itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME);
                //Cursor locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.ID + ", " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " ASC;", null);
                //int locationIdIndex = locationCursor.getColumnIndex(InventoryDatabase.ID);
                //int locationBarcodeIndex = locationCursor.getColumnIndex(InventoryDatabase.BARCODE);
                //int locationDateTimeIndex = locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME);
                //locationCursor.moveToFirst();
                int totalItemCount = itemCursor.getCount();
                //int totalItemCount = itemHashmaps.size();
                int currentLocationId = -1;

                PrintStream printStream = new PrintStream(TEMP_OUTPUT_FILE);
                //Cursor locationCursor;

                lineIndex = 0;
                int tempLocation;
                int itemIndex = 0;
                //int locationIndex = 0;
                String tempText;

                //for (int i = 0; i < totalItemCount; i++) {
                while (!itemCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    tempProgress = (int) (((((float) itemIndex) / totalItemCount) / 1.5) * maxProgress);
                    if (progress != tempProgress) {
                    //if (true) {
                        publishProgress(tempProgress);
                        progress = tempProgress;
                    }

                    //publishProgress((itemIndex * 50) / totalItemCount);
                    //publishProgress((i * 50) / totalItemCount);
                    tempLocation = itemCursor.getInt(itemLocationIdIndex);
                    //tempLocation = (Integer) itemHashmaps.get(i).get(InventoryDatabase.LOCATION_ID);

                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                    //locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? LIMIT 1;", new String[] {String.valueOf(currentLocationId)});

                    while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                        locationCursor.moveToNext();
                        if (locationCursor.isAfterLast()) {
                            return "Location of \"" + itemCursor.getString(itemBarcodeIndex).trim() + "\" does not exist";
                        }
                    }

                    /*while (((Integer) locationHashmaps.get(locationIndex).get(InventoryDatabase.ID)) != currentLocationId) {
                        locationIndex++;
                        if (locationIndex >= locationHashmaps.size()) {
                            return "Location of \"" + ((String) itemHashmaps.get(i).get(InventoryDatabase.BARCODE)).trim() + "\" does not exist";
                        }
                    }*/

                        tempText = locationCursor.getString(locationBarcodeIndex) + "|" + locationCursor.getString(locationDateTimeIndex);
                        //tempText = locationHashmaps.get(locationIndex).get(InventoryDatabase.BARCODE) + "|" + locationHashmaps.get(locationIndex).get(InventoryDatabase.DATE_TIME);
                        //locationCursor.close();

                        //Log.v(TAG, locationText);
                        printStream.println(tempText);

                        printStream.flush();
                        lineIndex++;
                    }

                    tempText = itemCursor.getString(itemBarcodeIndex) + "|" + itemCursor.getString(itemDateTimeIndex);
                    //tempText = itemHashmaps.get(i).get(InventoryDatabase.BARCODE) + "|" + itemHashmaps.get(i).get(InventoryDatabase.DATE_TIME);

                    //Log.v(TAG, itemText);
                    printStream.println(tempText);

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
                //locationIndex = 0;
                lineIndex = 0;

                //for (int i = 0; i < totalItemCount; i++) {
                while (!itemCursor.isAfterLast()) {
                    if (isCancelled())
                        return "Save canceled";

                    tempProgress = (int) (((((float) itemIndex / totalItemCount) / 3) + (2 / 3f)) * maxProgress);
                    if (progress != tempProgress) {
                    //if (true) {
                        publishProgress(tempProgress);
                        progress = tempProgress;
                    }

                    //publishProgress((maxProgress) / 3);
                    //publishProgress((i * 50) / totalItemCount);
                    line = br.readLine();
                    tempLocation = itemCursor.getInt(itemCursor.getColumnIndex(InventoryDatabase.LOCATION_ID));
                    //tempLocation = (Integer) itemHashmaps.get(i).get(InventoryDatabase.LOCATION_ID);

                    if (tempLocation != currentLocationId) {
                        currentLocationId = tempLocation;

                        //locationCursor = db.rawQuery("SELECT " + LocationTable.Keys.BARCODE + ", " + LocationTable.Keys.DATE_TIME + " FROM " + LocationTable.NAME + " WHERE " + LocationTable.Keys.ID + " = ? LIMIT 1;", new String[] {String.valueOf(currentLocationId)});

                        while (locationCursor.getInt(locationIdIndex) != currentLocationId) {
                            locationCursor.moveToNext();
                            if (locationCursor.isAfterLast()) {
                                return "Location of \"" + itemCursor.getString(itemBarcodeIndex).trim() + "\" does not exist";
                            }
                        }

                        /*while (((Integer) locationHashmaps.get(locationIndex).get(InventoryDatabase.ID)) != currentLocationId) {
                            locationIndex++;
                            if (locationIndex >= locationHashmaps.size()) {
                                return "Location of \"" + ((String) itemHashmaps.get(i).get(InventoryDatabase.BARCODE)).trim() + "\" does not exist";
                            }
                        }*/

                        tempText = locationCursor.getString(locationBarcodeIndex) + "|" + locationCursor.getString(locationDateTimeIndex);
                        //tempText = itemHashmaps.get(i).get(InventoryDatabase.BARCODE) + "|" + itemHashmaps.get(i).get(InventoryDatabase.DATE_TIME);

                        //locationCursor.moveToFirst();
                        //tempText = locationCursor.getString(locationCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + locationCursor.getString(locationCursor.getColumnIndex(InventoryDatabase.DATE_TIME));
                        //locationCursor.close();

                        if (!tempText.equals(line)) {
                            Log.e(TAG, "Error at line " + lineIndex + " of file output\n" +
                                    "Expected String: " + tempText + "\n" +
                                    "String in file: " + line);
                            return "There was a problem verifying the output file";

                        }

                        //Log.v(TAG, locationText);

                        line = br.readLine();
                        lineIndex++;
                    }

                    tempText = itemCursor.getString(itemCursor.getColumnIndex(InventoryDatabase.BARCODE)) + "|" + itemCursor.getString(itemCursor.getColumnIndex(InventoryDatabase.DATE_TIME));
                    //tempText = itemHashmaps.get(i).get(InventoryDatabase.BARCODE) + "|" + itemHashmaps.get(i).get(InventoryDatabase.DATE_TIME);

                    if (!tempText.equals(line)) {
                        Log.e(TAG, "Error at line " + lineIndex + " of file output\n" +
                                "Expected String: " + tempText + "\n" +
                                "String in file: " + line);
                        return "There was a problem verifying the output file";
                    }

                    //Log.v(TAG, itemText);

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

            Toast.makeText(InventoryActivity.this, result, Toast.LENGTH_SHORT).show();
            if (changedSinceLastArchive)
                archiveDatabase();
            postSave();
            MediaScannerConnection.scanFile(InventoryActivity.this, new String[]{outputFile.getAbsolutePath()}, null, null);
        }

        @Override
        protected void onCancelled(String s) {
            if (savingToast != null) {
                savingToast.cancel();
                savingToast = null;
            }

            Toast.makeText(InventoryActivity.this, s, Toast.LENGTH_SHORT).show();
            postSave();
        }
    }

    private void archiveDatabase() {
        //noinspection ResultOfMethodCallIgnored
        //archiveDirectory.mkdirs();
    }

    private class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (iScanner != null) {
                try {
                    iScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;
                    if (barcode.equals(">><<")) {
                        Toast.makeText(InventoryActivity.this, "Error scanning barcode: Empty result", Toast.LENGTH_SHORT).show();
                    } else if ((barcode.startsWith(">>")) && (barcode.endsWith("<<"))) {
                        barcode = barcode.substring(2, barcode.length() - 2);
                        if (barcode.equals("SCAN AGAIN")) return;
                        scanBarcode(barcode);
                    } else if (!barcode.equals("SCAN AGAIN")){
                        Toast.makeText(InventoryActivity.this, "Malformed barcode: " + barcode, Toast.LENGTH_SHORT).show();
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

