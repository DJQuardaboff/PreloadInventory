package com.porterlee.preload;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.porterlee.preload.inventory.PreloadInventoryActivity;
import com.porterlee.preload.location.PreloadLocationsActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String[] REQUIRED_PERMISSIONS = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.SCANNER_RESULT_RECEIVER, android.Manifest.permission.BROADCAST_STICKY};
    public static final String FILE_NAME_KEY = "file_name";
    public static final String DUPLICATE_BARCODE_TAG = "D";
    public static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    public static final int MAX_ITEM_HISTORY_INCREASE = 25;
    public static final int ERROR_COLOR = Color.RED;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            askForPermission();
        else
            startApplication();
    }

    @Nullable
    public Intent getPreloadIntent(Context context) throws IOException {
        SharedPreferences sharedPreferences = getSharedPreferences("preload_preferences", MODE_PRIVATE);

        //noinspection ResultOfMethodCallIgnored
        PreloadLocationsActivity.OUTPUT_PATH.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        PreloadInventoryActivity.INPUT_PATH.mkdirs();

        if (new File(PreloadInventoryActivity.INPUT_PATH, "data.txt").exists()) {
            sharedPreferences.edit().putBoolean("ongoing_inventory", false).apply();
            return new Intent(context, PreloadInventoryActivity.class);
        }

        return new Intent(context, sharedPreferences.getBoolean("ongoing_inventory", false) ? PreloadInventoryActivity.class : PreloadLocationsActivity.class);
        /*
        File[] fileOutputs = PreloadLocationsActivity.OUTPUT_PATH.listFiles();
        File[] fileInputs = PreloadInventoryActivity.INPUT_PATH.listFiles();

        if (fileOutputs == null || fileInputs == null) {
            Log.e(TAG, "cannot access external files");
            return null;
        }

        int outputNum = fileOutputs.length;
        int inputNum = fileInputs.length;
        Intent intent;

        if (inputNum > 0) {
            intent = new Intent(context, PreloadInventoryActivity.class);
            intent.putExtra(FILE_NAME_KEY, fileInputs[0].getName());
            return intent;
        } else if (outputNum > 0) {
            intent = new Intent(context, PreloadLocationsActivity.class);
            intent.putExtra(FILE_NAME_KEY, fileOutputs[0].getName());
            return intent;
        } else {
            intent = new Intent(context, PreloadLocationsActivity.class);
            intent.putExtra(FILE_NAME_KEY, File.createTempFile("data", ".txt", PreloadLocationsActivity.OUTPUT_PATH).getName());
            return intent;
        }*/
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

        startApplication();

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startApplication() {
        try {
            startActivity(getPreloadIntent(this));
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "Could not open files on shared memory. Exiting...", Toast.LENGTH_SHORT).show();
        } catch (NullPointerException e) {
            e.printStackTrace();
            //askForPermission();
            Toast.makeText(this, "You must give external write permission for this app to work", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

