package com.porterlee.preload.inventory;

public class PreloadInventoryDatabase {
    public static final String FILE_NAME = "preload_inventory.db";
    public static final String DIRECTORY = "Preload/Inventory";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "_id";
    public static final String TAGS = "tags";
    public static final String BARCODE = "barcode";
    public static final String LOCATION_ID = "location_id";
    public static final String PRELOAD_ITEM_ID = "preloaded_item_id";
    public static final String PRELOAD_CONTAINER_ID = "preloaded_container_id";
    public static final String PRELOAD_LOCATION_ID = "preloaded_location_id";
    public static final String DESCRIPTION = "description";
    public static final String CASE_NUMBER = "case_number";
    public static final String ITEM_NUMBER = "item_number";
    public static final String PACKAGE = "package";
    public static final String DATE_TIME = "datetime";

    public static final class ScannedItemTable {
        public static final String NAME = "scanned_items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + LOCATION_ID + " INTEGER NOT NULL, " + PRELOAD_LOCATION_ID + " INTEGER NOT NULL, " + PRELOAD_ITEM_ID + " INTEGER NOT NULL, " + PRELOAD_CONTAINER_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + TAGS + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )";

        public static final class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.LOCATION_ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String PRELOAD_ITEM_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_ITEM_ID;
            public static final String PRELOAD_CONTAINER_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_CONTAINER_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String TAGS = NAME + '.' + PreloadInventoryDatabase.TAGS;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }

    public static final class ScannedLocationTable {
        public static final String NAME = "scanned_locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOAD_LOCATION_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + TAGS + " TEXT NOT NULL, " + DATE_TIME + " TEXT NOT NULL )";

        public static final class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String TAGS = NAME + '.' + PreloadInventoryDatabase.TAGS;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }

    public static final class PreloadedItemTable {
        public static final String NAME = "preloaded_items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOAD_LOCATION_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + CASE_NUMBER + " TEXT NOT NULL, " + ITEM_NUMBER + " TEXT NOT NULL, " + PACKAGE + " TEXT NOT NULL, " + TAGS + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL )";

        public static final class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String CASE_NUMBER = NAME + '.' + PreloadInventoryDatabase.CASE_NUMBER;
            public static final String ITEM_NUMBER = NAME + '.' + PreloadInventoryDatabase.ITEM_NUMBER;
            public static final String PACKAGE = NAME + '.' + PreloadInventoryDatabase.PACKAGE;
            public static final String TAGS = NAME + '.' + PreloadInventoryDatabase.TAGS;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
        }
    }

    public static final class PreloadedContainerTable {
        public static final String NAME = "preloaded_containers";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOAD_LOCATION_ID + " INTEGER NOT NULL, " + BARCODE + " TEXT NOT NULL, " + CASE_NUMBER + " TEXT NOT NULL, " + TAGS + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL )";

        public static final class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String CASE_NUMBER = NAME + '.' + PreloadInventoryDatabase.CASE_NUMBER;
            public static final String TAGS = NAME + '.' + PreloadInventoryDatabase.TAGS;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
        }
    }

    public static final class PreloadedLocationTable {
        public static final String NAME = "preloaded_locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + BARCODE + " TEXT NOT NULL UNIQUE, " + TAGS + " TEXT NOT NULL, " + DESCRIPTION + " TEXT NOT NULL)";

        public static final class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String TAGS = NAME + '.' + PreloadInventoryDatabase.TAGS;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
        }
    }
}
