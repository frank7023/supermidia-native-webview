package tv.supermidia.site;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.EditText;

public class Site extends Activity {

    private static final String TAG = "SUPERMIDIA.TV:Site";
    public static final String EVENT_ALIVE = "tv.supermidia.site.event-site-alive";
    public static final String EVENT_DOWN = "tv.supermidia.site.event-site-down";
    public static final String EVENT_UP = "tv.supermidia.site.event-site-up";
    public static final String SITE_URL_BASE = "http://www.supermidia.tv/";

    private WebView site;
    private BroadcastReceiver mReceiver;
    private Thread aliveThread;
    private Preferences pref;

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

        /* discovery my name */
        pref = new Preferences(this);
        final String name =  pref.getName();
        if (name == null) {
            /* getting the local */
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            //alert.setTitle("Local");
            alert.setMessage("Local?");

            // Set an EditText view to get user input
            final EditText input = new EditText(this);
            alert.setView(input);

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    final String value = input.getText().toString();
                    // Do something with value!
                    pref.setName(value);

                    /* load URL */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String url = SITE_URL_BASE + value;
                            loadURL(url);
                        }
                    });
                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });

            alert.show();
        } else {
            String url = SITE_URL_BASE + name;
            loadURL(url);
        }

        sendBroadcast(new Intent(EVENT_UP));
        startAliveThread();
    }


    public void loadURL(String url) {
        Log.d(TAG, "Opening url: " + url);
        site.loadUrl(url);
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
