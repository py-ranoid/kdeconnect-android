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
import android.support.annotation.RequiresApi;
import android.support.v4.util.LongSparseArray;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
        Cursor cursor;
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
            } catch (Exception ignored) {
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
            //noinspection ConstantConditions
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return encodedPhoto.toString();
        } catch (Exception ex) {
            Log.e("ContactsHelper", ex.toString());
            return "";
        } finally {
            try {
                //noinspection ConstantConditions
                input.close();
            } catch (Exception ignored) {
            }

            try {
                //noinspection ConstantConditions
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
    public static List<uID> getAllContactContactIDs(Context context) {
        ArrayList<uID> toReturn = new ArrayList<>();

        // Define the columns we want to read from the Contacts database
        final String[] projection = new String[]{
                ContactsContract.Contacts.LOOKUP_KEY
        };

        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor contactsCursor = context.getContentResolver().query(
                contactsUri,
                projection,
                null, null, null);
        if (contactsCursor != null && contactsCursor.moveToFirst()) {
            do {
                uID contactID;

                int idIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                if (idIndex != -1) {
                    contactID = new uID(contactsCursor.getString(idIndex));
                } else {
                    // Something went wrong with this contact
                    // If you are experiencing this, please open a bug report indicating how you got here
                    Log.e("ContactsHelper", "Got a contact which does not have a LOOKUP_KEY");
                    continue;
                }

                toReturn.add(contactID);
            } while (contactsCursor.moveToNext());
            try {
                contactsCursor.close();
            } catch (Exception ignored) {
            }
        }

        return toReturn;
    }

    /**
     * Get VCards using the batch database query which requires Android API 21
     *
     * @param context    android.content.Context running the request
     * @param IDs        collection of raw contact IDs to look up
     * @param lookupKeys
     * @return Mapping of raw contact IDs to corresponding VCard
     */
    @SuppressWarnings("ALL") // Since this method is busted anyway
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Deprecated
    protected static Map<Long, VCardBuilder> getVCardsFast(Context context, Collection<Long> IDs, Map<Long, String> lookupKeys) {
        LongSparseArray<VCardBuilder> toReturn = new LongSparseArray<>();
        StringBuilder keys = new StringBuilder();

        List<Long> orderedIDs = new ArrayList<>(IDs);

        for (Long ID : orderedIDs) {
            String key = lookupKeys.get(ID);
            keys.append(key);
            keys.append(':');
        }

        // Remove trailing ':'
        keys.deleteCharAt(keys.length() - 1);

        Uri vcardURI = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
                Uri.encode(keys.toString()));

        InputStream input;
        StringBuilder vcardJumble = new StringBuilder();
        try {
            input = context.getContentResolver().openInputStream(vcardURI);

            BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));
            String line;

            while ((line = bufferedInput.readLine()) != null) {
                vcardJumble.append(line).append('\n');
            }
        } catch (IOException e) {
            // If you are experiencing this, please open a bug report indicating how you got here
            e.printStackTrace();
        }

        // At this point we are screwed:
        // There is no way to figure out, given the lookup we just made, which VCard belonges
        // to which ID. They appear to be in the same order as the request was made, but this
        // is (provably) unreliable. I am leaving this code in case it is useful, but unless
        // Android improves their API there is nothing we can do with it

        return null;
    }

    /**
     * Get VCards using serial database lookups. This is tragically slow, but the faster method using
     *
     * There is a faster API specified using ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
     * but there does not seem to be a way to figure out which ID resulted in which VCard using that API
     *
     * @param context    android.content.Context running the request
     * @param IDs        collection of uIDs to look up
     * @return Mapping of uIDs to the corresponding VCard
     */
    @SuppressWarnings("UnnecessaryContinue")
    protected static Map<uID, VCardBuilder> getVCardsSlow(Context context, Collection<uID> IDs) {
        Map<uID, VCardBuilder> toReturn = new HashMap<>();

        for (uID ID : IDs) {
            String lookupKey = ID.toString();
            Uri vcardURI = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);
            InputStream input;
            try {
                input = context.getContentResolver().openInputStream(vcardURI);

                if (input == null)
                {
                    throw new NullPointerException("ContentResolver did not give us a stream for the VCard for uID " + ID);
                }

                BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));

                StringBuilder vcard = new StringBuilder();
                String line;
                while ((line = bufferedInput.readLine()) != null) {
                    vcard.append(line).append('\n');
                }

                toReturn.put(ID, new VCardBuilder(vcard.toString()));
                input.close();
            } catch (IOException e) {
                // If you are experiencing this, please open a bug report indicating how you got here
                e.printStackTrace();
                continue;
            } catch (NullPointerException e)
            {
                // If you are experiencing this, please open a bug report indicating how you got here
                e.printStackTrace();
            }
        }

        return toReturn;
    }

    /**
     * Get the VCard for every specified raw contact ID
     *
     * @param context android.content.Context running the request
     * @param IDs     collection of raw contact IDs to look up
     * @return Mapping of raw contact IDs to the corresponding VCard
     */
    public static Map<uID, VCardBuilder> getVCardsForContactIDs(Context context, Collection<uID> IDs) {
        return getVCardsSlow(context, IDs);
    }

    /**
     * Return a mapping of contact IDs to a map of the requested data from the Contacts database
     * <p>
     * If for some reason there is no row associated with the contact ID in the database,
     * there will not be a corresponding field in the returned map
     *
     * @param context            android.content.Context running the request
     * @param IDs                collection of contact uIDs to look up
     * @param contactsProjection List of column names to extract, defined in ContactsContract.Contacts
     * @return mapping of contact uIDs to desired values, which are a mapping of column names to the data contained there
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // Needed for Cursor.getType(..)
    public static Map<uID, Map<String, Object>> getColumnsFromContactsForIDs(Context context, Collection<uID> IDs, String[] contactsProjection) {
        HashMap<uID, Map<String, Object>> toReturn = new HashMap<>();

        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;

        // Regardless of whether it was requested, we need to look up the uID column
        Set<String> lookupProjection = new HashSet<>(Arrays.asList(contactsProjection));
        lookupProjection.add(uID.COLUMN);

        // We need a selection which looks like "<column> IN(?,?,...?)" with one ? per ID
        StringBuilder contactsSelection = new StringBuilder(uID.COLUMN);
        contactsSelection.append(" IN(");

        for (int i = 0; i < IDs.size(); i++) {
            contactsSelection.append("?,");
        }
        // Remove trailing comma
        contactsSelection.deleteCharAt(contactsSelection.length() - 1);
        contactsSelection.append(")");

        // We need selection arguments as simply a String representation of each ID
        List<String> contactsArgs = new ArrayList<>();
        for (uID ID : IDs) {
            contactsArgs.add(ID.toString());
        }

        Cursor contactsCursor = context.getContentResolver().query(
                contactsUri,
                lookupProjection.toArray(new String[0]),
                contactsSelection.toString(),
                contactsArgs.toArray(new String[0]), null
        );

        if (contactsCursor != null && contactsCursor.moveToFirst()) {
            do {
                Map<String, Object> requestedData = new HashMap<>();

                int lookupKeyIdx = contactsCursor.getColumnIndexOrThrow(uID.COLUMN);
                String lookupKey = contactsCursor.getString(lookupKeyIdx);

                // For each column, collect the data from that column
                for (String column : contactsProjection) {
                    int index = contactsCursor.getColumnIndex(column);
                    // Since we might be getting various kinds of data, Object is the best we can do
                    Object data;
                    int type;
                    if (index == -1) {
                        // This contact didn't have the requested column? Something is very wrong.
                        // If you are experiencing this, please open a bug report indicating how you got here
                        Log.e("ContactsHelper", "Got a contact which does not have a requested column");
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

                toReturn.put(new uID(lookupKey), requestedData);
            } while (contactsCursor.moveToNext());
            try {
                contactsCursor.close();
            } catch (Exception ignored) {
            }
        }

        return toReturn;
    }

    /**
     * This is a cheap ripoff of com.android.vcard.VCardBuilder
     * <p>
     * Maybe in the future that library will be made public and we can switch to using that!
     * <p>
     * The main similarity is the usage of .toString() to produce the finalized VCard and the
     * usage of .appendLine(String, String) to add stuff to the vcard
     */
    public static class VCardBuilder {
        protected static final String VCARD_END = "END:VCARD"; // Written to terminate the vcard
        protected static final String VCARD_DATA_SEPARATOR = ":";

        final StringBuilder vcardBody;

        /**
         * Take a partial vcard as a string and make a VCardBuilder
         *
         * @param vcard vcard to build upon
         */
        public VCardBuilder(String vcard) {
            // Remove the end tag. We will add it back on in .toString()
            vcard = vcard.substring(0, vcard.indexOf(VCARD_END));

            vcardBody = new StringBuilder(vcard);
        }

        /**
         * Appends one line with a given property name and value.
         */
        public void appendLine(final String propertyName, final String rawValue) {
            vcardBody.append(propertyName)
                    .append(VCARD_DATA_SEPARATOR)
                    .append(rawValue)
                    .append("\n");
        }

        public String toString() {
            return vcardBody.toString() + VCARD_END;
        }
    }

    /**
     * Essentially a typedef of the type used for a unique identifier
     */
    public static class uID {
        /**
         * We use the LOOKUP_KEY column of the Contacts table as a unique ID, since that's what it's
         * for
         */
        final String contactLookupKey;

        /**
         * Which Contacts column this uID is pulled from
         */
        static final String COLUMN = ContactsContract.Contacts.LOOKUP_KEY;

        public uID(String lookupKey) {
            contactLookupKey = lookupKey;
        }

        public String toString() {
            return this.contactLookupKey;
        }

        @Override
        public int hashCode() {
            return contactLookupKey.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof uID) {
                return contactLookupKey.equals(((uID) other).contactLookupKey);
            }
            return contactLookupKey.equals(other);
        }
    }
}
