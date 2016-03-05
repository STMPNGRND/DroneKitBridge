package com.fognl.dronekitbridge.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Created by kellys on 3/6/16.
 */
public class Speech {
    static final String TAG = Speech.class.getSimpleName();

    private static Speech sInstance;

    public static void init(Context context) {
        if(sInstance == null) {
            sInstance = new Speech(context);
        }
    }

    public static Speech get() { return sInstance; }

    private final TextToSpeech.OnInitListener mInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if(status != TextToSpeech.ERROR) {
                mTts.setLanguage(Locale.getDefault());
            }
        }
    };

    private final Context mContext;
    private final TextToSpeech mTts;

    private Speech(Context context) {
        super();
        mContext = context;
        mTts = new TextToSpeech(mContext, mInitListener);
    }

    public void onDestroy() {
        mTts.stop();
        mTts.shutdown();
    }

    public void say(String text) {
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
