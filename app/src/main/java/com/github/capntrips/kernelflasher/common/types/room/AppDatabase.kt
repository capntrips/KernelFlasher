package com.github.capntrips.kernelflasher.common.types.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.capntrips.kernelflasher.common.types.room.updates.Update
import com.github.capntrips.kernelflasher.common.types.room.updates.UpdateDao

@Database(entities = [Update::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun updateDao(): UpdateDao
}
