package tech.ucoon.screenrecord;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Display;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mStartScreenRecorderBtn;
    private Button mStopScreenRecorderBtn;
    private SurfaceView mSurfaceView;

    private MediaProjectionManager projectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    private void initView(){
        mStartScreenRecorderBtn = findViewById(R.id.btnStartScreenRecorder);
        mStartScreenRecorderBtn.setOnClickListener(this);
        mStopScreenRecorderBtn = findViewById(R.id.btnStopScreenRecorder);
        mStopScreenRecorderBtn.setOnClickListener(this);
        mSurfaceView = findViewById(R.id.surfaceView);
    }

    private Intent getCaptureIntent(){
        return projectionManager.createScreenCaptureIntent();
    }

    private void createMediaProjection(int resultCode, Intent data){
        mMediaProjection = projectionManager.getMediaProjection(resultCode, data);
    }

    private void createVirtualDisplay(){
        int[] screenSize = getPhysicalResolution(this);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("Display-" + SystemClock.uptimeMillis(),
                upTo16X(screenSize[0]/2), upTo16X(screenSize[1]/2), 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurfaceView.getHolder().getSurface(), null, null);
    }

    private void releaseMediaProjection(){
        try {
            MediaProjection mediaProjection = mMediaProjection;
            if (mediaProjection != null){
                mediaProjection.stop();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            mMediaProjection = null;
        }
    }

    private void releaseVirtualDisplay(){
        try {
            VirtualDisplay virtualDisplay = mVirtualDisplay;
            if (virtualDisplay != null){
                virtualDisplay.release();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            mVirtualDisplay = null;
        }
    }

    private void startScreenRecorder(){
        startActivityForResult(getCaptureIntent(), 1024);
    }

    private void stopScreenRecorder(boolean destroy){
        releaseSurfaceView(destroy);
        releaseVirtualDisplay();
        releaseMediaProjection();
    }

    private void releaseSurfaceView(boolean destroy) {
        try {
            SurfaceView surfaceView = mSurfaceView;
            if (surfaceView != null){
                surfaceView.getHolder().getSurface().release();

            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if (destroy) mSurfaceView = null;
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        switch (viewId){
            case R.id.btnStartScreenRecorder:
                startScreenRecorder();
                break;
            case R.id.btnStopScreenRecorder:
                stopScreenRecorder(false);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScreenRecorder(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1024) {
            if (resultCode == RESULT_OK && data != null){
                createMediaProjection(resultCode, data);
                createVirtualDisplay();
            }
        }
    }

    /**
     * 手机物理分辨率
     *
     * @param context
     * @return
     */
    public static int[] getPhysicalResolution(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getRealSize(point);
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return new int[]{point.y, point.x};
        } else {
            return new int[]{point.x, point.y};
        }
    }

    private int upTo16X(int size) {
        return size + (16 - size % 16) % 16;
    }
}
