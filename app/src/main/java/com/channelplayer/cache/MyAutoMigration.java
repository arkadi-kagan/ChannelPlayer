package com.channelplayer.cache;

import androidx.annotation.NonNull;
import androidx.room.ProvidedAutoMigrationSpec;
import androidx.room.migration.AutoMigrationSpec;
import androidx.sqlite.db.SupportSQLiteDatabase;

@ProvidedAutoMigrationSpec
public class MyAutoMigration implements AutoMigrationSpec {

    @Override
    public void onPostMigrate(@NonNull SupportSQLiteDatabase db) {
        AutoMigrationSpec.super.onPostMigrate(db);
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_fetchedAt` ON `videos` (`fetchedAt`) ");
    }
}
