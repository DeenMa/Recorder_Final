
/*
*   Phone
* */

package com.example.deenma.recorder_final;

import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wallet.fragment.Dimension;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends ActionBarActivity implements
        DataApi.DataListener, GoogleApiClient.ConnectionCallbacks,
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
    boolean isRecording;
    AudioRecord recorder = null;
    Thread recordingThread = null;
    MediaPlayer mediaPlayer;

    private static final String directoryPath = "/storage/sdcard0/audioFiles/";
    private static final String TAG = "Mobile";
    private GoogleApiClient googleApiClient;
    // itemLayouts are dynamically storing and managing each layouts while linearLayout simply shows it
    ArrayList<ItemLayout> itemLayouts;
    LinearLayout linearLayout = null;
    String fileName;
    Integer textSize = 16;
    Integer paddingLeft = 16;

    public class ButtonPlay {
        Button button;
        ItemLayout associatedLayout;
        ButtonPlay (ItemLayout ll) {
            button = new Button(getApplicationContext());
            button.setText("Play");
            button.setPadding(paddingLeft, 0, 0, 0);
            associatedLayout = ll;
        }
    };

    public class ButtonDelete {
        Button button;
        ItemLayout associatedLayout;
        ButtonDelete (ItemLayout ll) {
            button = new Button(getApplicationContext());
            button.setPadding(paddingLeft, 0, 0, 0);
            associatedLayout = ll;
        }
    };

    public class ItemLayout {
        public LinearLayout itemLayout;
        public String fileName;
        public ButtonPlay play;
        public ButtonDelete delete;

        ItemLayout (String fn) {
            itemLayout = new LinearLayout(getApplicationContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            fileName = fn;
            TextView viewFileName = new TextView(getApplicationContext());
            viewFileName.setText(fileName);
            viewFileName.setTextColor(Color.BLACK);
            viewFileName.setTextSize(textSize);
            viewFileName.setPadding(paddingLeft, 0, 0, 0);
            play = new ButtonPlay (this);
            delete = new ButtonDelete (this);
            play.button.setClickable(false);
            delete.button.setText("Delete");
            delete.button.setClickable(false);
            itemLayout.addView(viewFileName);
            itemLayout.addView(play.button);
            itemLayout.addView(delete.button);
            modifyLayout(true);
        }

        void modifyLayout (final boolean message) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(message == true) {
                                linearLayout.addView(itemLayout,0);
                            }
                            else {
                                linearLayout.removeView(itemLayout);
                            }
                        }
                    });
                }
            };
            thread.start();
        }

        private void onPlay(boolean start) {
            if (start) {
                startPlaying();
            } else {
                stopPlaying();
            }
        }

        private void startPlaying() {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(directoryPath + play.associatedLayout.fileName);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (IOException e) {
                Log.e(TAG, "prepare() failed");
            }
        }

        private void stopPlaying() {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        void enableClick () {
            play.button.setClickable(true);
            play.button.setOnClickListener(new View.OnClickListener() {
                boolean mediaStartPlaying = true;
                public void onClick (View v) {
                    onPlay(mediaStartPlaying);
                    if (mediaStartPlaying) {
                        play.button.setText("Stop");
                    } else {
                        play.button.setText("Play");
                    }
                    mediaStartPlaying = !mediaStartPlaying;
                }
            });

            delete.button.setClickable(true);
            delete.button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    File deleteFile = new File (directoryPath + delete.associatedLayout.fileName);
                    deleteFile.delete();
                    modifyLayout(false);
                    itemLayouts.remove(this);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        linearLayout = (LinearLayout) findViewById(R.id.sub_linear_layout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        isRecording = false;
        final Button phoneRecord = (Button)findViewById(R.id.phone_record);
        phoneRecord.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(!isRecording) {
                    isRecording = true;
                    phoneRecord.setBackgroundColor(Color.RED);
                    phoneRecord.setText("Stop");
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
                else {
                    isRecording = false;
                    phoneRecord.setBackgroundColor(Color.BLACK);
                    phoneRecord.setText("Record");
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                    recordingThread = null;
                }
            }
        });

        final Button phoneDelete = (Button)findViewById(R.id.delete_all);
        phoneDelete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                while(!itemLayouts.isEmpty()) {
                    File deleteFile = new File (directoryPath + itemLayouts.get(0).fileName);
                    deleteFile.delete();
                    linearLayout.removeView(itemLayouts.get(0).itemLayout);
                    itemLayouts.remove(itemLayouts.get(0));
                }
            }
        });

        // add layout for all views
        File fileList = new File(directoryPath);
        itemLayouts = new ArrayList<>();
        for(File f : fileList.listFiles()) {
            if(f.getName().compareTo(".ttxfolder") != 0) { // this one is the only trouble
                Log.d(TAG, f.getName().length()+"   " + f.getName());
                ItemLayout fLayout = new ItemLayout(f.getName());
                // !!! add the condition (transmission complete) to enableClick later.
                fLayout.enableClick();
                itemLayouts.add(fLayout);
                Log.d(TAG, itemLayouts.get(itemLayouts.size() - 1).play.button.isClickable() ? "true" : "false");
            }
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

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
            ItemLayout fLayout = new ItemLayout(fileName);
            // !!! add the condition (transmission complete) to enableClick later.
            fLayout.enableClick();
            itemLayouts.add(fLayout);

            // delete temp file
            File deleteFile = new File(filePath);
            deleteFile.delete();
            os.close();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Connected to Google Api Service");
        }
        Wearable.DataApi.addListener(googleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "Connection failed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        // activity is not used to receive data, only used to create the layout for the new item.
        // the task of receiving data has moved to DataLayerListernerService.java
        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            if (item.getUri().getPath().compareTo("/path/") == 0) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                fileName = dataMap.getString("fileName");
                ItemLayout newLayout = new ItemLayout(fileName);
                newLayout.enableClick();
                itemLayouts.add(newLayout);
                Log.d(TAG, itemLayouts.get(itemLayouts.size()-1).play.button.isClickable()?"true":"false");
            }
        }
    }
}
