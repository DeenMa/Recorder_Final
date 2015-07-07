package com.example.deenma.recorder_final;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by deenma on 5/24/15.
 */
public class DataLayerListenerService extends WearableListenerService {

    private GoogleApiClient googleApiClient;
    String fileName, filePath;
    Integer fileSize;
    String directoryPath = "/storage/sdcard0/audioFiles/";
    private static final String TAG = "Mobile";
    private static final Integer bufferSize = 4096;

    @Override
    public void onCreate() {
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        googleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            if (item.getUri().getPath().compareTo("/path/") == 0) {
                // get name and buffer size
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                fileName = dataMap.getString("fileName");
                filePath = directoryPath + fileName;
                Log.d(TAG, filePath);
                fileSize = dataMap.getInt("fileSize");
                Log.d(TAG, "Received an asset, size " + fileSize.toString());
                Asset dataAsset = dataMap.getAsset("bufferData");
                writeDataFromAsset(dataAsset, filePath);
                Log.d(TAG, "Data transfer successful");
            }
        }
    }

    public void writeDataFromAsset (Asset asset, String filePath) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        /* ConnectionResult result =
                googleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        googleApiClient.disconnect();
        if (!result.isSuccess()) {
            return;
        } */
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                googleApiClient, asset).await().getInputStream();

        // why disconnect here?
        // googleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return;
        }

        try {
            byte[] buffer = new byte[bufferSize];
            FileOutputStream out = new FileOutputStream(filePath);
            while(assetInputStream.read(buffer) != -1) {
                out.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
