package com.porterlee.preloadinventory;

public class PreloadLocationsDatabase {
    public static final String FILE_NAME = "preload_locations.db";
    public static final String DIRECTORY = "Preload/Locations";
    public static final String ARCHIVE_DIRECTORY = "Archives";
    public static final String ID = "id";
    public static final String BARCODE = "barcode";
    public static final String DATE_TIME = "datetime";

    public class LocationTable {
        public static final String NAME = "locations";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + BARCODE + " TEXT, " + DATE_TIME + " BIGINT )";

        public class Keys {
            public static final String ID = NAME + '.' + PreloadLocationsDatabase.ID;
            public static final String BARCODE = NAME + '.' + PreloadLocationsDatabase.BARCODE;
            public static final String DATE_TIME = NAME + '.' + PreloadLocationsDatabase.DATE_TIME;
        }
    }
}
