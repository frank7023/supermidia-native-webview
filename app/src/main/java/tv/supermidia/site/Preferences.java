package tv.supermidia.site;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by iuri on 09/01/15.
 */
public class Preferences {

    public static final String NAME = "tv.supermidia.site.PREFERENCES";
    public static final String NAME_KEY = "NAME";
    private SharedPreferences sharedPref;

    public Preferences(Context c) {
        /* request the name of this device */
        sharedPref = c.getSharedPreferences(
                NAME, Context.MODE_PRIVATE);

    }

    public void setName(String value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(NAME_KEY, value);
        editor.commit();
    }

    public String getName() {
        return sharedPref.getString(NAME_KEY, null);
    }
}
