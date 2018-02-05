package com.porterlee.preloadinventory;

public class PreloadInventoryDatabase {
    public static final String FILE_NAME = "inventory.db";
    public static final String DIRECTORY = "Preload/Inventory";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "id";
    public static final String BARCODE = "barcode";
    public static final String LOCATION_ID = "location_id";
    public static final String PRELOAD_LOCATION_ID = "preload_location_id";
    public static final String PRELOAD_ITEM_ID = "preload_item_id";
    public static final String DESCRIPTION = "description";
    public static final String CASE_NUMBER = "case_number";
    public static final String ITEM_NUMBER = "item_number";
    public static final String PACKAGE = "package";
    public static final String DATE_TIME = "datetime";

    public class ScannedItemTable {
        public static final String NAME = "scanned_items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + LOCATION_ID + " INTEGER, " + PRELOAD_ITEM_ID + " INTEGER, " + BARCODE + " TEXT, " + DATE_TIME + " BIGINT )";

        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.LOCATION_ID;
            public static final String PRELOAD_ITEM_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_ITEM_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }

    public class ScannedLocationTable {
        public static final String NAME = "scanned_locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOAD_LOCATION_ID + " INTEGER, " + BARCODE + " TEXT, " + DATE_TIME + " BIGINT )";

        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }

    public class PreloadItemTable {
        public static final String NAME = "preload_items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PRELOAD_LOCATION_ID + " INTEGER, " + BARCODE + " TEXT, " + CASE_NUMBER + " TEXT, " + ITEM_NUMBER + " TEXT, " + PACKAGING + " TEXT, " + DESCRIPTION + " TEXT )";

        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PRELOAD_LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.PRELOAD_LOCATION_ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String CASE_NUMBER = NAME + '.' + PreloadInventoryDatabase.CASE_NUMBER;
            public static final String ITEM_NUMBER = NAME + '.' + PreloadInventoryDatabase.ITEM_NUMBER;
            public static final String PACKAGE = NAME + '.' + PreloadInventoryDatabase.PACKAGE;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
        }
    }

    public class PreloadLocationTable {
        public static final String NAME = "preload_locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + BARCODE + " TEXT, " + DESCRIPTION + " TEXT )";

        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
        }
    }
}
