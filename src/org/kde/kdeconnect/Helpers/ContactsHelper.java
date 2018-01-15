/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Helpers;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsHelper {


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    /**
     * Lookup the name and photoID of a contact given a phone number
     */
    public static Map<String, String> phoneNumberLookup(Context context, String number) {

        //Log.e("PhoneNumberLookup", number);

        Map<String, String> contactInfo = new HashMap<>();

        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[] {
                            PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.PHOTO_URI
                            /*, PhoneLookup.TYPE
                              , PhoneLookup.LABEL
                              , PhoneLookup.ID */
                    },
                    null, null, null);
        } catch (Exception e) {
            return contactInfo;
        }

        // Take the first match only
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME);
            if (nameIndex != -1) {
                contactInfo.put("name", cursor.getString(nameIndex));
            }

            nameIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI);
            if (nameIndex != -1) {
                contactInfo.put("photoID", cursor.getString(nameIndex));
            }

            try { cursor.close(); } catch (Exception e) {}

            if (!contactInfo.isEmpty()) {
                return contactInfo;
            }
        }

        return contactInfo;
    }

    public static String photoId64Encoded(Context context, String photoId) {
        if (photoId == null) {
            return "";
        }
        Uri photoUri = Uri.parse(photoId);

        InputStream input = null;
        Base64OutputStream output= null;
        try {
            ByteArrayOutputStream encodedPhoto = new ByteArrayOutputStream();
            output = new Base64OutputStream(encodedPhoto, Base64.DEFAULT);
            input = context.getContentResolver().openInputStream(photoUri);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return encodedPhoto.toString();
        } catch (Exception ex) {
            Log.e("ContactsHelper", ex.toString());
            return "";
        } finally {
            try { input.close(); } catch(Exception ignored) { };
            try { output.close(); } catch(Exception ignored) { };
        }
    }

    /**
     * Return all the NAME_RAW_CONTACT_IDS which contribute an entry to a Contact in the database
     *
     * If the user has, for example, joined several contacts, on the phone, the IDs returned will
     * be representative of the joined contact
     *
     * See here: https://developer.android.com/reference/android/provider/ContactsContract.Contacts.html
     * for more information about the connection between contacts and raw contacts
     *
     * @param context android.content.Context running the request
     * @return List of each NAME_RAW_CONTACT_ID in the Contacts database
     */
    public static List<Long> getAllContactRawContactIDs(Context context)
    {
        ArrayList<Long> toReturn = new ArrayList<Long>();

        // Define the columns we want to read from the Contacts database
        final String[] projection = new String[] {
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID
        };

        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor contactsCursor = context.getContentResolver().query(
                contactsUri,
                projection,
                null, null, null);
        if (contactsCursor != null && contactsCursor.moveToFirst())
        {
            do {
                Long contactID;

                int idIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID);
                if (idIndex != -1) {
                    contactID = contactsCursor.getLong(idIndex);
                } else {
                    // Something went wrong with this contact
                    // TODO: Investigate why this would happen
                    Log.e("ContactsHelper", "Got a contact which does not have a NAME_RAW_CONTACT_ID");
                    continue;
                }

                toReturn.add(contactID);
            } while (contactsCursor.moveToNext());
            try { contactsCursor.close(); } catch (Exception e) {}
        }

        return toReturn;
    }
}

