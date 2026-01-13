package com.channelplayer.cache;

import android.content.Context;
import android.util.Log;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.CountDownLatch;

@Database(entities = {VideoItem.class, ChannelInfo.class, HistoryInfo.class},
        version = 4,
        exportSchema = true,
        autoMigrations = {
                @AutoMigration(from = 2, to = 3, spec = MyAutoMigration.class),
                @AutoMigration(from = 3, to = 4)
        }
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract VideoDao videoDao();
    public abstract ChannelDao channelDao();
    public abstract HistoryDao historyDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "channel_player_database")
                            .addAutoMigrationSpec(new MyAutoMigration())
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
