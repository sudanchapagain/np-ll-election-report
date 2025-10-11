package np.com.sudanchapagain.localElectionReport.election

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream
import java.sql.Connection
import java.sql.DriverManager

fun electionDataToSqlite(
    filePath: String
) {
    val sqlitePath: String = "res/election.db"
    val tableName: String = "election"
    val tableData = readElectionRows(filePath)
    val mapping = mapHeadersToSchema(tableData.headers)
    val url = "jdbc:sqlite:$sqlitePath"
    DriverManager.getConnection(url).use { conn ->
        conn.autoCommit = false
        ensureElectionTable(conn, tableName)
        clearElectionTable(conn, tableName)
        insertElectionRows(conn, tableName, mapping, tableData.data)
        conn.commit()
    }
}

private data class TableData(
    val headers: List<String>, val data: List<List<Any?>>
)

private fun readElectionRows(filePath: String): TableData {
    FileInputStream(filePath).use { fis ->
        XSSFWorkbook(fis).use { wb ->
            val sheet = wb.getSheetAt(0)

            var headerRow: Row? = null
            var headerRowIdx = sheet.firstRowNum
            for (r in sheet.firstRowNum..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val any = (row.firstCellNum.toInt()..row.lastCellNum - 1).any { c ->
                    val cell = row.getCell(c)
                    cell != null && cell.toString().isNotBlank()
                }
                if (any) {
                    headerRow = row
                    headerRowIdx = r
                    break
                }
            }
            requireNotNull(headerRow) { "No header row found in election.xlsx" }

            val header = headerRow

            fun String.normalizeHeader(): String {
                return this.trim().lowercase().replace("\n", " ").replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    .ifBlank { "col" }
            }

            val maxCols = header.lastCellNum.toInt()
            val headers = (0 until maxCols).map { idx ->
                val raw = header.getCell(idx)?.toString()?.trim().orEmpty()
                val norm = raw.normalizeHeader()
                norm.ifEmpty { $$"col_${idx + 1}" }
            }

            val data = mutableListOf<List<Any?>>()
            for (r in headerRowIdx + 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val values = (0 until maxCols).map { c ->
                    val cell = row.getCell(c) ?: return@map null
                    when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue.trim().ifBlank { null }
                        CellType.NUMERIC -> cell.numericCellValue
                        CellType.BOOLEAN -> cell.booleanCellValue
                        CellType.FORMULA -> when (cell.cachedFormulaResultType) {
                            CellType.STRING -> cell.stringCellValue.trim().ifBlank { null }
                            CellType.NUMERIC -> cell.numericCellValue
                            CellType.BOOLEAN -> cell.booleanCellValue
                            else -> cell.toString().trim().ifBlank { null }
                        }

                        else -> cell.toString().trim().ifBlank { null }
                    }
                }
                val allNull = values.all { it == null }
                if (!allNull) data += values
            }

            return TableData(headers, data)
        }
    }
}

private fun ensureElectionTable(conn: Connection, table: String) {
    val sql = """
        CREATE TABLE IF NOT EXISTS $table (
            CandidateID INTEGER,
            CandidateName TEXT,
            CandidateNameEng TEXT,
            Gender TEXT,
            Age INTEGER,
            PartyID INTEGER,
            SymbolID INTEGER,
            SymbolName TEXT,
            SymbolNameEng TEXT,
            PoliticalPartyName TEXT,
            PoliticalPartyNameEng TEXT,
            TotalVoteReceived INTEGER,
            Remarks TEXT,
            RemarksEng TEXT,
            post_name TEXT,
            PostId INTEGER,
            Ward INTEGER,
            vdcmun_name TEXT,
            vdc_mun INTEGER,
            district_name TEXT,
            district INTEGER,
            state_name TEXT,
            state INTEGER
        );
    """.trimIndent()
    conn.createStatement().use { it.execute(sql) }
}

private fun clearElectionTable(conn: Connection, table: String) {
    conn.createStatement().use { it.execute("DELETE FROM $table") }
}

private fun mapHeadersToSchema(headers: List<String>): Map<String, Int?> {
    fun norm(s: String) = s.trim().lowercase().replace("\n", " ").replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val idxByNorm = headers.mapIndexed { idx, h -> norm(h) to idx }.toMap()
    fun find(vararg candidates: String): Int? {
        for (c in candidates) {
            val n = norm(c)
            if (idxByNorm.containsKey(n)) return idxByNorm[n]
        }
        return null
    }
    return mapOf(
        "CandidateID" to find("CandidateID", "candidate_id", "id"),
        "CandidateName" to find("CandidateName", "candidate_name"),
        "CandidateNameEng" to find("CandidateNameEng", "candidate_name_eng", "candidate_name_english"),
        "Gender" to find("Gender", "sex"),
        "Age" to find("Age"),
        "PartyID" to find("PartyID", "party_id"),
        "SymbolID" to find("SymbolID", "symbol_id"),
        "SymbolName" to find("SymbolName", "symbol_name"),
        "SymbolNameEng" to find("SymbolNameEng", "symbol_name_eng"),
        "PoliticalPartyName" to find("PoliticalPartyName", "party_name"),
        "PoliticalPartyNameEng" to find("PoliticalPartyNameEng", "party_name_eng"),
        "TotalVoteReceived" to find("TotalVoteReceived", "total_vote_received", "votes"),
        "Remarks" to find("Remarks"),
        "RemarksEng" to find("RemarksEng", "remarks_eng"),
        "post_name" to find("post_name", "post"),
        "PostId" to find("PostId", "post_id"),
        "Ward" to find("Ward", "ward_no", "ward_number"),
        "vdcmun_name" to find("vdcmun_name", "vdc_mun_name", "vdc_municipality_name"),
        "vdc_mun" to find("vdc_mun", "vdcmun", "vdc_mun_id"),
        "district_name" to find("district_name"),
        "district" to find("district", "district_id"),
        "state_name" to find("state_name", "province_name"),
        "state" to find("state", "province_id")
    )
}

private val schemaOrder = listOf(
    "CandidateID",
    "CandidateName",
    "CandidateNameEng",
    "Gender",
    "Age",
    "PartyID",
    "SymbolID",
    "SymbolName",
    "SymbolNameEng",
    "PoliticalPartyName",
    "PoliticalPartyNameEng",
    "TotalVoteReceived",
    "Remarks",
    "RemarksEng",
    "post_name",
    "PostId",
    "Ward",
    "vdcmun_name",
    "vdc_mun",
    "district_name",
    "district",
    "state_name",
    "state"
)

private fun insertElectionRows(conn: Connection, table: String, mapping: Map<String, Int?>, rows: List<List<Any?>>) {
    val placeholders = schemaOrder.joinToString(", ") { "?" }
    val sql = "INSERT INTO $table (${schemaOrder.joinToString(", ")}) VALUES ($placeholders)"
    conn.prepareStatement(sql).use { ps ->
        for (r in rows) {
            var param = 1
            for (col in schemaOrder) {
                val idx = mapping[col]
                val v: Any? = if (idx == null) null else r.getOrNull(idx)
                when (col) {
                    "CandidateID", "Age", "PartyID", "SymbolID", "TotalVoteReceived", "PostId", "Ward", "vdc_mun", "district", "state" -> {
                        when (v) {
                            null -> ps.setObject(param, null)
                            is Double -> ps.setLong(param, v.toLong())
                            is Number -> ps.setLong(param, v.toLong())
                            is String -> ps.setObject(param, v.toLongOrNull())
                            else -> ps.setObject(param, null)
                        }
                    }

                    else -> {
                        ps.setString(param, v?.toString())
                    }
                }
                param++
            }
            ps.addBatch()
        }
        ps.executeBatch()
    }
}
