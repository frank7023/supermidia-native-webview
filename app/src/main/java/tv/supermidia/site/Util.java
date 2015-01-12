package tv.supermidia.site;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by iuri on 12/01/15.
 */
public class Util {
    public static boolean checkURL(String urlString) {
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
            huc.connect();
            return huc.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            Log.e("UTIL::checkURL", "IOException: " + urlString);
            return false;
        }
    }
}
