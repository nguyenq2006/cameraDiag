package com.quocpnguyen.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class Camera extends Activity {

    final int IMAGE_WIDTH = 800;
    final int IMAGE_HEIGHT = 480;
    private CameraManager cameraManager;
    private ImageReader imageReader;
//    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private String cameraId;
    private CameraDevice cameraDevice;
    private Bitmap bitmapImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
    }

    @Override
    public void onResume(){
        super.onResume();
        startBackgroundThread();
        openCamera();
        if(bitmapImage != null){
            displayImage();
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy(){
        closeCamera();
        bitmapImage.recycle();
        stopBackgroundThread();
        cameraManager = null;
        super.onDestroy();
    }

    private CameraCaptureSession captureSession;
    private  CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice device) {
            cameraDevice = device;
            List<Surface> outputSurfaces = new LinkedList<>();
            outputSurfaces.add(imageReader.getSurface());
            try {
                cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        takePicture();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, null);

            }catch (CameraAccessException e){
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {}

        @Override
        public void onError(CameraDevice cameraDevice, int error) {}
    };
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =  new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                Log.d("OnImageAvailable", "new image available");
                image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                buffer.rewind();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                displayImage();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                image.close();
            }
        }
    };

    private void setupCamera2() {
        Log.d("setupCamera2", "setting up imageReader");

        try {
            this.cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            this.cameraId = cameraManager.getCameraIdList()[0];
            //set up camera output
            imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);


        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
        Log.d("setupCamera2", "camera setup is completed");
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        this.backgroundThread = new HandlerThread("CameraBackground");
        this.backgroundThread.start();
//        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());
        Log.d("startBackgroundThread", "Background thread started");
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        this.backgroundThread.quitSafely();
        try {
            this.backgroundThread.join();
            this.backgroundThread = null;
//            this.backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void openCamera(){
        Log.d("openCamera", "opening camera...");
        if (ContextCompat.checkSelfPermission(Camera.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        this.cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        setupCamera2();
        try {
            cameraManager.openCamera(cameraId, cameraStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.d("openCamera", "camera opened");
    }

    private void takePicture(){
        try{
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(imageReader.getSurface());

            // Focus
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Orientation
            WindowManager windowManager = getWindowManager();
            int rotation = windowManager.getDefaultDisplay().getRotation();
            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

            captureSession.capture(requestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    session.close();
                }
            }, null);


        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void displayImage(){
        ImageView imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setImageBitmap(bitmapImage);
    }
}
