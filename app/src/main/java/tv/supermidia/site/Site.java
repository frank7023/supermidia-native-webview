package tv.supermidia.site;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

public class Site extends Activity {

    private static final String TAG = "SUPERMIDIA.TV:Site";
    public static final String EVENT_ALIVE = "tv.supermidia.site.event-site-alive";
    public static final String EVENT_DOWN = "tv.supermidia.site.event-site-down";
    public static final String EVENT_UP = "tv.supermidia.site.event-site-up";
    public static final String SITE_URL_BASE = "http://www.supermidia.tv/";
    public static final String PING_URL_BASE = "http://service.supermidia.tv/service/salva/";
    public static final int PING_SECONDS = 15 * 60; /* ping every 15 minutes */

    private WebView site;
    private BroadcastReceiver mReceiver;
    private Thread aliveThread;
    private Thread pingThread;
    private Preferences pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WebSettings webSettings;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site);

        /* make it fullscreen */
        final View decorView = getWindow().getDecorView();
        final int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        /* listen for fullscreen changes */
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    /* exit fullscreen */
                    Log.d(TAG, "Not in fullscreen");
                    Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Try to enter fullscreen again");
                            decorView.setSystemUiVisibility(uiOptions);
                        }
                    }, 10000);
                } else {
                    Log.d(TAG, "Fullscreen");
                }
            }
        });
        /* fullscreen at start */
        decorView.setSystemUiVisibility(uiOptions);

        /* load the main webview */
        site = (WebView) findViewById(R.id.siteWebView);
        webSettings = site.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
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
            @Override
            public void onReceivedError (WebView view, int errorCode, String description, String failingUrl) {
                Log.d(TAG, "Cannot load URL: [" + errorCode + "]: " + description);
                Site.this.finish();
                overridePendingTransition(0, 0);
            }
        });

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received: " + intent.getAction());
                Site.this.finish();
                overridePendingTransition(R.anim.fadein, R.anim.fadeout);
            }
        };

        sendBroadcast(new Intent(EVENT_UP));


        /* discovery my name */
        pref = new Preferences(this);
        final String name =  pref.getName();

        if (name == null) {
            /* getting the local */
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setMessage("Local?");

            /* Set an EditText view to get user input */
            final EditText input = new EditText(this);
            alert.setView(input);

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    final String value = input.getText().toString();
                    /* Do something with value! */
                    pref.setName(value);

                    /* load URL */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onHasLocal(value);
                        }
                    });
                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    /* Canceled. */
                }
            });

            alert.show();
        } else {
            onHasLocal(name);
        }
    }

    private void onHasLocal(String local) {
        if (local == null) {
            return;
        }
        String url = SITE_URL_BASE + local;
        loadURL(url);

        TextView placeInfo = (TextView) findViewById(R.id.placeInfo);
        placeInfo.setText(local);
    }

    public void loadURL(final String url) {
        Log.d(TAG, "Opening url: " + url);

        Thread checkURL = new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean hasInternet;
                if (Util.checkURL(SITE_URL_BASE, 10000)) {
                    hasInternet = true;
                } else {
                    hasInternet = false;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebSettings webSettings = site.getSettings();

                        if (hasInternet) {
                            Log.d(TAG, "Internet seems good, using default settings");
                            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
                            webSettings.setBlockNetworkLoads(false);
                        } else {
                            /* no internet, use cache */
                            Log.d(TAG, "Internet seems horrible, use cache only");
                            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                            webSettings.setBlockNetworkLoads(true);
                        }
                        site.loadUrl(url);
                    }
                });
            }
        });

        checkURL.start();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, Manager.class);
        stopService(intent);
        Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 1000);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mReceiver != null) {
            registerReceiver(mReceiver, new IntentFilter(Manager.REQUEST_KILL_SITE));
        }

        startAliveThread();
        startPingThread();

    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister since the activity is not visible
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        stopAliveThread();
        stopPingThread();
    }

    @Override
    public void onDestroy() {
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

    private void startPingThread() {
        if (pingThread != null) {
            return;
        }
        pingThread = new Thread() {

            @Override
            public void run() {
                try {
                    Log.d(TAG, "Ping thread started");
                    while (!isInterrupted()) {
                        String name;
                        if (pref != null && (name = pref.getName()) != null) {
                            String url = PING_URL_BASE + name;
                            String status = Util.checkURL(url)?"online":"offline";
                            Log.d(TAG, "Ping from url: '" + url + "': " + status);
                        }
                        Thread.sleep(PING_SECONDS * 1000);
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        pingThread.start();
    }

    private void stopPingThread() {
        if (pingThread == null) {
            return;
        }
        pingThread.interrupt();
        while (pingThread != null) {
            try {
                pingThread.join();
                pingThread = null;
            } catch (InterruptedException e) {
            }
        }
    }
}
