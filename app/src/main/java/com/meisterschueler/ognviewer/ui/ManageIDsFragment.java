package com.meisterschueler.ognviewer.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.ListFragment;

import com.meisterschueler.ognviewer.CustomAircraftDescriptor;
import com.meisterschueler.ognviewer.R;
import com.meisterschueler.ognviewer.common.AircraftDescriptorProviderHelper;
import com.meisterschueler.ognviewer.common.AppConstants;
import com.meisterschueler.ognviewer.common.CustomAircraftDescriptorProvider;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class ManageIDsFragment extends ListFragment implements AircraftDialogCallback {

    private CustomAircraftDescriptorProvider customAircraftDescriptorProvider;
    private Map<String, CustomAircraftDescriptor> aircraftDescriptorMap;
    private CustomAircraftDescriptorAdapter adapter;

    private static final int REQUEST_CODE_IMPORT = 123;
    private static final int REQUEST_CODE_EXPORT = 456;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.manageid_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_add_item:
                AircraftDialog.showEmptyDialog(getActivity(), this);
                break;

            case R.id.action_import_items:
                importItems();
                break;

            case R.id.action_export_items:
                exportItems();
                break;

            case R.id.action_delete_all_items:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                customAircraftDescriptorProvider.removeAll();
                                resetListAdapter();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK && data != null) {
            try {
                InputStream inputStream = getContext().getContentResolver().openInputStream(data.getData());
                int importCount = customAircraftDescriptorProvider.readFromFile(inputStream);
                if (importCount > -1) {
                    resetListAdapter();
                    Toast.makeText(getActivity(), "Imported " + importCount + " aircraft.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getActivity(), "Error during import.", Toast.LENGTH_LONG).show();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


        } else if(requestCode == REQUEST_CODE_EXPORT && resultCode == Activity.RESULT_OK && data != null) {
            try {
                OutputStream outputStream = getContext().getContentResolver().openOutputStream(data.getData());
                int exportCount = customAircraftDescriptorProvider.writeToFile(outputStream);
                if (exportCount > -1) {
                    Toast.makeText(getActivity(), "Exported " + exportCount + " aircraft.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getActivity(), "Error during export.", Toast.LENGTH_LONG).show();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        customAircraftDescriptorProvider = (CustomAircraftDescriptorProvider) AircraftDescriptorProviderHelper.getCustomDbAircraftDescriptorProvider();
        aircraftDescriptorMap = customAircraftDescriptorProvider.getAircraftDescriptorMap();

        resetListAdapter();

        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final CustomAircraftDescriptor cad = adapter.getItem(position);

                if (cad != null) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Remove " + cad.address + " ?")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    customAircraftDescriptorProvider.removeCustomAircraftDescriptor(cad);
                                    resetListAdapter();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // cancel...
                                }
                            })
                            .create()
                            .show();
                }

                return true; //true to prevent call of other click listeners
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        CustomAircraftDescriptor cad = (CustomAircraftDescriptor) getListAdapter().getItem(position);
        AircraftDialog.showDialog(getActivity(), cad, this);
    }

    private void resetListAdapter() {
        adapter = new CustomAircraftDescriptorAdapter(getActivity(), aircraftDescriptorMap.values().toArray(new CustomAircraftDescriptor[aircraftDescriptorMap.size()]));
        setListAdapter(adapter);
    }

    @Override
    public void notifyUpdated() {
        resetListAdapter();
    }

    private boolean checkStoragePermissions(int requestCode) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);
            return false;
        }

        return true;

    }

    public void importItems() {
        if (checkStoragePermissions(AppConstants.REQUEST_CODE_STORAGE_IMPORT)) {
            Intent intentImport = new Intent(Intent.ACTION_GET_CONTENT);
            intentImport.addCategory(Intent.CATEGORY_OPENABLE);
            intentImport.setType("text/comma-separated-values");
            startActivityForResult(intentImport, REQUEST_CODE_IMPORT);
        }

    }

    public void exportItems() {
        if (checkStoragePermissions(AppConstants.REQUEST_CODE_STORAGE_EXPORT)) {
            String currentTimeString = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());

            String defaultFilename = "ognviewer_export_" + currentTimeString +".csv";

            Intent intentExport = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intentExport.addCategory(Intent.CATEGORY_OPENABLE);
            intentExport.setType("text/comma-separated-values");
            intentExport.putExtra(Intent.EXTRA_TITLE, defaultFilename);
            startActivityForResult(intentExport, REQUEST_CODE_EXPORT);
        }

    }

}
