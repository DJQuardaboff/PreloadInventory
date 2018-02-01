package com.porterlee.preloadinventory;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private static final String[] requiredPermissions = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.SCANNER_RESULT_RECEIVER, android.Manifest.permission.BROADCAST_STICKY};
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String INTENDED_DEVICE = "XM5";
    private static final String INTENDED_MANUFACTURER = "Janam Technologies";
    private static final String INTENDED_MODEL = "XM5";
    private static final String INTENDED_PRODUCT = "XM5";
    private static final int INTENDED_SDK_INT = 17;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File[] outputs =

        setContentView(R.layout.main_layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("Select Mode");
        builder.setMessage("Would you like to preload locations or start a standard inventory?");
        builder.setNegativeButton("Preload", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Preload");
                startActivity(getPreloadIntent(MainActivity.this));
            }
        });
        builder.setPositiveButton("Standard Inventory", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Standard Inventory");
                startActivity(new Intent(MainActivity.this, InventoryActivity.class));
            }
        });
        dialog = builder.create();
    }

    public static Intent getPreloadIntent(Context context) {

        return new Intent(context, PreloadLocationsActivity.class);
    }

    private boolean askForPermission() {
        boolean hasPermissions = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToGrant = new ArrayList<>();
            for (String requiredPermission : requiredPermissions) {
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

