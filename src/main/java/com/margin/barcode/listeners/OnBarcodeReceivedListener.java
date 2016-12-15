package com.margin.barcode.listeners;

/**
 * Created on Mar 21, 2016.
 *
 * @author Marta.Ginosyan
 */
public interface OnBarcodeReceivedListener {

    /**
     * Callback method to receive barcode scanned number
     */
    void onBarcodeReceived(String barcode);
}
