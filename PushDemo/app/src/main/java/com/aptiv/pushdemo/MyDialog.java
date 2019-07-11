package com.aptiv.pushdemo;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.Timer;
import java.util.TimerTask;

public class MyDialog extends Dialog {

    private Context context;
    private String location;

    private Window window = null;

    public MyDialog(Context context) {
        super(context);
    }

    public MyDialog(Context context, int theme, String location) {
        super(context, theme);
        this.location = location;
        this.context = context;
    }

    public MyDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //View view = View.inflate(context, R.layout.custom_dialog, null);
        View view = LayoutInflater.from(context).inflate(R.layout.custom_dialog, null);
        setContentView(view);

        setCanceledOnTouchOutside(false);

        TextView textView = findViewById(R.id.textView1);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(context.getResources().getColor(R.color.colorAccent, null));
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        //BackgroundColorSpan span = new BackgroundColorSpan(Color.GRAY);

        SpannableString spannableString = new SpannableString("需要为您导航到 " + location + " 吗？");
        spannableString.setSpan(foregroundColorSpan, 8, 8 + location.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(styleSpan, 8, 8 + location.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        //tvSpannable.setText(spannableString);
        textView.setText(spannableString);

        final Window win = getWindow();
        //win.setBackgroundDrawableResource(R.color.vifrification); //设置对话框背景为透明
        if (win != null) {
            win.setWindowAnimations(R.style.dialogWindowAnim);
            win.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            setCanceledOnTouchOutside(true);
            Display display = win.getWindowManager().getDefaultDisplay();
            Point point = new Point();
            display.getRealSize(point);
            WindowManager.LayoutParams lp = win.getAttributes();
            //lp.x = 80;
            //lp.y = 50;
            lp.width = point.x;
            lp.alpha = 0.9f;
            lp.gravity = Gravity.BOTTOM;
            win.setAttributes(lp);
        }

        view.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("ran.zhou", "Cancel button onclick");
                dismiss();
            }
        });
        view.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("ran.zhou", "Ok button onclick");
                gotoNavigation(location);
                dismiss();
            }
        });
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                MyDialog.this.dismiss();
            }
        }, 30000);
    }

    private void gotoNavigation(String location) {
        Log.i("ran.zhou", "location: " + location);
        Intent intent = new Intent();
        intent.setAction("AUTONAVI_STANDARD_BROADCAST_RECV");
        intent.putExtra("KEY_TYPE", 10036);
        intent.putExtra("KEYWORDS", location);
        intent.putExtra("SOURCE_APP", "Third App");
        context.sendBroadcast(intent);
    }

}
