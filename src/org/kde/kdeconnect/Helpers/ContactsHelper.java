/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * Copyright 2018 Simon Redman <simon@ergotech.com>
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    new String[]{
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

            try {
                cursor.close();
            } catch (Exception e) {
            }

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
        Base64OutputStream output = null;
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
            try {
                input.close();
            } catch (Exception ignored) {
            }

            try {
                output.close();
            } catch (Exception ignored) {
            }

        }
    }

    /**
     * Return all the NAME_RAW_CONTACT_IDS which contribute an entry to a Contact in the database
     * <p>
     * If the user has, for example, joined several contacts, on the phone, the IDs returned will
     * be representative of the joined contact
     * <p>
     * See here: https://developer.android.com/reference/android/provider/ContactsContract.Contacts.html
     * for more information about the connection between contacts and raw contacts
     *
     * @param context android.content.Context running the request
     * @return List of each NAME_RAW_CONTACT_ID in the Contacts database
     */
    public static List<Long> getAllContactRawContactIDs(Context context) {
        ArrayList<Long> toReturn = new ArrayList<Long>();

        // Define the columns we want to read from the Contacts database
        final String[] projection = new String[]{
                ContactsContract.Contacts.NAME_RAW_CONTACT_ID
        };

        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor contactsCursor = context.getContentResolver().query(
                contactsUri,
                projection,
                null, null, null);
        if (contactsCursor != null && contactsCursor.moveToFirst()) {
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
            try {
                contactsCursor.close();
            } catch (Exception e) {
            }
        }

        return toReturn;
    }

    /**
     * Get the VCard for every specified raw contact ID
     *
     * @param context android.content.Context running the request
     * @param IDs   collection of raw contact IDs to look up
     * @param pictures Whether pictures should be returned as part of the request
     * @return Mapping of raw contact IDs to the corresponding VCard
     */
    public static Map<Long, String> getVCardsForContactIDs(Context context, Collection<Long> IDs, boolean pictures) {
        Map<Long, String> toReturn = new HashMap<>();

        // Get the contacts' lookup keys, since that is how VCard is looked up
        final String[] contactsProjection = new String[]{
                ContactsContract.Contacts.LOOKUP_KEY
        };

        Map<Long, Map<String, Object>> lookupKeysMap = getColumnsFromContactsForRawContactIDs(context, IDs, contactsProjection);
        Map<Long, String> lookupKeys = new HashMap<>();

        for (Long ID : lookupKeysMap.keySet()) {
            Map<String, Object> returnedColumns = lookupKeysMap.get(ID);
            lookupKeys.put(ID, (String) returnedColumns.get(ContactsContract.Contacts.LOOKUP_KEY));
        }

        for ( Long ID : lookupKeys.keySet() ) {
            String lookupKey = lookupKeys.get(ID);
            Uri vcardURI = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);
            InputStream input;
            try {
                input = context.getContentResolver().openInputStream(vcardURI);
            } catch (FileNotFoundException e) {
                // TODO: In what case is the vcard not found?
                e.printStackTrace();
                continue;
            }

            BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));

            StringBuilder vcard = new StringBuilder();
            String line;

            try {
                while ((line = bufferedInput.readLine()) != null) {
                    vcard.append(line).append('\n');
                }

                toReturn.put(ID, vcard.toString());
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }

        return toReturn;
    }

    /**
     * Return a mapping of raw contact IDs to a map of the requested data from the Contacts database
     * <p>
     * If for some reason there is no row associated with the raw contact ID in the database,
     * there will not be a corresponding field in the returned map
     *
     * @param context            android.content.Context running the request
     * @param IDs                collection of raw contact IDs to look up
     * @param contactsProjection List of column names to extract, defined in ContactsContract.Contacts
     * @return mapping of raw contact IDs to desired values, which are a mapping of column names to the data contained there
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // Needed for Cursor.getType(..)
    public static Map<Long, Map<String, Object>> getColumnsFromContactsForRawContactIDs(Context context, Collection<Long> IDs, String[] contactsProjection) {
        HashMap<Long, Map<String, Object>> toReturn = new HashMap<>();

        // Define the columns we want to read from the RawContacts database
        final String[] rawContactsProjection = new String[]{
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID
        };

        Uri rawContactsUri = ContactsContract.RawContacts.CONTENT_URI;
        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;

        Cursor rawContactsCursor = context.getContentResolver().query(
                rawContactsUri,
                rawContactsProjection,
                null,
                null,
                null);

        if (rawContactsCursor != null && rawContactsCursor.moveToFirst()) {
            do {
                Long rawContactID;
                Long contactID;

                int rawContactIDIndex = rawContactsCursor.getColumnIndex(ContactsContract.RawContacts._ID);
                if (rawContactIDIndex != -1) {
                    rawContactID = rawContactsCursor.getLong(rawContactIDIndex);
                } else {
                    // This raw contact didn't have an ID? Something is very wrong.
                    // TODO: Investigate why this would happen
                    Log.e("ContactsHelper", "Got a raw contact which does not have an _ID");
                    continue;
                }

                // Filter only for the rawContactIDs we were asked to look up
                if (!IDs.contains(rawContactID)) {
                    // This should be achievable (and faster) by providing a selection
                    // and selectionArgs when fetching rawContactsCursor, but I can't
                    // figure that out
                    continue;
                }

                int contactIDIndex = rawContactsCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID);
                if (contactIDIndex != -1) {
                    contactID = rawContactsCursor.getLong(contactIDIndex);
                } else {
                    // Something went wrong with this contact
                    // TODO: Investigate why this would happen
                    Log.e("ContactsHelper", "Got a raw contact which does not have a CONTACT_ID");
                    continue;
                }

                // Filter on only the contact we are interested in
                final String contactsSelection = ContactsContract.Contacts._ID + " == ? ";
                final String[] contactsArgs = new String[]{contactID.toString()};

                Cursor contactsCursor = context.getContentResolver().query(
                        contactsUri,
                        contactsProjection,
                        contactsSelection,
                        contactsArgs, null
                );

                Map<String, Object> requestedData = new HashMap<>();

                if (contactsCursor != null && contactsCursor.moveToFirst()) {
                    // For each column, collect the data from that column
                    for (String column : contactsProjection) {
                        int index = contactsCursor.getColumnIndex(column);
                        // Since we might be getting various kinds of data, Object is the best we can do
                        Object data;
                        int type;
                        if (index == -1) {
                            // This raw contact didn't have an ID? Something is very wrong.
                            // TODO: Investigate why this would happen
                            Log.e("ContactsHelper", "Got a raw contact which does not have an _ID");
                            continue;
                        }

                        type = contactsCursor.getType(index);
                        switch (type) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                data = contactsCursor.getInt(index);
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                data = contactsCursor.getFloat(index);
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                data = contactsCursor.getString(index);
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                data = contactsCursor.getBlob(index);
                                break;
                            default:
                                Log.e("ContactsHelper", "Got an undefined type of column " + column);
                                continue;
                        }

                        requestedData.put(column, data);
                    }
                }
                contactsCursor.close();

                toReturn.put(rawContactID, requestedData);
            } while (rawContactsCursor.moveToNext());
            rawContactsCursor.close();
        }

        return toReturn;
    }

    /**
     * Return a mapping of raw contact IDs to a map of the requested data from the Data database
     * <p>
     * If for some reason there is no row associated with the raw contact ID in the database,
     * there will not be a corresponding field in the returned map
     * <p>
     * For some types of data, there may be many entries in the Data database with the same raw contact ID,
     * so a list of the relevant data is returned
     *
     * @param context        android.content.Context running the request
     * @param IDs            collection of raw contact IDs to look up
     * @param dataMimetype   Mimetype of the column to look up, defined in ContactsContract.CommonDataKinds.<type>.CONTENT_ITEM_TYPE
     * @param dataProjection List of column names to extract, defined in ContactsContract.CommonDataKinds.<type>
     * @return mapping of raw contact IDs to desired values, which are a mapping of column names to the data contained there
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // Needed for Cursor.getType(..)
    public static Map<Long, List<Map<String, Object>>> getColumnsFromDataForRawContactIDs(Context context, Collection<Long> IDs, String dataMimetype, String[] dataProjection) {
        HashMap<Long, List<Map<String, Object>>> toReturn = new HashMap<>();

        // Define a filter for the type of data we were asked to get
        final String dataSelection = ContactsContract.Data.MIMETYPE + " == ?";
        final String[] dataSelectionArgs = {dataMimetype};

        Uri dataUri = ContactsContract.Data.CONTENT_URI;

        // Regardless of what the user requested, we need the RAW_CONTACT_ID field
        // This will not be returned to the user if it wasn't asked for
        Set<String> actualDataProjectionSet = new HashSet<String>();
        actualDataProjectionSet.addAll(Arrays.asList(dataProjection));
        actualDataProjectionSet.add(ContactsContract.Data.RAW_CONTACT_ID);

        String[] actualDataProjection = new String[0];
        actualDataProjection = actualDataProjectionSet.toArray(actualDataProjection);

        Cursor dataCursor = context.getContentResolver().query(
                dataUri,
                actualDataProjection,
                dataSelection,
                dataSelectionArgs,
                null);

        if (dataCursor != null && dataCursor.moveToFirst()) {
            do {
                Long rawContactID;

                Map<String, Object> requestedData = new HashMap<>();

                int rawContactIDIndex = dataCursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
                if (rawContactIDIndex != -1) {
                    rawContactID = dataCursor.getLong(rawContactIDIndex);
                } else {
                    // This  didn't have a RAW_CONTACT_ID? Something is very wrong.
                    // TODO: Investigate why this would happen
                    Log.e("ContactsHelper", "Got a data contact which does not have a RAW_CONTACT_ID");
                    continue;
                }

                // Filter only for the rawContactIDs we were asked to look up
                if (!IDs.contains(rawContactID)) {
                    // This should be achievable (and faster) by providing a selection
                    // and selectionArgs when fetching dataCursor, but I can't
                    // figure that out
                    continue;
                }
                // For each column, collect the data from that column
                for (String column : dataProjection) {
                    int index = dataCursor.getColumnIndex(column);
                    // Since we might be getting various kinds of data, Object is the best we can do
                    Object data;
                    int type;
                    if (index == -1) {
                        // This raw contact didn't have an ID? Something is very wrong.
                        // TODO: Investigate why this would happen
                        Log.e("ContactsHelper", "Got a raw contact which does not have an _ID");
                        continue;
                    }

                    type = dataCursor.getType(index);
                    switch (type) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            data = dataCursor.getInt(index);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            data = dataCursor.getFloat(index);
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            data = dataCursor.getString(index);
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            data = dataCursor.getBlob(index);
                            break;
                        default:
                            Log.w("ContactsHelper", "Got an undefined type of column " + column + " -- Skipping");
                            continue;
                    }

                    requestedData.put(column, data);
                }

                // If we have not already stored some data for this contact, make a new list
                if (!toReturn.containsKey(rawContactID)) {
                    toReturn.put(rawContactID, new ArrayList<Map<String, Object>>());
                }
                toReturn.get(rawContactID).add(requestedData);
            } while (dataCursor.moveToNext());
            dataCursor.close();
        }

        return toReturn;
    }
}

