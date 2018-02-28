package com.porterlee.preload.inventory;

@SuppressWarnings("WeakerAccess")
public class PreloadInventoryDatabase {
    public static final String FILE_NAME = "preload_inventory.db";
    public static final String DIRECTORY = "Preload/Inventory";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "_id";
    public static final String PRELOADED_ITEM_ID = "preloaded_item_id";
    public static final String SCANNED_LOCATION_ID = "scanned_location_id";
    public static final String PRELOADED_LOCATION_ID = "preloaded_location_id";
    public static final String PRELOADED_ITEM_COUNT = "preloaded_item_count";
    public static final String BARCODE = "barcode";
    public static final String CASE_NUMBER = "case_number";
    public static final String ITEM_NUMBER = "item_number";
    public static final String PACKAGING = "packaging";
    public static final String DESCRIPTION = "description";
    public static final String SOURCE = "source";
    public static final String STATUS = "status";
    public static final String DATE_TIME = "datetime";

    public static class ItemTable {
        public static final String NAME = "items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOADED_ITEM_ID + " INTEGER NOT NULL, " + SCANNED_LOCATION_ID + " INTEGER NOT NULL, " + PRELOADED_LOCATION_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + CASE_NUMBER + " TEXT NOT NULL, " + ITEM_NUMBER + " TEXT NOT NULL, " + PACKAGING + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL, " + SOURCE + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )";

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
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }

        public static class Source {
            public static final String SCANNER = "scanner";
            public static final String PRELOADED = "preload";
        }

        public static class Status {
            public static final String SCANNED = "scanned";
            public static final String NOT_SCANNED = "not_scanned";
        }
    }

    public static class LocationTable {
        public static final String NAME = "locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOADED_LOCATION_ID + " INTEGER NOT NULL, " + PRELOADED_ITEM_COUNT + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL, " + PRELOADED_ITEM_COUNT + " INTEGER NOT NULL, " + SOURCE + " TEXT NOT NULL, " + STATUS + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )";

        public static class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOADED_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOADED_LOCATION_ID;
            public static final String PRELOADED_ITEM_COUNT = NAME + '.' + PreloadInventoryDatabase.PRELOADED_ITEM_COUNT;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
            public static final String SOURCE = NAME + '.' + PreloadInventoryDatabase.SOURCE;
            public static final String STATUS = NAME + '.' + PreloadInventoryDatabase.STATUS;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }

        public static class Source {
            public static final String SCANNER = "scanner";
            public static final String PRELOADED = "preload";
        }

        public static class Status {
            public static final String EMPTY = "empty";
            public static final String STARTED = "started";
            public static final String COMPLETE = "complete";
        }
    }
}
