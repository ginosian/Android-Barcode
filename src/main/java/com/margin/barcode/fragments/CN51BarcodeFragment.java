package com.margin.barcode.fragments;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;

import com.margin.barcode.listeners.OnBarcodeReaderError;
import com.margin.barcode.listeners.OnBarcodeReceivedListener;
import com.margin.components.utils.GATrackerUtils;
import com.intermec.aidc.AidcManager;
import com.intermec.aidc.BarcodeReadEvent;
import com.intermec.aidc.BarcodeReadListener;
import com.intermec.aidc.BarcodeReader;
import com.intermec.aidc.BarcodeReaderException;
import com.intermec.aidc.VirtualWedge;
import com.intermec.aidc.VirtualWedgeException;

import java.security.InvalidParameterException;

/**
 * Created on Mar 24, 2016.
 *
 * @author Marta.Ginosyan
 */
public class CN51BarcodeFragment extends Fragment implements BarcodeReadListener {

    private BarcodeReader mBarcodeReader;
    private VirtualWedge mWedge;
    private OnBarcodeReceivedListener mOnBarcodeReceivedListener;
    private OnBarcodeReaderError mOnBarcodeReaderError;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectBarcodeService();
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
        try {
            if (parent != null && parent instanceof OnBarcodeReaderError) {
                mOnBarcodeReaderError = (OnBarcodeReaderError) parent;
            } else {
                mOnBarcodeReaderError = (OnBarcodeReaderError) context;
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mOnBarcodeReceivedListener = null;
        mOnBarcodeReaderError = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mWedge != null) {
                mWedge.setEnable(true);
                mWedge = null;
            }

            if (mBarcodeReader != null) {
                mBarcodeReader.close();
                mBarcodeReader = null;
            }

        } catch (VirtualWedgeException e) {
            e.printStackTrace();
            if (mOnBarcodeReaderError != null) {
                mOnBarcodeReaderError.onError(e);
            }
            GATrackerUtils.trackException(getContext(), e);
        }

        //disconnect from data collection service
        // also make sure that current Android version is lower than 5.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AidcManager.disconnectService();
        }
    }

    /**
     * Sets callback for listening to barcode changes
     */
    public void setOnBarcodeNumberListener(OnBarcodeReceivedListener onBarcodeReceivedListener) {
        mOnBarcodeReceivedListener = onBarcodeReceivedListener;
    }

    /**
     * Sets callback for listening to barcode errors
     */
    public void setOnBarcodeErrorListener(OnBarcodeReaderError onBarcodeErrorListener) {
        mOnBarcodeReaderError = onBarcodeErrorListener;
    }

    @Override
    public void barcodeRead(BarcodeReadEvent barcodeReadEvent) {
        if (barcodeReadEvent != null) {
            String barcodeData = barcodeReadEvent.getBarcodeData();
            if (!TextUtils.isEmpty(barcodeData)) {
                if (mOnBarcodeReceivedListener != null) {
                    mOnBarcodeReceivedListener.onBarcodeReceived(barcodeData);
                }
            } else {
                if (mOnBarcodeReaderError != null) {
                    mOnBarcodeReaderError.onError(new InvalidParameterException(
                            "Barcode data is empty"));
                }
            }
        }
    }

    /**
     * Make sure the BarcodeReader depended service is connected and
     * register a callback for service connect and disconnect events.
     * AidcManager implicitly calls service intent, so you should also make sure
     * that current android version is lower then Android 5.0. Beginning with
     * Android 5.0 (API level 21), the system throws an exception if you call
     * bindService() with an implicit intent.
     *
     * @see <a href="http://developer.android.com/guide/components/intents-filters.html#Types">
     * Intent Types in Android</a>
     */
    private void connectBarcodeService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AidcManager.connectService(getActivity(), new AidcManager.IServiceListener() {
                public void onConnect() {

                    // The depended service is connected and it is ready
                    // to receive bar code requests and virtual wedge
                    try {
                        //Initial bar code reader instance
                        mBarcodeReader = new BarcodeReader();
                        //enable scanner
                        mBarcodeReader.setScannerEnable(true);
                        //set listener
                        mBarcodeReader.addBarcodeReadListener(CN51BarcodeFragment.this);

                        //disable virtual wedge
                        mWedge = new VirtualWedge();
                        mWedge.setEnable(false);

                    } catch (BarcodeReaderException | VirtualWedgeException e) {
                        e.printStackTrace();
                        if (mOnBarcodeReaderError != null) {
                            mOnBarcodeReaderError.onError(e);
                        }
                        GATrackerUtils.trackException(getContext(), e);
                    }
                }

                public void onDisconnect() {
                    //add disconnect message/action here
                }

            });
        } else {
            if (mOnBarcodeReaderError != null) {
                mOnBarcodeReaderError.onError(new Exception("Android version 5.0+ " +
                        "isn't supported yet"));
            }
        }
    }
}
