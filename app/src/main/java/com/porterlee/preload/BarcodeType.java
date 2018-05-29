package com.porterlee.preload;

import org.jetbrains.annotations.NotNull;

public enum BarcodeType {
    Item(BuildConfig.is_LAM_system ? new String[] { "j1", "J" } : new String[] { "e1", "E" }),
    Container("m1", "M"),
    Location("V", "L5"),
    Process("L3"),
    Invalid();

    private final String[] prefixes;

    BarcodeType(@NotNull String... prefixes) {
        this.prefixes = prefixes;
    }

    public boolean isOfType(String barcode) {
        for (String prefix : prefixes)
            if (barcode.startsWith(prefix))
                return true;
        return false;
    }

    public static BarcodeType getBarcodeType(String barcode) {
        if (barcode == null)
            return Invalid;
        for(BarcodeType barcodeType : BarcodeType.values())
            for (String prefix : barcodeType.prefixes)
                if (barcode.startsWith(prefix))
                    return barcodeType;
        return Invalid;
    }
}
