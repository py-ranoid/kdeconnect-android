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
import android.annotation.TargetApi;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ContactsPlugin extends Plugin {

    /**
     * Used to request the device send the unique ID of every contact
     */
    public static final String PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS = "kdeconnect.contacts.request_all_uids_timestamps";

    /**
     * Used to request the names for the contacts corresponding to a list of UIDs
     * <p>
     * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
     */
    public static final String PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS = "kdeconnect.contacts.request_vcards_by_uid";

    /**
     * Response indicating the packet contains a list of contact uIDs
     * <p>
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * The returned IDs can be used in future requests for more information about the contact
     */
    public static final String PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS = "kdeconnect.contacts.response_uids_timestamps";

    /**
     * Response indicating the packet contains a list of contact names
     * <p>
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * then, for each UID, there shall be a field with the key of that UID and the value of the name of the contact
     * <p>
     * For example:
     * ( 'uids' : ['1', '3', '15'],
     *     '1'  : 'John Smith',
     *     '3'  : 'Abe Lincoln',
     *     '15' : 'Mom' )
     */
    public static final String PACKET_TYPE_CONTACTS_RESPONSE_VCARDS = "kdeconnect.contacts.response_vcards";

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
    public String[] getSupportedPacketTypes() {
        return new String[]{
                PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS,
                PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS
        };
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{
                PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS,
                PACKET_TYPE_CONTACTS_RESPONSE_VCARDS
        };
    }

    @Override
    public boolean onCreate() {
        permissionExplanation = contactsPermissionExplanation;

        return true;
    }

    @Override
    /**
     * Since this plugin could leak sensitive information, probably best to leave disabled by default
     */
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS};
        // One day maybe we will also support WRITE_CONTACTS, but not yet
    }

    @Override
    public int getMinSdk() {
        // Need API 18 for contact timestamps
        return Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    /**
     * Add custom fields to the vcard to keep track of KDE Connect-specific fields
     *
     * These include the local device's uID as well as last-changed timestamp
     *
     * This might be extended in the future to include more fields
     *
     * @param vcard vcard to apply metadata to
     * @param uID   uID to which the vcard corresponds
     * @return
     */
    protected String addVCardMetadata(String vcard, Long uID) {
        StringBuilder newVCard = new StringBuilder();

        // Clean the END:VCARD tag
        String vcardBody = vcard.substring(0, vcard.indexOf("END:VCARD"));

        // Build the device ID line
        // Unclear if the deviceID forms a valid name per the vcard spec. Worry about that later..
        String uIDLine = "X-KDECONNECT-ID-DEV-" + device.getDeviceId() + ":" + uID.toString();

        // Build the timestamp line
        // Maybe one day this should be changed into the vcard-standard REV key
        List<Long> uIDs = new ArrayList<>();
        uIDs.add(uID);

        final String[] contactsProjection = new String[]{
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        Map<Long, Map<String, Object>> timestamp = ContactsHelper.getColumnsFromContactsForRawContactIDs(context, uIDs, contactsProjection);
        String timestampLine = "X-KDECONNECT-TIMESTAMP:" + ((Integer)timestamp.get(uID).get(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)).toString();

        newVCard.append(vcardBody) // Body already has a trailing newline
                .append(uIDLine).append('\n')
                .append(timestampLine).append('\n')
                .append("END:VCARD");

        return newVCard.toString();
    }

    /**
     * Return a unique identifier (long int) for all contacts in the Contacts database
     * <p>
     * The identifiers returned can be used in future requests to get more information
     * about the contact
     *
     * @param np The package containing the request
     * @return true if successfully handled, false otherwise
     */
    protected boolean handleRequestAllUIDsTimestamps(NetworkPacket np) {
        NetworkPacket reply = new NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS);

        List<Long> uIDs = ContactsHelper.getAllContactRawContactIDs(context);

        ContactsHelper.getVCardsForContactIDs(context, uIDs);

        List<String> uIDsAsStrings = new ArrayList<String>(uIDs.size());

        for (Long uID : uIDs) {
            uIDsAsStrings.add(uID.toString());
        }

        final String[] contactsProjection = new String[]{
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        reply.set("uids", uIDsAsStrings);

        // Add last-modified timestamps
        Map<Long, Map<String, Object>> uIDsToTimestamps = ContactsHelper.getColumnsFromContactsForRawContactIDs(context, uIDs, contactsProjection);
        for (Long ID : uIDsToTimestamps.keySet()) {
            Map<String, Object> data = uIDsToTimestamps.get(ID);
            reply.set(ID.toString(), (Integer)data.get(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP));
        }

        device.sendPacket(reply);

        return true;
    }

    protected boolean handleRequestVCardsByUIDs(NetworkPacket np) {
        if (!np.has("uids")) {
            Log.e("ContactsPlugin", "handleRequestNamesByUIDs received a malformed packet with no uids key");
            return false;
        }

        List<String> uIDsAsStrings = np.getStringList("uids");

        // Convert to Collection<Long> to call getVCardsForContactIDs
        Set<Long> uIDs = new HashSet<Long>(uIDsAsStrings.size());
        for (String uID : uIDsAsStrings) {
            uIDs.add(Long.parseLong(uID));
        }

        final String[] contactsProjection = new String[]{
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        };

        Map<Long, String> uIDsToVCards = ContactsHelper.getVCardsForContactIDs(context, uIDs);

        // ContactsHelper.getVCardsForContactIDs(..) is allowed to reply without
        // some of the requested uIDs if they were not in the database, so update our list
        uIDsAsStrings = new ArrayList<String>(uIDsToVCards.size());

        NetworkPacket reply = new NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_VCARDS);

        // Add the vcards to the packet
        for (Long uID : uIDsToVCards.keySet()) {
            String vcard = uIDsToVCards.get(uID);

            vcard = this.addVCardMetadata(vcard, uID);

            // Store this as a valid uID
            uIDsAsStrings.add(uID.toString());
            // Add the uid : name pairing to the packet
            reply.set(uID.toString(), vcard);
        }

        // Add the valid uIDs to the packet
        reply.set("uids", uIDsAsStrings);

        device.sendPacket(reply);

        return true;
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (np.getType().equals(PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS)) {
            return this.handleRequestAllUIDsTimestamps(np);
        } else if (np.getType().equals(PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS)) {
            return this.handleRequestVCardsByUIDs(np);
        } else {
            Log.e("ContactsPlugin", "Contacts plugin received an unexpected packet!");
            return false;
        }
    }
}
