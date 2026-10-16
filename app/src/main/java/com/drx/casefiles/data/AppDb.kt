package com.drx.casefiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Patient::class, Visit::class, Media::class, Diagnosis::class, Admission::class, Mark::class, Exam::class, Vital::class, MedOrder::class, RefRange::class, LabValue::class], version = 6, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): CaseDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "cases.db")
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5).build().also { instance = it }
        }
    }
}
