package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * GroupMessengerActivity is the main Activity to handle messaging
 * between 5 AVDs on a network. All messaging delivered and stored on
 * each AVD will be Total-FIFO ordered across the network and can handle
 * an AVD failure mid run without application hangup.
 *
 * @author caevans, using elements from SimpleMessenger (by steveko)
 */

public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;

    private int _count = -1, _count2 = -1;
    private Map<String, int[]> _messages = new HashMap<String, int[]>();
    private ArrayList<ContentValues> _messageBuffer = new ArrayList<ContentValues>();


    /* Creation method every time the application starts. Initializes all
     * variables and begins AsyncTasks as needed.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        // Attain and store the port of the current AVD
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        // Create a new ServerSocket and call the Server AsyncTask
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        // Allow for scrolling movement of the text view area
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /* Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /* Sets a listener for the "Send" button, which in turn sends the message to the
         * Client AsyncTask to be sent to all AVDs across the network (along with the
         * current message count and local port number)
         */
        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        _count++;
                        EditText editText = (EditText) findViewById(R.id.editText1);
                        String msg = editText.getText().toString();
                        editText.setText("");
                        String count = Integer.toString(_count);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort, count);
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    // Helper method to set the correct Uri
    private Uri build(){
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("content");
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        return uriBuilder.build();
    }

    // Method to send a string from one port to another via a TCP socket
    private Socket send(Socket socket, String... all) {

        // Pull all message parts to send as a string
        String phase = all[0];
        String localPort = all[1];
        String remotePort = all[2];
        String sequence = all[3];
        String message = all[4];

        try {
            // Create a socket for connection to the desired host
            if (socket == null) {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
            }

            String msgToSend = phase + ":::" + localPort + ":::" + remotePort + ":::" + sequence + ":::" + message;

            // Create an output stream to send data between client/server
            DataOutputStream send = new DataOutputStream(socket.getOutputStream());

            // Write string to stream using UTF-8 encoding
            send.writeUTF(msgToSend);

            // Flush the stream and close the socket
            send.flush();
            //socket.close();

        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        }

        // return the socket for reuse
        return socket;
    }

    /***
     * ServerTask is an AsyncTask to handle incoming messages.
     *
     */

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        // Starts alongside ServerTask and runs in the background
        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            // Set the ServerSocket and initialize a null socket
            ServerSocket serverSocket = sockets[0];
            Socket socket = null;

            // Loop the ServerTask indefinitely to receive multiple messages
            do {
                try {
                    // Listens for a connection and assigns the socket from given ServerSocket
                    socket = serverSocket.accept();

                    // Creates an input stream for client/server data transfer
                    DataInputStream receive = new DataInputStream(socket.getInputStream());

                    // Split all message details into separate strings
                    String[] reception = receive.readUTF().split(":::");
                    String phase = reception[0];
                    String remotePort = reception[1];
                    String localPort = reception[2];
                    String sequence = reception[3];
                    String message = reception[4];

                    // Check if a new message is received
                    if(phase.equals("initial")) {
                        // Reply back to the sender with next local sequence number
                        phase = "proposed";
                        _count2++;
                        sequence = Integer.toString(_count2);
                        send(socket, phase, localPort, remotePort, sequence, message);
                    }

                    // Ignore if proposal inadvertently received along this socket
                    else if(phase.equals("proposed")) {
                        // Do nothing (ClientTask to handle)
                    }

                    // Final delivery received
                    else {
                        // Change local sequence count if necessary
                        if(_count2 < Integer.parseInt(sequence)) {
                            _count2 = Integer.parseInt(sequence);
                        }
                        // Store the message
                        publishProgress(reception);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "ServerSocket IOException");
                }

            } while(!socket.isInputShutdown());

            return null;

        }

        // Called to store a delivered message
        protected void onProgressUpdate(String...strings) {

            // Display the received message in the text view area
            String localPort = strings[1].trim();
            String remotePort = strings[2].trim();
            String sequence = strings[3].trim();
            String message = strings[4].trim();

            // Increment the message counter and create new ContentValues object
            ContentValues next = new ContentValues();

            // Set the counter as the given key and the message as the value
            next.put("key", sequence);
            next.put("value", message);

            // Set flags for storage check
            boolean shift = false, insert = false;

            /* Check the list of messages for a later sequence number that may have been delivered
             * before the given message and sequence. Inserts the message in proper sequence order.
             */
            if(!_messageBuffer.isEmpty()) {
                for (int i = 0; i < _messageBuffer.size(); i++) {
                    int seq = Integer.parseInt(_messageBuffer.get(i).getAsString("key"));
                    if (seq > Integer.parseInt(sequence)) {
                        _messageBuffer.add(i, next);
                        shift = true;
                        insert = true;
                        break;
                    }
                }
                if(!insert) {
                    _messageBuffer.add(next);
                }
            }
            else {
                _messageBuffer.add(next);
            }

            /* Write or rewrite (if out of place sequence found) messages to storage based on
             * location in the list rather than arbitrary assigned sequence of increasing value
             */
            if(shift) {
                for(int i = 0; i < _messageBuffer.size(); i++) {
                    ContentValues renext = new ContentValues();
                    sequence = Integer.toString(i);
                    message = _messageBuffer.get(i).getAsString("value");
                    renext.put("key", sequence);
                    renext.put("value", message);

                    // Print message to app screen for clarity
                    TextView textView = (TextView) findViewById(R.id.textView1);
                    textView.append("\t" + localPort + ":" + remotePort + ":" + sequence + ":" + message + "\n");

                    // Call the Content Provider to insert the pair
                    Uri uri = build();
                    getContentResolver().insert(uri, renext);
                }
            }
            else {
                ContentValues renext = new ContentValues();
                sequence = Integer.toString(_messageBuffer.size() - 1);
                renext.put("key", sequence);
                renext.put("value", message);

                TextView textView = (TextView) findViewById(R.id.textView1);
                textView.append("\t" + localPort + ":" + remotePort + ":" + sequence + ":" + message + "\n");

                Uri uri = build();
                getContentResolver().insert(uri, renext);
            }

            return;
        }
    }


    /***
     * ClientTask is an AsyncTask that sends a message to remote ports.
     *
     */

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            // Declare message parameters
            String phase;
            String message;
            String localPort;
            String remotePort;
            String sequence;

            // Flag for app failure
            int down = 0;

            // Send the data to each of the devices on the network
            if(!msgs[0].equals("")) {
                for (int i = 0; i < 5; i++) {

                    // Assign message parameters
                    phase = "initial";
                    message = msgs[0];
                    localPort = msgs[1];
                    remotePort = PORTS[i];
                    sequence = msgs[2];

                    // Send the message to remote port
                    Socket socket = send(null, phase, localPort, remotePort, sequence, message);

                    // Read back a proposal, if no response then throw/catch an IOException
                    try {
                        DataInputStream receive = new DataInputStream(socket.getInputStream());
                        String[] reception = receive.readUTF().split(":::");
                        phase = reception[0];
                        localPort = reception[2];
                        sequence = reception[3];
                        message = reception[4];

                        // Check to make sure response is a sequence proposal
                        if(phase.equals("proposed")) {

                            // Check if first proposal received
                            if(_messages.containsKey(message)) {
                                int[] set = _messages.get(message);

                                // Check proposed sequence against current highest sequence
                                if(set[0] <= Integer.parseInt(sequence)) {

                                    // Store new sequence proposed
                                    set[0] = Integer.parseInt(sequence);
                                    set[1]++;

                                    // Check if all proposals received; send out message with agreed sequence if so
                                    if(set[1] == (5 - down)) {
                                        phase = "final";
                                        for(int j = 0; j < 5; j++){
                                            remotePort = PORTS[j];
                                            send(null, phase, localPort, remotePort, Integer.toString(set[0]), message);
                                            _messages.remove(message);
                                        }
                                    }
                                    else {
                                        _messages.put(message, set);
                                    }
                                }
                                else {
                                    set[1]++;

                                    // Check if all proposals received; send out message with agreed sequence if so
                                    if(set[1] == (5 - down)) {
                                        phase = "final";
                                        for(int j = 0; j < 5; j++){
                                            remotePort = PORTS[j];
                                            send(null, phase, localPort, remotePort, Integer.toString(set[0]), message);
                                            _messages.remove(message);
                                        }
                                    }
                                    else {
                                        _messages.put(message, set);
                                    }
                                    _messages.put(message, set);
                                }
                            }
                            else {
                                // Store the message alongside its proposed sequence and total propositions
                                int[] set = {Integer.parseInt(sequence), 1};
                                _messages.put(message, set);
                            }
                        }

                    } catch (IOException e) {
                        Log.e(TAG, "Read exception");

                        // Set number of downed AVDs
                        down = 1;

                        // Check if all other proposals received; send out message with agreed sequence if so
                        if (_messages.containsKey(message)) {
                            int[] set = _messages.get(message);
                            if(set[1] == (5 - down)) {
                                phase = "final";
                                for(int j = 0; j < 5; j++){
                                    remotePort = PORTS[j];
                                    send(null, phase, localPort, remotePort, Integer.toString(set[0]), message);
                                    _messages.remove(message);
                                }
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

}
