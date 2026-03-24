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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private enum class ActiveView {
        STROMKREISSUCHE,
        VERBRAUCHERSUCHE,
        RAUMSUCHE
    }

    companion object {
        private const val KEY_ACTIVE_VIEW = "active_view"
    }

    // Room
    private lateinit var db: StromplanDatenbank
    private lateinit var dao: StromEintragDao

    // Allgemeine UI
    private lateinit var buttonMenu: MaterialButton
    private lateinit var textCurrentView: TextView
    private lateinit var statusText: TextView

    private lateinit var panelResultStromkreis: View
    private lateinit var panelResultVerbraucher: View
    private lateinit var panelResultRaum: View
    private lateinit var panelSearchStromkreis: View
    private lateinit var panelSearchVerbraucher: View
    private lateinit var panelSearchRaum: View

    // UI – Stromkreissuche
    private lateinit var spinnerEtage: Spinner
    private lateinit var spinnerRaum: Spinner
    private lateinit var spinnerVerbraucher: Spinner

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

    // UI – Verbrauchersuche
    private lateinit var spinnerAktor: Spinner
    private lateinit var spinnerKanalVerbrauchersuche: Spinner
    private lateinit var buttonVerbraucherSuchen: MaterialButton
    private lateinit var textErgebnisVerbrauchersuche: TextView
    private lateinit var valueVerbraucherSucheErgebnis: TextView

    // UI – Raumsuche
    private lateinit var spinnerFiRaumsuche: Spinner
    private lateinit var spinnerLsRaumsuche: Spinner
    private lateinit var buttonRaumSuchen: MaterialButton
    private lateinit var textErgebnisRaumsuche: TextView
    private lateinit var valueRaumSucheErgebnis: TextView

    // Standardfarbe für "Aktiv"-Text merken
    private lateinit var aktivDefaultTextColors: ColorStateList

    private var activeView: ActiveView = ActiveView.STROMKREISSUCHE

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
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeOn) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activeView = savedInstanceState
            ?.getString(KEY_ACTIVE_VIEW)
            ?.let { saved -> runCatching { ActiveView.valueOf(saved) }.getOrNull() }
            ?: ActiveView.STROMKREISSUCHE

        db = StromplanDatenbank.getInstance(applicationContext)
        dao = db.stromEintragDao()

        bindViews()
        setupMenu()
        setupSpinnerCallbacks()
        setupButtons()

        clearStromkreisResult()
        clearVerbraucherResult()
        clearRaumResult()
        switchView(activeView)

        lifecycleScope.launch {
            loadEtagen()
            loadAktoren()
            loadFis()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE_VIEW, activeView.name)
        super.onSaveInstanceState(outState)
    }

    private fun bindViews() {
        buttonMenu = findViewById(R.id.buttonMenu)
        textCurrentView = findViewById(R.id.textCurrentView)
        statusText = findViewById(R.id.statusText)

        panelResultStromkreis = findViewById(R.id.panelResultStromkreis)
        panelResultVerbraucher = findViewById(R.id.panelResultVerbraucher)
        panelResultRaum = findViewById(R.id.panelResultRaum)
        panelSearchStromkreis = findViewById(R.id.panelSearchStromkreis)
        panelSearchVerbraucher = findViewById(R.id.panelSearchVerbraucher)
        panelSearchRaum = findViewById(R.id.panelSearchRaum)

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

        spinnerAktor = findViewById(R.id.spinnerAktor)
        spinnerKanalVerbrauchersuche = findViewById(R.id.spinnerKanalVerbrauchersuche)
        buttonVerbraucherSuchen = findViewById(R.id.buttonVerbraucherSuchen)
        textErgebnisVerbrauchersuche = findViewById(R.id.textErgebnisVerbrauchersuche)
        valueVerbraucherSucheErgebnis = findViewById(R.id.valueVerbraucherSucheErgebnis)

        spinnerFiRaumsuche = findViewById(R.id.spinnerFiRaumsuche)
        spinnerLsRaumsuche = findViewById(R.id.spinnerLsRaumsuche)
        buttonRaumSuchen = findViewById(R.id.buttonRaumSuchen)
        textErgebnisRaumsuche = findViewById(R.id.textErgebnisRaumsuche)
        valueRaumSucheErgebnis = findViewById(R.id.valueRaumSucheErgebnis)

        aktivDefaultTextColors = valueAktiv.textColors
    }

    private fun setupMenu() {
        buttonMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_stromkreissuche -> {
                        switchView(ActiveView.STROMKREISSUCHE)
                        true
                    }

                    R.id.menu_verbrauchersuche -> {
                        switchView(ActiveView.VERBRAUCHERSUCHE)
                        true
                    }

                    R.id.menu_raumsuche -> {
                        switchView(ActiveView.RAUMSUCHE)
                        true
                    }

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
    }

    private fun setupSpinnerCallbacks() {
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

        spinnerAktor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val aktor = parent?.getItemAtPosition(position) as? String ?: return
                lifecycleScope.launch {
                    loadKanaele(aktor)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerFiRaumsuche.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val fi = parent?.getItemAtPosition(position) as? String ?: return
                lifecycleScope.launch {
                    loadLsForFi(fi)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupButtons() {
        val buttonSuchen: MaterialButton = findViewById(R.id.buttonSuchen)

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

        buttonVerbraucherSuchen.setOnClickListener {
            val aktor = spinnerAktor.selectedItem as? String
            val kanal = spinnerKanalVerbrauchersuche.selectedItem as? String

            if (aktor.isNullOrBlank() || kanal.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "Bitte Aktor und Kanal auswählen.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                lifecycleScope.launch {
                    searchVerbraucher(aktor, kanal)
                }
            }
        }

        buttonRaumSuchen.setOnClickListener {
            val fi = spinnerFiRaumsuche.selectedItem as? String
            val ls = spinnerLsRaumsuche.selectedItem as? String

            if (fi.isNullOrBlank() || ls.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "Bitte FI und LS auswählen.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                lifecycleScope.launch {
                    searchRaeume(fi, ls)
                }
            }
        }
    }

    private fun switchView(view: ActiveView) {
        activeView = view

        when (view) {
            ActiveView.STROMKREISSUCHE -> {
                textCurrentView.text = "Stromkreissuche"
                panelResultStromkreis.visibility = View.VISIBLE
                panelSearchStromkreis.visibility = View.VISIBLE

                panelResultVerbraucher.visibility = View.GONE
                panelSearchVerbraucher.visibility = View.GONE

                panelResultRaum.visibility = View.GONE
                panelSearchRaum.visibility = View.GONE
            }

            ActiveView.VERBRAUCHERSUCHE -> {
                textCurrentView.text = "Verbrauchersuche"
                panelResultStromkreis.visibility = View.GONE
                panelSearchStromkreis.visibility = View.GONE

                panelResultVerbraucher.visibility = View.VISIBLE
                panelSearchVerbraucher.visibility = View.VISIBLE

                panelResultRaum.visibility = View.GONE
                panelSearchRaum.visibility = View.GONE
            }

            ActiveView.RAUMSUCHE -> {
                textCurrentView.text = "Raumsuche"
                panelResultStromkreis.visibility = View.GONE
                panelSearchStromkreis.visibility = View.GONE

                panelResultVerbraucher.visibility = View.GONE
                panelSearchVerbraucher.visibility = View.GONE

                panelResultRaum.visibility = View.VISIBLE
                panelSearchRaum.visibility = View.VISIBLE
            }
        }
    }

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

                    if (iter.hasNext()) {
                        iter.next()
                    }

                    while (iter.hasNext()) {
                        val line = iter.next()
                        if (line.isBlank()) continue

                        val parts = line.split(';')
                        if (parts.size < 13) continue

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

                        val aktiv = aktivString.equals("ja", ignoreCase = true) ||
                                aktivString == "1" ||
                                aktivString.equals("true", ignoreCase = true)

                        list.add(
                            StromEintragEntity(
                                etage = etage,
                                raumnr = raumnr,
                                raum = raum,
                                phase = phase,
                                verbraucher = verbraucher,
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

        clearStromkreisResult()
        clearVerbraucherResult()
        clearRaumResult()

        loadEtagen()
        loadAktoren()
        loadFis()
    }

    private suspend fun loadEtagen() {
        val etagen = withContext(Dispatchers.IO) { dao.getEtagen() }
        setSpinnerItems(spinnerEtage, etagen)

        if (etagen.isNotEmpty()) {
            loadRaeume(etagen.first())
        } else {
            clearSpinner(spinnerRaum)
            clearSpinner(spinnerVerbraucher)
        }
    }

    private suspend fun loadRaeume(etage: String) {
        val raeume = withContext(Dispatchers.IO) { dao.getRaeume(etage) }
        setSpinnerItems(spinnerRaum, raeume)

        if (raeume.isNotEmpty()) {
            loadVerbraucher(etage, raeume.first())
        } else {
            clearSpinner(spinnerVerbraucher)
        }
    }

    private suspend fun loadVerbraucher(etage: String, raum: String) {
        val verbraucherList = withContext(Dispatchers.IO) {
            dao.getVerbraucher(etage, raum)
        }
        setSpinnerItems(spinnerVerbraucher, verbraucherList)
    }

    private suspend fun loadAktoren() {
        val aktoren = withContext(Dispatchers.IO) { dao.getAktoren() }
        setSpinnerItems(spinnerAktor, aktoren)

        if (aktoren.isNotEmpty()) {
            loadKanaele(aktoren.first())
        } else {
            clearSpinner(spinnerKanalVerbrauchersuche)
        }
    }

    private suspend fun loadKanaele(aktor: String) {
        val kanaele = withContext(Dispatchers.IO) { dao.getKanaele(aktor) }
        setSpinnerItems(spinnerKanalVerbrauchersuche, kanaele)
    }

    private suspend fun loadFis() {
        val fis = withContext(Dispatchers.IO) { dao.getFis() }
        setSpinnerItems(spinnerFiRaumsuche, fis)

        if (fis.isNotEmpty()) {
            loadLsForFi(fis.first())
        } else {
            clearSpinner(spinnerLsRaumsuche)
        }
    }

    private suspend fun loadLsForFi(fi: String) {
        val lsList = withContext(Dispatchers.IO) { dao.getLsForFi(fi) }
        setSpinnerItems(spinnerLsRaumsuche, lsList)
    }

    private fun setSpinnerItems(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun clearSpinner(spinner: Spinner) {
        setSpinnerItems(spinner, emptyList())
    }

    private suspend fun searchStromkreis(etage: String, raum: String, verbraucher: String) {
        val eintrag = withContext(Dispatchers.IO) {
            dao.findEintrag(etage, raum, verbraucher)
        }

        if (eintrag == null) {
            showNoStromkreisFound()
        } else {
            showStromkreisResult(eintrag)
        }
    }

    private suspend fun searchVerbraucher(aktor: String, kanal: String) {
        val eintraege = withContext(Dispatchers.IO) {
            dao.findEintraegeByAktorKanal(aktor, kanal)
        }

        if (eintraege.isEmpty()) {
            showNoVerbraucherFound()
        } else {
            showVerbraucherResult(eintraege)
        }
    }

    private suspend fun searchRaeume(fi: String, ls: String) {
        val eintraege = withContext(Dispatchers.IO) {
            dao.findEintraegeByFiLs(fi, ls)
        }

        if (eintraege.isEmpty()) {
            showNoRaeumeFound()
        } else {
            showRaumResult(eintraege)
        }
    }

    private fun String?.orDash(): String = if (this.isNullOrBlank()) "-" else this

    private fun clearStromkreisResult() {
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
        valueAktiv.setTextColor(aktivDefaultTextColors)
        textErgebnis.text = "Bitte Auswahl treffen und auf Suchen tippen."
    }

    private fun showNoStromkreisFound() {
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
        valueAktiv.setTextColor(aktivDefaultTextColors)
        textErgebnis.text = "Kein Stromkreis gefunden."
    }

    private fun showStromkreisResult(e: StromEintragEntity) {
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

        if (e.aktiv) {
            valueAktiv.text = "Ja"
            valueAktiv.setTextColor(aktivDefaultTextColors)
        } else {
            valueAktiv.text = "Nein"
            valueAktiv.setTextColor(Color.RED)
        }

        textErgebnis.text = "Stromkreis gefunden."
    }

    private fun clearVerbraucherResult() {
        textErgebnisVerbrauchersuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueVerbraucherSucheErgebnis.text = "-"
    }

    private fun showNoVerbraucherFound() {
        textErgebnisVerbrauchersuche.text = "Keine Verbraucher gefunden."
        valueVerbraucherSucheErgebnis.text = "-"
    }

    private fun showVerbraucherResult(eintraege: List<StromEintragEntity>) {
        textErgebnisVerbrauchersuche.text = "${eintraege.size} Treffer gefunden."

        valueVerbraucherSucheErgebnis.text = eintraege.joinToString("\n") { eintrag ->
            buildString {
                append(eintrag.etage.orDash())
                append(" | ")
                append(eintrag.raum.orDash())

                if (!eintrag.raumnr.isNullOrBlank()) {
                    append(" (")
                    append(eintrag.raumnr)
                    append(")")
                }

                append(" | ")
                append(eintrag.verbraucher.orDash())
            }
        }
    }

    private fun clearRaumResult() {
        textErgebnisRaumsuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueRaumSucheErgebnis.text = "-"
    }

    private fun showNoRaeumeFound() {
        textErgebnisRaumsuche.text = "Keine Räume gefunden."
        valueRaumSucheErgebnis.text = "-"
    }

    private fun showRaumResult(eintraege: List<StromEintragEntity>) {
        val eindeutigeRaeume = eintraege
            .distinctBy { "${it.etage}|${it.raumnr}|${it.raum}" }

        textErgebnisRaumsuche.text = "${eindeutigeRaeume.size} Räume gefunden."

        valueRaumSucheErgebnis.text = eindeutigeRaeume.joinToString("\n") { eintrag ->
            buildString {
                append(eintrag.etage.orDash())
                append(" | ")
                append(eintrag.raum.orDash())

                if (!eintrag.raumnr.isNullOrBlank()) {
                    append(" (")
                    append(eintrag.raumnr)
                    append(")")
                }
            }
        }
    }
}