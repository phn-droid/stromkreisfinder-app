package com.example.stromplanfinder

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale

class StromkreisRepository(private val dao: StromEintragDao) {

    suspend fun importCsv(inputStream: InputStream): Int {
        val rows = parseCsv(inputStream)
        val entities = rows.mapNotNull { row -> row.toEntityOrNull() }
        dao.clearAll()
        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
        return entities.size
    }

    suspend fun getEtagen(): List<String> = dao.getEtagen().cleanSorted()

    suspend fun getRaeume(etage: String): List<String> = dao.getRaeume(etage).cleanSorted()

    suspend fun getVerbraucher(etage: String, raum: String): List<String> = dao.getVerbraucher(etage, raum).cleanSorted()

    suspend fun findEintrag(etage: String, raum: String, verbraucher: String): StromEintragEntity? {
        return dao.findEintrag(etage, raum, verbraucher)
    }

    suspend fun getAktoren(): List<String> = dao.getAktoren().cleanSorted()

    suspend fun getKanaeleFuerAktor(aktor: String): List<String> = dao.getKanaele(aktor).cleanSortedHuman()

    suspend fun findEintraegeByAktorKanal(aktor: String, kanal: String): List<StromEintragEntity> {
        return dao.findEintraegeByAktorKanal(aktor, kanal)
    }

    suspend fun getFis(): List<String> = dao.getFis().cleanSortedHuman()

    suspend fun getLsFuerFi(fi: String): List<String> = dao.getLsForFi(fi).cleanSortedHuman()

    suspend fun findRaeumeByFiLs(fi: String, ls: String): List<StromEintragEntity> {
        return dao.findEintraegeByFiLs(fi, ls)
    }

    suspend fun getLeitungsPraefixe(): List<String> {
        return dao.getLeitungsbezeichnungen()
            .mapNotNull { it.splitLeitung()?.first }
            .distinct()
            .cleanSortedHuman()
    }

    suspend fun getLeitungsSuffixe(prefix: String): List<String> {
        return dao.getLeitungsbezeichnungen()
            .mapNotNull { it.splitLeitung() }
            .filter { it.first.equals(prefix, ignoreCase = true) }
            .map { it.second }
            .distinct()
            .cleanSortedHuman()
    }

    suspend fun findEintraegeByLeitung(prefix: String, suffix: String): List<StromEintragEntity> {
        val fullLeitung = "$prefix.$suffix"
        return dao.findEintraegeByLeitungsbezeichnung(fullLeitung)
            .sortedWith(
                compareBy<StromEintragEntity> { it.etage }
                    .thenBy { it.raum }
                    .thenBy { it.raumnr }
                    .thenBy { it.verbraucher }
            )
    }

    private fun parseCsv(inputStream: InputStream): List<Map<String, String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val separator = detectSeparator(lines.first())
        val headers = splitCsvLine(lines.first(), separator).map { it.normalizeHeader() }

        return lines.drop(1).mapNotNull { line ->
            val values = splitCsvLine(line, separator)
            if (values.all { it.isBlank() }) return@mapNotNull null
            headers.mapIndexed { index, header ->
                header to values.getOrElse(index) { "" }.trim()
            }.toMap()
        }
    }

    private fun detectSeparator(headerLine: String): Char {
        val semicolons = headerLine.count { it == ';' }
        val commas = headerLine.count { it == ',' }
        return if (semicolons >= commas) ';' else ','
    }

    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                char == '"' -> inQuotes = !inQuotes
                char == separator && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun Map<String, String>.toEntityOrNull(): StromEintragEntity? {
        val etage = value("etage")
        val raum = value("raum", "raumname")
        val verbraucher = value("verbraucher")
        if (etage.isBlank() && raum.isBlank() && verbraucher.isBlank()) return null

        return StromEintragEntity(
            etage = etage,
            raumnr = value("raumnr", "raumnummer", "raum nr", "raum-nr"),
            raum = raum,
            phase = value("phase"),
            verbraucher = verbraucher,
            fi = value("fi"),
            ls = value("ls", "sicherung"),
            aktor = value("aktor"),
            kanal = value("kanal"),
            klemmblock = value("klemmblock", "block"),
            klemme = value("klemme"),
            leitungsbezeichnung = value("leitungsbezeichnung", "leitung"),
            kabelart = value("kabelart", "kabel"),
            aktiv = value("aktiv").toBooleanFlag(),
            bemerkungen = value("bemerkungen", "bemerkung"),
            blatt = value("blatt")
        )
    }

    private fun Map<String, String>.value(vararg keys: String): String {
        for (key in keys) {
            val normalized = key.normalizeHeader()
            val value = this[normalized]
            if (value != null) return value.trim()
        }
        return ""
    }

    private fun String.toBooleanFlag(): Boolean {
        return trim().lowercase(Locale.GERMANY) in setOf("1", "true", "ja", "j", "yes", "y", "aktiv", "x")
    }

    private fun String.normalizeHeader(): String {
        return trim()
            .lowercase(Locale.GERMANY)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(".", "")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
    }

    private fun String.splitLeitung(): Pair<String, String>? {
        val cleaned = trim()
        val dotIndex = cleaned.indexOf('.')
        if (dotIndex <= 0 || dotIndex >= cleaned.lastIndex) return null
        val prefix = cleaned.substring(0, dotIndex).trim()
        val suffix = cleaned.substring(dotIndex + 1).trim()
        if (prefix.isBlank() || suffix.isBlank()) return null
        return prefix to suffix
    }

    private fun List<String>.cleanSorted(): List<String> {
        return map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(java.lang.String.CASE_INSENSITIVE_ORDER)
    }

    private fun List<String>.cleanSortedHuman(): List<String> {
        return map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith { a, b -> compareHuman(a, b) }
    }

    private fun compareHuman(a: String, b: String): Int {
        val regex = Regex("^(\\D*)(\\d+)(.*)$")
        val ma = regex.matchEntire(a)
        val mb = regex.matchEntire(b)
        if (ma != null && mb != null) {
            val prefixCompare = ma.groupValues[1].compareTo(mb.groupValues[1], ignoreCase = true)
            if (prefixCompare != 0) return prefixCompare
            val numCompare = ma.groupValues[2].toInt().compareTo(mb.groupValues[2].toInt())
            if (numCompare != 0) return numCompare
            return ma.groupValues[3].compareTo(mb.groupValues[3], ignoreCase = true)
        }
        return a.compareTo(b, ignoreCase = true)
    }
}
