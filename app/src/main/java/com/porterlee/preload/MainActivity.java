package com.porterlee.preload;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.porterlee.preload.inventory.PreloadInventoryActivity;
import com.porterlee.preload.location.PreloadLocationsActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String[] REQUIRED_PERMISSIONS = new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE };
    public static final String DATE_FORMAT = "yyyy/MM/dd kk:mm:ss";
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (askForPermission()) {
                startApplication();
            }
        } else {
            startApplication();
        }
    }

    @Nullable
    public Intent getPreloadIntent(Context context) throws IOException {
        SharedPreferences sharedPreferences = getSharedPreferences("preload_preferences", MODE_PRIVATE);

        //noinspection ResultOfMethodCallIgnored
        PreloadLocationsActivity.EXTERNAL_PATH.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        PreloadInventoryActivity.EXTERNAL_PATH.mkdirs();

        return new Intent(context, sharedPreferences.getBoolean("ongoing_inventory", false) ? PreloadInventoryActivity.class : PreloadLocationsActivity.class);
        /*
        File[] fileOutputs = PreloadLocationsActivity.EXTERNAL_PATH.listFiles();
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
            intent.putExtra(FILE_NAME_KEY, File.createTempFile("data", ".txt", PreloadLocationsActivity.EXTERNAL_PATH).getName());
            return intent;
        }*/
    }

    private boolean askForPermission() {
        //Log.e(TAG, "askForPermission()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToGrant = new ArrayList<>();
            for (String requiredPermission : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToGrant.add(requiredPermission);
                    //Log.e(TAG, requiredPermission + " is not granted");
                }
            }

            if (permissionsToGrant.size() > 0) {
                String[] permissionsStringsToGrant = new String[permissionsToGrant.size()];
                for (int i = 0; i < permissionsStringsToGrant.length; i++) {
                    permissionsStringsToGrant[i] = permissionsToGrant.get(i);
                }

                ActivityCompat.requestPermissions(this, permissionsStringsToGrant, 0);
                return false;
            }
        }
        return true;
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
        //Log.e(TAG, "startApplication()");
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

