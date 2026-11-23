package com.pulso.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Recibe la alarma programada desde Bridge.schedule() y muestra la notificación del recordatorio.
 * Al tocarla se abre la app (MainActivity).
 */
public class Reminder extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent in) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent p = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, "pulso_rutina")
            .setSmallIcon(R.drawable.ic_stat_v).setContentTitle(in.getStringExtra("t")).setContentText(in.getStringExtra("b"))
            .setAutoCancel(true).setContentIntent(p).setColor(0xFFFF5B2E).build();
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(in.getIntExtra("id", 1), n);
    }
}
