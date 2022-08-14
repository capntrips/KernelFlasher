package com.github.capntrips.kernelflasher.common.types.room.updates

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UpdateDao {
    @Query("""SELECT * FROM "update"""")
    fun getAll(): List<Update>

    @Query("""SELECT * FROM "update" WHERE id IN (:id)""")
    fun load(id: Int): Update

    @Insert
    fun insert(update: Update): Long

    @androidx.room.Update
    fun update(update: Update)

    @Delete
    fun delete(update: Update)
}
