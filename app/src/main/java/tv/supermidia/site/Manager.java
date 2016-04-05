package tv.supermidia.site;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class Manager extends Service {
    private static final String TAG = "SUPERMIDIA.TV.Manager";

    //private Thread mThreadSiteUp;

    /**
     * Messenger for communicating with the service.
     */
    private Messenger mService = null;

    /**
     * Flag indicating whether we have called bind on the service.
     */
    private boolean mBound;
    private RepeatTask mCheckSiteUp;
    private String mLocal;
    private RepeatTask mUpdatePlayList;
    private Playlist mCurrentPlaylist;
    private Playlist mCachePlaylist;
    private boolean mIsPlaying = false;
    RequestQueue mRequestQueue;

    public static final String PLAYLIST_URL = "http://nifty-time-95518.appspot.com/api/playlist/%s";

    HashMap<String, Boolean> mBlacklist = new HashMap<String, Boolean>();
    private RepeatTask mPingTask;

    private void startManager() {
        if (mCheckSiteUp == null) {
            mCheckSiteUp = new RepeatTask(3000) {
                @Override
                public void run() {
                    Intent intent = new Intent(Manager.this, Site.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            };
            mCheckSiteUp.start();
        }
        /* check for name */
        if (mLocal == null) {
            Preferences pref = new Preferences(this);
            String name = pref.getName();
            if (name == null) {
                sendMessage(MessengerService.MSG_SITE_REQUEST_LOCAL);
            } else {
                sendMessage(MessengerService.MSG_SITE_GOT_LOCAL, name);
            }
        }

        /* check for playlist */
        if (mCurrentPlaylist == null) {
            Preferences pref = new Preferences(this);
            String saved = pref.getKeyPlaylist();
            if (saved != null) {
                gotPlaylist(saved);
            }
        } else {
            gotPlaylist(mCurrentPlaylist.raw);
        }

        if (mUpdatePlayList == null) {
            mUpdatePlayList = new RepeatTask(30000) {
                @Override
                public void run() {
                    if (mLocal != null) {
//                    /* fake playlist */
//                        String fake = "{\"id\": \"c\", \"playlist\": [{\"url\": \"http://tv.araripina.com.br/%s/\", \"duration\": 3600}], " +
//                                "\"ping\": \"http://tv.araripina.com.br/service/service/salva/%s\", " +
//                                "\"offline\": \"http://192.168.0.116:8080/content/offline.html\"}";
//                        fake = String.format(fake, mLocal, mLocal);
                        final String url = String.format(PLAYLIST_URL, mLocal);
                        Log.d(TAG, String.format("Requesting playlist at %s'", url));
                        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                                    @Override
                                    public void onResponse(JSONObject response) {
                                        Log.d(TAG, String.format("GOT playlist from %s: '%s'", url, response.toString()));
                                        sendMessage(MessengerService.MSG_MANAGER_GOT_PLAYLIST, response.toString());
                                    }
                                }, new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        Log.d(TAG, String.format("Cannot get playlist at %s", url));
                                    }
                                });
                        mRequestQueue.add(jsObjRequest);
                    }
                }
            };
            mUpdatePlayList.start();
        }

        if (mPingTask == null) {
            mPingTask = new RepeatTask(60 * 1 * 1000) {
                @Override
                public void run() {
                    if (mCurrentPlaylist != null) {
                        Thread ping = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (Util.checkURL(mCurrentPlaylist.ping, 3000)) {
                                    Log.d(TAG, String.format("URL: %s is online", mCurrentPlaylist.ping));
                                } else {
                                    Log.d(TAG, String.format("URL: %s is offline", mCurrentPlaylist.ping));
                                };
                            }
                        });
                        ping.start();
                    }
                }
            };
            mPingTask.start();
        }
    }

    private void stopManager() {
        if (mCheckSiteUp != null) {
            mCheckSiteUp.stop();
            mCheckSiteUp = null;
        }
        if (mUpdatePlayList != null) {
            mUpdatePlayList.stop();
            mUpdatePlayList = null;
        }
        if (mPingTask != null) {
            mPingTask.stop();
            mPingTask = null;
        }
//        mThreadSiteUp.interrupt();
//        while (mThreadSiteUp != null) {
//            try {
//                mThreadSiteUp.join();
//                mThreadSiteUp = null;
//            } catch (InterruptedException e) {
//            }
//        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Manage service starting");

        Log.d(TAG, "Binding to MessengerService");
        bindService(new Intent(this, MessengerService.class), mConnection,
                Context.BIND_AUTO_CREATE);

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

        if (mBound) {
            Log.d(TAG, "Unbinding to MessengerService");
            unbindService(mConnection);
            mBound = false;
        }

        stopManager();
        if (mRequestQueue != null) {
            mRequestQueue.stop();
        }
        Log.d(TAG, "Manage service stopping");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Manage service created");
        /* start threads */
        startManager();

        // Instantiate the cache
        Cache cache = new DiskBasedCache(getCacheDir(), 1024 * 1024); // 1MB cap

        // Set up the network to use HttpURLConnection as the HTTP client.
        Network network = new BasicNetwork(new HurlStack());

        // Instantiate the RequestQueue with the cache and network.
        mRequestQueue = new RequestQueue(cache, network);
        mRequestQueue.start();

    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            String url;
            //Log.d(TAG, String.format("Message received %s", msg));
            switch (msg.what) {
                case MessengerService.MSG_HAS_SITE:
                    Log.d(TAG, "Site seems up");
                    mIsPlaying = false;
                    startManager();
                    break;
                case MessengerService.MSG_SITE_GOT_LOCAL:
                    mLocal = msg.getData().getString(MessengerService.ARGUMENT_ONE);
                    saveLocal();
                    break;
                case MessengerService.MSG_MANAGER_GOT_PLAYLIST:
                    gotPlaylist(msg.getData().getString(MessengerService.ARGUMENT_ONE));
                    break;
                case MessengerService.MSG_SITE_FETCH_FAIL_OFFLINE:
                    Log.d(TAG, String.format("Cannot fetch url %s on cache [offline]", msg.getData().getString(MessengerService.ARGUMENT_ONE)));
                    fetchNextPlaylist();
                    break;
                case MessengerService.MSG_SITE_FETCH_FAIL:
                    url = msg.getData().getString(MessengerService.ARGUMENT_ONE);
                    Log.d(TAG, String.format("Cannot fetch url %s on cache", url));
                    /* black list it */
                    mBlacklist.put(url, true);
                    fetchNextPlaylist();
                    break;
                case MessengerService.MSG_SITE_FETCH_OK:
                    url = msg.getData().getString(MessengerService.ARGUMENT_ONE);
                    Log.d(TAG, String.format("URL %s was fetched on cache", url));
                    /* undo black list it */
                    mBlacklist.remove(url);
                    fetchNextPlaylist();
                    break;
                case MessengerService.MSG_SITE_SHOW_FAIL:
                    Log.d(TAG, String.format("Cannot show %s", msg.getData().getString(MessengerService.ARGUMENT_ONE)));
                    Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            playNext();
                        }
                    }, 5000);
                    break;
                case MessengerService.MSG_SITE_SHOW_OK:
                    Log.d(TAG, String.format("Url %s being showed", msg.getData().getString(MessengerService.ARGUMENT_ONE)));
                    playNextAfterCurrent();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void saveLocal() {
        Preferences pref = new Preferences(this);
        pref.setName(mLocal);
    }

    private void gotPlaylist(String string) {
        Playlist n = null;
        try {
            n = new Playlist(string);

        } catch (JSONException e) {
            Log.w(TAG, String.format("Cannot decode playlist"), e);
            return;
        }

        if (mCachePlaylist == null) {
            Log.d(TAG, String.format("Updating the playlist: have no caching list"));
            updatePlaylistTo(n);
        } else if (n.id.compareTo(mCachePlaylist.id) != 0) {
            Log.d(TAG, String.format("Updating the playlist: id changed"));
            updatePlaylistTo(n);
        } else if (n.timestamp - mCachePlaylist.timestamp > 2 * 60 * 60 * 1000) {
            Log.d(TAG, String.format("Updating the playlist: previous is too old"));
            updatePlaylistTo(n);
        } else if (mIsPlaying == false) {
            Log.d(TAG, String.format("Updating the playlist: not playing"));
            updatePlaylistTo(n);
        }
//        if (mCurrentPlaylist == null) {
//            mCurrentPlaylist = n;
//        }

    }

    private void updatePlaylistTo(Playlist n) {
        mCachePlaylist = n;
        //fetchNextPlaylist();
        /* fetch first offline */
        sendMessage(MessengerService.MSG_SITE_SET_SPLASH_URL, n.offline);
        sendMessage(MessengerService.MSG_SITE_FETCH_URL, n.offline);
    }

    private void fetchNextPlaylist() {
        int oldIndex = mCachePlaylist.index;
        final PlaylistItem i = mCachePlaylist.next();
        int newIndex = mCachePlaylist.index;
        if (newIndex > oldIndex) {
            sendMessage(MessengerService.MSG_SITE_FETCH_URL, i.url);
        } else if (i != null) {
            Log.d(TAG, "Playlist was totally fetched");
            /* the list was restarted: delay this fetch */
            int duration = 10 * 60 * 1000;
            Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendMessage(MessengerService.MSG_SITE_FETCH_URL, i.url);
                }
            }, duration);

            /* set current playlist and play it */
            mCurrentPlaylist = mCachePlaylist.copy();
            Preferences pref = new Preferences(this);
            pref.setPlaylist(mCurrentPlaylist.raw);

            if (mIsPlaying == false) {
                playNext();
            }
            /* set the offline */
            sendMessage(MessengerService.MSG_SITE_SET_SPLASH_URL, mCurrentPlaylist.offline);

        }
    }

    private void playNext() {
        final PlaylistItem i = mCurrentPlaylist.next();
        if (i == null) {
            return;
        }
        mIsPlaying = true;
        if (mBlacklist.containsKey(i.url) == true) {
            /* skip it */
            Handler h = new Handler();
            Log.d(TAG, String.format("URL %s was blacklisted, skipping it", i.url));
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            }, 3000);
        } else {
            Log.d(TAG, String.format("Showing %s", i.url));
            sendMessage(MessengerService.MSG_SITE_SHOW_URL, i.url);
        }
    }

    private void playNextAfterCurrent() {
        final PlaylistItem i = mCachePlaylist.current();
        if (i != null) {
            Handler h = new Handler();
            Log.d(TAG, String.format("Waiting %s seconds before show next url", i.duration));
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            }, i.duration * 1000);
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            Log.d(TAG, "Bound to MessengerService");
            mBound = true;
            sendMessage(MessengerService.MSG_REGISTER_MANAGER);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
            Log.d(TAG, "Unbound to MessengerService");

        }
    };

    private void sendMessage(int msgKind, String msgValue) {
        if (mBound) {
            MessengerService.sendMessage(mService, msgKind, msgValue, mMessenger);
        }
    }

    private void sendMessage(int msgKind) {
        sendMessage(msgKind, null);
    }

}
