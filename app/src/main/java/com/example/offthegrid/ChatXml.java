package com.example.offthegrid;

import android.content.Context;
import android.database.Cursor;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class ChatXml {

    public static final class Summary {
        public int total;
        public int sent;
        public int received;
        public String path;
    }

    private ChatXml() {
    }

    public static Summary export(Context context, String peer)
            throws IOException, XmlPullParserException {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File file = new File(dir, "chat_" + peer.replaceAll("[^A-Za-z0-9]", "_") + ".xml");
        write(context, peer, file);
        Summary summary = read(file);
        summary.path = file.getAbsolutePath();
        return summary;
    }

    private static void write(Context context, String peer, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file);
             Cursor cursor = context.getContentResolver().query(
                     MessageStorage.CONTENT_URI,
                     null,
                     MessageStorage.COL_PEER + "=?",
                     new String[]{peer},
                     MessageStorage.COL_TIMESTAMP + " ASC, " + MessageStorage.COL_ID + " ASC")) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, "UTF-8");
            serializer.startDocument("UTF-8", true);
            serializer.startTag(null, "chat");
            serializer.attribute(null, "peer", clean(peer));
            serializer.attribute(null, "exported", String.valueOf(System.currentTimeMillis()));
            if (cursor != null) {
                int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
                int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
                int imageIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_IMAGE);
                int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
                while (cursor.moveToNext()) {
                    boolean sent = MessageStorage.ME.equals(cursor.getString(senderIndex));
                    String image = cursor.getString(imageIndex);
                    boolean photo = image != null && !image.isEmpty();
                    serializer.startTag(null, "message");
                    serializer.attribute(null, "from", sent ? "me" : "them");
                    serializer.attribute(null, "type", photo ? "photo" : "text");
                    serializer.attribute(null, "time", String.valueOf(cursor.getLong(timeIndex)));
                    serializer.text(photo ? "" : clean(cursor.getString(textIndex)));
                    serializer.endTag(null, "message");
                }
            }
            serializer.endTag(null, "chat");
            serializer.endDocument();
            serializer.flush();
        }
    }

    private static Summary read(File file) throws IOException, XmlPullParserException {
        Summary summary = new Summary();
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "message".equals(parser.getName())) {
                    summary.total++;
                    if ("me".equals(parser.getAttributeValue(null, "from"))) {
                        summary.sent++;
                    } else {
                        summary.received++;
                    }
                }
                event = parser.next();
            }
        }
        return summary;
    }

    private static String clean(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}