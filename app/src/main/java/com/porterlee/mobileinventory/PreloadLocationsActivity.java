package com.porterlee.mobileinventory;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.porterlee.mobileinventory.PreloadLocationsDatabase.LocationTable;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScannerService;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

public class PreloadLocationsActivity extends AppCompatActivity {
    private static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadLocationsDatabase.DIRECTORY);
    private static final String DUPLICATE_BARCODE_TAG = "D";
    private static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = PreloadLocationsActivity.class.getSimpleName();
    private static final int MAX_ITEM_HISTORY_INCREASE = 25;
    private static final int errorColor = Color.RED;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preload_locations_layout);

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
        outputFile.mkdirs();
        databaseFile = new File(getFilesDir() + "/" + PreloadLocationsDatabase.DIRECTORY + "/" + PreloadLocationsDatabase.FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        databaseFile.mkdirs();

        try {
            initialize();
        } catch (SQLiteCantOpenDatabaseException e) {
            try {
                if (databaseFile.renameTo(File.createTempFile("error", ".db", archiveDirectory)))
                    Toast.makeText(this, "There was an error loading the database. It has been archived", Toast.LENGTH_SHORT).show();
            } catch (IOException e1) {
                //Toast.makeText(this, "There was an error loading the database and it could not be archived", Toast.LENGTH_SHORT).show();
                e1.printStackTrace();
                AlertDialog.Builder builder = new AlertDialog.Builder(PreloadLocationsActivity.this);
                builder.setCancelable(true);
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
        }
    }

    private void initialize() throws SQLiteCantOpenDatabaseException {
        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);

        //db.execSQL("DROP TABLE IF EXISTS " + LocationTable.NAME);

        db.execSQL("CREATE TABLE IF NOT EXISTS " + LocationTable.TABLE_CREATION);

        LAST_LOCATION_BARCODE_STATEMENT = db.compileStatement("SELECT " + LocationTable.Keys.BARCODE + " FROM " + LocationTable.NAME + " ORDER BY " + LocationTable.Keys.ID + " DESC LIMIT 1;");

        locationCount = getLocationCount();
        lastLocationBarcode = getLastLocationBarcode();

        progressBar = findViewById(R.id.progress_saving);

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
                long id = cursor.getLong(cursor.getColumnIndex(InventoryDatabase.ID));
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
                                builder.setMessage("Are you sure you want to remove this location?");
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

                final long locationId = cursor.getInt(cursor.getColumnIndex(InventoryDatabase.ID));
                final String locationBarcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.BARCODE));
                final String locationDescription = cursor.getString(cursor.getColumnIndex(InventoryDatabase.DESCRIPTION));
                final String locationTags = cursor.getString(cursor.getColumnIndex(InventoryDatabase.TAGS));

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
        inflater.inflate(R.menu.preload_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_inventory:
                startActivity(new Intent(this, InventoryActivity.class));
                finish();
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
            //tags = tags.concat(DUPLICATE_BARCODE_TAG);
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
        newItem.put(InventoryDatabase.BARCODE, barcode);
        newItem.put(InventoryDatabase.TAGS, tags);
        newItem.put(InventoryDatabase.DATE_TIME, String.valueOf(formatDate(System.currentTimeMillis())));

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
                barcode = cursor.getString(cursor.getColumnIndex(InventoryDatabase.ItemTable.Keys.BARCODE));
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
        //todo finish

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

