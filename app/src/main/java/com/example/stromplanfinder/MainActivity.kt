package com.example.stromplanfinder

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private enum class ViewMode {
        DESKTOP,
        STROMKREISE_MENU,
        STROMKREISSUCHE,
        VERBRAUCHERSUCHE,
        RAUMSUCHE,
        LEITUNGSSUCHE
    }

    private val placeholder = "Bitte wählen"

    private lateinit var repository: StromkreisRepository

    private lateinit var textCurrentView: TextView
    private lateinit var buttonMenu: View
    private lateinit var buttonBack: View
    private lateinit var desktopScroll: ScrollView
    private lateinit var desktopRootContent: View
    private lateinit var desktopStromkreiseContent: View
    private lateinit var contentScroll: ScrollView
    private lateinit var resultContainer: View
    private lateinit var statusText: TextView

    private lateinit var panelSearchStromkreis: LinearLayout
    private lateinit var panelSearchVerbraucher: LinearLayout
    private lateinit var panelSearchRaum: LinearLayout
    private lateinit var panelSearchLeitung: LinearLayout

    private lateinit var panelResultStromkreis: View
    private lateinit var panelResultVerbraucher: View
    private lateinit var panelResultRaum: View
    private lateinit var panelResultLeitung: View

    private lateinit var spinnerEtage: Spinner
    private lateinit var spinnerRaum: Spinner
    private lateinit var spinnerVerbraucher: Spinner

    private lateinit var spinnerAktor: Spinner
    private lateinit var spinnerKanalVerbrauchersuche: Spinner

    private lateinit var spinnerFiRaumsuche: Spinner
    private lateinit var spinnerLsRaumsuche: Spinner

    private lateinit var spinnerLeitungPrefix: Spinner
    private lateinit var spinnerLeitungSuffix: Spinner

    private lateinit var textErgebnis: TextView
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

    private lateinit var textErgebnisVerbrauchersuche: TextView
    private lateinit var valueVerbraucherSucheErgebnis: TextView

    private lateinit var textErgebnisRaumsuche: TextView
    private lateinit var valueRaumSucheErgebnis: TextView

    private lateinit var textErgebnisLeitungssuche: TextView
    private lateinit var valueLeitungSucheErgebnis: LinearLayout

    private var activeMode: ViewMode = ViewMode.DESKTOP

    private val csvPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                lifecycleScope.launch {
                    val imported = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            repository.importCsv(inputStream)
                        } ?: 0
                    }
                    Toast.makeText(this@MainActivity, "$imported Datensätze importiert", Toast.LENGTH_LONG).show()
                    reloadCurrentViewData()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedNightMode = getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedNightMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = StromplanDatenbank.getInstance(applicationContext)
        repository = StromkreisRepository(db.stromEintragDao())

        bindViews()
        setupBackNavigation()
        setupMenu()
        setupDesktopCards()
        setupSearchButtons()

        switchView(ViewMode.DESKTOP)
    }

    private fun bindViews() {
        textCurrentView = findViewById(R.id.textCurrentView)
        buttonBack = findViewById(R.id.buttonBack)
        buttonMenu = findViewById(R.id.buttonMenu)
        desktopScroll = findViewById(R.id.desktopScroll)
        desktopRootContent = findViewById(R.id.desktopRootContent)
        desktopStromkreiseContent = findViewById(R.id.desktopStromkreiseContent)
        contentScroll = findViewById(R.id.contentScroll)
        resultContainer = findViewById(R.id.resultContainer)
        statusText = findViewById(R.id.statusText)

        panelSearchStromkreis = findViewById(R.id.panelSearchStromkreis)
        panelSearchVerbraucher = findViewById(R.id.panelSearchVerbraucher)
        panelSearchRaum = findViewById(R.id.panelSearchRaum)
        panelSearchLeitung = findViewById(R.id.panelSearchLeitung)

        panelResultStromkreis = findViewById(R.id.panelResultStromkreis)
        panelResultVerbraucher = findViewById(R.id.panelResultVerbrauchersuche)
        panelResultRaum = findViewById(R.id.panelResultRaumsuche)
        panelResultLeitung = findViewById(R.id.panelResultLeitungssuche)

        spinnerEtage = findViewById(R.id.spinnerEtage)
        spinnerRaum = findViewById(R.id.spinnerRaum)
        spinnerVerbraucher = findViewById(R.id.spinnerVerbraucher)

        spinnerAktor = findViewById(R.id.spinnerAktor)
        spinnerKanalVerbrauchersuche = findViewById(R.id.spinnerKanalVerbrauchersuche)

        spinnerFiRaumsuche = findViewById(R.id.spinnerFiRaumsuche)
        spinnerLsRaumsuche = findViewById(R.id.spinnerLsRaumsuche)

        spinnerLeitungPrefix = findViewById(R.id.spinnerLeitungPrefix)
        spinnerLeitungSuffix = findViewById(R.id.spinnerLeitungSuffix)

        textErgebnis = findViewById(R.id.textErgebnis)
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

        textErgebnisVerbrauchersuche = findViewById(R.id.textErgebnisVerbrauchersuche)
        valueVerbraucherSucheErgebnis = findViewById(R.id.valueVerbraucherSucheErgebnis)

        textErgebnisRaumsuche = findViewById(R.id.textErgebnisRaumsuche)
        valueRaumSucheErgebnis = findViewById(R.id.valueRaumSucheErgebnis)

        textErgebnisLeitungssuche = findViewById(R.id.textErgebnisLeitungssuche)
        valueLeitungSucheErgebnis = findViewById(R.id.valueLeitungSucheErgebnis)
    }

    private fun goBackOneLevel(): Boolean {
        return when (activeMode) {
            ViewMode.DESKTOP -> false
            ViewMode.STROMKREISE_MENU -> {
                switchView(ViewMode.DESKTOP)
                true
            }
            ViewMode.STROMKREISSUCHE,
            ViewMode.VERBRAUCHERSUCHE,
            ViewMode.RAUMSUCHE,
            ViewMode.LEITUNGSSUCHE -> {
                switchView(ViewMode.STROMKREISE_MENU)
                true
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!goBackOneLevel()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupMenu() {
        buttonBack.setOnClickListener {
            goBackOneLevel()
        }

        buttonMenu.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menuInflater.inflate(R.menu.main_menu, menu)
                menu.findItem(R.id.menu_darkmode)?.isChecked = isNightModeActive()
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_desktop -> switchView(ViewMode.DESKTOP)
                        R.id.menu_stromkreise -> switchView(ViewMode.STROMKREISE_MENU)
                        R.id.menu_csv_import -> openCsvPicker()
                        R.id.menu_darkmode -> toggleDarkMode()
                        R.id.menu_info -> showInfoDialog()
                    }
                    true
                }
                show()
            }
        }
    }

    private fun setupDesktopCards() {
        findViewById<View>(R.id.cardStromkreise).setOnClickListener {
            switchView(ViewMode.STROMKREISE_MENU)
        }
        findViewById<View>(R.id.cardStromkreissuche).setOnClickListener {
            switchView(ViewMode.STROMKREISSUCHE)
        }
        findViewById<View>(R.id.cardVerbrauchersuche).setOnClickListener {
            switchView(ViewMode.VERBRAUCHERSUCHE)
        }
        findViewById<View>(R.id.cardRaumsuche).setOnClickListener {
            switchView(ViewMode.RAUMSUCHE)
        }
        findViewById<View>(R.id.cardLeitungssuche).setOnClickListener {
            switchView(ViewMode.LEITUNGSSUCHE)
        }
        findViewById<View>(R.id.cardStromkreiseBack).setOnClickListener {
            switchView(ViewMode.DESKTOP)
        }
        findViewById<View>(R.id.cardDarkmode).setOnClickListener {
            toggleDarkMode()
        }

        listOf(
            R.id.cardZaehlerkasten,
            R.id.cardBalkonkraftwerk,
            R.id.cardNetzwerk,
            R.id.cardKnx
        ).forEach { cardId ->
            findViewById<View>(cardId).setOnClickListener {
                Toast.makeText(this, "Diese Fachanwendung ist noch nicht aktiviert.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearchButtons() {
        findViewById<View>(R.id.buttonSuchen).setOnClickListener { searchStromkreis() }
        findViewById<View>(R.id.buttonVerbraucherSuchen).setOnClickListener { searchVerbraucher() }
        findViewById<View>(R.id.buttonRaumSuchen).setOnClickListener { searchRaum() }
        findViewById<View>(R.id.buttonLeitungSuchen).setOnClickListener { searchLeitung() }
    }

    private fun switchView(mode: ViewMode) {
        activeMode = mode
        resetResults()

        val isDesktopMode = mode == ViewMode.DESKTOP || mode == ViewMode.STROMKREISE_MENU
        desktopScroll.visibility = if (isDesktopMode) View.VISIBLE else View.GONE
        desktopRootContent.visibility = if (mode == ViewMode.DESKTOP) View.VISIBLE else View.GONE
        desktopStromkreiseContent.visibility = if (mode == ViewMode.STROMKREISE_MENU) View.VISIBLE else View.GONE
        contentScroll.visibility = if (isDesktopMode) View.GONE else View.VISIBLE
        resultContainer.visibility = if (isDesktopMode) View.GONE else View.VISIBLE

        panelSearchStromkreis.visibility = if (mode == ViewMode.STROMKREISSUCHE) View.VISIBLE else View.GONE
        panelSearchVerbraucher.visibility = if (mode == ViewMode.VERBRAUCHERSUCHE) View.VISIBLE else View.GONE
        panelSearchRaum.visibility = if (mode == ViewMode.RAUMSUCHE) View.VISIBLE else View.GONE
        panelSearchLeitung.visibility = if (mode == ViewMode.LEITUNGSSUCHE) View.VISIBLE else View.GONE

        panelResultStromkreis.visibility = if (mode == ViewMode.STROMKREISSUCHE) View.VISIBLE else View.GONE
        panelResultVerbraucher.visibility = if (mode == ViewMode.VERBRAUCHERSUCHE) View.VISIBLE else View.GONE
        panelResultRaum.visibility = if (mode == ViewMode.RAUMSUCHE) View.VISIBLE else View.GONE
        panelResultLeitung.visibility = if (mode == ViewMode.LEITUNGSSUCHE) View.VISIBLE else View.GONE

        textCurrentView.text = when (mode) {
            ViewMode.DESKTOP -> "Fachanwendungen"
            ViewMode.STROMKREISE_MENU -> "Stromkreise"
            ViewMode.STROMKREISSUCHE -> "Stromkreissuche"
            ViewMode.VERBRAUCHERSUCHE -> "Verbrauchersuche"
            ViewMode.RAUMSUCHE -> "Raumsuche"
            ViewMode.LEITUNGSSUCHE -> "Leitungssuche"
        }

        buttonBack.visibility = if (mode == ViewMode.DESKTOP) View.INVISIBLE else View.VISIBLE

        reloadCurrentViewData()
    }

    private fun toggleDarkMode() {
        val newMode = if (isNightModeActive()) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putInt("night_mode", newMode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun isNightModeActive(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(attribute, typedValue, true)) {
            if (typedValue.resourceId != 0) getColor(typedValue.resourceId) else typedValue.data
        } else {
            fallback
        }
    }

    private fun reloadCurrentViewData() {
        when (activeMode) {
            ViewMode.DESKTOP,
            ViewMode.STROMKREISE_MENU -> Unit
            ViewMode.STROMKREISSUCHE -> loadStromkreisFilters()
            ViewMode.VERBRAUCHERSUCHE -> loadVerbraucherFilters()
            ViewMode.RAUMSUCHE -> loadRaumFilters()
            ViewMode.LEITUNGSSUCHE -> loadLeitungFilters()
        }
    }

    private fun loadStromkreisFilters() {
        lifecycleScope.launch {
            val etagen = withContext(Dispatchers.IO) { repository.getEtagen() }
            setSpinnerItems(spinnerEtage, etagen)
            setSpinnerItems(spinnerRaum, emptyList())
            setSpinnerItems(spinnerVerbraucher, emptyList())

            spinnerEtage.onItemSelectedListener = simpleSelectedListener {
                val etage = selectedValue(spinnerEtage)
                lifecycleScope.launch {
                    val raeume = withContext(Dispatchers.IO) { repository.getRaeume(etage) }
                    setSpinnerItems(spinnerRaum, raeume)
                    setSpinnerItems(spinnerVerbraucher, emptyList())
                }
            }

            spinnerRaum.onItemSelectedListener = simpleSelectedListener {
                val etage = selectedValue(spinnerEtage)
                val raum = selectedValue(spinnerRaum)
                lifecycleScope.launch {
                    val verbraucher = withContext(Dispatchers.IO) { repository.getVerbraucher(etage, raum) }
                    setSpinnerItems(spinnerVerbraucher, verbraucher)
                }
            }
        }
    }

    private fun loadVerbraucherFilters() {
        lifecycleScope.launch {
            val aktoren = withContext(Dispatchers.IO) { repository.getAktoren() }
            setSpinnerItems(spinnerAktor, aktoren)
            setSpinnerItems(spinnerKanalVerbrauchersuche, emptyList())

            spinnerAktor.onItemSelectedListener = simpleSelectedListener {
                val aktor = selectedValue(spinnerAktor)
                lifecycleScope.launch {
                    val kanaele = withContext(Dispatchers.IO) { repository.getKanaeleFuerAktor(aktor) }
                    setSpinnerItems(spinnerKanalVerbrauchersuche, kanaele)
                }
            }
        }
    }

    private fun loadRaumFilters() {
        lifecycleScope.launch {
            val fis = withContext(Dispatchers.IO) { repository.getFis() }
            setSpinnerItems(spinnerFiRaumsuche, fis)
            setSpinnerItems(spinnerLsRaumsuche, emptyList())

            spinnerFiRaumsuche.onItemSelectedListener = simpleSelectedListener {
                val fi = selectedValue(spinnerFiRaumsuche)
                lifecycleScope.launch {
                    val ls = withContext(Dispatchers.IO) { repository.getLsFuerFi(fi) }
                    setSpinnerItems(spinnerLsRaumsuche, ls)
                }
            }
        }
    }

    private fun loadLeitungFilters() {
        lifecycleScope.launch {
            val prefixes = withContext(Dispatchers.IO) { repository.getLeitungsPraefixe() }
            setSpinnerItems(spinnerLeitungPrefix, prefixes)
            setSpinnerItems(spinnerLeitungSuffix, emptyList())

            spinnerLeitungPrefix.onItemSelectedListener = simpleSelectedListener {
                val prefix = selectedValue(spinnerLeitungPrefix)
                lifecycleScope.launch {
                    val suffixes = withContext(Dispatchers.IO) { repository.getLeitungsSuffixe(prefix) }
                    setSpinnerItems(spinnerLeitungSuffix, suffixes)
                }
            }
        }
    }

    private fun searchStromkreis() {
        val etage = selectedValue(spinnerEtage)
        val raum = selectedValue(spinnerRaum)
        val verbraucher = selectedValue(spinnerVerbraucher)

        if (etage.isBlank() || raum.isBlank() || verbraucher.isBlank()) {
            statusText.text = "Bitte Etage, Raum und Verbraucher auswählen."
            return
        }

        lifecycleScope.launch {
            val eintrag = withContext(Dispatchers.IO) { repository.findEintrag(etage, raum, verbraucher) }
            if (eintrag == null) {
                textErgebnis.text = "Kein Treffer gefunden."
                clearStromkreisValues()
            } else {
                showStromkreisResult(eintrag)
            }
        }
    }

    private fun searchVerbraucher() {
        val aktor = selectedValue(spinnerAktor)
        val kanal = selectedValue(spinnerKanalVerbrauchersuche)

        if (aktor.isBlank() || kanal.isBlank()) {
            statusText.text = "Bitte Aktor und Kanal auswählen."
            return
        }

        lifecycleScope.launch {
            val treffer = withContext(Dispatchers.IO) { repository.findEintraegeByAktorKanal(aktor, kanal) }
            textErgebnisVerbrauchersuche.text = if (treffer.isEmpty()) "Kein Treffer gefunden." else "${treffer.size} Treffer gefunden:"
            valueVerbraucherSucheErgebnis.text = treffer.joinToString(separator = "\n\n") { eintrag ->
                "${eintrag.etage} | ${eintrag.raum} (${eintrag.raumnr}) | ${eintrag.verbraucher}"
            }.ifBlank { "-" }
        }
    }

    private fun searchRaum() {
        val fi = selectedValue(spinnerFiRaumsuche)
        val ls = selectedValue(spinnerLsRaumsuche)

        if (fi.isBlank() || ls.isBlank()) {
            statusText.text = "Bitte FI und LS auswählen."
            return
        }

        lifecycleScope.launch {
            val treffer = withContext(Dispatchers.IO) { repository.findRaeumeByFiLs(fi, ls) }
            val eindeutigeRaeume = treffer
                .map { Triple(it.etage, it.raum, it.raumnr) }
                .distinct()
                .sortedWith(compareBy<Triple<String, String, String>> { it.first }.thenBy { it.second }.thenBy { it.third })

            textErgebnisRaumsuche.text = if (eindeutigeRaeume.isEmpty()) "Kein Treffer gefunden." else "${eindeutigeRaeume.size} Räume gefunden:"
            valueRaumSucheErgebnis.text = eindeutigeRaeume.joinToString(separator = "\n") { (etage, raum, raumnr) ->
                "$etage | $raum ($raumnr)"
            }.ifBlank { "-" }
        }
    }

    private fun searchLeitung() {
        val prefix = selectedValue(spinnerLeitungPrefix)
        val suffix = selectedValue(spinnerLeitungSuffix)

        if (prefix.isBlank() || suffix.isBlank()) {
            statusText.text = "Bitte beide Teile der Leitungsbezeichnung auswählen."
            return
        }

        lifecycleScope.launch {
            val treffer = withContext(Dispatchers.IO) { repository.findEintraegeByLeitung(prefix, suffix) }
            textErgebnisLeitungssuche.text = if (treffer.isEmpty()) "Kein Treffer gefunden." else "${treffer.size} Treffer gefunden:"
            valueLeitungSucheErgebnis.removeAllViews()
            if (treffer.isEmpty()) {
                valueLeitungSucheErgebnis.addView(resultLine("-"))
            } else {
                treffer.forEachIndexed { index, eintrag ->
                    valueLeitungSucheErgebnis.addView(resultLine(formatLeitungsTreffer(eintrag)))
                    if (index < treffer.lastIndex) {
                        valueLeitungSucheErgebnis.addView(separatorLine())
                    }
                }
            }
        }
    }

    private fun showStromkreisResult(eintrag: StromEintragEntity) {
        textErgebnis.text = "Treffer gefunden: ${eintrag.etage} | ${eintrag.raum} | ${eintrag.verbraucher}"
        valueFi.text = dash(eintrag.fi)
        valueSicherung.text = dash(eintrag.ls)
        valueAktor.text = dash(eintrag.aktor)
        valueKanal.text = dash(eintrag.kanal)
        valuePhase.text = dash(eintrag.phase)
        valueAktiv.text = if (eintrag.aktiv) "Ja" else "Nein"
        valueBlock.text = dash(eintrag.klemmblock)
        valueKlemme.text = dash(eintrag.klemme)
        valueLeitung.text = dash(eintrag.leitungsbezeichnung)
        valueKabelart.text = dash(eintrag.kabelart)
        valueRaumnr.text = dash(eintrag.raumnr)
        valueBlatt.text = dash(eintrag.blatt)
        valueBemerkung.text = dash(eintrag.bemerkungen)
        statusText.text = ""
    }

    private fun clearStromkreisValues() {
        listOf(
            valueFi,
            valueSicherung,
            valueAktor,
            valueKanal,
            valuePhase,
            valueAktiv,
            valueBlock,
            valueKlemme,
            valueLeitung,
            valueKabelart,
            valueRaumnr,
            valueBlatt,
            valueBemerkung
        ).forEach { it.text = "-" }
    }

    private fun resetResults() {
        statusText.text = ""
        textErgebnis.text = "Bitte Auswahl treffen und auf Suchen tippen."
        clearStromkreisValues()
        textErgebnisVerbrauchersuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueVerbraucherSucheErgebnis.text = "-"
        textErgebnisRaumsuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueRaumSucheErgebnis.text = "-"
        textErgebnisLeitungssuche.text = "Bitte Auswahl treffen und auf Suchen tippen."
        valueLeitungSucheErgebnis.removeAllViews()
        valueLeitungSucheErgebnis.addView(resultLine("-"))
    }

    private fun setSpinnerItems(spinner: Spinner, values: List<String>) {
        val items = listOf(placeholder) + values.filter { it.isNotBlank() }.distinct()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(0)
    }

    private fun simpleSelectedListener(onSelected: () -> Unit): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) onSelected()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun selectedValue(spinner: Spinner): String {
        return spinner.selectedItem?.toString()?.takeUnless { it == placeholder } ?: ""
    }

    private fun dash(value: String?): String {
        return value?.takeIf { it.isNotBlank() } ?: "-"
    }

    private fun resultLine(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(themeColor(android.R.attr.textColorPrimary, Color.BLACK))
            setPadding(0, 8, 0, 8)
        }
    }

    private fun separatorLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density).toInt().coerceAtLeast(1)
            ).apply {
                topMargin = 4
                bottomMargin = 4
            }
            setBackgroundColor(themeColor(android.R.attr.colorAccent, Color.parseColor("#B39DDB")))
        }
    }

    private fun formatLeitungsTreffer(eintrag: StromEintragEntity): String {
        return listOf(
            eintrag.etage,
            "${eintrag.raum} (${eintrag.raumnr})",
            eintrag.verbraucher,
            joinNonBlank(eintrag.phase, eintrag.fi, eintrag.ls),
            joinNonBlank(eintrag.aktor, eintrag.kanal),
            joinNonBlank(eintrag.klemmblock, eintrag.klemme),
            eintrag.kabelart
        ).joinToString(" | ") { dash(it) }
    }

    private fun joinNonBlank(vararg values: String?): String {
        return values.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun openCsvPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        csvPicker.launch(intent)
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("StromkreisFinder")
            .setMessage("Fachanwendungen-Desktop mit Stromkreissuche, Verbrauchersuche, Raumsuche und Leitungssuche. Weitere Module sind vorbereitet, aber noch deaktiviert.")
            .setPositiveButton("OK", null)
            .show()
    }
}
