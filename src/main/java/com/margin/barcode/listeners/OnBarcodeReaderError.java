package com.margin.barcode.listeners;

/**
 * Created on Mar 24, 2016.
 *
 * @author Marta.Ginosyan
 */
public interface OnBarcodeReaderError {

    /**
     * Callback method to do actions on barcode reader errors
     */
    void onError(Exception e);
}
