package tv.supermidia.site;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class MessengerService extends Service {

    private static final String TAG = "SUPERMIDIA.TV.MsgSrv";

    /** Command to the service to register the process */

    static final int MSG_REGISTER_SITE                      = 0x0101;
    static final int MSG_REGISTER_MANAGER                   = 0x0102;

    static final int MSG_HAS_SITE                           = 0x0201;
    static final int MSG_HAS_MANAGER                        = 0x0202;

    public static final int MSG_SITE_SHOW_URL               = 0x0301;
    public static final int MSG_SITE_FETCH_URL              = 0x0302;
    public static final int MSG_SITE_SET_SPLASH_URL         = 0x0304;

    public static final int MSG_SITE_SHOW_OK                = 0x0401;
    public static final int MSG_SITE_FETCH_OK               = 0x0402;
    public static final int MSG_SITE_SPLASH_OK              = 0x0404;

    public static final int MSG_SITE_SHOW_FAIL              = 0x0501;
    public static final int MSG_SITE_FETCH_FAIL             = 0x0502;
    public static final int MSG_SITE_SPLASH_FAIL            = 0x0503;
    public static final int MSG_SITE_FETCH_FAIL_OFFLINE     = 0x0504;

    public static final int MSG_SITE_REQUEST_LOCAL = 0x0701;
    public static final int MSG_SITE_GOT_LOCAL = 0x0702;

    public static final int MSG_MANAGER_GOT_PLAYLIST = 0x0901;

    public static final String ARGUMENT_ONE = "URL";



    Messenger mManager = null;
    Messenger mSite = null;

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG, String.format("Message received %s from {%s}", msg, msg.replyTo));
            switch (msg.what) {
                case MSG_REGISTER_MANAGER:
                    mManager = msg.replyTo;
                    Log.d(TAG, String.format("Registering Manager %s", mManager));
                    MessengerService.sendMessage(mSite, MSG_HAS_MANAGER);
                    MessengerService.sendMessage(mManager, MSG_HAS_SITE);
                    break;
                case MSG_REGISTER_SITE:
                    mSite = msg.replyTo;
                    Log.d(TAG, String.format("Registering Site %s", mManager));
                    MessengerService.sendMessage(mSite, MSG_HAS_MANAGER);
                    MessengerService.sendMessage(mManager, MSG_HAS_SITE);
                    break;
                default:
                    /* broadcast msg */
                    MessengerService.sendMessage(mSite, Message.obtain(msg));
                    MessengerService.sendMessage(mManager, Message.obtain(msg));
                    break;
                    //super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, String.format("MessageService binded"));
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MessengerService service starting");

        return START_STICKY;
        //return START_NOT_STICKY;
    }

    protected static void sendMessage(Messenger service, int msgKind) {
        sendMessage(service, msgKind, null, null);
    }

    protected static void sendMessage(Messenger service, int msgKind, Messenger replyTo) {
        sendMessage(service, msgKind, null, replyTo);
    }

    protected static void sendMessage(Messenger service, int msgKind, String msgValue) {
        sendMessage(service, msgKind, msgValue, null);
    }

    protected static void sendMessage(Messenger service, int msgKind, String msgValue, Messenger replyTo) {
        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, msgKind, 0, 0);
        if (msgValue != null) {
            msg.getData().putString(MessengerService.ARGUMENT_ONE, msgValue);
        }
        if (replyTo != null) {
            msg.replyTo = replyTo;
        }
        sendMessage(service, msg);
    }

    private static void sendMessage(Messenger service, Message msg) {
        try {
            //Log.d(TAG, String.format("Sending message: %s to {%s}", msg, service));
            service.send(msg);
        } catch (RemoteException|NullPointerException e) {
            Log.w(TAG, String.format("Cannot send message: %s", msg), e);
        }
    }
}
