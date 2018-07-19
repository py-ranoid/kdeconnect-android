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

package org.kde.kdeconnect.Plugins.PingPlugin;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;


public class PingPlugin extends Plugin {

    public final static String PACKET_TYPE_PING = "kdeconnect.ping";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {


        if (!np.getType().equals(PACKET_TYPE_PING)) {
            Log.e("PingPlugin", "Ping plugin should not receive packets other than pings!");
            return false;
        }

        Log.e("PingPacketReceiver", "was a ping!");

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);

        int id;
        String message;
        if (np.has("message")) {
            message = np.getString("message");
            id = (int) System.currentTimeMillis();
            if (message.startsWith("::DIALER")){
                if (dialer_handler(message,stackBuilder,id))
                    return true;
            }
        } else {
            message = "Ping!";
            id = 42; //A unique id to create only one notification
        }

        stackBuilder.addNextIntent(new Intent(context, MainActivity.class));
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification noti = new NotificationCompat.Builder(context)
                .setContentTitle(device.getName())
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setTicker(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationHelper.notifyCompat(notificationManager, id, noti);

        return true;

    }

    public boolean dialer_handler(String message,TaskStackBuilder stackBuilder,int notif_id){
        String mparts[] = message.split("::");
        String mtype = mparts[2];
        System.out.println("MTYPE :"+mtype);
        String notif_message = "default";
        if (mtype.equals("DIAL")){
            Intent dialer_intent = new Intent(Intent.ACTION_DIAL);
            String number = mparts[3];
            dialer_intent.setData(Uri.parse("tel:"+number));
            stackBuilder.addNextIntent(dialer_intent);
            notif_message = "Would you like to call "+number;
        }
        else if (mtype.equals("ADD")){

            Intent contact_intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            contact_intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);

            String name = mparts[3];

            contact_intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
            System.out.println("LEN:"+mparts.length);
            switch (mparts.length){
                case 7:contact_intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, mparts[6]);
                case 6:contact_intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, mparts[5]);
                case 5:contact_intent.putExtra(ContactsContract.Intents.Insert.PHONE, mparts[4]);break;
                default:return false;
            }
            stackBuilder.addNextIntent(contact_intent);
            notif_message = "Add a new contact '"+ name + "'";
        }
        else {
            return false;
        }


        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification noti = new NotificationCompat.Builder(context)
                .setContentTitle(device.getName())
                .setContentText(notif_message)
                .setContentIntent(resultPendingIntent)
                .setTicker(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationHelper.notifyCompat(notificationManager, notif_id, noti);
        return true;

    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            device.sendPacket(new NetworkPacket(PACKET_TYPE_PING));
        }
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_PING};
    }

}