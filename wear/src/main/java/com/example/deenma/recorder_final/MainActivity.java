
/*
*   Wear
* */

package com.example.deenma.recorder_final;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    // recording settings
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_NUM = 1;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDER_BPP = 16;
    private static final int BytesPerElement = RECORDER_BPP/8;
    int minBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    int bufferSize = minBufferSize > 1024 ? minBufferSize : 1024;

    // data
    private AudioRecord recorder = null;
    private Button recordButton = null;
    private LinearLayout linearLayout = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private String directoryPath = "/sdcard/audioFiles/";
    private static final String TAG = "Wear";
    private static Integer screenHeight;
    private static Integer screenWidth;
    private final static Integer textColor = Color.WHITE;
    private final static Integer textSize = 24;
    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // this line is to keep screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "Activity Created");
        linearLayout = new LinearLayout(this);
        linearLayout.setAnimation(null);
        isRecording = false;
        recordButton = new Button(this);
        setButton("initializing");
        linearLayout.addView(recordButton);
        setContentView(linearLayout);
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();

        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if(!googleApiClient.isConnected()) {
                    googleApiClient.connect();
                }
                if (!isRecording) {
                    isRecording = true;
                    setButton("returning");
                    startRecording();
                } else {
                    isRecording = false;
                    setButton("returning");
                    stopRecording();
                }
            }
        });


    }

    private void setButton (String state) {
        if(state == "initializing") {
            // automatically adjust to different screens
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            screenWidth = size.x; // screen width
            screenHeight = size.y; // screen height
            recordButton.setHeight(screenHeight);
            recordButton.setWidth(screenWidth);
            recordButton.setGravity(Gravity.CENTER);
            recordButton.setTextColor(textColor);

            recordButton.setBackgroundColor(Color.BLACK);
            recordButton.setText("Record");
            recordButton.setTextSize(textSize);
        }
        else {
            if(!isRecording) {
                recordButton.setBackgroundColor(Color.BLACK);
                recordButton.setText("Record");
            }
            else {
                recordButton.setBackgroundColor(Color.RED);
                recordButton.setText("Stop");
            }
        }
    }

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferSize * BytesPerElement);

        recorder.startRecording();
        final String temp = directoryPath + "temp.wav";
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile(temp);
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void writeAudioDataToFile(String filePath) {
        // Write the output audio in byte

        short sData[] = new short[bufferSize];
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            // gets the voice output from microphone to byte format

            recorder.read(sData, 0, bufferSize);
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                byte bData[] = short2byte(sData); // this is the actual data that stores the audio information
                os.write(bData, 0, bufferSize * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            Calendar cal = Calendar.getInstance();
            final String fileName = dateFormat.format(cal.getTime()) + ".wav";
            final String finalPath = directoryPath + fileName;
            // add header and write to watch
            copyWaveFile(filePath, finalPath);
            // send another copy to phone.
            sendFileToPhone(fileName);
            // delete temp file
            File deleteFile = new File(filePath);
            deleteFile.delete();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFileToPhone(String fileName) {
        // send name and buffer size
        try {
            PutDataMapRequest dataMap = PutDataMapRequest.create("/path/");
            Log.d(TAG, fileName);
            dataMap.getDataMap().putString("fileName", fileName);
            // send all data into the buffer
            final String finalPath = directoryPath + fileName;
            File file = new File(finalPath);
            InputStream fis = new FileInputStream(file);
            byte[] fileData = new byte[(int) file.length()];
            dataMap.getDataMap().putInt("fileSize", (int) file.length());
            fis.read(fileData, 0, fileData.length);
            fis.close();
            // put the fileData into Asset
            Asset assetData = Asset.createFromBytes(fileData);
            dataMap.getDataMap().putAsset("bufferData", assetData);
            // send data
            PutDataRequest request = dataMap.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(googleApiClient, request);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    // LOGS SHOW STATUS "MainActivity﹕ result available. Status: Status{statusCode=SUCCESS, resolution=null}"
                    Log.d(TAG, "result available. Status: " + dataItemResult.getStatus());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // copy one from buffer to watch and another one from buffer to phone
    private void copyWaveFile(String filePath, String finalPath) {
        FileInputStream in;
        FileOutputStream out;
        long totalAudioLen;
        long totalDataLen;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * CHANNEL_NUM/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(filePath);
            out = new FileOutputStream(finalPath);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            System.out.println("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    RECORDER_SAMPLERATE, CHANNEL_NUM, byteRate);
            while(in.read(data) != -1){
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen, int recorderSamplerate, int recorderChannels, long byteRate) throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) recorderChannels;
        header[23] = 0;
        header[24] = (byte) (recorderSamplerate & 0xff);
        header[25] = (byte) ((recorderSamplerate >> 8) & 0xff);
        header[26] = (byte) ((recorderSamplerate >> 16) & 0xff);
        header[27] = (byte) ((recorderSamplerate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "Activity Resumed");
        if(!googleApiClient.isConnected()) {
            googleApiClient.connect();
        }
        super.onStop();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Activity Stopped");
        if(googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Activity Destroyed");
        googleApiClient.disconnect();
        super.onDestroy();
    }

    // Placeholders for required connection callbacks
    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed");
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "Connected successfully");
    }
}
