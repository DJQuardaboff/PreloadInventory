package com.porterlee.preload.inventory;

import android.database.sqlite.SQLiteDatabase;

@SuppressWarnings("WeakerAccess")
public class PreloadInventoryDatabase {
    public static final String FILE_NAME = "preload_inventory.db";
    public static final String DIRECTORY = "Preload/Inventory";
    public static final String DIRECTORY2 = "Inventory";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "_id";
    public static final String PRELOADED_ITEM_ID = "preloaded_item_id";
    public static final String SCANNED_LOCATION_ID = "scanned_location_id";
    public static final String PRELOADED_LOCATION_ID = "preloaded_location_id";
    public static final String PROGRESS = "progress";
    public static final String BARCODE = "barcode";
    public static final String CASE_NUMBER = "case_number";
    public static final String ITEM_NUMBER = "item_number";
    public static final String PACKAGING = "packaging";
    public static final String DESCRIPTION = "description";
    public static final String SOURCE = "source";
    public static final String STATUS = "status";
    public static final String ITEM_TYPE = "item_type";
    public static final String DATE_TIME = "date_time";
    public static final String ITEM_BARCODE_INDEX = "item_barcode_index";
    public static final String LOCATION_BARCODE_INDEX = "location_barcode_index";

    public static class ItemTable {
        public static final String NAME = "items";

        static public void create(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOADED_ITEM_ID + " INTEGER NOT NULL, " + SCANNED_LOCATION_ID + " INTEGER NOT NULL, " + PRELOADED_LOCATION_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + CASE_NUMBER + " TEXT NOT NULL, " + ITEM_NUMBER + " TEXT NOT NULL, " + PACKAGING + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL, " + SOURCE + " TEXT NOT NULL, " + STATUS + " TEXT NOT NULL, " + ITEM_TYPE + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + ITEM_BARCODE_INDEX + " ON " + NAME + " ( " + BARCODE + " );");
        }

        public static class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOADED_ITEM_ID = NAME + '.' + PreloadInventoryDatabase.PRELOADED_ITEM_ID;
            public static final String SCANNED_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.SCANNED_LOCATION_ID;
            public static final String PRELOADED_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOADED_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String CASE_NUMBER = NAME + '.' + PreloadInventoryDatabase.CASE_NUMBER;
            public static final String ITEM_NUMBER = NAME + '.' + PreloadInventoryDatabase.ITEM_NUMBER;
            public static final String PACKAGING = NAME + '.' + PreloadInventoryDatabase.PACKAGING;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
            public static final String SOURCE = NAME + '.' + PreloadInventoryDatabase.SOURCE;
            public static final String STATUS = NAME + '.' + PreloadInventoryDatabase.STATUS;
            public static final String ITEM_TYPE = NAME + '.' + PreloadInventoryDatabase.ITEM_TYPE;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }

        public static class Source {
            public static final String SCANNER = "S";
            public static final String PRELOAD = "P";
        }

        public static class Status {
            public static final String SCANNED = "S";
            public static final String MISPLACED = "M";
        }

        public static class ItemType {
            public static final String ITEM = "I";
            public static final String CASE_CONTAINER = "C";
            public static final String BULK_CONTAINER = "B";
        }
    }

    public static class LocationTable {
        public static final String NAME = "locations";

        static public void create(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOADED_LOCATION_ID + " INTEGER NOT NULL, " + PROGRESS + " REAL NOT NULL, " + BARCODE + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL, " + SOURCE + " TEXT NOT NULL, " + STATUS + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + LOCATION_BARCODE_INDEX + " ON " + NAME + " ( " + BARCODE + " );");
        }

        public static class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOADED_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOADED_LOCATION_ID;
            public static final String PROGRESS = NAME + '.' + PreloadInventoryDatabase.PROGRESS;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
            public static final String SOURCE = NAME + '.' + PreloadInventoryDatabase.SOURCE;
            public static final String STATUS = NAME + '.' + PreloadInventoryDatabase.STATUS;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }

        public static class Source {
            public static final String SCANNER = "S";
            public static final String PRELOAD = "P";
        }

        public static class Status {
            public static final String WARNING = "W";
            public static final String ERROR = "E";
            public static final String WARNING_ERROR = "WE";
        }
    }
}
