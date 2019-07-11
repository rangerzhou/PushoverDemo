package com.aptiv.pushdemo;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public TextView tvResponse;
    private EditText editEmail, editPwd;
    private Button btnLogin, btnRegist, btnDwnMsg, btnDelMsg, btnLoginWebsocket;
    private String strEmail, strPwd;
    public static AsyncHttpClient syncHttpClient = new SyncHttpClient();
    public static AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

    public static final String TAG = "PushDemo";
    public static final int CONNECT_OPEN_CODE = 0x0001;
    public static final int CONNECT_MESSAGE_CODE = 0x0002;
    public static final int CONNECT_CLOSE_CODE = 0x0003;
    public static final int CONNECT_ERROR_CODE = 0x0004;
    public static final int MSG_DOWNLOAD_CODE = 0x0005;
    public static final int MSG_DELETE_FAIL_CODE = 0x0006;
    public static final int SHOW_DIALOG_CODE = 0x0007;
    public static final int SHOW_TIRE_DIALOG_CODE = 0x0008;
    public static final int MSG_DELETE_CODE = 0x0009;

    public static final String ACTION_SHOW_DIALOG = "action_show_dialog";
    public static final String ACTION_CONNECT_OPEN = "action_connect_open";
    public static final String ACTION_CONNECT_MESSAGE = "action_connect_message";
    public static final String ACTION_CONNECT_CLOSE = "action_connect_close";
    public static final String ACTION_CONNECT_ERROR = "action_connect_error";
    public static final String ACTION_DOWNLOAD_MSG = "action_download_msg";
    public static final String ACTION_DELETE_MSG = "action_delete_msg";
    public static final String ACTION_LOGIN_WEBSOCKET = "action_login_websocket";
    public static final String ACTION_TIREPRESSURE_LOW = "action_tirepressure_low";
    private static final int GRANT_OVERLAY_PERMISSION = 1;

    private static String LOGIN_JSON_URL = "https://api.pushover.net/1/users/login.json";
    private static String REGIST_JSON_URL = "https://api.pushover.net/1/devices.json";
    private static String MSG_DWN_JSON_URL = "https://api.pushover.net/1/messages.json?";
    private static String MSG_DEL_JSON_URL = "https://api.pushover.net/1/devices/";
    private static String SERVER_URI = "wss://client.pushover.net/push";

    private String loginJson = "";
    private String devicesJson = "";
    private String msgDwnJson = "";
    private String secret = "dorhd6n1af7ii5fy38bsumsgn4isarx1tp1g2b6tbms6c26yq9vvfnjukqou";
    private String device_id = "3zoxxnttv4brs91wexk1rqacoha6icinmxdo2hyp";
    private String messageId = "";
    private String msgTitle = "";
    private String msgDescription = "";
    private String msgLocation = "";
    private String msgStartTime = "";
    private String msgEndTime = "";

    private String msgMessage = "";
    private List<String> msgList = new ArrayList<>();
    private HashSet<String> msgSet = new HashSet<>();
    Handler mHandler = new MyHandler(this);
    private WebSocketClient webSocketClient;
    public StringBuilder sb = new StringBuilder();

    private NotificationManager mNotificationManager;
    BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        // 初始不用 service 的方法
        //connectWebServer();

        // Start WebSocketService
        Intent serviceIntent = new Intent();
        serviceIntent.setClass(this, WebSocketService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        grantPermission();
    }

    public void initView() {
        tvResponse = findViewById(R.id.tv_response);
        editEmail = findViewById(R.id.edit_email);
        editPwd = findViewById(R.id.edit_pwd);
        btnLogin = findViewById(R.id.btn_login);
        btnRegist = findViewById(R.id.btn_regist);
        btnDwnMsg = findViewById(R.id.btn_msgdwn);
        btnDelMsg = findViewById(R.id.btn_msgdel);
        btnLoginWebsocket = findViewById(R.id.btn_loginsocket);

        btnDelMsg.setOnClickListener(this);

        initBroadcast();
    }

    private void grantPermission() {
        if (!Settings.canDrawOverlays(MainActivity.this)) {
            new AlertDialog.Builder(this)
                    .setTitle("权限申请")
                    .setMessage("允许显示在其他应用的上层，否则部分功能将无法使用")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, GRANT_OVERLAY_PERMISSION);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).create().show();

        }
    }

    private void connectWebServer() {
        URI serverURI = URI.create(SERVER_URI);
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
                    Message msg = Message.obtain();
                    msg.what = CONNECT_OPEN_CODE;
                    mHandler.sendMessage(msg);
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
                        sb.append("New Message Arrived ...");
                        Message msg = Message.obtain();
                        msg.what = CONNECT_MESSAGE_CODE;
                        mHandler.sendMessage(msg);
                        sendPostRequest(MSG_DWN_JSON_URL);
                    } else if ((char) bytes.get(0) == 'R') {
                        connectWebServer();
                    } else if ((char) bytes.get(0) == 'E') {
                        Log.i(TAG, "连接异常，请重新登录或者重启设备");
                    } else if ((char) bytes.get(0) == '#') {
                        Log.i(TAG, "获取到服务器信息: " + (char) bytes.get(0) + " Keep-alive packet, no response needed");
                    }
                }


                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "服务器断开连接: " + getURI() + "; Code: " + code + ", reason: " + reason + ", remote: " + remote + "\n");
                    sb.append("服务器断开连接：\n" + new Date() + "\ncode: " + code + ", reason: " + reason + ", remote: " + remote + "\n");
                    Message msg = Message.obtain();
                    msg.what = CONNECT_CLOSE_CODE;
                    mHandler.sendMessage(msg);
                    connectWebServer();
                }

                @Override
                public void onError(Exception ex) {
                    Log.i(TAG, "连接发生异常: " + ex + "\n");
                    sb.append("连接发生异常：\n" + new Date() + "\nException: " + ex);
                    Message msg = Message.obtain();
                    msg.what = CONNECT_ERROR_CODE;
                    mHandler.sendMessage(msg);
                    //connectWebServer();
                }


            };
            webSocketClient.connect();
            sendPostRequest(LOGIN_JSON_URL);
            btnRegist.setEnabled(true);
            btnDwnMsg.setEnabled(true);
            btnDelMsg.setEnabled(true);
            btnLoginWebsocket.setEnabled(true);

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
                    Log.i(TAG, "\n messages: " + messages);
                    JSONArray msgsArray = JSONObject.parseArray(messages);
                    if (msgsArray != null) {
                        for (Object obj : msgsArray) {
                            JSONObject jsonObj = (JSONObject) obj;
                            messageId = jsonObj.getString("id");
                            msgMessage = jsonObj.getString("message");
                            Log.i(TAG, "messageId: " + messageId + ", message: " + msgMessage);
                            msgSet.add(messageId);
                            String[] splitMsg = msgMessage.split(",");
                            if (splitMsg.length > 1) {
                                msgTitle = splitMsg[0];
                                msgDescription = splitMsg[1];
                                msgLocation = splitMsg[2];
                                msgStartTime = splitMsg[3];
                                msgEndTime = splitMsg[4];

                                Message downloadMsg = Message.obtain();
                                downloadMsg.what = SHOW_DIALOG_CODE;
                                downloadMsg.obj = msgLocation;
                                mHandler.sendMessage(downloadMsg);

                                //pushNotification(msgTitle, msgDescription, messageId);
                            } else if (msgMessage.equals("轮胎店")) {
                                Log.i(TAG, "******message == " + msgMessage);
                                Message tirePressureMsg = Message.obtain();
                                tirePressureMsg.what = SHOW_TIRE_DIALOG_CODE;
                                tirePressureMsg.obj = msgMessage;
                                mHandler.sendMessage(tirePressureMsg);
                            }

                        }
                        Log.i(TAG, "msgSet.size: " + msgSet.size());
                        Message delMsg = Message.obtain();
                        delMsg.what = MSG_DELETE_CODE;
                        mHandler.sendMessage(delMsg);
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
            Log.i(TAG, "Start Delete messages...............................................................................");
            String delUrl = MSG_DEL_JSON_URL + device_id + "/update_highest_message.json";
            Log.i(TAG, "delUrl: " + delUrl + "\n" + "device_id: " + device_id + ", msgSet.size: " + msgSet.size());
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
                Log.i(TAG, "Delete Done.........................................................................................");
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_login:
                /*sendPostRequest(LOGIN_JSON_URL);
                btnRegist.setEnabled(true);
                btnDwnMsg.setEnabled(true);
                btnDelMsg.setEnabled(true);
                btnLoginWebsocket.setEnabled(true);
                break;*/
            case R.id.btn_regist:
                //sendPostRequest(REGIST_JSON_URL);
                //break;
            case R.id.btn_msgdwn:
                //sendPostRequest(MSG_DWN_JSON_URL);
                //break;
            case R.id.btn_msgdel:
                //sendPostRequest(MSG_DEL_JSON_URL);
                //break;
                //new MyDialog(this).showDialog(R.layout.custom_dialog, 80, 50, "location");
                MyDialog myDialog = new MyDialog(this, R.style.ThemeOverlay_AppCompat_Dialog, "上海火车站");
                myDialog.show();
                break;
            case R.id.btn_loginsocket:
                Log.i(TAG, "Start Login Server !!!");
                //loginSocket(webSocketClient);
                //break;
            default:
                break;
        }
    }

    private void loginSocket(WebSocketClient webSocketClient) {
        if (webSocketClient.isClosed() || webSocketClient.isClosing()) {
            Toast.makeText(this, "Client正在关闭...", Toast.LENGTH_SHORT).show();
            //webSocketClient.connect();
            connectWebServer();
        }
        String loginStr = "login:" + device_id + ":" + secret + "\n";
        webSocketClient.send(loginStr);

        Log.i(TAG, "Login WebSocketServer ....................");
        sb.append("\n客户端登陆 WebServer：\n");
        sb.append(new Date());
        sb.append("\n");
        tvResponse.setText(sb.toString());
    }

    private class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        private MyHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case CONNECT_OPEN_CODE:
                    case CONNECT_MESSAGE_CODE:
                    case CONNECT_CLOSE_CODE:
                    case CONNECT_ERROR_CODE:
                        tvResponse.setText(sb.toString());
                        break;
                    case MSG_DOWNLOAD_CODE:
                        //Toast.makeText(getApplicationContext(), "没有新消息 ...", Toast.LENGTH_SHORT).show();
                        break;
                    case MSG_DELETE_CODE:
                        sendPostRequest(MSG_DEL_JSON_URL);
                        break;
                    case MSG_DELETE_FAIL_CODE:
                        Toast.makeText(getApplicationContext(), "id为" + msg.obj + "的消息删除失败 ...", Toast.LENGTH_SHORT).show();
                        break;
                    case SHOW_DIALOG_CODE:
                    case SHOW_TIRE_DIALOG_CODE:
                        showMyDialog(msg.obj.toString());
                        break;
                    default:
                        break;

                }
            }
        }
    }

    private NotificationManager getNotificationManager() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }

    private void pushNotification(String title, String msg, String CHANNEL_ONE_ID) {
        //String CHANNEL_ONE_ID = id;
        String CHANNEL_ONE_NAME = "Msg";
        NotificationChannel notificationChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationChannel.setDescription("CalendarDemo Service");
            mNotificationManager = getNotificationManager();
            mNotificationManager.createNotificationChannel(notificationChannel);
        }

        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ONE_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setTicker("Nature")
                .setAutoCancel(false)
                .setContentTitle(title)
                .setContentText(msg)
                .build();
        mNotificationManager.notify(1, notification);
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

    public void initBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CONNECT_OPEN);
        intentFilter.addAction(ACTION_CONNECT_MESSAGE);
        intentFilter.addAction(ACTION_CONNECT_CLOSE);
        intentFilter.addAction(ACTION_CONNECT_ERROR);
        intentFilter.addAction(ACTION_DOWNLOAD_MSG);
        intentFilter.addAction(ACTION_DELETE_MSG);
        intentFilter.addAction(ACTION_SHOW_DIALOG);
        intentFilter.addAction(ACTION_LOGIN_WEBSOCKET);
        intentFilter.addAction(ACTION_TIREPRESSURE_LOW);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                if (ACTION_CONNECT_OPEN.equals(intent.getAction()) || ACTION_CONNECT_MESSAGE.equals(intent.getAction())
                        || ACTION_CONNECT_CLOSE.equals(intent.getAction()) || ACTION_CONNECT_ERROR.equals(intent.getAction())
                        || ACTION_LOGIN_WEBSOCKET.equals(intent.getAction())) {
                    if (bundle != null) {
                        String textView = bundle.getString("text");
                        Log.i(TAG, "action: " + intent.getAction() + ", text: \n{\n" + textView + "}");
                        tvResponse.setText(textView);
                    }

                } else if (ACTION_TIREPRESSURE_LOW.equals(intent.getAction()) || ACTION_SHOW_DIALOG.equals(intent.getAction())) {
                    if (bundle != null) {
                        String location = bundle.getString("location");
                        Log.i(TAG, "action: " + intent.getAction() + ", location: \n{\n" + location + "}");
                        showMyDialog(location);
                    }
                }
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult");
        if (requestCode == GRANT_OVERLAY_PERMISSION) {
            Log.i(TAG, "onActivityResult: " + Settings.canDrawOverlays(MainActivity.this));
        }
    }

    @Override
    protected void onDestroy() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

}
