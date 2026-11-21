package com.cookiepatrol.demosender;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class DemoMessageReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.cookiepatrol.demosender.SHOW_MESSAGE";
    private static final String CHANNEL_ID = "demo_messages";
    private static final String DEMO_LABEL = "쿠키패트롤 시연";
    private static final int NOTIFICATION_ID = 1;
    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final int MAX_ENCODED_LENGTH = MAX_MESSAGE_LENGTH * 4;
    private static final long REPEAT_WINDOW_MILLIS = 3_000L;
    private static final AtomicLong LAST_MESSAGE_TIME = new AtomicLong();
    private static final Pattern RUN_ID = Pattern.compile("[0-9A-F]{8}");

    @Override
    public void onReceive(Context context, Intent intent) {
        String body = decodeBody(intent);
        if (body == null) {
            finish(Activity.RESULT_CANCELED, "INVALID_MESSAGE");
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
            finish(Activity.RESULT_CANCELED, "NOTIFICATIONS_DISABLED");
            return;
        }
        SharedPreferences recent = context.getSharedPreferences("recent_demo_message", Context.MODE_PRIVATE);
        String digest = digest(body);
        long now = SystemClock.elapsedRealtime();
        long previousSend = recent.getLong("elapsed_time", -REPEAT_WINDOW_MILLIS);
        if (digest.equals(recent.getString("digest", "")) &&
                now >= previousSend && now - previousSend < REPEAT_WINDOW_MILLIS) {
            finish(Activity.RESULT_OK, "ALREADY_POSTED");
            return;
        }

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "시연 메시지", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            finish(Activity.RESULT_CANCELED, "NOTIFICATIONS_DISABLED");
            return;
        }

        long sentAt = LAST_MESSAGE_TIME.updateAndGet(previous ->
                Math.max(System.currentTimeMillis(), previous + 1));
        String runId = intent.getStringExtra("run_id");
        String senderName = runId != null && RUN_ID.matcher(runId).matches()
                ? DEMO_LABEL + " · " + runId : DEMO_LABEL;
        Person recipient = new Person.Builder().setName("받는 기기").build();
        Person sender = new Person.Builder().setName(senderName).build();
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentTitle(senderName)
                .setContentText(body)
                .setWhen(sentAt)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setStyle(new Notification.MessagingStyle(recipient).addMessage(body, sentAt, sender))
                .build();

        try {
            manager.notify(NOTIFICATION_ID, notification);
            recent.edit().putString("digest", digest).putLong("elapsed_time", now).apply();
            finish(Activity.RESULT_OK, "POSTED");
        } catch (SecurityException e) {
            finish(Activity.RESULT_CANCELED, "NOTIFICATIONS_DISABLED");
        }
    }

    private static String digest(String body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String decodeBody(Intent intent) {
        if (!ACTION.equals(intent.getAction())) return null;
        String encoded = intent.getStringExtra("body64");
        if (encoded == null || encoded.length() > MAX_ENCODED_LENGTH) return null;

        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return body.length() <= MAX_MESSAGE_LENGTH && !body.trim().isEmpty() ? body : null;
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return null;
        }
    }

    private void finish(int code, String data) {
        if (isOrderedBroadcast()) {
            setResultCode(code);
            setResultData(data);
        }
    }
}
