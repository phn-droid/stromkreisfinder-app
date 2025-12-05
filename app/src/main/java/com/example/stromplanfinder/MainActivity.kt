package com.example.stromplanfinder

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // Room
    private lateinit var db: StromplanDatenbank
    private lateinit var dao: StromEintragDao

    // UI – Spinner
    private lateinit var spinnerEtage: Spinner
    private lateinit var spinnerRaum: Spinner
    private lateinit var spinnerVerbraucher: Spinner

    // UI – Ergebnisfelder
    private lateinit var valueFi: TextView
    private lateinit var valueSicherung: TextView
    private lateinit var valueAktor: TextView
    private lateinit var valueKanal: TextView
    private lateinit var valuePhase: TextView
    private lateinit var valueBlock: TextView
    private lateinit var valueKlemme: TextView
    private lateinit var valueRaumnr: TextView
    private lateinit var valueBlatt: TextView
    private lateinit var valueAktiv: TextView
    private lateinit var valueBemerkung: TextView
    private lateinit var textErgebnis: TextView
    private lateinit var statusText: TextView

    // Standardfarbe für "Aktiv"-Text merken
    private lateinit var aktivDefaultTextColors: ColorStateList

    // CSV-Auswahl
    private val csvPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                lifecycleScope.launch {
                    importCsv(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme aus Einstellungen holen
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeOn) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // DB initialisieren
        db = Room.databaseBuilder(
            applicationContext,
            StromplanDatenbank::class.java,
            "stromplan.db"
        ).build()
        dao = db.stromEintragDao()

        // Views holen
        spinnerEtage = findViewById(R.id.spinnerEtage)
        spinnerRaum = findViewById(R.id.spinnerRaum)
        spinnerVerbraucher = findViewById(R.id.spinnerVerbraucher)

        valueFi = findViewById(R.id.valueFi)
        valueSicherung = findViewById(R.id.valueSicherung)
        valueAktor = findViewById(R.id.valueAktor)
        valueKanal = findViewById(R.id.valueKanal)
        valuePhase = findViewById(R.id.valuePhase)
        valueBlock = findViewById(R.id.valueBlock)
        valueKlemme = findViewById(R.id.valueKlemme)
        valueRaumnr = findViewById(R.id.valueRaumnr)
        valueBlatt = findViewById(R.id.valueBlatt)
        valueAktiv = findViewById(R.id.valueAktiv)
        valueBemerkung = findViewById(R.id.valueBemerkung)
        textErgebnis = findViewById(R.id.textErgebnis)
        statusText = findViewById(R.id.statusText)

        // Standard-Textfarbe von "Aktiv" merken (für Ja / Default-Zustand)
        aktivDefaultTextColors = valueAktiv.textColors

        val buttonSuchen: MaterialButton = findViewById(R.id.buttonSuchen)
        val buttonMenu: MaterialButton = findViewById(R.id.buttonMenu)

        // Burgermenü
        buttonMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_csv -> {
                        openCsvPicker()
                        true
                    }

                    R.id.menu_toggle_theme -> {
                        toggleTheme()
                        true
                    }

                    R.id.menu_info -> {
                        startActivity(Intent(this, InfoActivity::class.java))
                        true
                    }

                    R.id.menu_exit -> {
                        finishAffinity()
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        // Spinner-Callbacks
        spinnerEtage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val etage = parent?.getItemAtPosition(position) as? String ?: return
                lifecycleScope.launch {
                    loadRaeume(etage)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerRaum.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val etage = spinnerEtage.selectedItem as? String ?: return
                val raum = parent?.getItemAtPosition(position) as? String ?: return
                lifecycleScope.launch {
                    loadVerbraucher(etage, raum)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        buttonSuchen.setOnClickListener {
            val etage = spinnerEtage.selectedItem as? String
            val raum = spinnerRaum.selectedItem as? String
            val verbraucher = spinnerVerbraucher.selectedItem as? String

            if (etage.isNullOrBlank() || raum.isNullOrBlank() || verbraucher.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "Bitte Etage, Raum und Verbraucher auswählen.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                lifecycleScope.launch {
                    searchStromkreis(etage, raum, verbraucher)
                }
            }
        }

        // Beim Start Etagen laden
        lifecycleScope.launch {
            loadEtagen()
        }
    }

    // ---------------- Theme-Umschalter ----------------

    private fun toggleTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val current = prefs.getBoolean("dark_mode", false)
        val newValue = !current
        prefs.edit().putBoolean("dark_mode", newValue).apply()

        AppCompatDelegate.setDefaultNightMode(
            if (newValue) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ---------------- CSV-Auswahl & Import ----------------

    private fun openCsvPicker() {
        csvPickerLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
    }

    private suspend fun importCsv(uri: Uri) {
        statusText.text = "CSV wird geladen ..."

        val entries = withContext(Dispatchers.IO) {
            val list = mutableListOf<StromEintragEntity>()

            contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.useLines { lines ->
                    val iter = lines.iterator()

                    // Kopfzeile überspringen
                    if (iter.hasNext()) {
                        iter.next()
                    }

                    while (iter.hasNext()) {
                        val line = iter.next()
                        if (line.isBlank()) continue

                        val parts = line.split(';')
                        if (parts.size < 13) continue

                        // NEUE SPALTENREIHENFOLGE DER CSV:
                        // 0: Etage
                        // 1: Raumnr.
                        // 2: Raum
                        // 3: Phase
                        // 4: Verbraucher
                        // 5: FI
                        // 6: LS
                        // 7: Aktor
                        // 8: Kanal
                        // 9: Klemmblock
                        // 10: Klemme
                        // 11: Blatt
                        // 12: Aktiv
                        // 13: Bemerkungen

                        val etage = parts[0].trim()
                        val raumnr = parts[1].trim()
                        val raum = parts[2].trim()
                        val phase = parts[3].trim()
                        val verbraucher = parts[4].trim()
                        val fi = parts[5].trim()
                        val ls = parts[6].trim()
                        val aktor = parts[7].trim()
                        val kanal = parts[8].trim()
                        val block = parts[9].trim()
                        val klemme = parts[10].trim()
                        val blatt = parts[11].trim()
                        val aktivString = parts[12].trim()
                        val bemerkung = if (parts.size > 13) parts[13].trim() else ""

                        val aktiv =
                            aktivString.equals("ja", ignoreCase = true) ||
                                    aktivString == "1" ||
                                    aktivString.equals("true", ignoreCase = true)

                        list.add(
                            StromEintragEntity(
                                etage = etage,
                                raum = raum,
                                verbraucher = verbraucher,
                                raumnr = raumnr,
                                phase = phase,
                                fi = fi,
                                ls = ls,
                                aktor = aktor,
                                kanal = kanal,
                                klemmblock = block,
                                klemme = klemme,
                                blatt = blatt,
                                aktiv = aktiv,
                                bemerkungen = bemerkung
                            )
                        )
                    }
                }

            list
        }

        withContext(Dispatchers.IO) {
            dao.clearAll()
            if (entries.isNotEmpty()) {
                dao.insertAll(entries)
            }
        }

        statusText.text = "Daten aus CSV geladen (${entries.size} Einträge)."
        loadEtagen()
    }

    // --------------- DB-Abfragen für Spinner ---------------

    private suspend fun loadEtagen() {
        val etagen = withContext(Dispatchers.IO) {
            dao.getEtagen()
        }

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            etagen
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerEtage.adapter = adapter

        if (etagen.isNotEmpty()) {
            loadRaeume(etagen[0])
        }
    }

    private suspend fun loadRaeume(etage: String) {
        val raeume = withContext(Dispatchers.IO) {
            dao.getRaeume(etage)
        }

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            raeume
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerRaum.adapter = adapter

        if (raeume.isNotEmpty()) {
            loadVerbraucher(etage, raeume[0])
        } else {
            spinnerVerbraucher.adapter =
                ArrayAdapter(this, R.layout.spinner_item, emptyList<String>())
        }
    }

    private suspend fun loadVerbraucher(etage: String, raum: String) {
        val verbraucherList = withContext(Dispatchers.IO) {
            dao.getVerbraucher(etage, raum)
        }

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            verbraucherList
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerVerbraucher.adapter = adapter
    }

    // --------------- Suche & Ergebnisanzeige ---------------

    private suspend fun searchStromkreis(etage: String, raum: String, verbraucher: String) {
        val eintrag = withContext(Dispatchers.IO) {
            dao.findEintrag(etage, raum, verbraucher)
        }
        showResult(eintrag)
    }

    private fun String?.orDash(): String = if (this.isNullOrBlank()) "-" else this

    private fun showResult(e: StromEintragEntity?) {
        if (e == null) {
            valueFi.text = "-"
            valueSicherung.text = "-"
            valueAktor.text = "-"
            valueKanal.text = "-"
            valuePhase.text = "-"
            valueBlock.text = "-"
            valueKlemme.text = "-"
            valueRaumnr.text = "-"
            valueBlatt.text = "-"
            valueAktiv.text = "-"
            valueBemerkung.text = "-"
            textErgebnis.text = "Kein Stromkreis gefunden."

            // sicherstellen, dass wir nicht versehentlich in Rot stehen bleiben
            valueAktiv.setTextColor(aktivDefaultTextColors)
            return
        }

        valueFi.text = e.fi.orDash()
        valueSicherung.text = e.ls.orDash()
        valueAktor.text = e.aktor.orDash()
        valueKanal.text = e.kanal.orDash()
        valuePhase.text = e.phase.orDash()
        valueBlock.text = e.klemmblock.orDash()
        valueKlemme.text = e.klemme.orDash()
        valueRaumnr.text = e.raumnr.orDash()
        valueBlatt.text = e.blatt.orDash()
        valueBemerkung.text = e.bemerkungen.orDash()

        // Aktiv-Text + Farbe
        if (e.aktiv) {
            valueAktiv.text = "Ja"
            valueAktiv.setTextColor(aktivDefaultTextColors)
        } else {
            valueAktiv.text = "Nein"
            valueAktiv.setTextColor(Color.RED)
        }

        textErgebnis.text = "Stromkreis gefunden."
    }
}
