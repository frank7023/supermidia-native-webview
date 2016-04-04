package tv.supermidia.site;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class Site extends Activity {

    private static final String TAG = "SUPERMIDIA.TV.Site";
    public static final String EVENT_ALIVE = "tv.supermidia.site.event-site-alive";
    private static final String LOCAL_SPLASH_URL = "file:///android_asset/www/offline.html";

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG, String.format("Message received %s", msg));
            switch (msg.what) {
                case MessengerService.MSG_SITE_SHOW_URL:
                    loadUrlOnCurrent(msg.getData().getString(MessengerService.ARGUMENT_ONE));
                    break;
                case MessengerService.MSG_SITE_FETCH_URL:
                    loadUrlOnCache(msg.getData().getString(MessengerService.ARGUMENT_ONE));
                    break;
                case MessengerService.MSG_SITE_SET_SPLASH_URL:
                    loadUrlOnSplash(msg.getData().getString(MessengerService.ARGUMENT_ONE));
                    break;
                case MessengerService.MSG_SITE_REQUEST_LOCAL:
                    requestLocal();
                    break;
                case MessengerService.MSG_HAS_MANAGER:
                    Log.d(TAG, "Manager seems up");
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void requestLocal() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setMessage("Local?");

            /* Set an EditText view to get user input */
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String value = input.getText().toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendMessage(MessengerService.MSG_SITE_GOT_LOCAL, value);
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

    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            Log.d(TAG, "Bound to MessengerService");
            mService = new Messenger(service);
            mBound = true;
            sendMessage(MessengerService.MSG_REGISTER_SITE);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            Log.d(TAG, "Unbound to MessengerService");
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //WebSettings webSettings;

        super.onCreate(savedInstanceState);
        startManagerService();

        /* set content view */
        setContentView(R.layout.activity_site);
        setupFullscreen();

        /* setup Webviews */
        setupCacheWebview();
        setupCurrentWebview();
        setupSplashWebview();

        loadUrlOnSplash(LOCAL_SPLASH_URL);
        //loadUrlOnCache("http://tv.araripina.com.br/portal");
        //loadUrlOnCurrent("http://tv.araripina.com.br/portal");
    }

    private void setupSplashWebview() {
        WebView view = (WebView) findViewById(R.id.web_splash);
        configureWebView(view);

        WebSettings webSettings = view.getSettings();
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ONLY);
        webSettings.setBlockNetworkLoads(true);
        /* splash is always visible */
        view.setVisibility(View.VISIBLE);
    }

    private void setupCurrentWebview() {
        WebView view = (WebView) findViewById(R.id.web_current);
        configureWebView(view);

        /* CURRENT must display only cached pages */
        WebSettings webSettings = view.getSettings();
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ONLY);
        webSettings.setBlockNetworkLoads(true);
    }

    private void setupCacheWebview() {
        WebView view = (WebView) findViewById(R.id.web_cache);
        configureWebView(view);

        /* cache is always invisible */
        view.setVisibility(View.INVISIBLE);
    }

    private static void configureWebView(WebView view) {
        WebSettings webSettings = view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setBlockNetworkLoads(false);
    }

    private void loadUrlOnCache(final String url) {
        Log.d(TAG, String.format("[CACHE] Caching URL '%s'", url));
        Thread checkURL = new Thread(new Runnable() {
            @Override
            public void run() {
                if (Util.checkURL(url, 10000) == false) {
                     /* cannot reach, do not cache it */
                    Log.d(TAG, String.format("[CACHE] URL '%s' cannot be reached", url));
                    sendMessage(MessengerService.MSG_SITE_FETCH_FAIL_OFFLINE, url);
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebView view = (WebView) findViewById(R.id.web_cache);
                        view.setWebViewClient(new WebViewClient() {
                            boolean hasError = false;
                            boolean sent = false;

                            @Override
                            public void onPageFinished(WebView view, String finishedUrl) {
                                if (sent == false && hasError) {
                                    Log.d(TAG, String.format("[CACHE] URL '%s' was unsuccessful loaded", finishedUrl));
                                    sendMessage(MessengerService.MSG_SITE_FETCH_FAIL, url);
                                } else if (sent == false) {
                                    Log.d(TAG, String.format("[CACHE] URL '%s' was successful loaded", finishedUrl));
                                    sendMessage(MessengerService.MSG_SITE_FETCH_OK, url);
                                }
                                sent = true;
                            }

                            @Override
                            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                                Log.d(TAG, String.format("[CACHE] Cannot load URL '%s': %d - %s", failingUrl, errorCode, description));
                                hasError = true;
                            }
                        });
                        view.loadUrl(url);
                    }
                });
            }
        });
        checkURL.start();
    }

    private void loadUrlOnCurrent(final String url) {
        WebView view = (WebView) findViewById(R.id.web_current);
        Log.d(TAG, String.format("[CURRENT] Opening URL '%s'", url));
        view.setWebViewClient(new WebViewClient() {
            boolean hasError = false;
            boolean sent = false;

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                if (sent == false && hasError) {
                    Log.d(TAG, String.format("[CURRENT] URL '%s' was unsuccessful loaded", finishedUrl));
                    view.setVisibility(View.INVISIBLE);
                    sendMessage(MessengerService.MSG_SITE_SHOW_FAIL, url);
                } else if (sent == false) {
                    Log.d(TAG, String.format("[CURRENT] URL '%s' was successful loaded", finishedUrl));
                    /* current is visible on successful load */
                    view.setVisibility(View.VISIBLE);
                    sendMessage(MessengerService.MSG_SITE_SHOW_OK, url);
                }
                //view.setVisibility(View.VISIBLE);
                sent = true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.d(TAG, String.format("[CURRENT] Cannot load URL '%s': %d - %s", failingUrl, errorCode, description));
                hasError = true;
            }
        });
        view.loadUrl(url);
    }

    private void loadUrlOnSplash(final String url) {
        WebView view = (WebView) findViewById(R.id.web_splash);
        Log.d(TAG, String.format("[SPLASH] Opening URL '%s'", url));
        view.setWebViewClient(new WebViewClient() {
            boolean hasError = false;
            boolean sent = false;

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                if (sent == false && hasError) {
                    Log.d(TAG, String.format("[SPLASH] URL '%s' was unsuccessful loaded", finishedUrl));
                    sendMessage(MessengerService.MSG_SITE_SPLASH_FAIL, url);
                    if (url.compareTo(LOCAL_SPLASH_URL) != 0) {
                        sendMessage(MessengerService.MSG_SITE_SET_SPLASH_URL, LOCAL_SPLASH_URL);
                    }
                } else if (sent == false) {
                    Log.d(TAG, String.format("[SPLASH] URL '%s' was successful loaded", finishedUrl));
                    sendMessage(MessengerService.MSG_SITE_SPLASH_OK, url);
                }
                sent = true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.d(TAG, String.format("[SPLASH] Cannot load URL '%s': %d - %s", failingUrl, errorCode, description));
                hasError = true;
            }
        });
        view.loadUrl(url);
    }

    private void setupFullscreen() {
    /* make it fullscreen (Always)*/
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
                    Log.d(TAG, "Not in fullscreen. Trying to get fullscreen on 30 seconds");
                    Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Try to enter fullscreen again");
                            decorView.setSystemUiVisibility(uiOptions);
                        }
                    }, 30000);
                } else {
                    Log.d(TAG, "Fullscreen");
                }
            }
        });
        /* fullscreen at start */
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void startManagerService() {
        /* start my manager service */
        Intent intent = new Intent(this, Manager.class);
        startService(intent);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service
        Log.d(TAG, "Binding to MessengerService");
        bindService(new Intent(this, MessengerService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            Log.d(TAG, "Unbinding to MessengerService");
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void sendMessage(int msgKind, String msgValue) {
        if (mBound) {
            MessengerService.sendMessage(mService, msgKind, msgValue, mMessenger);
        }
    }

    public void sendMessage(int msgKind) {
        sendMessage(msgKind, null);
    }
}

