package tv.supermidia.site;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class Site extends Activity {

    private static final String TAG = "SUPERMIDIA.TV:Site";
    public static final String EVENT_ALIVE = "tv.supermidia.site.event-site-alive";
    public static final String EVENT_DOWN = "tv.supermidia.site.event-site-down";
    public static final String EVENT_UP = "tv.supermidia.site.event-site-up";

    private WebView site;
    private BroadcastReceiver mReceiver;
    private Thread aliveThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WebSettings webSettings;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site);

        /* make it fullscreen */
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        /* load the main webview */
        site = (WebView) findViewById(R.id.siteWebView);
        webSettings = site.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        site.loadUrl("http://www.supermidia.tv/");

        /* only display on load is done */
        final Animation animationFadeIn = AnimationUtils.loadAnimation(this, R.anim.fadein);
        //final Animation animationFadeOut = AnimationUtils.loadAnimation(this, R.anim.fadeout);
        site.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                /* animate */
                site.startAnimation(animationFadeIn);
                site.setVisibility(View.VISIBLE);
            }
        });

        final Activity parent = this;
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received: " + intent.getAction());
                overridePendingTransition(R.anim.fadein, R.anim.fadeout);
                parent.finish();
            }
        };

        sendBroadcast(new Intent(EVENT_UP));
        startAliveThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mReceiver != null) {
            registerReceiver(mReceiver, new IntentFilter(Manager.REQUEST_KILL_SITE));
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister since the activity is not visible
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        //finish();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        sendBroadcast(new Intent(EVENT_DOWN));
        stopAliveThread();
        System.exit(0);
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
