package com.aptiv.pushdemo;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cz.msebera.android.httpclient.Header;

public class WebSocketService extends Service {
    public static AsyncHttpClient syncHttpClient = new SyncHttpClient();
    public static AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

    public static final String TAG = "PushDemo";
    private static final int SHOW_DIALOG_CODE = 0x0001;
    private static final int SHOW_TIRE_DIALOG_CODE = 0x0002;
    private static final int MSG_DELETE_CODE = 0x0003;

    private static String LOGIN_JSON_URL = "https://api.pushover.net/1/users/login.json";
    private static String REGIST_JSON_URL = "https://api.pushover.net/1/devices.json";
    private static String MSG_DWN_JSON_URL = "https://api.pushover.net/1/messages.json?";
    private static String MSG_DEL_JSON_URL = "https://api.pushover.net/1/devices/";
    private static String SERVER_URI = "wss://client.pushover.net/push";

    private String secret = "dorhd6n1af7ii5fy38bsumsgn4isarx1tp1g2b6tbms6c26yq9vvfnjukqou";
    private String device_id = "3zoxxnttv4brs91wexk1rqacoha6icinmxdo2hyp";
    private String messageId = "";
    private String msgTitle = "";
    private String msgDescription = "";
    private String msgLocation = "";
    private String msgStartTime = "";
    private String msgEndTime = "";
    private String msgMessage = "";

    public StringBuilder sb = new StringBuilder();
    private HashSet<String> msgSet = new HashSet<>();
    private WebSocketClient webSocketClient;
    private Handler serviceHandler = new ServiceHandler();
    Bundle bundle = new Bundle();

    public WebSocketService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeService();

        Log.i(TAG, "onCreate");
        connectWebServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private void connectWebServer() {
        final URI serverURI = URI.create(SERVER_URI);
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }
        try {
            webSocketClient = new WebSocketClient(serverURI) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "已连接到服务器: " + getURI() + "\n");
                    sb.append("已连接到服务器：\n" + new Date() + "\n服务器状态: \n" + handshakedata.getHttpStatusMessage() + "\n");
                    //Bundle bundle = new Bundle();
                    bundle.putString("text", sb.toString());
                    sendBroadcast("action_connect_open", bundle);

                }

                @Override
                public void onMessage(String message) {
                    Log.i(TAG, "获取到服务器信息: " + message + "\n");
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    super.onMessage(bytes);
                    if ((char) bytes.get(0) == '!') {
                        Log.i(TAG, "获取到服务器信息: " + (char) bytes.get(0) + "有新的消息，请同步 ...");
                        sb.append("New Message Arrived ...\n");

                        bundle.putString("text", sb.toString());
                        sendBroadcast("action_connect_message", bundle);

                        sendPostRequest(MSG_DWN_JSON_URL);
                    } else if ((char) bytes.get(0) == 'R') {
                        Log.i(TAG, "Reload request; Drop connection and re-connect ...");
                        connectWebServer();
                    } else if ((char) bytes.get(0) == 'E') {
                        Log.i(TAG, "连接异常，请重新登录或者重启设备");
                    } else if ((char) bytes.get(0) == '#') {
                        sb.append("onMessage: # - (Keep-alive packet, no response needed)\n");
                        bundle.putString("text", sb.toString());
                        sendBroadcast("action_connect_message", bundle);
                        Log.i(TAG, "获取到服务器信息: " + (char) bytes.get(0) + " Keep-alive packet, no response needed");
                    }
                }


                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "onClose---You have been disconnected from: " + getURI() + "; Code: " + code + ", reason: " + reason + ", remote: " + remote + "\n");
                    sb.append("服务器断开连接：\n" + new Date() + "\ncode: " + code + ", reason: " + reason + ", remote: " + remote + "\n");
                    //Bundle bundle = new Bundle();
                    bundle.putString("text", sb.toString());
                    sendBroadcast("action_connect_close", bundle);
                    connectWebServer();
                }

                @Override
                public void onError(Exception ex) {
                    Log.i(TAG, "onError---Exception occured ...\n" + ex + "\n");
                    sb.append("连接发生异常：\n" + new Date() + "\nException: " + ex + "\n");
                    //Bundle bundle = new Bundle();
                    bundle.putString("text", sb.toString());
                    sendBroadcast("action_connect_error", bundle);
                }
            };
            webSocketClient.connect();
            sendPostRequest(LOGIN_JSON_URL);

        } catch (Exception ex) {
            Log.e(TAG, serverURI + " is not a valid WebSocket URI \n" + ex);
        }
    }

    private void sendPostRequest(String url) {
        if (LOGIN_JSON_URL.equals(url)) {
            Log.i(TAG, "Start Login...");
            HashMap<String, String> loginParamsMap = new HashMap<>();
            loginParamsMap.put("email", "leiduke@163.com");
            loginParamsMap.put("password", "IAmDuke1");
            RequestParams loginParams = new RequestParams(loginParamsMap);
            getClient().post(LOGIN_JSON_URL, loginParams, new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    String str = new String(responseBody);
                    Log.i(TAG, "getLoginPost onSuccess \n" + "responseBody: " + str);
                    JSONObject jsonObject = JSONObject.parseObject(str);
                    secret = jsonObject.getString("secret");
                    Log.i(TAG, "secret: " + secret);
                    loginSocket(webSocketClient);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    String str = "";
                    if (responseBody != null) {
                        str = new String(responseBody);
                    }
                    Log.i(TAG, "getLoginPost onFailure responseBody: " + str + ", error: " + error.toString());
                }

            });
        } else if (REGIST_JSON_URL.equals(url)) {
            Log.i(TAG, "Start Registration...");
            HashMap<String, String> registParamsMap = new HashMap<>();
            registParamsMap.put("secret", secret);
            registParamsMap.put("name", "HomeAssistant");
            registParamsMap.put("os", "O");
            RequestParams registParams = new RequestParams(registParamsMap);
            getClient().post(REGIST_JSON_URL, registParams, new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    String str = new String(responseBody);
                    Log.i(TAG, "getRegistPost onSuccess \n" + "responseBody: " + str);
                    JSONObject jsonObject = JSONObject.parseObject(str);
                    device_id = jsonObject.getString("id");
                    Log.i(TAG, "device_id: " + device_id);
                    // 19v5rnfdzofua76hs6toqseuwtxv9c2spocrjfc5
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    Log.i(TAG, "getRegistPost onFailure error: " + error.toString());
                    Log.i(TAG, "Old device_id: " + "3zoxxnttv4brs91wexk1rqacoha6icinmxdo2hyp");
                    //device_id = "sfyvgsk3tja2sd9jovfvbo4on8huxmdry1n7xw77";
                }
            });
        } else if (MSG_DWN_JSON_URL.equals(url)) {
            Log.i(TAG, "Start Download messages↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓");
            String dwnUrl = MSG_DWN_JSON_URL + "secret=" + secret + "&device_id=" + device_id;
            Log.i(TAG, "dwnUrl: " + dwnUrl);
            getClient().get(dwnUrl, new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    String str = new String(responseBody);
                    Log.i(TAG, "Download messages onSuccess, " + "responseBody: " + str + "\n");
                    JSONObject jsonObject = JSONObject.parseObject(str);
                    String messages = jsonObject.getString("messages");
                    Log.i(TAG, "messages: " + messages);
                    JSONArray msgsArray = JSONObject.parseArray(messages);
                    if (msgsArray != null) {
                        for (Object obj : msgsArray) {
                            JSONObject jsonObj = (JSONObject) obj;
                            messageId = jsonObj.getString("id");
                            msgMessage = jsonObj.getString("message");
                            Log.i(TAG, "messageId: " + messageId + "\nmessage: " + msgMessage);
                            msgSet.add(messageId);
                            String[] splitMsg = msgMessage.split(",");
                            if (splitMsg.length == 5) {
                                msgTitle = splitMsg[0];
                                msgDescription = splitMsg[1];
                                msgLocation = splitMsg[2];
                                msgStartTime = splitMsg[3];
                                msgEndTime = splitMsg[4];

                                Message downloadMsg = Message.obtain();
                                downloadMsg.what = SHOW_DIALOG_CODE;
                                downloadMsg.obj = msgLocation;
                                serviceHandler.sendMessage(downloadMsg);

                                /*bundle.putString("location", msgLocation);
                                sendBroadcast("action_show_dialog", bundle);*/

                            } else if (msgMessage.equals("轮胎店")) {
                                Message tirePressureMsg = Message.obtain();
                                tirePressureMsg.what = SHOW_TIRE_DIALOG_CODE;
                                tirePressureMsg.obj = msgMessage;
                                serviceHandler.sendMessage(tirePressureMsg);

                                /*bundle.putString("location", msgMessage);
                                sendBroadcast("action_tirepressure_low", bundle);*/
                            } else {
                                pushNotification("CarAssist通知", msgMessage, Integer.parseInt(messageId));
                            }
                            /*Message dialogMsg = Message.obtain();
                            dialogMsg.what = SHOW_DIALOG_CODE;
                            dialogMsg.obj = splitMsg.length > 1 ? msgLocation : msgMessage;
                            serviceHandler.sendMessage(dialogMsg);*/

                        }
                        Log.i(TAG, "msgSet.size: " + msgSet.size());
                        Message delMsg = Message.obtain();
                        delMsg.what = MSG_DELETE_CODE;
                        serviceHandler.sendMessage(delMsg);
                    }
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    Log.i(TAG, "getDwnMsgPost onFailure responseBody: " + ((responseBody != null) ? new String(responseBody) : "responseBody is null")
                            + "\n" + error.toString());
                }
            });
            Log.i(TAG, "Download Done↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓");
        } else if (MSG_DEL_JSON_URL.equals(url)) {
            Log.i(TAG, "Start Delete messages-------------------------------------------------------------------------------");
            String delUrl = MSG_DEL_JSON_URL + device_id + "/update_highest_message.json";
            Log.i(TAG, "delUrl: " + delUrl + "\n" + "device_id: " + device_id);
            final RequestParams delParams = new RequestParams();
            delParams.put("secret", secret);
            if (msgSet.size() > 0) {
                for (final String msgId : msgSet) {
                    delParams.put("message", msgId);
                    getClient().post(delUrl, delParams, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                            String str = new String(responseBody);
                            Log.i(TAG, "msgId:" + msgId + " Delete onSuccess, " + "responseBody: " + str);
                            delParams.remove("message");
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                            Log.i(TAG, "msgId: " + msgId + " Delete onFailure responseBody: " + new String(responseBody) + "\n" + error.toString());
                        }
                    });
                }
                msgSet.clear();
                Log.i(TAG, "Delete Done-----------------------------------------------------------------------------------------");
            } else {
                Toast.makeText(this, "messages 列表为空...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * @return an async client when calling from the main thread, otherwise a sync client.
     */
    private static AsyncHttpClient getClient() {
        // Return the synchronous HTTP client when the thread is not prepared
        if (Looper.myLooper() == null)
            return syncHttpClient;
        return asyncHttpClient;
    }

    private void loginSocket(WebSocketClient webSocketClient) {
        if (webSocketClient.isClosed() || webSocketClient.isClosing()) {
            Toast.makeText(this, "Client正在关闭...", Toast.LENGTH_SHORT).show();
            //webSocketClient.connect();
            Log.i(TAG, "isClosed or isClosing...reConnect");
            connectWebServer();
        }

        String loginStr = "login:" + device_id + ":" + secret + "\n";
        try {
            webSocketClient.send(loginStr);
        } catch (WebsocketNotConnectedException e) {
            Log.e(TAG, "Websocket not connected: " + e);
        }

        Log.i(TAG, "Login WebSocketServer ....................");
        sb.append("\n客户端登陆 WebServer：\n");
        sb.append(new Date());
        sb.append("\n");

        //Bundle bundle = new Bundle();
        bundle.putString("text", sb.toString());
        sendBroadcast("action_login_websocket", bundle);
    }

    private void pushNotification(String title, String msg, int notifiId) {
        Intent hassIntent = new Intent(this, MainActivity.class);
        hassIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent hassPendingIntent = PendingIntent.getActivity(this, notifiId, hassIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String CHANNEL_ID = "HASS";
        String CHANNEL_NAME = "HASS消息";
        NotificationChannel notificationChannel;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationChannel.setDescription("CalendarDemo Service");
            notificationManager.createNotificationChannel(notificationChannel);
        }

        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setTicker("Nature")
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(msg)
                .setContentIntent(hassPendingIntent)
                .build();
        notificationManager.notify(notifiId, notification);
    }

    public void startForeService() {
        Intent foreServiceIntent = new Intent(this, MainActivity.class);
        foreServiceIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent forePendingIntent = PendingIntent.getActivity(this, 0, foreServiceIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String CHANNEL_ID = "WebSocketService";
        String CHANNEL_NAME = "ForegroundService";
        NotificationChannel notificationChannel;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationChannel.setDescription("CalendarDemo Service");
            notificationManager.createNotificationChannel(notificationChannel);
        }

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("Nature")
                .setAutoCancel(false)
                .setContentIntent(forePendingIntent)
                .setContentTitle("WebSocketService")
                .build();
        startForeground(1, notification);
    }

    public void sendBroadcast(String action, Bundle bundle) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.putExtras(bundle);
        sendBroadcast(intent);
    }

    private class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_DELETE_CODE:
                    sendPostRequest(MSG_DEL_JSON_URL);
                    break;
                case SHOW_TIRE_DIALOG_CODE:
                case SHOW_DIALOG_CODE:
                    showMyDialog(msg.obj.toString());
                    break;
            }
        }
    }

    public void gotoNavigation(String location) {
        Intent intent = new Intent();
        intent.setAction("AUTONAVI_STANDARD_BROADCAST_RECV");
        intent.putExtra("KEY_TYPE", 10036);
        intent.putExtra("KEYWORDS", location);
        intent.putExtra("SOURCE_APP", "Third App");
        sendBroadcast(intent);
    }

    public void showMyDialog(final String location) {
        Log.i(TAG, "location.length: " + location.length() + ", location: " + location);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setMessage("需要导航到 " + location + " 吗？")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        gotoNavigation(location);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        if (location.equals("轮胎店")) {
            builder.setTitle("胎压太低");
        } else {
            builder.setTitle("有新的日程");
        }
        AlertDialog dialog = builder.create();
        if (dialog != null && dialog.getWindow() != null) {
            if (Build.VERSION.SDK_INT >= 26) {//8.0新特性
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            dialog.show();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webSocketClient.close();
    }
}
