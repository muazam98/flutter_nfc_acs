package com.vnet.flutter_nfc_acs;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.BluetoothReader;

import java.util.Arrays;

import io.flutter.plugin.common.EventChannel;

import static android.content.ContentValues.TAG;

/**
 * A StreamHandler that emits the IDs of scanned cards.
 */
class CardStreamHandler implements EventChannel.StreamHandler {
    private static final byte[] AUTO_POLLING_START = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x01};
    private static final byte[] AUTO_POLLING_STOP = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x00};
    private static final String requestCardId = "FFCA000000";
    // private static final String requestCardId = "FFB000";
    private BluetoothReader reader;
    private EventChannel.EventSink events;
    public String mTxtResponseApdu = "";
    public boolean APDUResponseReceive = false;
    int currentPage = 1;
    boolean lockRead = false;
    String combineHex = "";
    boolean foundEoR = false;


    void setReader(final BluetoothReader reader) {
        if (reader instanceof Acr1255uj1Reader) {
            this.reader = reader;

            reader.setOnResponseApduAvailableListener((_r, response, errorCode) -> {
                if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (events != null) {
                            String hexString = (Utils.toHexString(Arrays.copyOf(response, response.length - 2)).trim().replace(" ", ""));
                            if (currentPage >= 5) {
                                combineHex += hexString;
                            }
                            byte byteResponse[] = Utils.hexStringToByteArray(hexString);
                            if (currentPage >= 5){
                                for (byte b : byteResponse) {
                                    if (b == (byte) 0x00) {
                                        foundEoR = true;
                                        break;
                                        //Log.d(">>>Found FE", "LF stop here");
                                    }
                                }
                            }
                            if (!foundEoR) {
                                currentPage += 4;
                                getTransmit(reader, currentPage);
                            } else {
                                Log.i(TAG,"Success");
                                events.success(Utils.convertHexToString(combineHex.substring(10)));
                            }

                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (events != null) {
                            events.error("unknown_reader_error", String.valueOf(errorCode), null);
                        }
                    });
                }
            });


            reader.setOnCardStatusChangeListener((bluetoothReader, cardStatusCode) -> {
                combineHex = "";
                currentPage = 1;
                lockRead = true;
                foundEoR = false;

                if (cardStatusCode == BluetoothReader.CARD_STATUS_PRESENT) {

                    String apduToSend = "FFCA000000";
                    byte[] apduByte = Utils.hexStringToByteArray(apduToSend);
                    if (apduByte != null && apduByte.length > 0) {
                        mTxtResponseApdu = "";
                        bluetoothReader.transmitApdu(apduByte);
                    } else {
                        mTxtResponseApdu = "Character format error!";
                    }

                }
            });
        } else {
            Log.i(TAG, "Card stream not supported for this device");
        }
    }

    public void getTransmit(BluetoothReader bReader, int pageToRead) {
        byte[] page_length = {(byte) pageToRead, (byte) 16};
        String apduToSend = "FFB000" + Utils.toHexString(page_length).replace(" ", "");
        byte[] apduByte = Utils.hexStringToByteArray(apduToSend);

        bReader.transmitApdu(apduByte);


    }

    private String getCardStatusString(int cardStatus) {
        if (cardStatus == BluetoothReader.CARD_STATUS_ABSENT) {
            return "Absent";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_PRESENT) {
            return "Present";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWERED) {
            return "Powered";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWER_SAVING_MODE) {
            return "Power saving mode";
        }

        return "Unknown";
    }

    void startPolling() {
        if (reader != null) {
            reader.transmitEscapeCommand(AUTO_POLLING_START);
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        this.events = events;
        startPolling();
    }

    @Override
    public void onCancel(Object arguments) {
        dispose();
    }

    void dispose() {
        if (reader != null) {
            reader.transmitEscapeCommand(AUTO_POLLING_STOP);
            reader.setOnResponseApduAvailableListener(null);
            reader.setOnCardStatusChangeListener(null);
        }
        events = null;
    }
}
