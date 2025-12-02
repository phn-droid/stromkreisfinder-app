package com.example.stromplanfinder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stromEintraege")
data class StromEintragEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val etage: String,
    val raumnr: String,
    val raum: String,
    val phase: String,
    val verbraucher: String,
    val fi: String,
    val ls: String,
    val aktor: String,
    val kanal: String,
    val klemmblock: String,
    val klemme: String,
    val blatt: String,
    val aktiv: Boolean,
    val bemerkungen: String
)
