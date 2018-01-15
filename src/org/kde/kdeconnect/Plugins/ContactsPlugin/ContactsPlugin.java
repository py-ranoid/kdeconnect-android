/*
 * ContactsPlugin.java - This file is part of KDE Connect's Android App
 * Implement a way to request and send contact information
 *
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

package org.kde.kdeconnect.Plugins.ContactsPlugin;

import android.Manifest;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsPlugin extends Plugin {

    /**
     * Used to request this device's entire contacts book
     */
    public static final String PACKAGE_TYPE_CONTACTS_REQUEST_ALL = "kdeconnect.contacts.request_all";

    /**
     * Send a list of pairings of contact names and phone numbers
     */
    public static final String PACKAGE_TYPE_CONTACTS_RESPONSE = "kdeconnect.contacts.response";

    private int contactsPermissionExplanation = R.string.contacts_permission_explanation;

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_contacts);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_contacts_desc);
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[] {PACKAGE_TYPE_CONTACTS_REQUEST_ALL};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[] {PACKAGE_TYPE_CONTACTS_RESPONSE};
    }

    @Override
    public boolean onCreate()
    {
        permissionExplanation = contactsPermissionExplanation;

        return true;
    }

    @Override
    /**
     * Since this plugin could leak sensitive information, probably best to leave disabled by default
     */
    public boolean isEnabledByDefault()
    {
        return false;
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS};
        // One day maybe we will also support WRITE_CONTACTS, but not yet
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (np.getType().equals(PACKAGE_TYPE_CONTACTS_REQUEST_ALL))
        {
            // Return the whole contacts book
            // The reply is formatted as a series of:
            // <int>: Name, Category, Number
            // Where int is a unique (incrementing) integer,
            // Name is the contact's name
            // Category is the contact's number category, to differentiate in case there is more
            // than one
            // Number is the contact's number

            NetworkPackage reply = new NetworkPackage(PACKAGE_TYPE_CONTACTS_RESPONSE);

            int index = 0;

            Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;
            Cursor contactsCursor = null;
            try {
                contactsCursor = context.getContentResolver().query(
                        contactsUri,
                        new String[] {
                                ContactsContract.Contacts.DISPLAY_NAME
                                //, ContactsContract.PhoneLookup.PHOTO_URI // One day...
                                , ContactsContract.Contacts.NAME_RAW_CONTACT_ID // Used to index the Data table
                        },
                        null, null, null);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            if (contactsCursor != null && contactsCursor.moveToFirst())
            {
                do {
                    String contactName;
                    String contactNumber;
                    String contactNumberCategory;
                    Long contactID;

                    int nameIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

                    if (nameIndex != -1) {
                        contactName = contactsCursor.getString(nameIndex);
                    } else {
                        // Something went wrong with this contact
                        // TODO: Investigate why this would happen
                        continue;
                    }

                    int idIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID);
                    if (idIndex != -1) {
                        contactID = contactsCursor.getLong(idIndex);
                    } else {
                        // Something went wrong with this contact
                        // TODO: Investigate why this would happen
                        continue;
                    }

                    // For this contact, query the phone's database for its number(s)
                    Uri dataUri = ContactsContract.Data.CONTENT_URI;
                    Cursor dataCursor = null;

                    dataCursor = context.getContentResolver().query(
                            dataUri,
                            new String[]{
                                    ContactsContract.Data.MIMETYPE
                                    // We may need to handle more than "Phone"-type contacts, but for now, only those
                                    , ContactsContract.CommonDataKinds.Phone.NUMBER
                                    , ContactsContract.CommonDataKinds.Phone.TYPE
                                    // Stores the label of the type of the number
                                    , ContactsContract.CommonDataKinds.Phone.LABEL
                            },
                            "RAW_CONTACT_ID == " + contactID, null, null);

                    if (dataCursor != null && dataCursor.moveToFirst())
                    {
                        do
                        {
                            int mimetypeIndex = dataCursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
                            if (mimetypeIndex != -1)
                            {
                                // Check if this is actually a phone record (not email, etc.)
                                String mimetype = dataCursor.getString(mimetypeIndex);

                                if (!(mimetype.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)))
                                {
                                    // Not a phone record
                                    continue;
                                }
                            }

                            int numberIndex = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                            if (numberIndex != -1) {
                                contactNumber = dataCursor.getString(numberIndex);
                            } else {
                                // Something went wrong with this contact
                                // TODO: Investigate why this would happen
                                continue;
                            }

                            int labelIndex = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL);
                            if (labelIndex != -1) {
                                contactNumberCategory = dataCursor.getString(labelIndex);
                            } else {
                                // Something went wrong with this contact
                                // TODO: Investigate why this would happen
                                continue;
                            }

                            // TODO: Decode ContactsContract.CommonDataKinds.Phone.TYPE to get categories
                            if (contactNumberCategory == null)
                            {
                                contactNumberCategory = "Unimplemented";
                            }

                            List<String> contactInfo = new ArrayList<String>();
                            contactInfo.add(contactName);
                            contactInfo.add(contactNumberCategory); // Category
                            contactInfo.add(contactNumber); // Number
                            reply.set(Integer.toString(index), new JSONArray(contactInfo));

                            index ++;
                        } while (dataCursor.moveToNext());
                        try { dataCursor.close(); } catch (Exception e) {}
                    }
                } while (contactsCursor.moveToNext());
                try { contactsCursor.close(); } catch (Exception e) {}
            }

            device.sendPackage(reply);

            return true;
        } else
        {
            Log.e("ContactsPlugin", "Contacts plugin received an unexpected packet!");
            return false;
        }
    }
}