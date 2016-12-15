package com.margin.barcode.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.margin.barcode.R;
import com.margin.barcode.listeners.OnBarcodeReaderError;
import com.margin.barcode.listeners.OnBarcodeReceivedListener;
import com.margin.components.utils.GATrackerUtils;

/**
 * Created on Mar 21, 2016.
 *
 * @author Marta.Ginosyan
 */
public class BarcodeReaderFragment extends Fragment implements OnBarcodeReceivedListener,
        OnBarcodeReaderError {

    private static final String CN51_FRAGMENT_TAG = "cn51_fragment";
    private static final String CAMERA_FRAGMENT_TAG = "camera_fragment";

    private EditText mBarcodeNumber;
    private OnBarcodeReceivedListener mOnBarcodeReceivedListener;

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
            throw new ClassCastException("Parent must implement OnBarcodeReceivedListener!");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mOnBarcodeReceivedListener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // add CN51Fragment without UI
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.add(new CN51BarcodeFragment(), CN51_FRAGMENT_TAG);
        ft.addToBackStack(CN51_FRAGMENT_TAG);
        ft.replace(R.id.fragment_container, new CameraBarcodeFragment(), CAMERA_FRAGMENT_TAG);
        ft.addToBackStack(CAMERA_FRAGMENT_TAG);
        ft.commit();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_barcode_reader, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBarcodeNumber = (EditText) view.findViewById(R.id.barcode_number);
        View tryAgainButton = view.findViewById(R.id.try_again_button);
        tryAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBarcodeNumber.getText().clear();
            }
        });
        View continueButton = view.findViewById(R.id.continue_button);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBarcodeNumber.getText() != null) {
                    try {
                        mOnBarcodeReceivedListener.onBarcodeReceived(
                                mBarcodeNumber.getText().toString());
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        onError(e);
                        GATrackerUtils.trackException(getContext(), e);
                    }
                }
            }
        });
    }

    @Override
    public void onError(Exception e) {
        Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBarcodeReceived(String barcode) {
        mBarcodeNumber.setText(barcode);
    }
}
