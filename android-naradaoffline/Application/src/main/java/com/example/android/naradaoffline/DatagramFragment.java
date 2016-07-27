/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.naradaoffline;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import com.example.android.common.logger.Log;

import java.util.ArrayList;
/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class DatagramFragment extends Fragment {

    private static final String TAG = "DatagramFragment";
    private final Gson gson = new Gson();

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private String mText = "";
    private String mEmail = "";

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private ImageButton mGetNewsPaper;
    private ImageButton mGetEmail;
    private ImageButton mWhatsapp;
    private ImageButton mSendEmail;
    private ImageButton mGetAlert;

    public Context cont;


    //Datagram Enums
    public enum DatagramRequestType {GET_NEWSPAPER, GET_NEW_EMAILS, SEND_WHATSAPP_MSG, SEND_EMAIL, GET_ALERTS}

    public enum DatagramResponseType {GET_NEWSPAPER, GET_NEW_EMAILS, SEND_WHATSAPP_MSG, SEND_EMAIL, GET_ALERTS}

    public enum DatagramError {BAD_REQUEST, RECIPIENT_NO_EXIST, SENDER_REFUSED}

    private class DatagramResponse {
        DatagramResponseType type;
        String mDeviceName;
        String mRequest;
        //newspaper data
        String mNewspaperHtml;

        public DatagramResponse(DatagramResponseType type, String newspaperHtml, String deviceName) {
            this.type = type;
            mDeviceName = deviceName;
            mNewspaperHtml = Base64.encodeToString(newspaperHtml.getBytes(), Base64.NO_WRAP);
        }

        //EMAIL TYPE DATAGRAM
//        public DatagramResponse(DatagramRequestType type, String deviceName, String from, String to, String body) {
//            this.type = type;
//            this.mDeviceName = deviceName;
//            mEmailFrom = from;
//            mEmailTo = to;
//            mEmailBody = body;
//        }

        public String readNewspaperHtml() {
            return new String(Base64.decode(mNewspaperHtml, Base64.NO_WRAP));
        }
    }

    private class DatagramRequest {
        DatagramRequestType type;
        String mDeviceName;
        String mRequest;
        //email data
        String mEmailFrom;
        String mEmailTo;
        String mEmailBody;

        public DatagramRequest(DatagramRequestType type, String deviceName) {
            this.type = type;
            mDeviceName = deviceName;
        }

        //EMAIL TYPE DATAGRAM
        public DatagramRequest(DatagramRequestType type, String deviceName, String from, String to, String body) {
            this.type = type;
            this.mDeviceName = deviceName;
            mEmailFrom = from;
            mEmailTo = to;
            mEmailBody = body;
        }
    }

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    private ArrayList<DatagramRequest> mDatagramRequests = new ArrayList<DatagramRequest>();

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothDatagramService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothDatagramService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        //mConversationView = (ListView) view.findViewById(R.id.in);
        mGetNewsPaper = (ImageButton) view.findViewById(R.id.button_news);
        mGetEmail = (ImageButton) view.findViewById(R.id.button_email);
        mWhatsapp = (ImageButton) view.findViewById(R.id.button_whatsapp);
        mSendEmail = (ImageButton) view.findViewById(R.id.button_email3);
        mGetAlert = (ImageButton) view.findViewById(R.id.button_alert);
    }


    protected final void sendEmail(DatagramRequest data) {
        if (data.type != DatagramRequestType.SEND_EMAIL) {
            Log.w(TAG, "This datagram is actually of type " + data.type.name());
        }
        Log.i("Send email", "");

        String[] TO = {data.mEmailTo};
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");


        emailIntent.putExtra(Intent.EXTRA_EMAIL, TO);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Your Datagram");
        emailIntent.putExtra(Intent.EXTRA_TEXT, data.mEmailBody);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            Log.i("Finished sending email...", "");
        } catch (android.content.ActivityNotFoundException ex) {
//            Toast.makeText(MainActivity.this,
//            "There is no email client installed.", Toast.LENGTH_SHORT).show();
        }
    }




    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG);

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        //mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
//        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events



        mGetNewsPaper.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {

//                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    DatagramRequest d = new DatagramRequest(DatagramRequestType.GET_NEWSPAPER, mConnectedDeviceName);
                    sendDatagramRequest(d);
                }
            }
        });

        // Initialize the send button with a listener that for click events
        mGetEmail.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
//                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);

                    Log.i(TAG, "button clicked");

                    AlertDialog.Builder builder = new AlertDialog.Builder(DatagramFragment.this.getContext());
                    builder.setTitle("Write your email here");

                    LinearLayout layout = new LinearLayout(getContext());
                    layout.setOrientation(LinearLayout.VERTICAL);

// Set up the input
                    final EditText email = new EditText(DatagramFragment.this.getContext());
                    email.setHint("Recipient e-mail address");
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                    email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    layout.addView(email);
                    final EditText input = new EditText(DatagramFragment.this.getContext());
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                    input.setHint("Type your e-mail here");
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    layout.addView(input);

                    builder.setView(layout);
// Set up the buttons
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mEmail = email.getText().toString();
                            mText = input.getText().toString();
                            DatagramRequest d = new DatagramRequest(DatagramRequestType.SEND_EMAIL, mConnectedDeviceName, "", mEmail, mText);
                            sendDatagramRequest(d);

                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();

                }
            }
        });




        // Initialize the BluetoothDatagramService to perform bluetooth connections
        mChatService = new BluetoothDatagramService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */

    /**
     * Sends a message.
     *
     */
    private void sendDatagramRequest(DatagramRequest request) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothDatagramService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.i(TAG, "sending datagram");
        byte[] send = gson.toJson(request).getBytes();
        mChatService.write(send);

        // Check that there's actually something to send
//        if (message.length() > 0) {
//            // Get the message bytes and tell the BluetoothDatagramService to write
//            byte[] send = message.getBytes();
//            mChatService.write(send);
//
//            // Reset out string buffer to zero and clear the edit text field
//            mOutStringBuffer.setLength(0);
//            mOutEditText.setText(mOutStringBuffer);
//        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothDatagramService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothDatagramService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothDatagramService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothDatagramService.STATE_LISTEN:
                        case BluetoothDatagramService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);

                    Log.i(TAG, "Message is: " + readMessage.length());
                    DatagramRequest req = gson.fromJson(readMessage, DatagramRequest.class);

                    if(req.type == DatagramRequestType.SEND_EMAIL) sendEmail(req);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG);
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}
