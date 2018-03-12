package com.example.sunyao.audiovisualizer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.sunyao.audiovisualizer.utils.AudioDataUtil;
import com.example.sunyao.audiovisualizer.utils.FileUtil;
import com.example.sunyao.audiovisualizer.view.AudioWaveView;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.EvaluatorListener;
import com.iflytek.cloud.EvaluatorResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvaluator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    private static final int RATE_IN_HZ = 48000;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int RECORD_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;

    private static final int WAVE_VIEW = 0;
    private static final int RECORD_WAVE_VIEW = 1;
    private Toast mToast;

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

    @BindView(R.id.compareWave)
    Button mCompareWave;

    @BindView(R.id.et_evaluate_text)
    EditText mEtEva;

    @BindView(R.id.btn_xf_eva_start)
    Button mBtnEvaStart;

    @BindView(R.id.btn_xf_eva_stop)
    Button mBtnEvaStop;

    private int mBuffSize;

    MediaPlayer mPlayer;
    Visualizer mVisualizer;

    private double[] mWaveResults;
    private double[] mRecordWaveResults;

    private AudioRecord mAudioRecord;
    private MediaCodec mMediaDecode;
    private MediaExtractor mMediaExtractor;
    private SpeechEvaluator mIse;

    private boolean isRecording;
    private boolean isPause;

    private boolean isDecode;

    private boolean decodePause;

    private Handler mHandler;
    private String mLastResult;

    // 评测监听接口
    private EvaluatorListener mEvaluatorListener = new EvaluatorListener() {

        @Override
        public void onResult(EvaluatorResult result, boolean isLast) {
            Log.d(TAG, "evaluator result :" + isLast);

            if (isLast) {
                StringBuilder builder = new StringBuilder();
                builder.append(result.getResultString());

                mLastResult = builder.toString();
                Log.d(TAG, "onResult: " + mLastResult);
                showTip("评测结束");
            }
        }

        @Override
        public void onError(SpeechError error) {
            if (error != null) {
                showTip("error:" + error.getErrorCode() + "," + error.getErrorDescription());
            } else {
                Log.d(TAG, "evaluator over");
            }
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            Log.d(TAG, "evaluator begin");
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            Log.d(TAG, "evaluator stoped");
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前音量：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
            Message message = mHandler.obtainMessage();
            message.what = BYTES_HANDLER;
            message.obj = data;
            message.arg1 = RECORD_WAVE_VIEW;
            mHandler.sendMessage(message);

        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mToast = Toast.makeText(this, "", Toast.LENGTH_LONG);
        initHandler();
//        initDecode();
        initMediaPlayer();
        initIse();
        initView();
    }

    private void initIse() {
        mIse = SpeechEvaluator.createEvaluator(this, null);
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
            AssetFileDescriptor afd = getAssets().openFd("12.mp3");

            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            Log.d(TAG, "initDecode: audio len: " + afd.getLength());
            mWaveView.setCounts(2);
            mRecordWaveView.setCounts(2);

            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mMediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                Log.d(TAG, "initDecode: sample rate " + sampleRate);
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
            AssetFileDescriptor afd = getAssets().openFd("12.mp3");
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
                Message message = mHandler.obtainMessage();
                message.what = BYTES_HANDLER;
                message.obj = waveform;
                message.arg1 = WAVE_VIEW;
                mHandler.sendMessage(message);
                Log.d(TAG, "onWaveFormDataCapture: sampling rate " + samplingRate);
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {

                byte[] model = new byte[fft.length / 2 + 1];
                model[0] = (byte) Math.abs(fft[1]);
                int j = 1;

                for (int i = 2; i < 18; ) {
                    model[j] = (byte) Math.hypot(fft[i], fft[i + 1]);
                    i += 2;
                    j++;
                }

                Message message = mHandler.obtainMessage();
                message.what = BYTES_HANDLER;
                message.obj = model;
                message.arg1 = WAVE_VIEW;
                mHandler.sendMessage(message);
                Log.d(TAG, "onWaveFormDataCapture: sampling rate " + samplingRate);
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
        String[] permissionList = new String[]
                {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.LOCATION_HARDWARE, Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.WRITE_SETTINGS, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS};


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
            R.id.btn_decode, R.id.compareWave, R.id.btn_xf_eva_stop, R.id.btn_xf_eva_start})
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
                if (isDecode) {
                    if (decodePause) {
                        decodePause = false;
                    } else {
                        decodePause = true;
                    }
                } else {
                    startDecode();
                }

                break;
            case R.id.compareWave:
                compareValue();
                break;
            case R.id.btn_xf_eva_start:
                xfEvaStart();
                break;
            case R.id.btn_xf_eva_stop:
                xfEvaStop();
                break;
        }
    }

    private void xfEvaStop() {
        mIse.stopEvaluating();
    }

    private void xfEvaStart() {
        setParams();
        String evaText = mEtEva.getText().toString();
        int ref = mIse.startEvaluating(evaText, null, mEvaluatorListener);
    }

    private void startDecode() {
        mWaveResults = null;
        initDecode();
        mMediaDecode.start();
        mMediaDecode.dequeueInputBuffer(5000);
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        final AudioTrack audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                2048,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
        audioTrack.play();

        isDecode = true;
        decodePause = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isDecode) {
                    if (decodePause) {
                        continue;
                    }

                    int inputIndex = mMediaDecode.dequeueInputBuffer(-1);

                    if (inputIndex < 0) {
                        isDecode = false;
                        return;
                    }

                    ByteBuffer buffer = mMediaDecode.getInputBuffer(inputIndex);
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
                        outBuffer = mMediaDecode.getOutputBuffer(outputIndex);
                        pcmBytes = new byte[bufferInfo.size];
                        outBuffer.get(pcmBytes);
                        outBuffer.clear();

                        audioTrack.write(pcmBytes, 0, pcmBytes.length);

                        Message message = mHandler.obtainMessage();
                        message.what = BYTES_HANDLER;
                        message.obj = pcmBytes;
                        message.arg1 = WAVE_VIEW;
                        mHandler.sendMessage(message);

                        mMediaDecode.releaseOutputBuffer(outputIndex, false);

                        outputIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, 10 * 1000);

                    }


                }
                audioTrack.stop();
                audioTrack.release();
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
        mRecordWaveResults = null;
        isRecording = true;
        isPause = false;
        mBtnRecordPause.setText("Pause");
        mBtnRecordPause.setEnabled(true);
        mBtnRecordStop.setEnabled(true);

//        if (mWaveResults != null) {
//            mWaveResults = null;
//        }

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

    private void setParams() {
        mIse.setParameter(SpeechConstant.LANGUAGE, "en_us");
        mIse.setParameter(SpeechConstant.ISE_CATEGORY, "read_sentence");
        mIse.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        mIse.setParameter(SpeechConstant.VAD_BOS, "5000");
        mIse.setParameter(SpeechConstant.VAD_EOS, "1800");
        mIse.setParameter(SpeechConstant.KEY_SPEECH_TIMEOUT, "-1");
        mIse.setParameter(SpeechConstant.RESULT_LEVEL, "complete");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIse.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIse.setParameter(SpeechConstant.ISE_AUDIO_PATH, Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/ise.wav");
        //通过writeaudio方式直接写入音频时才需要此设置
//        mIse.setParameter(SpeechConstant.AUDIO_SOURCE,"-1");
    }

    private void saveWavFile(File pcm, File wav) {
        try {
            FileUtil.convertAudioFiles(pcm, wav);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     *
     */
    private float compareValue() {
        int lenW = mWaveResults.length;
        int lenR = mRecordWaveResults.length;
        int len = lenW > lenR ? lenR : lenW;

        float a = 0, b = 0, c = 0, pxy = 0;
        for (int i = 0; i < len; i++) {
            a = ((float) (mWaveResults[i] * mRecordWaveResults[i]));
            b = ((float) (mWaveResults[i] * mWaveResults[i]));
            c = ((float) (mRecordWaveResults[i] * mRecordWaveResults[i]));
        }

        pxy = (float) (a / Math.sqrt(b * c));

        Log.d(TAG, "compareValue: " + pxy);

        return pxy;
    }

    private void showTip(String str) {
        if (!TextUtils.isEmpty(str)) {
            mToast.setText(str);
            mToast.show();
        }
    }

}
