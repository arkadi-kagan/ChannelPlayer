package com.channelplayer.cache;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.ProvidedAutoMigrationSpec;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.AutoMigrationSpec;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {VideoItem.class, ChannelInfo.class},
        version = 3,
        exportSchema = true,
        autoMigrations = {
                @AutoMigration(from = 2, to = 3, spec = MyAutoMigration.class)
        }
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract VideoDao videoDao();
    public abstract ChannelDao channelDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

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
