package com.margin.barcode.views;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import com.margin.barcode.R;
import com.margin.barcode.fragments.CN51BarcodeFragment;
import com.margin.barcode.fragments.ZXingCameraBarcodeFragment;
import com.margin.barcode.listeners.OnBarcodeClearListener;
import com.margin.barcode.listeners.OnBarcodeReaderError;
import com.margin.barcode.listeners.OnBarcodeReceivedListener;

/**
 * Simple view component for capturing barcode with CN51 hardware or camera preview (with Google
 * Vision API)
 * <p/>
 * Created on Apr 11, 2016.
 *
 * @author Marta.Ginosyan
 */
public class BarcodeEditText extends EditText implements OnBarcodeReceivedListener,
        View.OnTouchListener {

    private static final String BLACK = "black";
    private static final String WHITE = "white";

    private static final String CN51_FRAGMENT_TAG = "cn51_fragment";
    private static final String CAMERA_FRAGMENT_TAG = "camera_fragment";

    private Drawable mClearDrawable;
    private Drawable mCameraDrawable;

    private OnBarcodeReceivedListener mOnBarcodeReceivedListener;
    private OnBarcodeReaderError mOnBarcodeReaderError;
    private OnBarcodeClearListener mOnBarcodeClearListener;
    private Theme mTheme;

    public BarcodeEditText(Context context, Theme theme) {
        super(context);
        mTheme = theme;
        init(null);
    }

    public BarcodeEditText(Context context) {
        this(context, Theme.Black);
    }

    public BarcodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public BarcodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /**
     * Prepares barcode edittext to work
     */
    private void init(AttributeSet attrs) {
        initTheme(attrs);
        Context context = getContext();
        mClearDrawable = ContextCompat.getDrawable(context, mTheme == Theme.Black ?
                R.drawable.clear_button_icon_black : R.drawable.clear_button_icon_white);
        mClearDrawable.setBounds(0, 0, mClearDrawable.getIntrinsicWidth(),
                mClearDrawable.getIntrinsicHeight());
        mCameraDrawable = ContextCompat.getDrawable(context, mTheme == Theme.Black ?
                R.drawable.camera_button_icon_black : R.drawable.camera_button_icon_white);
        mCameraDrawable.setBounds(0, 0, mCameraDrawable.getIntrinsicWidth(),
                mCameraDrawable.getIntrinsicHeight());
        setRightDrawable(mCameraDrawable);
        setOnTouchListener(this);
        setOnFocusChangeListener(new BarcodeEditTextFocusListener());
        addTextChangedListener(new BarcodeEditTextWatcher());
        setTextColor(mTheme == Theme.Black ? Color.BLACK : Color.WHITE);
        setHintTextColor(mTheme == Theme.Black ? ContextCompat.getColor(context, R.color.black38)
                : ContextCompat.getColor(context, R.color.white50));
    }

    /**
     * Parses xml attributes from {@link AttributeSet} and gets theme value Theme.Black is a default
     * value
     */
    private void initTheme(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs,
                    R.styleable.BarcodeEditText, 0, 0);
            try {
                int ordinal = typedArray.getInt(R.styleable.BarcodeEditText_barcode_theme, 0);
                mTheme = Theme.values()[ordinal];
            } finally {
                typedArray.recycle();
            }
        }
        if (mTheme == null) mTheme = Theme.Black;
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

    /**
     * Sets callback for listening to barcode edittext cleanings
     */
    public void setOnBarcodeClearListener(OnBarcodeClearListener onBarcodeClearListener) {
        mOnBarcodeClearListener = onBarcodeClearListener;
    }

    /**
     * The camera button was pressed. The dialog with barcode camera will be shown.
     */
    private void onCameraButtonPressed() {
        Context context = getContext();
        // we should do this if we show barcodeEditText in fragment
        if (!(context instanceof FragmentActivity)) {
            // TODO: How do we know this will only end up being 5?
            for (int i = 0; i < 5; i++) {
                if (context instanceof ContextWrapper) {
                    context = ((ContextWrapper) context).getBaseContext();
                    if (context instanceof FragmentActivity) break;
                }
            }
        }
        if (context instanceof FragmentActivity) {
            // DialogFragment.show() will take care of adding the fragment
            // in a transaction.  We also want to remove any currently showing
            // dialog, so make our own transaction and take care of that here.
            FragmentActivity activity = (FragmentActivity) context;
            FragmentManager fm = activity.getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            Fragment prev = fm.findFragmentByTag(CAMERA_FRAGMENT_TAG);
            if (prev != null) ft.remove(prev);
            ft.addToBackStack(null);
            // Create and show the dialog.
            ZXingCameraBarcodeFragment fragment = ZXingCameraBarcodeFragment.createDialog();
            fragment.show(ft, CAMERA_FRAGMENT_TAG);
            fm.executePendingTransactions();
            fragment.setOnBarcodeNumberListener(this);
        }
    }

    /**
     * The clear button was pressed. If the barcode edittext contains number then it will remove it.
     * The camera button will be shown after it.
     */
    private void onClearButtonPressed() {
        if (mOnBarcodeClearListener != null) {
            mOnBarcodeClearListener.onBarcodeCleared(getText().toString());
        }
        getText().clear();
        setRightDrawable(mCameraDrawable);
    }

    /**
     * Sets right drawable in barcode edittext
     */
    private void setRightDrawable(Drawable rightDrawable) {
        setCompoundDrawables(getCompoundDrawables()[0],
                getCompoundDrawables()[1], rightDrawable, getCompoundDrawables()[3]);
    }

    @Override
    public void onBarcodeReceived(String barcode) {
        setText(barcode);
        if (getContext() instanceof FragmentActivity) {
            Fragment cameraFragment = ((FragmentActivity) getContext()).getSupportFragmentManager()
                    .findFragmentByTag(CAMERA_FRAGMENT_TAG);
            if (cameraFragment != null) {
                DialogFragment dialogFragment = (DialogFragment) cameraFragment;
                dialogFragment.dismiss();
            }
        }
        if (mOnBarcodeReceivedListener != null) {
            mOnBarcodeReceivedListener.onBarcodeReceived(barcode);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (getCompoundDrawables()[2] != null) {
            if (event.getX() > (getWidth() - getPaddingRight() -
                    getCompoundDrawables()[2].getIntrinsicWidth())) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (TextUtils.isEmpty(getText().toString())) {
                        onCameraButtonPressed();
                    } else {
                        onClearButtonPressed();
                    }
                }
                return true;
            }
        }
        return false;
    }

    public enum Theme {

        Black(BLACK),
        White(WHITE);

        private String mTheme;

        Theme(String theme) {
            mTheme = theme;
        }

        @Override
        public String toString() {
            return mTheme;
        }
    }

    /**
     * Focus change listener for barcode number edittext
     */
    private class BarcodeEditTextFocusListener implements OnFocusChangeListener {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (getContext() instanceof FragmentActivity) {
                FragmentActivity activity = (FragmentActivity) getContext();
                FragmentManager fm = activity.getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                Fragment prev = fm.findFragmentByTag(CN51_FRAGMENT_TAG);
                if (prev != null) {
                    ft.remove(prev);
                }
                CN51BarcodeFragment cn51Fragment = null;
                if (hasFocus) {
                    cn51Fragment = new CN51BarcodeFragment();
                    ft.add(cn51Fragment, CN51_FRAGMENT_TAG);
                    ft.addToBackStack(null);
                }
                ft.commit();
                fm.executePendingTransactions();
                if (cn51Fragment != null) {
                    cn51Fragment.setOnBarcodeNumberListener(BarcodeEditText.this);
                    cn51Fragment.setOnBarcodeErrorListener(mOnBarcodeReaderError);
                }
            }
        }
    }

    /**
     * Text change listener for barcode number edittext
     */
    private class BarcodeEditTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            boolean isEmpty = TextUtils.isEmpty(s);
            setRightDrawable(isEmpty ? mCameraDrawable : mClearDrawable);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
