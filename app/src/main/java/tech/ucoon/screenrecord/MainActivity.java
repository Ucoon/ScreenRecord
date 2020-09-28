package tech.ucoon.screenrecord;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mStartScreenRecorderBtn;
    private Button mStopScreenRecorderBtn;
    private ImageView imageView;
    private SurfaceView mSurfaceView;

    private MediaProjectionManager projectionManager;
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private Handler mHandler;
    private Bitmap bitmap;

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
        imageView = findViewById(R.id.imgIcon);
    }

    private Intent getCaptureIntent(){
        return projectionManager.createScreenCaptureIntent();
    }

    private void createMediaProjection(int resultCode, Intent data){
        mMediaProjection = projectionManager.getMediaProjection(resultCode, data);
    }

    private void createVirtualDisplay(Surface surface){
        int[] screenSize = getPhysicalResolution(this);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("Display-" + SystemClock.uptimeMillis(),
                upTo16X(screenSize[0]/2), upTo16X(screenSize[1]/2), 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
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
        releaseImageReader(mImageReader);
        releaseCaptureHandler();
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
                createCaptureHandler();
                Surface surface = createImageReader();
                createVirtualDisplay(surface);
            }
        }
    }

    private void createCaptureHandler(){
        if (mHandler == null){
            HandlerThread handlerThread = new HandlerThread("screen-capture");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
        }
    }

    private void releaseCaptureHandler(){
        try {
            Handler handler = mHandler;
            if (handler != null){
                handler.getLooper().quitSafely();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            mHandler = null;
        }
    }

    private Surface createImageReader(){
        int[] screenSize = getPhysicalResolution(this);
        ImageReader imageReader = ImageReader.newInstance(upTo16X(screenSize[0]/2), upTo16X(screenSize[1]/2),
                PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireNextImage();
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int width = image.getWidth();
                    int height = image.getHeight();
                    int rowPadding = rowStride - pixelStride * width;
                    bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    if (image != null){
                        try {
                            image.close();
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    if (bitmap != null){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            }
        }, mHandler);
        synchronized (MainActivity.this){
            mImageReader = imageReader;
        }
        return imageReader.getSurface();
    }

    private void releaseImageReader(final ImageReader imageReader){
        if (imageReader == null || mHandler == null) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    imageReader.close();
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    synchronized (MainActivity.this){
                        if (imageReader == mImageReader){
                            mImageReader = null;
                        }
                    }
                }
            }
        });
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
