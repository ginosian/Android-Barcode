package com.margin.barcode.fragments;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.margin.barcode.R;
import com.margin.barcode.listeners.OnBarcodeReceivedListener;
import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

/**
 * Created on Jun 22, 2016.
 *
 * @author Marta.Ginosyan
 */
public class ZXingCameraBarcodeFragment extends DialogFragment implements
        ZXingScannerView.ResultHandler {

    private static final String TAG = ZXingCameraBarcodeFragment.class.getSimpleName();
    private static final int PERMISSIONS_CAMERA = 111;
    private static final String IS_DIALOG = "is_dialog";
    private OnBarcodeReceivedListener mOnBarcodeReceivedListener;
    private boolean mIsDialog;

    private ZXingScannerView mScannerView;
    private View mCloseButton;
    private boolean isRequestingPermission;

    /**
     * Create a new instance of ZXingCameraBarcodeFragment as a dialog
     */
    public static ZXingCameraBarcodeFragment createDialog() {
        ZXingCameraBarcodeFragment fragment = new ZXingCameraBarcodeFragment();
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
        if (mIsDialog) {
            setStyle(DialogFragment.STYLE_NORMAL, R.style.Fullscreen_Dialog);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mIsDialog) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
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
        return inflater.inflate(R.layout.fragment_zxing_barcode_capture, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mScannerView = (ZXingScannerView) view.findViewById(R.id.scanner_view);
        mCloseButton = view.findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsDialog) getDialog().dismiss();
                else getActivity().onBackPressed();
            }
        });
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

    @Override
    public void onResume() {
        super.onResume();
        if (isRequestingPermission) {
            isRequestingPermission = false;
        } else {
            initCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
        mScannerView.setResultHandler(null);
    }

    @Override
    public void handleResult(Result rawResult) {
        if (mOnBarcodeReceivedListener != null) {
            mCloseButton.callOnClick();
            mOnBarcodeReceivedListener.onBarcodeReceived(rawResult.getText());
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScannerView.resumeCameraPreview(ZXingCameraBarcodeFragment.this);
                }
            }, 1000);
        }
    }

    private void initCamera() {
        if (checkPermissions()) {
            mScannerView.setResultHandler(this);
            mScannerView.startCamera();
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_CAMERA);
                isRequestingPermission = true;
                return false;
            }
        }
        return true;
    }

    /**
     * In case that the user denied permission, a {@link Snackbar} will be shown with asking to go
     * to the settings and allow app permissions.
     */
    private void showSnackbar() {
        Snackbar snackbar = Snackbar.make(getActivity().findViewById(android.R.id.content),
                getResources().getString(R.string.no_camera_permission), Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(getResources().getString(R.string.settings), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        });
        snackbar.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_CAMERA:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initCamera();
                } else {
                    showSnackbar();
                }
                isRequestingPermission = true;
                break;
        }
    }
}
