package com.margin.barcode.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.margin.barcode.BarcodeGraphic;
import com.margin.barcode.BarcodeTrackerFactory;
import com.margin.barcode.R;
import com.margin.barcode.camera.CameraSource;
import com.margin.barcode.camera.CameraSourcePreview;
import com.margin.barcode.camera.GraphicOverlay;
import com.margin.barcode.listeners.OnBarcodeReaderError;
import com.margin.barcode.listeners.OnBarcodeReceivedListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

/**
 * Created on Mar 25, 2016.
 *
 * @author Marta.Ginosyan
 */
public class CameraBarcodeFragment extends DialogFragment implements OnBarcodeReaderError,
        CameraSource.OnFrameReceivedListener {

    private static final String TAG = CameraBarcodeFragment.class.getSimpleName();
    private static final String IS_DIALOG = "is_dialog";
    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private OnBarcodeReceivedListener mOnBarcodeReceivedListener;
    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;

    private Handler mHandler = new Handler();

    private Runnable mOnFrameReceivedAction = new Runnable() {
        @Override
        public void run() {
            if (mOnBarcodeReceivedListener != null) {
                BarcodeGraphic graphic = mGraphicOverlay.getFirstGraphic();
                Barcode barcode;
                if (graphic != null) {
                    barcode = graphic.getBarcode();
                    if (barcode != null) {
                        try {
                            mOnBarcodeReceivedListener.onBarcodeReceived(barcode.displayValue);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            onError(e);
                        }
                    } else {
                        onError(new Exception("barcode data is null"));
                    }
                }
            }
        }
    };

    private boolean mIsDialog;

    /**
     * Create a new instance of CameraBarcodeFragment as a dialog
     */
    public static CameraBarcodeFragment createDialog() {
        CameraBarcodeFragment fragment = new CameraBarcodeFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(IS_DIALOG, true);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().containsKey(IS_DIALOG)) {
            mIsDialog = getArguments().getBoolean(IS_DIALOG);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_barcode_capture, container, false);
    }

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setOnTouchListener(new BarcodeTouchListener());
        mPreview = (CameraSourcePreview) view.findViewById(R.id.preview);
        mPreview.setCameraPreviewOnTop(mIsDialog);
        mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) view.findViewById(R.id.graphicOverlay);

        Snackbar.make(getActivity().findViewById(android.R.id.content), "Pinch/Stretch to zoom",
                Snackbar.LENGTH_LONG).show();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(true, false);
        } else {
            requestCameraPermission();
        }

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        try {
            if (parent != null && parent instanceof OnBarcodeReceivedListener) {
                mOnBarcodeReceivedListener = (OnBarcodeReceivedListener) parent;
            } else {
                mOnBarcodeReceivedListener = (OnBarcodeReceivedListener) context;
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mOnBarcodeReceivedListener = null;
    }

    /**
     * Sets callback for listening to barcode changes
     */
    public void setOnBarcodeNumberListener(OnBarcodeReceivedListener onBarcodeReceivedListener) {
        mOnBarcodeReceivedListener = onBarcodeReceivedListener;
    }

    /**
     * Handles the requesting of the camera permission.  This includes showing a "Snackbar" message
     * of why the permission is needed then sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermissions(permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermissions(permissions, RC_HANDLE_CAMERA_PERM);
            }
        };

        if (!mIsDialog) {
            Snackbar.make(getActivity().findViewById(android.R.id.content),
                    R.string.permission_camera_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, listener).show();
        } else {
            requestPermissions(permissions, RC_HANDLE_CAMERA_PERM);
        }
    }

    @Override
    public void onError(Exception e) {
        Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFrameReceived() {
        mHandler.post(mOnFrameReceivedAction);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison to
     * other detection examples to enable the barcode detector to detect small barcodes at long
     * distances.
     * <p/>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = getContext().getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = getActivity().registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(getContext(), R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getContext()
                .getApplicationContext(),
                barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
        mCameraSource.setOnFrameReceivedListener(this);
    }

    /**
     * Restarts the camera.
     */
    @Override
    public void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method is invoked for every call on
     * {@link #requestPermissions(String[], int)}. <p> <strong>Note:</strong> It is possible that
     * the permissions request interaction with the user is interrupted. In this case you will
     * receive empty permissions and results arrays which should be treated as a cancellation. </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either
     *                     {@link PackageManager#PERMISSION_GRANTED} or {@link
     *                     PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource(true, false);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                getActivity().finish();
            }
        };

        new AlertDialog.Builder(getContext())
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener).create()
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getContext().getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(
                    getActivity(), code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private class BarcodeTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            boolean b = scaleGestureDetector.onTouchEvent(event);

            return b || getActivity().onTouchEvent(event);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress. Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to retrieve extended info
         *                 about event state.
         * @return Whether or not the detector should consider this event as handled. If an event
         * was not handled, the detector will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example, only wants to update scaling
         * factors if the change is greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by new pointers going down.
         *
         * @param detector The detector reporting the event - use this to retrieve extended info
         *                 about event state.
         * @return Whether or not the detector should continue recognizing this gesture. For
         * example, if a gesture is beginning with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()} and {@link
         * ScaleGestureDetector#getFocusY()} will return focal point of the pointers remaining on
         * the screen.
         *
         * @param detector The detector reporting the event - use this to retrieve extended info
         *                 about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }
}
