/*
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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SMSHelper {

    /**
     * Get the base address for the SMS content
     *
     * If we want to support API < 19, it seems to be possible to read via this query
     * This is highly undocumented and very likely varies between vendors but appears to work
     */
    protected static Uri getSMSURIBad() {
        return Uri.parse("content://sms/");
    }

    /**
     * Get the base address for the SMS content
     *
     * Use the new API way which should work on any phone API >= 19
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    protected static Uri getSMSURIGood() {
        return Telephony.Sms.CONTENT_URI;
    }

    protected static Uri getSMSUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getSMSURIGood();
        } else
        {
            return getSMSURIBad();
        }
    }

    /**
     * Get all the SMS threads on the phone as well as a bunch of useful-looking data
     *
     * Return a map keyed by Android's Thread ID to a list of all the messages in that thread
     *  Each message is represented by a map containing the keys which seemed most useful and interesting
     *
     * @param context android.content.Context running the request
     * @return Mapping of thread ID to list of messages in that thread
     */
    public static Map<String, List<Map<String, String>>> getSMS(Context context) {
        HashMap<String, List<Map<String, String>>> toReturn = new HashMap<>();

        Uri smsUri = getSMSUri();

        final String[] smsProjection = new String[]{
                Telephony.Sms.ADDRESS,  // Phone number of the remote
                Telephony.Sms.BODY,     // Body of the message
                Telephony.Sms.DATE,     // Some date associated with the message (Received?)
                Telephony.Sms.TYPE,     // Compare with Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
                Telephony.Sms.PERSON,   // Some obscure value that corresponds to the contact
                Telephony.Sms.READ,     // Whether we have received a read report for this message (int)
                Telephony.Sms.THREAD_ID, // Magic number which binds (message) threads
        };

        Cursor smsCursor = context.getContentResolver().query(
                smsUri,
                smsProjection,
                null,
                null,
                null);

        if (smsCursor.moveToFirst()) {
            int addressColumn = smsCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID);
            do {
                String thread = smsCursor.getString(addressColumn);
                if (! toReturn.containsKey(thread))
                {
                    toReturn.put(thread, new ArrayList<Map<String, String>>());
                }
                Map<String, String> messageInfo = new HashMap<>();
                for (int columnIdx = 0; columnIdx < smsCursor.getColumnCount(); columnIdx++) {
                    String colName = smsCursor.getColumnName(columnIdx);
                    String body = smsCursor.getString(columnIdx);
                    messageInfo.put(colName, body);
                }
                toReturn.get(thread).add(messageInfo);
            } while (smsCursor.moveToNext());
        } else
        {
            // No SMSes available?
        }

    return toReturn;
    }

}

