package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerProvider is a key-value table to act as a ContentProvider
 *
 * @author caevans
 *
 */

public class GroupMessengerProvider extends ContentProvider {

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        // The key-value pair is extracted from the ContentValues object
        String key = values.getAsString("key");
        String value = values.getAsString("value");

        // If a file under the name of the given key already exists, delete it
        getContext().deleteFile("key");

        // A new FileOutputStream is declared to write to internal storage
        FileOutputStream outputStream;
        try {
            // The string is converted to bytes and written to the newly created file under the name of the key
            outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());

            // The stream is then closed
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }

        // The URI of the given ContentProvider is returned to the user
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {


        // A new MatrixCursor object is created with the columns "key" and "value"
        String[] columns = {"key", "value"};
        MatrixCursor cursor = new MatrixCursor(columns);

        /* A String to be added to the cursor is initialized and a FileInputStream is
         * declared to read from internal storage
         */
        String value = null;
        FileInputStream inputStream;

        try {
            // A file with the name of the 'selection' parameter is queried to be read from
            inputStream = getContext().openFileInput(selection);

            // Using a BufferedReader the file is read and the string is saved to the String value
            BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
            value = input.readLine();

            // The stream is then closed
            inputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }

        // A new row indicating the stored key-value pair is added to the cursor object before it is returned
        String[] row= {selection, value};
        cursor.addRow(row);
        Log.v("query", selection);
        return cursor;
    }
}
