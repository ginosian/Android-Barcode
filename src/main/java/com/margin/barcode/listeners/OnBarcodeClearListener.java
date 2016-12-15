package com.margin.barcode.listeners;

/**
 * Created on May 06, 2016.
 *
 * @author Marta.Ginosyan
 */
public interface OnBarcodeClearListener {

    /**
     * Callback method to catch barcode cleared event
     *
     * @param erasedText text that has been erased
     */
    void onBarcodeCleared(String erasedText);
}
