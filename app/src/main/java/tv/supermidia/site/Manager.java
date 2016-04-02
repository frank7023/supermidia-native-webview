package tv.supermidia.site;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

public class Manager extends Service {
    public static final String TAG = "SUPERMIDIA.TV:Manager";

    public static final String EVENT_UP = "tv.supermidia.site.event-manager-up";
    public static final String EVENT_DOWN = "tv.supermidia.site.event-manager-down";
    public static final int REFRESH_ONLINE_SECONDS = 60 * 60 * 2; /* refresh site every 2 hours (if online)  */
    public static final int REFRESH_CACHE_SECONDS = 60 * 60 * 6; /* refresh site every 6 hours (if cache) */
    public static final int REFRESH_SWITCH_SECONDS = 60 * 5; /* switch every 5 minutes (if online/cache)  */
    public static final int REFRESH_OFFLINE_SECONDS = 60 * 5; /* refresh site every 5 minutes (if offline) */
    public static final int REFRESH_CHECK_SECONDS = 5;

    private WifiManager mWifiManager;
    private Thread mThreadRefresh;
    private BroadcastReceiver mReceiver;

    private boolean siteUp = false;

    private boolean siteOffline = false;
    private boolean siteOnline = false;
    private boolean siteCache = false;

    private CountDownTimer siteUpCountdown;

    public Manager() {
        super();
        //mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                //Log.d(TAG, "Received: " + action);
                if (action.compareTo(Site.EVENT_UP) == 0 || action.compareTo(Site.EVENT_ALIVE) == 0) {
                    setSiteUp(true);
                } else if (action.compareTo(Site.EVENT_DOWN) == 0) {
                    setSiteUp(false);
                } else if (action.compareTo(Site.EVENT_CACHE) == 0) {
                    setSiteCache();
                } else if (action.compareTo(Site.EVENT_OFFLINE) == 0) {
                    setSiteOffline();
                } else if (action.compareTo(Site.EVENT_ONLINE) == 0) {
                    setSiteOnline();
                }
            }
        };
        startThreads();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "Manager service starting");
        sendBroadcast(new Intent(EVENT_UP));

        if (mReceiver != null) {
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_ALIVE));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_UP));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_DOWN));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_OFFLINE));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_ONLINE));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_CACHE));
        }
        return START_STICKY;
        //return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Manage service stopping");
        stopThreads();
        sendBroadcast(new Intent(EVENT_DOWN));

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    private void startSite() {
        //Log.d(TAG, "Staring site");

        Intent intent = new Intent(this, Site.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    private void stopSite() {
        Intent i = new Intent(Site.KILL_SITE);
        sendBroadcast(i);
    }

    private boolean hasWifi() {
        if (mWifiManager == null) {
            /* unknown state, assume offline */
            return false;
        }
        return mWifiManager.isWifiEnabled();
    }

    private void startThreads() {
        startThreadRefresh();
    }

    private void startThreadRefresh() {
        if (mThreadRefresh != null) {
            return;
        }
        mThreadRefresh = new Thread() {
            @Override
            public void run() {
                long secondsRunning = 0;

                try {
                    Log.d(TAG, "thread  for refresh is started");
                    while (!isInterrupted()) {
                        Thread.sleep(1000 * REFRESH_CHECK_SECONDS);
                        /* limit it to 24 hours */
                        secondsRunning = secondsRunning >= 24 * 60 * 60 ? 0 : secondsRunning;
                        secondsRunning += REFRESH_CHECK_SECONDS;
                        /* finish the site activity and start it again */
                        boolean refresh = false;
                        boolean switch_ = false;
                        if (isSiteUp()) {
                            if (isSiteOffline() &&
                                 secondsRunning % REFRESH_OFFLINE_SECONDS < REFRESH_CHECK_SECONDS) {
                                refresh = true;
                            }
                            if (isSiteOnline() &&
                                    secondsRunning % REFRESH_ONLINE_SECONDS < REFRESH_CHECK_SECONDS) {
                                refresh = true;
                            }
//                            if (isSiteCache() &&
//                                    secondsRunning % REFRESH_CACHE_SECONDS < REFRESH_CHECK_SECONDS) {
//                                refresh = true;
//                            }
                            if ((isSiteOnline() || isSiteCache()) &&
                                    secondsRunning % REFRESH_SWITCH_SECONDS < REFRESH_CHECK_SECONDS) {
                                switch_ = true;
                            }
                            if (refresh) {
                                Log.d(TAG, "It's time to refresh, so let's stop site");
                                stopSite();
                                continue;
                            }
                            if (switch_) {
                                Log.d(TAG, "It's time to switch");
                                switchSite();
                                continue;
                            }
                        }

                        /* no reload */
                        if (isSiteUp()) {
                            /* continue to show site */
                            continue;
                        }
                        Log.d(TAG, "Site is not running, starting it");
                        startSite();

                    }
                } catch (InterruptedException e) {
                }
                Log.d(TAG, "thread for refresh was stopped");
            }
        };
        mThreadRefresh.start();
    }

    private void switchSite() {
        Intent i = new Intent(Site.SWITCH_SITE);
        sendBroadcast(i);
    }

    private void stopThreads() {
        stopThreadRefresh();
    }

    private void stopThreadRefresh() {
        if (mThreadRefresh == null) {
            return;
        }
        mThreadRefresh.interrupt();
        while (mThreadRefresh != null) {
            try {
                mThreadRefresh.join();
                mThreadRefresh = null;
            } catch (InterruptedException e) {
            }
        }
    }

    public synchronized boolean isSiteUp() {
        return siteUp;
    }

    public synchronized void setSiteUp(boolean siteUp) {
        if (this.siteUp != siteUp) {
            Log.d(TAG, "siteUp is now " + siteUp);
        }
        this.siteUp = siteUp;

        if (siteUpCountdown != null) {
            siteUpCountdown.cancel();
            siteUpCountdown = null;
        }

        if (siteUp == true) {
            siteUpCountdown = new CountDownTimer(5000, 5000) {
                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    siteUpCountdown = null;
                    setSiteUp(false);
                }
            };
            siteUpCountdown.start();
        }
    }

    public synchronized boolean isSiteOffline() {
        return siteOffline;
    }

    public synchronized void setSiteOffline() {
        Log.d(TAG, "siteOffline is now true");
        this.siteOffline = true;
        this.siteOnline = false;
        this.siteCache = false;
    }

    public synchronized boolean isSiteOnline() {
        return siteOnline;
    }

    public synchronized void setSiteOnline() {
        Log.d(TAG, "siteOnline is now true");

        this.siteOffline = false;
        this.siteOnline = true;
        this.siteCache = false;
    }

    public synchronized boolean isSiteCache() {
        return siteCache;
    }

    public synchronized void setSiteCache() {
        Log.d(TAG, "siteCache is now true");

        this.siteOffline = false;
        this.siteOnline = false;
        this.siteCache = true;
    }
}
