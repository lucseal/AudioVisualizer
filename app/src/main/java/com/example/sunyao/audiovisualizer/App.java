package com.example.sunyao.audiovisualizer;

import android.app.Application;

import com.iflytek.cloud.SpeechUtility;

/**
 * @author sunyao
 * @Description:
 * @date 2018/3/2 上午10:21
 */
public class App extends Application {
    // Branch X
    // Branch X
    // Branch Y
    // Branch YY
    // Branch Z + Z

    @Override
    public void onCreate() {
        SpeechUtility.createUtility(App.this, "appid=" + getString(R.string.app_id));
        super.onCreate();
    }

}
