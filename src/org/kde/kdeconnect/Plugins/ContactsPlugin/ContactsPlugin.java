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
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactsPlugin extends Plugin {

    /**
     * Used to request the device send the unique ID of every contact
     */
    public static final String PACKAGE_TYPE_CONTACTS_REQUEST_ALL_UIDS = "kdeconnect.contacts.request_all_uids";

    /**
     * Used to request the names for the contacts corresponding to a list of UIDs
     *
     * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
     */
    public static final String PACKAGE_TYPE_CONTACTS_REQUEST_NAMES_BY_UIDS = "kdeconnect.contacts.request_names_by_uid";

    /**
     * Used to request the phone numbers for the contacts corresponding to a list of UIDs
     *
     * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
     */
    public static final String PACKAGE_TYPE_CONTACTS_REQUEST_PHONES_BY_UIDS = "kdeconnect.contacts.request_phones_by_uid";

    /**
     * Used to request the email addresses for the contacts corresponding to a list of UIDs
     *
     * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
     */
    public static final String PACKAGE_TYPE_CONTACTS_REQUEST_EMAILS_BY_UIDS = "kdeconnect.contacts.request_emails_by_uid";

    /**
     * Response indicating the package contains a list of contact uIDs
     *
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * The returned IDs can be used in future requests for more information about the contact
     */
    public static final String PACKAGE_TYPE_CONTACTS_RESPONSE_UIDS = "kdeconnect.contacts.response_uids";

    /**
     * Response indicating the package contains a list of contact names
     *
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * then, for each UID, there shall be a field with the key of that UID and the value of the name of the contact
     *
     * For example:
     * ( 'uids' : ['1', '3', '15'],
     *  '1'  : 'John Smith',
     *  '3'  : 'Abe Lincoln',
     *  '15' : 'Mom' )
     */
    public static final String PACKAGE_TYPE_CONTACTS_RESPONSE_NAMES = "kdeconnect.contacts.response_names";

    /**
     * Response indicating the package contains a list of contact numbers
     *
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * then, for each UID, there shall be a 3-field list containing the phone number, the type, and the label
     *
     * For now, the values in types are undefined, but coincidentally match the list here:
     * https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone.html
     *
     * The label field is defined to be the custom label if the number is a custom type, otherwise the empty string
     *
     * For example:
     * ( 'uids' : ['1', '3', '15'],
     *  '1'  : [ [ '+221234',  '2', '' ] ]
     *  '3'  : [ [ '+1(222)333-4444', '0', 'Big Red Button' ] ] // This number has a custom type
     *  '15' : [ [ '6061234', '1', '' ] ] )
     */
    public static final String PACKAGE_TYPE_CONTACTS_RESPONSE_PHONES = "kdeconnect.contacts.response_phones";

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
        return new String[] {
                PACKAGE_TYPE_CONTACTS_REQUEST_ALL_UIDS,
                PACKAGE_TYPE_CONTACTS_REQUEST_NAMES_BY_UIDS,
                PACKAGE_TYPE_CONTACTS_REQUEST_PHONES_BY_UIDS,
                PACKAGE_TYPE_CONTACTS_REQUEST_EMAILS_BY_UIDS
        };
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[] {
                PACKAGE_TYPE_CONTACTS_RESPONSE_UIDS,
                PACKAGE_TYPE_CONTACTS_RESPONSE_NAMES,
                PACKAGE_TYPE_CONTACTS_RESPONSE_PHONES
        };
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

    /**
     * Return a unique identifier (long int) for all contacts in the Contacts database
     *
     * The identifiers returned can be used in future requests to get more information
     * about the contact
     *
     * @param np The package containing the request
     * @return true if successfully handled, false otherwise
     */
    protected boolean handleRequestAllUIDs(NetworkPackage np) {
        NetworkPackage reply = new NetworkPackage(PACKAGE_TYPE_CONTACTS_RESPONSE_UIDS);

        List<Long> uIDs = ContactsHelper.getAllContactRawContactIDs(context);

        List<String> uIDsAsStrings = new ArrayList<String>(uIDs.size());

        for (Long uID : uIDs)
        {
            uIDsAsStrings.add(uID.toString());
        }

        reply.set("uids", uIDsAsStrings);

        device.sendPackage(reply);

        return true;
    }

    protected boolean handleRequestNamesByUIDs(NetworkPackage np) {
        if (!np.has("uids"))
        {
            Log.e("ContactsPlugin", "handleRequestNamesByUIDs received a malformed packet with no uids key");
            return false;
        }

        List<String> uIDsAsStrings = np.getStringList("uids");

        // Convert to Set to call getColumnsFromContactsForRawContactIDs
        Set<Long> uIDs = new HashSet<Long>(uIDsAsStrings.size());
        for (String uID : uIDsAsStrings)
        {
            uIDs.add(Long.parseLong(uID));
        }

        final String[] contactsProjection = new String[] {
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        };

        Map<Long, Map<String, Object>> uIDsToNames = ContactsHelper.getColumnsFromContactsForRawContactIDs(context, uIDs, contactsProjection);

        // ContactsHelper.getColumnsFromContactsForRawContactIDs(..) is allowed to reply without
        // some of the requested uIDs if they were not in the database, so update our list
        uIDsAsStrings = new ArrayList<>(uIDsToNames.size());

        NetworkPackage reply = new NetworkPackage(PACKAGE_TYPE_CONTACTS_RESPONSE_NAMES);

        // Add the names to the packet
        for (Long uID : uIDsToNames.keySet())
        {
            Map<String, Object> data = uIDsToNames.get(uID);
            String name;

            if (! data.containsKey(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) {
                // This contact apparently does not have a name
                Log.w("ContactsPlugin", "Got a uID " + uID + " which does not have a name");
                continue;
            }

            name = (String)data.get(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);

            // Store this as a valid uID
            uIDsAsStrings.add(uID.toString());
            // Add the uid : name pairing to the packet
            reply.set(uID.toString(), name);
        }

        // Add the valid uIDs to the packet
        reply.set("uids", uIDsAsStrings);

        device.sendPackage(reply);

        return true;
    }

    protected boolean handleRequestPhonesByUIDs(NetworkPackage np) {
        if (!np.has("uids"))
        {
            Log.e("ContactsPlugin", "handleRequestPhonesByUIDs received a malformed packet with no uids key");
            return false;
        }

        List<String> uIDsAsStrings = np.getStringList("uids");

        // Convert to Set to call getColumnsFromDataForRawContactIDs
        Set<Long> uIDs = new HashSet<Long>(uIDsAsStrings.size());
        for (String uID : uIDsAsStrings)
        {
            uIDs.add(Long.parseLong(uID));
        }

        final String dataMimetype = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE;
        final String[] dataProjection = {
                // We want the actual phone number
                ContactsContract.CommonDataKinds.Phone.NUMBER
                // As well as what type it is
                , ContactsContract.CommonDataKinds.Phone.TYPE
                // Stores the label of the type of the number if it is a custom type
                , ContactsContract.CommonDataKinds.Phone.LABEL};

        Map<Long, List<Map<String, Object>>> uIDsToPhones =
                ContactsHelper.getColumnsFromDataForRawContactIDs(context, uIDs, dataMimetype, dataProjection);

        // ContactsHelper.getColumnsFromContactsForRawContactIDs(..) is allowed to reply without
        // some of the requested uIDs if they were not in the database, so update our list
        uIDsAsStrings = new ArrayList<>(uIDsToPhones.size());

        NetworkPackage reply = new NetworkPackage(PACKAGE_TYPE_CONTACTS_RESPONSE_PHONES);

        // Add the phone numbers to the packet
        for (Long uID : uIDsToPhones.keySet())
        {
            List<List<String>> allPhoneNumbers = new ArrayList<>();
            for (Map<String, Object> data : uIDsToPhones.get(uID))
            {
                HashMap<Integer, String> numberTypesToNumbers = new HashMap<>();
                String number;
                Integer type;
                String label; // Label appears to only be defined if type is custom


                if (!data.containsKey(ContactsContract.CommonDataKinds.Phone.NUMBER)) {
                    // This is the wrong data type?
                    Log.w("ContactsPlugin", "Got a uID " + uID + " which does not have a phone number");
                    continue;
                }

                number = (String) data.get(ContactsContract.CommonDataKinds.Phone.NUMBER);

                // The Android docs say type should be an int
                // However, my phone has that field stored as a string...
                Object typeField = data.get(ContactsContract.CommonDataKinds.Phone.TYPE);
                if (typeField instanceof String)
                {
                    type = Integer.parseInt((String)typeField);
                }
                else if (typeField instanceof Integer){
                    type = (Integer) typeField;
                } else
                {
                    Log.w("ContactsPlugin", "Android docs are wrong -- cannot get Java type of 'type' field");
                    continue; // Continue in case something works...
                }

                if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
                {
                    // If the label is defined, use it
                    label = (String) data.get(ContactsContract.CommonDataKinds.Phone.LABEL);
                }
                else
                {
                    // Otherwise, use the empty string
                    label = "";
                }
                allPhoneNumbers.add(Arrays.asList(new String[]{number, type.toString(), label}));
            }

            // Store this as a valid uID
            uIDsAsStrings.add(uID.toString());
            // Add the uid : [ ['number', 'type', 'label'] ... ] pairing to the packet
            reply.set(uID.toString(), new JSONArray(allPhoneNumbers));
        }

        // Add the valid uIDs to the packet
        reply.set("uids", uIDsAsStrings);

        device.sendPackage(reply);

        return true;
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (np.getType().equals(PACKAGE_TYPE_CONTACTS_REQUEST_ALL_UIDS))
        {
            return this.handleRequestAllUIDs(np);
        } else if (np.getType().equals(PACKAGE_TYPE_CONTACTS_REQUEST_NAMES_BY_UIDS)) {
            return this.handleRequestNamesByUIDs(np);
        } else if (np.getType().equals(PACKAGE_TYPE_CONTACTS_REQUEST_PHONES_BY_UIDS)) {
            return this.handleRequestPhonesByUIDs(np);
        } else
        {
            Log.e("ContactsPlugin", "Contacts plugin received an unexpected packet!");
            return false;
        }
    }
}
