package org.kiibord.hpop;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.Log;

public class AutoDictionary extends ExpandableDictionary {
    static final int FREQUENCY_FOR_PICKED = 3;
    static final int FREQUENCY_FOR_TYPED = 1;
    static final int FREQUENCY_FOR_AUTO_ADD = 250;
    private static final int VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED;
    private static final int PROMOTION_THRESHOLD = 4 * FREQUENCY_FOR_PICKED;
    private LatinIME mIme;
    private String mLocale;
    private HashMap<String,Integer> mPendingWrites = new HashMap<String,Integer>();
    private final Object mPendingWritesLock = new Object();
    private static final String DATABASE_NAME = "auto_dict.db";
    private static final int DATABASE_VERSION = 1;
    private static final String COLUMN_ID = BaseColumns._ID;
    private static final String COLUMN_WORD = "word";
    private static final String COLUMN_FREQUENCY = "freq";
    private static final String COLUMN_LOCALE = "locale";
    public static final String DEFAULT_SORT_ORDER = COLUMN_FREQUENCY + " DESC";
    private static final String AUTODICT_TABLE_NAME = "words";
    private static HashMap<String, String> sDictProjectionMap;

    static { sDictProjectionMap = new HashMap<String, String>();
        sDictProjectionMap.put(COLUMN_ID, COLUMN_ID); sDictProjectionMap.put(COLUMN_WORD, COLUMN_WORD);
        sDictProjectionMap.put(COLUMN_FREQUENCY, COLUMN_FREQUENCY); sDictProjectionMap.put(COLUMN_LOCALE, COLUMN_LOCALE);
    }
    private static DatabaseHelper sOpenHelper = null;
    public AutoDictionary(Context context, LatinIME ime, String locale, int dicTypeId) { super(context, dicTypeId);mIme = ime;mLocale = locale;
        if (sOpenHelper == null) sOpenHelper = new DatabaseHelper(getContext());
        if (mLocale != null && mLocale.length() > 1) loadDictionary();
    }
    @Override public boolean isValidWord(CharSequence word) { final int frequency = getWordFrequency(word);return frequency >= VALIDITY_THRESHOLD; }
    @Override public void close() { flushPendingWrites();super.close(); }
    @Override public void loadDictionaryAsync() {
        Cursor cursor = query(COLUMN_LOCALE + "=?", new String[] { mLocale });
        try {
            if (cursor.moveToFirst()) {
                int wordIndex = cursor.getColumnIndex(COLUMN_WORD);
                int frequencyIndex = cursor.getColumnIndex(COLUMN_FREQUENCY);
                while (!cursor.isAfterLast()) {
                    String word = cursor.getString(wordIndex);
                    int frequency = cursor.getInt(frequencyIndex);
                    if (word.length() < getMaxWordLength()) super.addWord(word, frequency);
                    cursor.moveToNext();
                }
            }
        } finally { cursor.close(); }
    }
    @Override public void addWord(String word, int addFrequency) {
        final int length = word.length();
        if (length < 2 || length > getMaxWordLength()) return;
        if (mIme.getCurrentWord().isAutoCapitalized()) word = Character.toLowerCase(word.charAt(0)) + word.substring(1);
        int freq = getWordFrequency(word);
        freq = freq < 0 ? addFrequency : freq + addFrequency;
        super.addWord(word, freq);
        if (freq >= PROMOTION_THRESHOLD) { mIme.promoteToUserDictionary(word, FREQUENCY_FOR_AUTO_ADD);freq = 0; }
        synchronized (mPendingWritesLock) { mPendingWrites.put(word, freq == 0 ? null : new Integer(freq)); }
    }
    public void flushPendingWrites() {
        synchronized (mPendingWritesLock) {
            if (mPendingWrites.isEmpty()) return;
            new UpdateDbTask(getContext(), sOpenHelper, mPendingWrites, mLocale).execute();
            mPendingWrites = new HashMap<String, Integer>();
        }
    }
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + AUTODICT_TABLE_NAME + " (" + COLUMN_ID + " INTEGER PRIMARY KEY," + COLUMN_WORD + " TEXT," + COLUMN_FREQUENCY + " INTEGER," + COLUMN_LOCALE + " TEXT);");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w("AutoDictionary", "upgrading database from version " + oldVersion + " to " + newVersion + ", vhich will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + AUTODICT_TABLE_NAME); onCreate(db);
        }
    }
    private Cursor query(String selection, String[] selectionArgs) { SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(AUTODICT_TABLE_NAME);qb.setProjectionMap(sDictProjectionMap);SQLiteDatabase db = sOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, null, selection, selectionArgs, null, null, DEFAULT_SORT_ORDER);
        return c;
    }
    private static class UpdateDbTask extends AsyncTask<Void, Void, Void> {
        private final HashMap<String, Integer> mMap;private final DatabaseHelper mDbHelper;private final String mLocale;
        public UpdateDbTask(Context context, DatabaseHelper openHelper, HashMap<String, Integer> pendingWrites, String locale) {
            mMap = pendingWrites;mLocale = locale;mDbHelper = openHelper;
        }
        @Override protected Void doInBackground(Void... v) {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            Set<Entry<String,Integer>> mEntries = mMap.entrySet();
            for (Entry<String,Integer> entry : mEntries) { Integer freq = entry.getValue();
                db.delete(AUTODICT_TABLE_NAME, COLUMN_WORD + "=? AND " + COLUMN_LOCALE + "=?", new String[] { entry.getKey(), mLocale });
                if (freq != null) db.insert(AUTODICT_TABLE_NAME, null, getContentValues(entry.getKey(), freq, mLocale));
            }
            return null;
        }
        private ContentValues getContentValues(String word, int frequency, String locale) { ContentValues values = new ContentValues(4);
            values.put(COLUMN_WORD, word);values.put(COLUMN_FREQUENCY, frequency);values.put(COLUMN_LOCALE, locale);
            return values;
        }
    }
}
