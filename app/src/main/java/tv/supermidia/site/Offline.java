package tv.supermidia.site;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;


public class Offline extends Activity {

    private static final String TAG = "SUPERMIDIA.TV:Offline";
    public static final String EVENT_ALIVE = "tv.supermidia.site.event-offline-alive";
    private WebView offline;
    public static final String EVENT_DOWN = "tv.supermidia.site.event-offline-down";
    public static final String EVENT_UP = "tv.supermidia.site.event-offline-up";
    private Thread aliveThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline);


        /* make it fullscreen */
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        /* load webview */
        offline = (WebView) findViewById(R.id.offlineWebView);
        WebSettings webSettings = offline.getSettings();
        //webSettings.setJavaScriptEnabled(true);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        offline.loadUrl("file:///android_asset/www/offline.html");

        /* start my manager service */
        Intent intent = new Intent(this, Manager.class);
        startService(intent);

        sendBroadcast(new Intent(EVENT_UP));

    }
    @Override
    protected void onResume() {
        super.onResume();
        startAliveThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAliveThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendBroadcast(new Intent(EVENT_DOWN));
    }

    private void startAliveThread() {
        if (aliveThread != null) {
            return;
        }
        aliveThread = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sendBroadcast(new Intent(EVENT_ALIVE));
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        aliveThread.start();
    }

    private void stopAliveThread() {
        if (aliveThread == null) {
            return;
        }
        aliveThread.interrupt();
        while (aliveThread != null) {
            try {
                aliveThread.join();
                aliveThread = null;
            } catch (InterruptedException e) {
            }
        }
    }
}
