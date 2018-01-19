package com.example.sunyao.audiovisualizer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.sunyao.audiovisualizer.utils.AudioDataUtil;
import com.example.sunyao.audiovisualizer.utils.FileUtil;
import com.example.sunyao.audiovisualizer.view.AudioWaveView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "visualizer_tag";
    private static final int PERMISSION_REQUEST_CODE = 0x33;
    private static final int BYTES_HANDLER = 0x01;

    private static final int RATE_IN_HZ = 44100;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int RECORD_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;

    private static final int WAVE_VIEW = 0;
    private static final int RECORD_WAVE_VIEW = 1;

    @BindView(R.id.wave_view)
    AudioWaveView mWaveView;

    @BindView(R.id.record_wave_view)
    AudioWaveView mRecordWaveView;

    @BindView(R.id.btn_log_buff)
    Button mBtnLogBuff;

    @BindView(R.id.btn_record)
    Button mBtnRecord;

    @BindView(R.id.btn_record_pause)
    Button mBtnRecordPause;

    @BindView(R.id.btn_record_stop)
    Button mBtnRecordStop;

    @BindView(R.id.btn_decode)
    Button mBtnDecode;

    private int mBuffSize;

    MediaPlayer mPlayer;
    Visualizer mVisualizer;

    private double[] mWaveResults;
    private double[] mRecordWaveResults;

    private AudioRecord mAudioRecord;
    private MediaCodec mMediaDecode;
    private MediaExtractor mMediaExtractor;

    private boolean isRecording;
    private boolean isPause;

    private boolean isDecode;

    private Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initHandler();
        initDecode();
        initMediaPlayer();
        initView();
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("a background task for cal bytes");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void dispatchMessage(Message msg) {
                if (msg.what == BYTES_HANDLER) {
                    if (msg.arg1 == WAVE_VIEW) {

                        byte[] bytes = ((byte[]) msg.obj);

                        byte[] b = Arrays.copyOf(bytes, bytes.length);
                        short[] shorts = new short[b.length / 2];
                        ByteBuffer.wrap(b)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .get(shorts);
                        double[] result = AudioDataUtil.normalize(shorts);

                        if (mWaveResults != null) {
                            int originLen = mWaveResults.length;
                            mWaveResults = Arrays.copyOf(mWaveResults, mWaveResults.length + result.length);
                            System.arraycopy(result, 0, mWaveResults, originLen, result.length);
                        } else {
                            mWaveResults = result;
                        }

                        mWaveView.setAudioData(mWaveResults);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mWaveView.invalidate();
                            }
                        });
                    } else if (msg.arg1 == RECORD_WAVE_VIEW) {
                        byte[] bytes = ((byte[]) msg.obj);

                        byte[] b = Arrays.copyOf(bytes, bytes.length);
                        short[] shorts = new short[b.length / 2];
                        ByteBuffer.wrap(b)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .get(shorts);
                        double[] result = AudioDataUtil.normalize(shorts);

                        if (mRecordWaveResults != null) {
                            int originLen = mRecordWaveResults.length;
                            mRecordWaveResults = Arrays.copyOf(mRecordWaveResults, mRecordWaveResults.length + result.length);
                            System.arraycopy(result, 0, mRecordWaveResults, originLen, result.length);
                        } else {
                            mRecordWaveResults = result;
                        }

                        mRecordWaveView.setAudioData(mRecordWaveResults);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mRecordWaveView.invalidate();
                            }
                        });
                    }

                }
            }
        };
    }

    private void initDecode() {
        try {
            AssetFileDescriptor afd = getAssets().openFd("FC_100001.mp3");

            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mMediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio")) {
                    mMediaExtractor.selectTrack(i);
                    mMediaDecode = MediaCodec.createDecoderByType(mime);
                    mMediaDecode.configure(format, null, null, 0);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initView() {
        mBtnRecordPause.setEnabled(false);
        mBtnRecordStop.setEnabled(false);
    }

    private void initMediaPlayer() {
        mPlayer = new MediaPlayer();
        AudioAttributes aas = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        mPlayer.setAudioAttributes(aas);

        try {
            AssetFileDescriptor afd = getAssets().openFd("FC_100001.mp3");
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mVisualizer.setEnabled(false);
//                mWaveResults = null;
            }
        });

        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {


            }
        });
    }

    private void logBuff() {
        mVisualizer = new Visualizer(mPlayer.getAudioSessionId());
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                byte[] b = Arrays.copyOf(waveform, waveform.length);
                short[] shorts = new short[b.length / 2];
                ByteBuffer.wrap(b)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shorts);
                double[] result = AudioDataUtil.normalize(shorts);

                Log.d(TAG, "onWaveFormDataCapture: " + Arrays.toString(result));

                if (mWaveResults != null) {
                    int originLen = mWaveResults.length;
                    mWaveResults = Arrays.copyOf(mWaveResults, mWaveResults.length + result.length);
                    System.arraycopy(result, 0, mWaveResults, originLen, result.length);
                } else {
                    mWaveResults = result;
                }
                mWaveView.setAudioData(mWaveResults);
                mWaveView.invalidate();
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                byte[] b = Arrays.copyOf(fft, fft.length);
                short[] shorts = new short[b.length / 2];
                ByteBuffer.wrap(b)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shorts);
                double[] result = AudioDataUtil.normalize(shorts);
                Log.d(TAG, "onFftDataCapture: " + Arrays.toString(result));
            }
        }, Visualizer.getMaxCaptureRate() / 2, true, false);

        if (mWaveResults != null) {
            mWaveResults = null;
        }
        mVisualizer.setEnabled(true);
        mPlayer.start();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            permissionCheck();
        }
    }

    private void permissionCheck() {
        List<String> permissionList = new ArrayList<>();
        permissionList.add(Manifest.permission.RECORD_AUDIO);
        permissionList.add(Manifest.permission.CAMERA);
        permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        List<String> checkList = new ArrayList<>();
        for (String s : permissionList) {
            if (ContextCompat.checkSelfPermission(this, s)
                    != PackageManager.PERMISSION_GRANTED) {
                checkList.add(s);
            }
        }

        if (checkList.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    checkList.toArray(new String[checkList.size()]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void initAudioRecord() {
        if (mAudioRecord == null) {
            mBuffSize = AudioRecord.getMinBufferSize(RATE_IN_HZ, CHANNEL, AUDIO_ENCODING);
            mAudioRecord = new AudioRecord(RECORD_SOURCE, RATE_IN_HZ, CHANNEL, AUDIO_ENCODING, mBuffSize);
        }

    }


    @OnClick({R.id.btn_log_buff, R.id.btn_record, R.id.btn_record_pause, R.id.btn_record_stop,
            R.id.btn_decode})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_log_buff:
//                logBuff();
                break;
            case R.id.btn_record:
                startRecord();
                break;
            case R.id.btn_record_pause:
                recordPause();
                break;
            case R.id.btn_record_stop:
                stopRecord();
                break;
            case R.id.btn_decode:
                startDecode();
        }
    }

    private void startDecode() {
        mMediaDecode.start();
        final ByteBuffer[] inputBuffers = mMediaDecode.getInputBuffers();
        final ByteBuffer[] outputBuffers = mMediaDecode.getOutputBuffers();
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        isDecode = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isDecode) {
                    Log.d(TAG, "startDecode: input buffer len: " + inputBuffers.length);
                    for (int i = 0; i < inputBuffers.length; i++) {
                        int inputIndex = mMediaDecode.dequeueInputBuffer(-1);

                        if (inputIndex < 0) {
                            isDecode = false;
                            return;
                        }

                        ByteBuffer buffer = inputBuffers[inputIndex];
                        buffer.clear();
                        //音轨读取数据到buffer里
                        int sampleSize = mMediaExtractor.readSampleData(buffer, 0);

                        if (sampleSize < 0) {
                            isDecode = false;
                        } else {
                            mMediaDecode.queueInputBuffer(inputIndex, 0, sampleSize, 0, 0);
                            mMediaExtractor.advance();
                        }

                        int outputIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, 10 * 1000);
                        ByteBuffer outBuffer;
                        byte[] pcmBytes;
                        while (outputIndex > 0) {
                            outBuffer = outputBuffers[outputIndex];
                            pcmBytes = new byte[bufferInfo.size];
                            outBuffer.get(pcmBytes);
                            outBuffer.clear();

                            Message message = mHandler.obtainMessage();
                            message.what = BYTES_HANDLER;
                            message.obj = pcmBytes;
                            message.arg1 = WAVE_VIEW;
                            mHandler.sendMessage(message);

                            mMediaDecode.releaseOutputBuffer(outputIndex, false);

                            outputIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, 10 * 1000);

                        }


                    }
                }

                mMediaDecode.release();
            }
        }).start();


    }

    private void recordPause() {
        if (isPause) {
            isPause = false;
            mBtnRecordPause.setText("Pause");
        } else {
            isPause = true;
            mBtnRecordPause.setText("Resume");
        }
    }

    private void stopRecord() {
        isRecording = false;
        mBtnRecordPause.setEnabled(false);
        mBtnRecordStop.setEnabled(false);
    }

    private void startRecord() {
        isRecording = true;
        mBtnRecordPause.setText("Pause");
        mBtnRecordPause.setEnabled(true);
        mBtnRecordStop.setEnabled(true);

        if (mWaveResults != null) {
            mWaveResults = null;
        }

        initAudioRecord();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-DD HH:mm:ss", Locale.ENGLISH);
        long time = System.currentTimeMillis();
        final File pcmFile = FileUtil.getFile(sdf.format(time) + ".pcm");
        final File wavFile = FileUtil.getFile(sdf.format(time) + ".wav");

        Thread thread = new Thread() {
            @Override
            public void run() {
                mAudioRecord.startRecording();
                FileOutputStream fos;
                try {
                    fos = new FileOutputStream(pcmFile);

                    while (isRecording) {
                        if (isPause) {
                            continue;
                        }
                        byte[] bytes = new byte[mBuffSize];
                        mAudioRecord.read(bytes, 0, mBuffSize);

                        Message message = mHandler.obtainMessage();
                        message.what = BYTES_HANDLER;
                        message.obj = bytes;
                        message.arg1 = RECORD_WAVE_VIEW;
                        mHandler.sendMessage(message);

//
//                        byte[] b = Arrays.copyOf(bytes, bytes.length);
//                        short[] shorts = new short[b.length / 2];
//                        ByteBuffer.wrap(b)
//                                .order(ByteOrder.LITTLE_ENDIAN)
//                                .asShortBuffer()
//                                .get(shorts);
//                        double[] result = AudioDataUtil.normalize(shorts);
//
//                        if (mWaveResults != null) {
//                            int originLen = mWaveResults.length;
//                            mWaveResults = Arrays.copyOf(mWaveResults, mWaveResults.length + result.length);
//                            System.arraycopy(result, 0, mWaveResults, originLen, result.length);
//                        } else {
//                            mWaveResults = result;
//                        }
//                        mWaveView.setAudioData(mWaveResults);
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                mWaveView.invalidate();
//                            }
//                        });

                        fos.write(bytes);
                        fos.flush();
                    }

                    fos.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                saveWavFile(pcmFile, wavFile);

            }
        };

        thread.start();

    }

    private void saveWavFile(File pcm, File wav) {
        try {
            FileUtil.convertAudioFiles(pcm, wav);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
