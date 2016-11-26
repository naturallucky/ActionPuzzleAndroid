package biz.myworkstyle.fruitsparadise;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity {

    private static final String LOGID ="IKIBITO";

    private boolean sizeChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 19) {
            View decor = this.getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        //最大化
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);


        Log.d(LOGID, "onCreate");
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            Log.d(LOGID, "onStart");

        } catch (Exception e) {
            Log.d(LOGID, Log.getStackTraceString(e));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            Log.d(LOGID, "onResume");
            GameManager game = (GameManager) findViewById(R.id.canvasv);
            game.onResume();
            game.setDrawCallBack(this);

            if (Build.VERSION.SDK_INT >= 19) {
                View decor = this.getWindow().getDecorView();
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Exception e) {
            Log.d(LOGID, Log.getStackTraceString(e));
        }

    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !sizeChecked){
            sizeChecked = true;
            GameManager game = (GameManager) findViewById(R.id.canvasv);
            game.windowChanged();
        }
    }

    //-----------------------------------------------------------
/*
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        Log.d(LOGID, "ontouch in activity");
        return false;
    }*/
}