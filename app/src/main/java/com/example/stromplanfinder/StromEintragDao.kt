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

    @Query(
        """
        SELECT DISTINCT etage
        FROM stromEintraege
        ORDER BY etage
        """
    )
    fun getEtagen(): List<String>

    @Query(
        """
        SELECT DISTINCT raum
        FROM stromEintraege
        WHERE etage = :etage
        ORDER BY raumnr
        """
    )
    fun getRaeume(etage: String): List<String>

    @Query(
        """
        SELECT DISTINCT verbraucher
        FROM stromEintraege
        WHERE etage = :etage
          AND raum = :raum
        ORDER BY verbraucher
        """
    )
    fun getVerbraucher(etage: String, raum: String): List<String>

    @Query(
        """
        SELECT DISTINCT aktor
        FROM stromEintraege
        WHERE TRIM(aktor) <> ''
        ORDER BY aktor
        """
    )
    fun getAktoren(): List<String>

    @Query(
        """
        SELECT DISTINCT kanal
        FROM stromEintraege
        WHERE aktor = :aktor
          AND TRIM(kanal) <> ''
        ORDER BY kanal
        """
    )
    fun getKanaele(aktor: String): List<String>

    @Query(
        """
        SELECT DISTINCT fi
        FROM stromEintraege
        WHERE TRIM(fi) <> ''
        ORDER BY fi
        """
    )
    fun getFis(): List<String>

    @Query(
        """
        SELECT DISTINCT ls
        FROM stromEintraege
        WHERE fi = :fi
          AND TRIM(ls) <> ''
        ORDER BY ls
        """
    )
    fun getLsForFi(fi: String): List<String>

    @Query(
        """
        SELECT *
        FROM stromEintraege
        WHERE etage = :etage
          AND raum = :raum
          AND verbraucher = :verbraucher
        LIMIT 1
        """
    )
    fun findEintrag(
        etage: String,
        raum: String,
        verbraucher: String
    ): StromEintragEntity?

    @Query(
        """
        SELECT *
        FROM stromEintraege
        WHERE aktor = :aktor
          AND kanal = :kanal
        ORDER BY etage, raumnr, verbraucher
        """
    )
    fun findEintraegeByAktorKanal(
        aktor: String,
        kanal: String
    ): List<StromEintragEntity>

    @Query(
        """
        SELECT *
        FROM stromEintraege
        WHERE fi = :fi
          AND ls = :ls
        ORDER BY etage, raumnr, raum, verbraucher
        """
    )
    fun findEintraegeByFiLs(
        fi: String,
        ls: String
    ): List<StromEintragEntity>
}