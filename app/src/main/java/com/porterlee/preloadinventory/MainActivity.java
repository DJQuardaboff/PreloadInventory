package com.porterlee.preloadinventory;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String[] REQUIRED_PERMISSIONS = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.SCANNER_RESULT_RECEIVER, android.Manifest.permission.BROADCAST_STICKY};
    static final String DUPLICATE_BARCODE_TAG = "D";
    static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    static final int MAX_ITEM_HISTORY_INCREASE = 25;
    static final int ERROR_COLOR = Color.RED;
    private static final String TAG = MainActivity.class.getSimpleName();
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        askForPermission();

        //noinspection ResultOfMethodCallIgnored
        PreloadLocationsActivity.OUTPUT_PATH.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        PreloadInventoryActivity.INPUT_PATH.mkdirs();

        File[] fileOutputs = PreloadLocationsActivity.OUTPUT_PATH.listFiles();
        File[] fileInputs = PreloadInventoryActivity.OUTPUT_PATH.listFiles();

        if ()

        if (fileOutputs.length)

        setContentView(R.layout.main_layout);

        Log.d(TAG, "Preload");
        startActivity(getPreloadIntent(MainActivity.this));
    }

    public static Intent getPreloadIntent(Context context) {

        return new Intent(context, PreloadLocationsActivity.class);
    }

    private boolean askForPermission() {
        boolean hasPermissions = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToGrant = new ArrayList<>();
            for (String requiredPermission : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToGrant.add(requiredPermission);
                    hasPermissions = false;
                }
            }

            Object[] permissionStringsAsObjects = permissionsToGrant.toArray();
            String[] permissionStrings = new String[permissionStringsAsObjects.length];

            for (int i = 0; i < permissionStrings.length; i++)
                permissionStrings[i] = (String) permissionStringsAsObjects[i];

            ActivityCompat.requestPermissions(this, permissionStrings, 0);
        }
        return hasPermissions;
    }

    private void askForMode() {
        if (!dialog.isShowing()) dialog.show();
        /*final AlertDialog d = dialog;

        if(d != null) {
            d.getButton(Dialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Preload");
                    Toast.makeText(MainActivity.this, "Preload mode is not ready yet", Toast.LENGTH_SHORT).show();
                }
            });
        }*/
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean havePermissions = true;
        if (permissions.length != 0 && grantResults.length != 0) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    havePermissions = false;
                //System.out.print(result == PackageManager.PERMISSION_GRANTED);
            }
        }

        //if (!havePermissions)
            //askForPermission();
        //else
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        askForPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        askForMode();
    }
}

