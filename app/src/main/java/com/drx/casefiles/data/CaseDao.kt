package com.drx.casefiles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM patients ORDER BY updatedAt DESC")
    fun patients(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    fun patient(id: Long): Flow<Patient?>

    @Insert
    suspend fun insertPatient(p: Patient): Long

    @Update
    suspend fun updatePatient(p: Patient)

    @Delete
    suspend fun deletePatient(p: Patient)

    @Query("SELECT * FROM visits ORDER BY date")
    fun allVisits(): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE patientId = :pid ORDER BY date")
    fun visits(pid: Long): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE id = :id")
    fun visit(id: Long): Flow<Visit?>

    @Insert
    suspend fun insertVisit(v: Visit): Long

    @Update
    suspend fun updateVisit(v: Visit)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun visitOnce(id: Long): Visit?

    @Query("SELECT * FROM admissions WHERE id = :id")
    suspend fun admissionOnce(id: Long): Admission?

    @Query("SELECT * FROM admissions")
    fun allAdmissions(): Flow<List<Admission>>

    @Delete
    suspend fun deleteVisit(v: Visit)

    @Query("UPDATE visits SET notes = :notes WHERE id = :id")
    suspend fun setVisitNotes(id: Long, notes: String)

    @Query("UPDATE visits SET date = :date WHERE id = :id")
    suspend fun setVisitDate(id: Long, date: Long)

    @Query("SELECT * FROM media WHERE visitId = :vid ORDER BY takenAt")
    fun media(vid: Long): Flow<List<Media>>

    @Query("SELECT * FROM media ORDER BY takenAt DESC")
    fun allMedia(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE patientId = :pid ORDER BY takenAt")
    fun mediaOfPatientFlow(pid: Long): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE patientId = :pid")
    suspend fun mediaOfPatient(pid: Long): List<Media>

    @Query("SELECT * FROM media WHERE visitId = :vid")
    suspend fun mediaOfVisit(vid: Long): List<Media>

    @Insert
    suspend fun insertMedia(m: Media): Long

    @Update
    suspend fun updateMedia(m: Media)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<Long>)

    @Query("UPDATE media SET visitId = :visitId, patientId = :patientId WHERE id IN (:ids)")
    suspend fun moveMedia(ids: List<Long>, visitId: Long, patientId: Long)

    @Query("UPDATE media SET stage = :stage WHERE id IN (:ids)")
    suspend fun setStage(ids: List<Long>, stage: String)

    @Query("UPDATE media SET healthy = :healthy WHERE id IN (:ids)")
    suspend fun setHealthy(ids: List<Long>, healthy: Boolean)

    @Query("UPDATE patients SET updatedAt = :t WHERE id = :id")
    suspend fun touchPatient(id: Long, t: Long)
    // ---- diagnoses
    @Query("SELECT * FROM diagnoses")
    fun allDiagnoses(): Flow<List<Diagnosis>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiagnosis(d: Diagnosis)

    @Query("DELETE FROM diagnoses WHERE patientId = :pid AND region = :region AND side = :side")
    suspend fun clearDiagnosis(pid: Long, region: String, side: String)

    // ---- admissions
    @Query("SELECT * FROM admissions WHERE patientId = :pid ORDER BY admitDate")
    fun admissions(pid: Long): Flow<List<Admission>>

    @Insert
    suspend fun insertAdmission(a: Admission): Long

    @Update
    suspend fun updateAdmission(a: Admission)

    @Delete
    suspend fun deleteAdmission(a: Admission)

    // ---- marks
    @Query("SELECT * FROM marks")
    fun allMarks(): Flow<List<Mark>>

    @Insert
    suspend fun insertMark(m: Mark): Long

    @Query("DELETE FROM marks WHERE id = (SELECT MAX(id) FROM marks WHERE mediaId = :mid)")
    suspend fun deleteLastMark(mid: Long)
    // ---- exams, vitals, orders
    @Query("SELECT * FROM exams WHERE visitId = :vid")
    fun exams(vid: Long): Flow<List<Exam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(e: Exam)

    @Delete
    suspend fun deleteExam(e: Exam)

    @Query("SELECT * FROM vitals WHERE visitId = :vid ORDER BY id")
    fun vitals(vid: Long): Flow<List<Vital>>

    @Insert
    suspend fun insertVital(v: Vital): Long

    @Delete
    suspend fun deleteVital(v: Vital)

    @Query("SELECT * FROM orders WHERE visitId = :vid ORDER BY id")
    fun orders(vid: Long): Flow<List<MedOrder>>

    @Query("SELECT * FROM ref_ranges ORDER BY name")
    fun refRanges(): Flow<List<RefRange>>

    @Query("SELECT COUNT(*) FROM ref_ranges")
    suspend fun refCount(): Int

    @Insert
    suspend fun insertRef(r: RefRange): Long

    @Update
    suspend fun updateRef(r: RefRange)

    @Delete
    suspend fun deleteRef(r: RefRange)

    @Query("SELECT * FROM lab_values")
    fun allLabValues(): Flow<List<LabValue>>

    @Insert
    suspend fun insertLabValue(v: LabValue): Long

    @Delete
    suspend fun deleteLabValue(v: LabValue)

    @Query("SELECT * FROM orders")
    fun allOrders(): Flow<List<MedOrder>>

    @Query("DELETE FROM orders WHERE visitId = :vid")
    suspend fun clearOrders(vid: Long)

    @Insert
    suspend fun insertOrder(o: MedOrder): Long

    @Update
    suspend fun updateOrder(o: MedOrder)

    @Delete
    suspend fun deleteOrder(o: MedOrder)
}
