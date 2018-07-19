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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SMSHelper {

    /**
     * Get the base address for the SMS content
     * <p>
     * If we want to support API < 19, it seems to be possible to read via this query
     * This is highly undocumented and very likely varies between vendors but appears to work
     */
    protected static Uri getSMSURIBad() {
        return Uri.parse("content://sms/");
    }

    /**
     * Get the base address for the SMS content
     * <p>
     * Use the new API way which should work on any phone API >= 19
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    protected static Uri getSMSURIGood() {
        // TODO: Why not use Telephony.MmsSms.CONTENT_URI?
        return Telephony.Sms.CONTENT_URI;
    }

    protected static Uri getSMSUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getSMSURIGood();
        } else {
            return getSMSURIBad();
        }
    }

    /**
     * Get the base address for all message conversations
     */
    protected static Uri getConversationUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
        } else {
            // As with getSMSUriBad, this is potentially unsafe depending on whether a specific
            // manufacturer decided to do their own thing
            return Uri.parse("content://mms-sms/conversations");
        }
    }

    /**
     * Get all the messages in a requested thread
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @return List of all messages in the thread
     */
    public static List<Message> getMessagesInThread(Context context, ThreadID threadID) {
        List<Message> toReturn = new ArrayList<>();

        Uri smsUri = getSMSUri();

        final String selection = ThreadID.lookupColumn + " == ?";
        final String[] selectionArgs = new String[] { threadID.toString() };

        Cursor smsCursor = context.getContentResolver().query(
                smsUri,
                Message.smsColumns,
                selection,
                selectionArgs,
                null);

        if (smsCursor != null && smsCursor.moveToFirst()) {
            int threadColumn = smsCursor.getColumnIndexOrThrow(ThreadID.lookupColumn);
            do {
                int thread = smsCursor.getInt(threadColumn);

                HashMap<String, String> messageInfo = new HashMap<>();
                for (int columnIdx = 0; columnIdx < smsCursor.getColumnCount(); columnIdx++) {
                    String colName = smsCursor.getColumnName(columnIdx);
                    String body = smsCursor.getString(columnIdx);
                    messageInfo.put(colName, body);
                }
                toReturn.add(new Message(messageInfo));
            } while (smsCursor.moveToNext());
        } else {
            // No SMSes available?
        }

        if (smsCursor != null) {
            smsCursor.close();
        }

        return toReturn;
    }

    /**
     * Get the last message from each conversation. Can use those thread_ids to look up more
     * messages in those conversations
     *
     * @param context android.content.Context running the request
     * @return Mapping of thread_id to the first message in each thread
     */
    public static Map<ThreadID, Message> getConversations(Context context) {
        HashMap<ThreadID, Message> toReturn = new HashMap<>();

        Uri conversationUri = getConversationUri();

        Cursor conversationsCursor = context.getContentResolver().query(
                conversationUri,
                Message.smsColumns,
                null,
                null,
                null);

        if (conversationsCursor != null && conversationsCursor.moveToFirst()) {
            int threadColumn = conversationsCursor.getColumnIndexOrThrow(ThreadID.lookupColumn);
            do {
                int thread = conversationsCursor.getInt(threadColumn);

                HashMap<String, String> messageInfo = new HashMap<>();
                for (int columnIdx = 0; columnIdx < conversationsCursor.getColumnCount(); columnIdx++) {
                    String colName = conversationsCursor.getColumnName(columnIdx);
                    String body = conversationsCursor.getString(columnIdx);
                    messageInfo.put(colName, body);
                }
                toReturn.put(new ThreadID(thread), new Message(messageInfo));
            } while (conversationsCursor.moveToNext());
        } else {
            // No conversations available?
        }

        if (conversationsCursor != null) {
            conversationsCursor.close();
        }

        return toReturn;
    }

    /**
     * Represent an ID used to uniquely identify a message thread
     */
    public static class ThreadID {
        Integer threadID;
        static final String lookupColumn = Telephony.Sms.THREAD_ID;

        public ThreadID(Integer threadID) {
            this.threadID = threadID;
        }

        public String toString() {
            return this.threadID.toString();
        }

        @Override
        public int hashCode() {
            return this.threadID.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other.getClass().isAssignableFrom(ThreadID.class)) {
                return ((ThreadID) other).threadID.equals(this.threadID);
            }

            return false;
        }
    }

    /**
     * Represent a message and all of its interesting data columns
     */
    public static class Message {

        public final String m_address;
        public final String m_body;
        public final long m_date;
        public final int m_type;
        public final int m_read;
        public final int m_threadID;
        public final int m_uID;

        /**
         * Named constants which are used to construct a Message
         * See: https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html for full documentation
         */
        public static final String ADDRESS = Telephony.Sms.ADDRESS;   // Contact information (phone number or otherwise) of the remote
        public static final String BODY = Telephony.Sms.BODY;         // Body of the message
        public static final String DATE = Telephony.Sms.DATE;         // Date (Unix epoch millis) associated with the message
        public static final String TYPE = Telephony.Sms.TYPE;         // Compare with Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
        public static final String READ = Telephony.Sms.READ;         // Whether we have received a read report for this message (int)
        public static final String THREAD_ID = ThreadID.lookupColumn; // Magic number which binds (message) threads
        public static final String U_ID = Telephony.Sms._ID;           // Something which uniquely identifies this message

        /**
         * Define the columns which are to be extracted from the Android SMS database
         */
        public static final String[] smsColumns = new String[]{
                Message.ADDRESS,
                Message.BODY,
                Message.DATE,
                Message.TYPE,
                Message.READ,
                Message.THREAD_ID,
                Message.U_ID,
        };

        public Message(final HashMap<String, String> messageInfo) {
            m_address = messageInfo.get(Message.ADDRESS);
            m_body = messageInfo.get(Message.BODY);
            m_date = Long.parseLong(messageInfo.get(Message.DATE));
            if (messageInfo.get(Message.TYPE) == null)
            {
                // To be honest, I have no idea why this happens. The docs say the TYPE field is mandatory.
                // Just stick some junk in here and hope we can figure it out later.
                // Quick investigation suggests that these are multi-target MMSes
                m_type = -1;
            } else {
                m_type = Integer.parseInt(messageInfo.get(Message.TYPE));
            }
            m_read = Integer.parseInt(messageInfo.get(Message.READ));
            m_threadID = Integer.parseInt(messageInfo.get(Message.THREAD_ID));
            m_uID = Integer.parseInt(messageInfo.get(Message.U_ID));
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject json = new JSONObject();

            json.put(Message.ADDRESS, m_address);
            json.put(Message.BODY, m_body);
            json.put(Message.DATE, m_date);
            json.put(Message.TYPE, m_type);
            json.put(Message.READ, m_read);
            json.put(Message.THREAD_ID, m_threadID);
            json.put(Message.U_ID, m_uID);

            return json;
        }

        @Override
        public String toString() {
            return this.m_body;
        }
    }
}

