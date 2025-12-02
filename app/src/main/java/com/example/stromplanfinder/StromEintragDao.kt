package com.example.stromplanfinder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StromEintragDao {

    @Query("DELETE FROM stromEintraege")
    fun clearAll()

    @Insert
    fun insertAll(eintraege: List<StromEintragEntity>)

    // Für die hierarchische Auswahl
    @Query("SELECT DISTINCT etage FROM stromEintraege WHERE aktiv = 1 ORDER BY etage")
    fun getEtagen(): List<String>

    @Query("SELECT DISTINCT raum FROM stromEintraege WHERE aktiv = 1 AND etage = :etage ORDER BY raumnr")
    fun getRaeume(etage: String): List<String>

    @Query("SELECT DISTINCT verbraucher FROM stromEintraege WHERE aktiv = 1 AND etage = :etage AND raum = :raum ORDER BY verbraucher")
    fun getVerbraucher(etage: String, raum: String): List<String>

    // Konkreten Eintrag finden
    @Query("""
        SELECT * FROM stromEintraege
        WHERE aktiv = 1 AND etage = :etage AND raum = :raum AND verbraucher = :verbraucher
        LIMIT 1
    """)
    fun findEintrag(etage: String, raum: String, verbraucher: String): StromEintragEntity?
}
