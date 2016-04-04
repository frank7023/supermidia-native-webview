package tv.supermidia.site;

import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by iuri on 12/01/15.
 */
public class Util {

    public static boolean checkURL(String urlString) {
        return checkURL(urlString, 0);
    }
    public static boolean checkURL(String urlString, int timeoutMili) {
        URL u = null;
        try {
            u = new URL(urlString);
        } catch (MalformedURLException e) {
            Log.e("UTIL::checkURL", "MalformedURLException: " + urlString);
            return false;
        }
        HttpURLConnection huc = null;
        try {
            huc = (HttpURLConnection)  u.openConnection();
            huc.setRequestMethod("GET");
            huc.setConnectTimeout(timeoutMili);
            huc.connect();
            return huc.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (SocketTimeoutException e) {
            Log.e("UTIL::checkURL", "SocketTimeoutException: " + urlString);
            return false;
        } catch (IOException e) {
            Log.e("UTIL::checkURL", "IOException: " + urlString);
            return false;
        }
    }

}

class Playlist {
    public String id;
    public String raw;
    public String ping;
    public String offline;
    public PlaylistItem[] playlist;
    public int index = -1;
    public long timestamp;

    private Playlist() {};

    Playlist(String json) throws JSONException {
        this(new JSONObject(json));
    }

    Playlist(JSONObject jsonObject) throws JSONException {
        offline = jsonObject.getString("offline");
        id = jsonObject.getString("id");
        ping = jsonObject.getString("ping");
        JSONArray jsonArray = jsonObject.getJSONArray("playlist");
        playlist = new PlaylistItem[jsonArray.length()];
        for (int i=0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);
            String url = item.getString("url");
            int duration = item.getInt("duration");
            playlist[i] = new PlaylistItem(url, duration);
        }
        timestamp = System.currentTimeMillis();
        raw = jsonObject.toString();
    }

    public Playlist copy() {
        Playlist n = null;
        try {
             n = new Playlist(this.raw);
        } catch (JSONException e) {

        } finally {
            return n;
        }
    }

    public PlaylistItem next() {
        if (playlist.length == 0) {
            return null;
        }
        if (index+1 >= playlist.length|| index < -1) {
            index = -1;
        }
        return playlist[++index];
    }

    public PlaylistItem current() {
        if (index == -1) {
            return next();
        }
        return playlist[index];
    }
}

class PlaylistItem extends Pair<String, Integer> {

    public String url;
    public int duration;
    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public PlaylistItem(String first, Integer second) {
        super(first, second);
        url = first;
        duration = second;
    }

}

abstract class RepeatTask implements Runnable {
    private int mInterval;
    private Handler mHandler;

    private RepeatTask() {

    }

    RepeatTask(int interval) {
        mInterval = interval;
        mHandler = new Handler();
    }

    private Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                RepeatTask.this.run();
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mStatusChecker, mInterval);
            }
        }
    };

    public void start() {
        mStatusChecker.run();
    }

    public void stop() {
        mHandler.removeCallbacks(mStatusChecker);
    }

}
