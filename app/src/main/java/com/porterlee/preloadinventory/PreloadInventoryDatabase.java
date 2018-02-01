package com.porterlee.preloadinventory;

public class PreloadInventoryDatabase {
    public static final String FILE_NAME = "inventory.db";
    public static final String FILE_NAME = "inventory.db";
    public static final String ID = "id";
    public static final String PICTURE = "picture";
    public static final String BARCODE = "barcode";
    public static final String LOCATION_ID = "location";
    public static final String DESCRIPTION = "description";
    public static final String DATE_TIME = "datetime";

    public class ItemTable {
        public static final String NAME = "items";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + BARCODE + " TEXT, " + LOCATION_ID + " INTEGER, " + DESCRIPTION + " TEXT, " + DATE_TIME + " BIGINT )";
        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String BARCODE = NAME + '.' + PreloadInventoryDatabase.BARCODE;
            public static final String LOCATION_ID = NAME + '.' + PreloadInventoryDatabase.LOCATION_ID;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }

    public class PicturesTable {
        public static final String NAME = "pictures";
        public static final String TABLE_CREATION = NAME + " ( " + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PICTURE + " BLOB, "+ DESCRIPTION + " TEXT, " + DATE_TIME + " BIGINT )";
        public class Keys {
            public static final String ID = NAME + '.' + PreloadInventoryDatabase.ID;
            public static final String PICTURE = NAME + '.' + PreloadInventoryDatabase.PICTURE;
            public static final String DESCRIPTION = NAME + '.' + PreloadInventoryDatabase.DESCRIPTION;
            public static final String DATE_TIME = NAME + '.' + PreloadInventoryDatabase.DATE_TIME;
        }
    }
}
