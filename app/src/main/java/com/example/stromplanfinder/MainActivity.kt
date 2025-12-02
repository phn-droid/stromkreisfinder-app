package com.example.stromplanfinder

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var db: StromplanDatenbank
    private lateinit var dao: StromEintragDao

    private lateinit var spinnerEtage: Spinner
    private lateinit var spinnerRaum: Spinner
    private lateinit var spinnerVerbraucher: Spinner

    // Ergebnis-Felder
    private lateinit var valueRaumnr: TextView
    private lateinit var valuePhase: TextView
    private lateinit var valueFi: TextView
    private lateinit var valueSicherung: TextView
    private lateinit var valueAktor: TextView
    private lateinit var valueKanal: TextView
    private lateinit var valueBlock: TextView
    private lateinit var valueKlemme: TextView
    private lateinit var valueBlatt: TextView
    private lateinit var valueAktiv: TextView
    private lateinit var valueBemerkung: TextView

    private lateinit var textErgebnis: TextView
    private lateinit var statusText: TextView

    private lateinit var buttonSuchen: MaterialButton
    private lateinit var buttonMenu: MaterialButton

    private val executor = Executors.newSingleThreadExecutor()

    private var selectedEtage: String? = null
    private var selectedRaum: String? = null
    private var selectedVerbraucher: String? = null

    private lateinit var csvPickerLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = StromplanDatenbank.getInstance(this)
        dao = db.stromEintragDao()

        spinnerEtage = findViewById(R.id.spinnerEtage)
        spinnerRaum = findViewById(R.id.spinnerRaum)
        spinnerVerbraucher = findViewById(R.id.spinnerVerbraucher)

        valueRaumnr = findViewById(R.id.valueRaumnr)
        valuePhase = findViewById(R.id.valuePhase)
        valueFi = findViewById(R.id.valueFi)
        valueSicherung = findViewById(R.id.valueSicherung)
        valueAktor = findViewById(R.id.valueAktor)
        valueKanal = findViewById(R.id.valueKanal)
        valueBlock = findViewById(R.id.valueBlock)
        valueKlemme = findViewById(R.id.valueKlemme)
        valueBlatt = findViewById(R.id.valueBlatt)
        valueAktiv = findViewById(R.id.valueAktiv)
        valueBemerkung = findViewById(R.id.valueBemerkung)

        textErgebnis = findViewById(R.id.textErgebnis)
        statusText = findViewById(R.id.statusText)

        buttonSuchen = findViewById(R.id.buttonSuchen)
        buttonMenu = findViewById(R.id.buttonMenu)

        setupCsvPicker()
        setupMenu()
        setupSpinners()
        setupSearchButton()
    }

    // --- CSV-Auswahl über Dokumenten-Dialog ---

    private fun setupCsvPicker() {
        csvPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                importCsv(uri)
            }
        }
    }

    private fun openCsvPicker() {
        csvPickerLauncher.launch(arrayOf("*/*"))
    }

    // --- Burgermenü (CSV laden / Beenden) ---

    private fun setupMenu() {
        buttonMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("CSV laden")
            popup.menu.add("Beenden")

            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "CSV laden" -> {
                        openCsvPicker()
                        true
                    }
                    "Beenden" -> {
                        finish()
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
    }

    // --- Spinners: Etage -> Raum -> Verbraucher ---

    private fun setupSpinners() {
        loadEtagen()
    }

    private fun loadEtagen() {
        executor.execute {
            val etagen = dao.getEtagen()
            runOnUiThread {
                if (etagen.isEmpty()) {
                    spinnerEtage.adapter = null
                    spinnerRaum.adapter = null
                    spinnerVerbraucher.adapter = null
                    statusText.text = "Noch keine Daten. Bitte CSV laden."
                    showEmptyResult()
                    return@runOnUiThread
                }

                val adapter = ArrayAdapter(
                    this,
                    R.layout.spinner_item,
                    etagen
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerEtage.adapter = adapter

                spinnerEtage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        selectedEtage = parent.getItemAtPosition(position) as String
                        loadRaeume(selectedEtage!!)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                        selectedEtage = null
                    }
                }

                selectedEtage = etagen[0]
                loadRaeume(selectedEtage!!)
                statusText.text = "Daten aus Datenbank geladen."
            }
        }
    }

    private fun loadRaeume(etage: String) {
        executor.execute {
            val raeume = dao.getRaeume(etage)
            runOnUiThread {
                if (raeume.isEmpty()) {
                    spinnerRaum.adapter = null
                    spinnerVerbraucher.adapter = null
                    selectedRaum = null
                    selectedVerbraucher = null
                    showEmptyResult()
                    return@runOnUiThread
                }

                val adapter = ArrayAdapter(
                    this,
                    R.layout.spinner_item,
                    raeume
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerRaum.adapter = adapter

                spinnerRaum.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        selectedRaum = parent.getItemAtPosition(position) as String
                        loadVerbraucher(etage, selectedRaum!!)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                        selectedRaum = null
                    }
                }

                selectedRaum = raeume[0]
                loadVerbraucher(etage, selectedRaum!!)
            }
        }
    }

    private fun loadVerbraucher(etage: String, raum: String) {
        executor.execute {
            val verbraucherListe = dao.getVerbraucher(etage, raum)
            runOnUiThread {
                if (verbraucherListe.isEmpty()) {
                    spinnerVerbraucher.adapter = null
                    selectedVerbraucher = null
                    showEmptyResult()
                    return@runOnUiThread
                }

                val adapter = ArrayAdapter(
                    this,
                    R.layout.spinner_item,
                    verbraucherListe
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerVerbraucher.adapter = adapter

                spinnerVerbraucher.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            selectedVerbraucher =
                                parent.getItemAtPosition(position) as String
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                            selectedVerbraucher = null
                        }
                    }

                selectedVerbraucher = verbraucherListe[0]
            }
        }
    }

    // --- Suchen-Button ---

    private fun setupSearchButton() {
        buttonSuchen.setOnClickListener {
            val etage = selectedEtage
            val raum = selectedRaum
            val verbraucher = selectedVerbraucher

            if (etage.isNullOrBlank() || raum.isNullOrBlank() || verbraucher.isNullOrBlank()) {
                textErgebnis.text = "Bitte alle Felder auswählen."
                showEmptyResult()
                return@setOnClickListener
            }

            executor.execute {
                val eintrag = dao.findEintrag(etage, raum, verbraucher)
                runOnUiThread {
                    if (eintrag != null) {
                        valueRaumnr.text = eintrag.raumnr.ifBlank { "-" }
                        valuePhase.text = eintrag.phase.ifBlank { "-" }
                        valueFi.text = eintrag.fi.ifBlank { "-" }
                        valueSicherung.text = eintrag.ls.ifBlank { "-" }
                        valueAktor.text = eintrag.aktor.ifBlank { "-" }
                        valueKanal.text = eintrag.kanal.ifBlank { "-" }
                        valueBlock.text = eintrag.klemmblock.ifBlank { "-" }
                        valueKlemme.text = eintrag.klemme.ifBlank { "-" }
                        valueBlatt.text = eintrag.blatt.ifBlank { "-" }
                        valueAktiv.text = if (eintrag.aktiv) "Ja" else "Nein"
                        valueBemerkung.text = eintrag.bemerkungen.ifBlank { "-" }

                        textErgebnis.text = "Stromkreis gefunden."
                    } else {
                        textErgebnis.text = "Kein Eintrag gefunden."
                        showEmptyResult()
                    }
                }
            }
        }
    }

    private fun showEmptyResult() {
        valueRaumnr.text = "-"
        valuePhase.text = "-"
        valueFi.text = "-"
        valueSicherung.text = "-"
        valueAktor.text = "-"
        valueKanal.text = "-"
        valueBlock.text = "-"
        valueKlemme.text = "-"
        valueBlatt.text = "-"
        valueAktiv.text = "-"
        valueBemerkung.text = "-"
    }

    // --- CSV einlesen und in Datenbank schreiben ---

    private fun importCsv(uri: Uri) {
        executor.execute {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("CSV konnte nicht geöffnet werden.")
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                val lines = reader.readLines()
                if (lines.isEmpty()) return@execute

                val eintraege = mutableListOf<StromEintragEntity>()

                for ((index, line) in lines.withIndex()) {
                    if (index == 0) continue // Header
                    if (line.isBlank()) continue

                    val cols = line.split(";")
                    if (cols.size < 13) continue

                    fun col(i: Int): String =
                        cols.getOrNull(i)?.trim().orEmpty()

                    val aktivStr = col(12)
                    val aktiv = aktivStr == "1" || aktivStr.equals("true", ignoreCase = true)

                    val eintrag = StromEintragEntity(
                        etage = col(0),
                        raumnr = col(1),
                        raum = col(2),
                        phase = col(3),
                        verbraucher = col(4),
                        fi = col(5),
                        ls = col(6),
                        aktor = col(7),
                        kanal = col(8),
                        klemmblock = col(9),
                        klemme = col(10),
                        blatt = col(11),
                        aktiv = aktiv,
                        bemerkungen = col(13)
                    )

                    eintraege.add(eintrag)
                }

                dao.clearAll()
                if (eintraege.isNotEmpty()) {
                    dao.insertAll(eintraege)
                }

                runOnUiThread {
                    statusText.text = "CSV geladen: ${eintraege.size} Einträge."
                    textErgebnis.text = "Bitte Auswahl treffen und auf Suchen tippen."
                    loadEtagen()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusText.text = "Fehler beim CSV-Import: ${e.message}"
                    showEmptyResult()
                }
            }
        }
    }
}
