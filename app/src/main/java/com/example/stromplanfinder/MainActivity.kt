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
        RAUMSUCHE,
        LEITUNGSSUCHE
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
    private lateinit var panelResultLeitung: View
    private lateinit var panelSearchStromkreis: View
    private lateinit var panelSearchVerbraucher: View
    private lateinit var panelSearchRaum: View
    private lateinit var panelSearchLeitung: View

    // UI – Stromkreissuche
    private lateinit var spinnerEtage: Spinner
    private lateinit var spinnerRaum: Spinner
    private lateinit var spinnerVerbraucher: Spinner

    private lateinit var valueFi: TextView
    private lateinit var valueSicherung: TextView
    private lateinit var valueAktor: TextView
    private lateinit var valueKanal: TextView
    private lateinit var valuePhase: TextView
    private lateinit var valueAktiv: TextView
    private lateinit var valueBlock: TextView
    private lateinit var valueKlemme: TextView
    private lateinit var valueLeitung: TextView
    private lateinit var valueKabelart: TextView
    private lateinit var valueRaumnr: TextView
    private lateinit var valueBlatt: TextView
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

    // UI – Leitungssuche
    private lateinit var spinnerLeitungPrefix: Spinner
    private lateinit var spinnerLeitungSuffix: Spinner
    private lateinit var buttonLeitungSuchen: MaterialButton
    private lateinit var textErgebnisLeitungssuche: TextView
    private lateinit var valueLeitungSucheErgebnis: TextView

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
        clearLeitungResult()
        switchView(activeView)

        lifecycleScope.launch {
            loadEtagen()
            loadAktoren()
            loadFis()
            loadLeitungsPrefixes()
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
        panelResultLeitung = findViewById(R.id.panelResultLeitung)
        panelSearchStromkreis = findViewById(R.id.panelSearchStromkreis)
        panelSearchVerbraucher = findViewById(R.id.panelSearchVerbraucher)
        panelSearchRaum = findViewById(R.id.panelSearchRaum)
        panelSearchLeitung = findViewById(R.id.panelSearchLeitung)

        spinnerEtage = findViewById(R.id.spinnerEtage)
        spinnerRaum = findViewById(R.id.spinnerRaum)
        spinnerVerbraucher = findViewById(R.id.spinnerVerbraucher)

        valueFi = findViewById(R.id.valueFi)
        valueSicherung = findViewById(R.id.valueSicherung)
        valueAktor = findViewById(R.id.valueAktor)
        valueKanal = findViewById(R.id.valueKanal)
        valuePhase = findViewById(R.id.valuePhase)
        valueAktiv = findViewById(R.id.valueAktiv)
        valueBlock = findViewById(R.id.valueBlock)
        valueKlemme = findViewById(R.id.valueKlemme)
        valueLeitung = findViewById(R.id.valueLeitung)
        valueKabelart = findViewById(R.id.valueKabelart)
        valueRaumnr = findViewById(R.id.valueRaumnr)
        valueBlatt = findViewById(R.id.valueBlatt)
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

        spinnerLeitungPrefix = findViewById(R.id.spinnerLeitungPrefix)
        spinnerLeitungSuffix = findViewById(R.id.spinnerLeitungSuffix)
        buttonLeitungSuchen = findViewById(R.id.buttonLeitungSuchen)
        textErgebnisLeitungssuche = findViewById(R.id.textErgebnisLeitungssuche)
        valueLeitungSucheErgebnis = findViewById(R.id.valueLeitungSucheErgebnis)

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

                    R.id.menu_leitungssuche -> {
                        switchView(ActiveView.LEITUNGSSUCHE)
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

        spinnerLeitungPrefix.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val prefix = parent?.getItemAtPosition(position) as? String ?: return
                lifecycleScope.launch {
                    loadLeitungsSuffixes(prefix)
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

        buttonLeitungSuchen.setOnClickListener {
            val prefix = spinnerLeitungPrefix.selectedItem as? String
            val suffix = spinnerLeitungSuffix.selectedItem as? String

            if (prefix.isNullOrBlank() || suffix == null) {
                Toast.makeText(
                    this,
                    "Bitte Leitungsbezeichnung auswählen.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                lifecycleScope.launch {
                    searchLeitung(prefix, suffix)
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

                panelResultLeitung.visibility = View.GONE
                panelSearchLeitung.visibility = View.GONE
            }

            ActiveView.VERBRAUCHERSUCHE -> {
                textCurrentView.text = "Verbrauchersuche"
                panelResultStromkreis.visibility = View.GONE
                panelSearchStromkreis.visibility = View.GONE

                panelResultVerbraucher.visibility = View.VISIBLE
                panelSearchVerbraucher.visibility = View.VISIBLE

                panelResultRaum.visibility = View.GONE
                panelSearchRaum.visibility = View.GONE

                panelResultLeitung.visibility = View.GONE
                panelSearchLeitung.visibility = View.GONE
            }

            ActiveView.RAUMSUCHE -> {
                textCurrentView.text = "Raumsuche"
                panelResultStromkreis.visibility = View.GONE
                panelSearchStromkreis.visibility = View.GONE

                panelResultVerbraucher.visibility = View.GONE
                panelSearchVerbraucher.visibility = View.GONE

                panelResultRaum.visibility = View.VISIBLE
                panelSearchRaum.visibility = View.VISIBLE

                panelResultLeitung.visibility = View.GONE
                panelSearchLeitung.visibility = View.GONE
            }

            ActiveView.LEITUNGSSUCHE -> {
                textCurrentView.text = "Leitungssuche"
                panelResultStromkreis.visibility = View.GONE
                panelSearchStromkreis.visibility = View.GONE

                panelResultVerbraucher.visibility = View.GONE
                panelSearchVerbraucher.visibility = View.GONE

                panelResultRaum.visibility = View.GONE
                panelSearchRaum.visibility = View.GONE

                panelResultLeitung.visibility = View.VISIBLE
                panelSearchLeitung.visibility = View.VISIBLE
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

                    if (!iter.hasNext()) {
                        return@useLines
                    }

                    val headerParts = parseCsvLine(iter.next())
                    val headerIndex = headerParts
                        .mapIndexed { index, header -> normalizeCsvHeader(header) to index }
                        .toMap()

                    fun read(parts: List<String>, vararg headers: String): String {
                        for (header in headers) {
                            val index = headerIndex[normalizeCsvHeader(header)]
                            if (index != null) {
                                return parts.getOrNull(index)?.trim().orEmpty()
                            }
                        }
                        return ""
                    }

                    while (iter.hasNext()) {
                        val line = iter.next()
                        if (line.isBlank()) continue

                        val parts = parseCsvLine(line)

                        val etage = read(parts, "Etage")
                        val raumnr = read(parts, "Raumnr.", "Raumnr", "Raumnummer")
                        val raum = read(parts, "Raum")
                        val phase = read(parts, "Phase")
                        val verbraucher = read(parts, "Verbraucher")
                        val fi = read(parts, "FI")
                        val ls = read(parts, "LS")
                        val aktor = read(parts, "Aktor")
                        val kanal = read(parts, "Kanal")
                        val block = read(parts, "Klemmblock", "Klemmblock (Xn)", "Block")
                        val klemme = read(parts, "Klemme")
                        val leitungsbezeichnung = read(parts, "Leitungsbezeichnung", "Leitung")
                        val kabelart = read(parts, "Kabelart")
                        val blatt = read(parts, "Blatt")
                        val aktivString = read(parts, "Aktiv")
                        val bemerkung = read(parts, "Bemerkungen", "Bemerkung")

                        if (etage.isBlank() && raum.isBlank() && verbraucher.isBlank()) {
                            continue
                        }

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
                                leitungsbezeichnung = leitungsbezeichnung,
                                kabelart = kabelart,
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
        clearLeitungResult()

        loadEtagen()
        loadAktoren()
        loadFis()
        loadLeitungsPrefixes()
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }

                char == '"' -> {
                    inQuotes = !inQuotes
                }

                char == ';' && !inQuotes -> {
                    values.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
            index++
        }

        values.add(current.toString())
        return values
    }

    private fun normalizeCsvHeader(value: String): String {
        return value
            .trim()
            .trimStart('\uFEFF')
            .lowercase()
            .replace(".", "")
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
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

    private suspend fun loadLeitungsPrefixes() {
        val leitungsbezeichnungen = withContext(Dispatchers.IO) { dao.getLeitungsbezeichnungen() }
        val prefixes = sortNatural(
            leitungsbezeichnungen
                .mapNotNull { splitLeitungsbezeichnung(it)?.first }
                .filter { it.isNotBlank() }
                .distinct()
        )

        setSpinnerItems(spinnerLeitungPrefix, prefixes)

        if (prefixes.isNotEmpty()) {
            loadLeitungsSuffixes(prefixes.first())
        } else {
            clearSpinner(spinnerLeitungSuffix)
        }
    }

    private suspend fun loadLeitungsSuffixes(prefix: String) {
        val leitungsbezeichnungen = withContext(Dispatchers.IO) { dao.getLeitungsbezeichnungen() }
        val suffixes = sortNatural(
            leitungsbezeichnungen
                .mapNotNull { splitLeitungsbezeichnung(it) }
                .filter { it.first == prefix }
                .map { it.second }
                .distinct()
        )

        setSpinnerItems(spinnerLeitungSuffix, suffixes)
    }

    private fun splitLeitungsbezeichnung(leitungsbezeichnung: String): Pair<String, String>? {
        val value = leitungsbezeichnung.trim()
        if (value.isBlank()) return null

        val dotIndex = value.indexOf('.')
        return if (dotIndex >= 0) {
            value.substring(0, dotIndex) to value.substring(dotIndex + 1)
        } else {
            value to ""
        }
    }

    private fun joinLeitungsbezeichnung(prefix: String, suffix: String): String {
        return if (suffix.isBlank()) prefix else "$prefix.$suffix"
    }

    private fun sortNatural(items: Iterable<String>): List<String> {
        return items.sortedWith(Comparator { first, second -> compareNaturalText(first, second) })
    }

    private fun compareNaturalText(first: String, second: String): Int {
        var firstIndex = 0
        var secondIndex = 0

        while (firstIndex < first.length && secondIndex < second.length) {
            val firstChar = first[firstIndex]
            val secondChar = second[secondIndex]

            if (firstChar.isDigit() && secondChar.isDigit()) {
                val firstNumberStart = firstIndex
                val secondNumberStart = secondIndex

                while (firstIndex < first.length && first[firstIndex].isDigit()) firstIndex++
                while (secondIndex < second.length && second[secondIndex].isDigit()) secondIndex++

                val firstNumberRaw = first.substring(firstNumberStart, firstIndex)
                val secondNumberRaw = second.substring(secondNumberStart, secondIndex)
                val firstNumber = firstNumberRaw.trimStart('0').ifEmpty { "0" }
                val secondNumber = secondNumberRaw.trimStart('0').ifEmpty { "0" }

                val lengthCompare = firstNumber.length.compareTo(secondNumber.length)
                if (lengthCompare != 0) return lengthCompare

                val valueCompare = firstNumber.compareTo(secondNumber)
                if (valueCompare != 0) return valueCompare

                val rawLengthCompare = firstNumberRaw.length.compareTo(secondNumberRaw.length)
                if (rawLengthCompare != 0) return rawLengthCompare
            } else {
                val charCompare = firstChar.lowercaseChar().compareTo(secondChar.lowercaseChar())
                if (charCompare != 0) return charCompare

                firstIndex++
                secondIndex++
            }
        }

        return first.length.compareTo(second.length)
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

    private suspend fun searchLeitung(prefix: String, suffix: String) {
        val leitungsbezeichnung = joinLeitungsbezeichnung(prefix, suffix)
        val eintraege = withContext(Dispatchers.IO) {
            dao.findEintraegeByLeitungsbezeichnung(leitungsbezeichnung)
        }

        if (eintraege.isEmpty()) {
            showNoLeitungFound()
        } else {
            showLeitungResult(eintraege)
        }
    }

    private fun String?.orDash(): String = if (this.isNullOrBlank()) "-" else this

    private fun formatRoomWithNumber(eintrag: StromEintragEntity): String {
        return buildString {
            append(eintrag.raum.orDash())
            if (!eintrag.raumnr.isNullOrBlank()) {
                append(" (")
                append(eintrag.raumnr)
                append(")")
            }
        }
    }

    private fun clearStromkreisResult() {
        valueFi.text = "-"
        valueSicherung.text = "-"
        valueAktor.text = "-"
        valueKanal.text = "-"
        valuePhase.text = "-"
        valueAktiv.text = "-"
        valueBlock.text = "-"
        valueKlemme.text = "-"
        valueLeitung.text = "-"
        valueKabelart.text = "-"
        valueRaumnr.text = "-"
        valueBlatt.text = "-"
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
        valueAktiv.text = "-"
        valueBlock.text = "-"
        valueKlemme.text = "-"
        valueLeitung.text = "-"
        valueKabelart.text = "-"
        valueRaumnr.text = "-"
        valueBlatt.text = "-"
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
        valueLeitung.text = e.leitungsbezeichnung.orDash()
        valueKabelart.text = e.kabelart.orDash()
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
                append(formatRoomWithNumber(eintrag))
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
                append(formatRoomWithNumber(eintrag))
            }
        }
    }

    private fun clearLeitungResult() {
        textErgebnisLeitungssuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueLeitungSucheErgebnis.text = "-"
    }

    private fun showNoLeitungFound() {
        textErgebnisLeitungssuche.text = "Keine Einträge gefunden."
        valueLeitungSucheErgebnis.text = "-"
    }

    private fun showLeitungResult(eintraege: List<StromEintragEntity>) {
        textErgebnisLeitungssuche.text = "${eintraege.size} Treffer gefunden."

        valueLeitungSucheErgebnis.text = eintraege.joinToString("\n") { eintrag ->
            buildString {
                append(eintrag.etage.orDash())
                append(" | ")
                append(formatRoomWithNumber(eintrag))
                append(" | ")
                append(eintrag.verbraucher.orDash())
                append(" | ")
                append(eintrag.phase.orDash())
                append(", ")
                append(eintrag.fi.orDash())
                append(", ")
                append(eintrag.ls.orDash())
                append(" | ")
                append(eintrag.aktor.orDash())
                append(", ")
                append(eintrag.kanal.orDash())
                append(" | ")
                append(eintrag.klemmblock.orDash())
                append(", ")
                append(eintrag.klemme.orDash())
                append(" | ")
                append(eintrag.kabelart.orDash())
            }
        }
    }
}
