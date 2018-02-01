package com.porterlee.preloadinventory;

public class PreloadLocationsDatabase {
    public static final String FILE_NAME = "preload_locations.db";
    public static final String DIRECTORY = "Preload/Locations";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "id";
    public static final String BARCODE = "barcode";
    public static final String DESCRIPTION = "description";
    public static final String TAGS = "tags";
    public static final String DATE_TIME = "datetime";

    public class LocationTable {
        public static final String NAME = "locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + BARCODE + " TEXT, " + DESCRIPTION + " TEXT, " + TAGS + " TEXT, " + DATE_TIME + " BIGINT )";

        public class Keys {
            public static final String ID = NAME + '.' + InventoryDatabase.ID;
            public static final String BARCODE = NAME + '.' + InventoryDatabase.BARCODE;
            public static final String DESCRIPTION = NAME + '.' + InventoryDatabase.DESCRIPTION;
            public static final String TAGS = NAME + '.' + InventoryDatabase.TAGS;
            public static final String DATE_TIME = NAME + '.' + InventoryDatabase.DATE_TIME;
        }
    }
}
