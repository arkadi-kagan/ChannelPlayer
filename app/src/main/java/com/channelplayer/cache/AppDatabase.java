package com.channelplayer.cache;

import android.content.Context;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

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
