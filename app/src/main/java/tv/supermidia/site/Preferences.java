package tv.supermidia.site;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by iuri on 09/01/15.
 */
public class Preferences {

    private static final String NAME = "tv.supermidia.site.PREFERENCES";
    private static final String KEY_NAME = "NAME";
    private static final String KEY_PLAYLIST = "PLAYLIST";

    private SharedPreferences sharedPref;

    public Preferences(Context c) {
        /* request the name of this device */
        sharedPref = c.getSharedPreferences(
                NAME, Context.MODE_PRIVATE);

    }

    public void setName(String value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(KEY_NAME, value);
        editor.commit();
    }

    public String getName() {
        return sharedPref.getString(KEY_NAME, null);
    }

    public void setPlaylist(String value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(KEY_PLAYLIST, value);
        editor.commit();
    }
    public String getKeyPlaylist() {
        return sharedPref.getString(KEY_PLAYLIST, null);
    }
}
