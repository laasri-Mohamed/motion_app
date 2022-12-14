package com.pfasn.detective_pf;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pfasn.detective_pf.data.GlobalData;
import com.pfasn.detective_pf.data.Preferences;
import com.pfasn.detective_pf.detection.AggregateLumaMotionDetection;
import com.pfasn.detective_pf.detection.IMotionDetection;
import com.pfasn.detective_pf.detection.LumaMotionDetection;
import com.pfasn.detective_pf.detection.RgbMotionDetection;
import com.pfasn.detective_pf.image.ImageProcessing;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;




public class MotionDetectionActivity extends SensorsActivity {

    private static final String TAG = "MotionDetectionActivity";

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static boolean inPreview = false;
    private static long mReferenceTime = 0;
    private static IMotionDetection detector = null;
    public static  Button camButton ;
    public static Button resetButton;
    public static TextView disp = null;
    private static int motionCounter=0;
    private static volatile AtomicBoolean processing = new AtomicBoolean(false);
    private static int cameraId=1;
    private ToggleButton switchCamera;
    private ToggleButton switchButton;
    private static boolean wantToSave=false;
    private static SeekBar seekBar;


    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thefirstmain);
        //toggle buttons
        switchButton = (ToggleButton) findViewById(R.id.switchButton);
        switchCamera = (ToggleButton) findViewById(R.id.switchCamera);
        switchCamera.setChecked(false);
        //seek bar to let the user set the delay between images
        seekBar = (SeekBar) findViewById(R.id.senBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = Preferences.PICTURE_DELAY/1000;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MotionDetectionActivity.this, "Delay :" + progressChangedValue+" seconds",
                        Toast.LENGTH_SHORT).show();
                Preferences.PICTURE_DELAY=progressChangedValue*1000;

            }
        });

        //if we press camera button
        camButton = (Button) findViewById(R.id.cameraButton);
        camButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wantToSave=switchButton.isChecked();
                setContentView(R.layout.main);
                resetButton = (Button) findViewById(R.id.resetButton);
                preview = (SurfaceView) findViewById(R.id.preview);
                disp = (TextView) findViewById(R.id.motionCounter);
                previewHolder = preview.getHolder();
                previewHolder.addCallback(surfaceCallback);
                previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                if (Preferences.USE_RGB) {
                    detector = new RgbMotionDetection();
                } else if (Preferences.USE_LUMA) {
                    detector = new LumaMotionDetection();
                } else {
                    // Using State based (aggregate map)
                    detector = new AggregateLumaMotionDetection();
                }
            }
        });
    }

    public void onSwitchClick(View v)
    {
        switchCamera();
    }
    public void onResetButton(View v){
        motionCounter=0;
        disp.setText(String.valueOf(motionCounter));
    }
    public void switchCamera(){

        if (cameraId==Camera.CameraInfo.CAMERA_FACING_BACK){
            cameraId=Camera.CameraInfo.CAMERA_FACING_FRONT;
        }else{
            cameraId=Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        camera=Camera.open(cameraId);
        camera.startPreview();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        camera.setPreviewCallback(null);
        if (inPreview) camera.stopPreview();
        inPreview = false;
        camera.release();
        camera = null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        camera=Camera.open(cameraId);
    }

    private PreviewCallback previewCallback = new PreviewCallback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            if (!GlobalData.isPhoneInMotion()) {
                DetectionThread thread = new DetectionThread(data, size.width, size.height);
                thread.start();
                disp.setText(String.valueOf(motionCounter));
            }
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @SuppressLint("LongLogTag")
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
            inPreview = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    private static final class DetectionThread extends Thread {

        private byte[] data;
        private int width;
        private int height;

        public DetectionThread(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            if (!processing.compareAndSet(false, true)) return;

            // Log.d(TAG, "BEGIN PROCESSING...");
            try {
                // Previous frame
                int[] pre = null;
                if (Preferences.SAVE_PREVIOUS) pre = detector.getPrevious();

                // Current frame (with changes)
                // long bConversion = System.currentTimeMillis();
                int[] img = null;
                if (Preferences.USE_RGB) {
                    img = ImageProcessing.decodeYUV420SPtoRGB(data, width, height);
                } else {
                    img = ImageProcessing.decodeYUV420SPtoLuma(data, width, height);
                }
                // long aConversion = System.currentTimeMillis();
                // Log.d(TAG, "Converstion="+(aConversion-bConversion));

                // Current frame (without changes)
                int[] org = null;
                if (Preferences.SAVE_ORIGINAL && img != null) org = img.clone();

                if (img != null && detector.detect(img, width, height)) {
                    // The delay is necessary to avoid taking a picture while in
                    // the
                    // middle of taking another. This problem can causes some
                    // phones
                    // to reboot.
                    long now = System.currentTimeMillis();
                    if (now > (mReferenceTime + Preferences.PICTURE_DELAY)) {
                        mReferenceTime = now;

                        Bitmap previous = null;
                        if (Preferences.SAVE_PREVIOUS && pre != null) {
                            if (Preferences.USE_RGB) previous = ImageProcessing.rgbToBitmap(pre, width, height);
                            else previous = ImageProcessing.lumaToGreyscale(pre, width, height);
                        }

                        Bitmap original = null;
                        if (Preferences.SAVE_ORIGINAL && org != null) {
                            if (Preferences.USE_RGB) original = ImageProcessing.rgbToBitmap(org, width, height);
                            else original = ImageProcessing.lumaToGreyscale(org, width, height);
                        }

                        Bitmap bitmap = null;
                        if (Preferences.SAVE_CHANGES) {
                            if (Preferences.USE_RGB){
                                motionCounter++;
                                bitmap = ImageProcessing.rgbToBitmap(img, width, height);}
                            else bitmap = ImageProcessing.lumaToGreyscale(img, width, height);
                        }

                        Log.i(TAG, "Saving.. previous=" + previous + " original=" + original + " bitmap=" + bitmap);
                        Looper.prepare();
                        //to demand if the user want to save photos or not
                        if(wantToSave==true) {
                            new SavePhotoTask().execute(previous, original, bitmap);
                        }
                    } else {
                        Log.i(TAG, "Not taking picture because not enough time has passed since the creation of the Surface");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                processing.set(false);
            }
            // Log.d(TAG, "END PROCESSING...");

            processing.set(false);
        }
    };

    private static final class SavePhotoTask extends AsyncTask<Bitmap, Integer, Integer> {

        /**
         * {@inheritDoc}
         */
        @SuppressLint("WrongThread")
        //@Override
        protected Integer doInBackground(Bitmap... data) {
            for (int i = 0; i < data.length; i++) {
                Bitmap bitmap = data[i];
                String name = String.valueOf(System.currentTimeMillis());
                if (bitmap != null) save(name, bitmap);

            }
            return 1;
        }

        private void save(String name, Bitmap bitmap) {
            File photo = new File(Environment.getExternalStorageDirectory(), name + ".jpg");
            if (photo.exists()) photo.delete();

            try {
                FileOutputStream fos = new FileOutputStream(photo.getPath());
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
            } catch (java.io.IOException e) {
                Log.e("PictureDemo", "Exception in photoCallback", e);
            }
        }
    }
}