package com.porterlee.preloadinventory;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.io.File;

public class PreloadInventoryActivity extends AppCompatActivity {
    static final File OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadLocationsDatabase.DIRECTORY);
    static final File INPUT_PATH = new File(Environment.getExternalStorageDirectory(), PreloadLocationsDatabase.DIRECTORY);
    private static final String TAG = PreloadInventoryActivity.class.getSimpleName();
    //todo finish

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preload_inventory_layout);
        //todo finish
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preload_locations_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_inventory:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class PreloadItemViewHolder extends RecyclerView.ViewHolder {
        //todo finish

        PreloadItemViewHolder(final View itemView) {
            super(itemView);
            //todo finish
        }

        void bindViews(Cursor cursor) {
            //todo finish
        }
    }
}

