package np.com.sudanchapagain.localElectionReport.census

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream
import java.sql.Connection
import java.sql.DriverManager

data class CensusRow(
    val province: String,
    val district: String,
    val locality: String,
    val sex: String,
    val casteEthnicity: String,
    val value: Double
)

fun censusWrangle(filePath: String): List<CensusRow> {
    FileInputStream(filePath).use { fis ->
        val wb = XSSFWorkbook(fis)
        val sheet = wb.getSheetAt(0)

        fun Row.str(colIdx: Int): String? {
            val cell = this.getCell(colIdx) ?: return null
            val s = when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                    val d = cell.numericCellValue
                    val asLong = d.toLong()
                    if (d == asLong.toDouble()) asLong.toString() else d.toString()
                }

                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                    when (cell.cachedFormulaResultType) {
                        org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                        org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                            val d = cell.numericCellValue
                            val asLong = d.toLong()
                            if (d == asLong.toDouble()) asLong.toString() else d.toString()
                        }

                        org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> cell.toString()
                    }
                }

                else -> cell.toString()
            }?.trim()
            return if (s.isNullOrEmpty()) null else s
        }

        fun Row.num(colIdx: Int): Double? {
            val cell = this.getCell(colIdx) ?: return null
            return when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.toDoubleOrNull()
                else -> null
            }
        }

        val columnPROVINCE = 1
        val columnDISTRICT = 2
        val columnLOCAL = 3
        val columnSEX = 4
        val columnCASTE = 5
        val columnVALUE = 6

        val startRow = 35
        val lastRow = sheet.lastRowNum

        data class Raw(
            val rowNum: Int,
            val province: String?,
            val district: String?,
            val locality: String?,
            val sex: String?,
            val caste: String?,
            val value: Double?
        )

        val raws = mutableListOf<Raw>()
        for (r in startRow..lastRow) {
            val row = sheet.getRow(r) ?: continue
            val province = row.str(columnPROVINCE)
            val district = row.str(columnDISTRICT)
            val locality = row.str(columnLOCAL)
            val sex = row.str(columnSEX)
            val caste = row.str(columnCASTE)
            val value = row.num(columnVALUE)

            val isAllNull =
                province == null && district == null && locality == null && sex == null && caste == null && value == null
            if (isAllNull) continue

            raws += Raw(r, province, district, locality, sex, caste, value)
        }

        var currentProvince: String? = null
        val provFilled = raws.map { raw ->
            if (raw.province != null) currentProvince = raw.province
            raw.copy(province = currentProvince)
        }

        var currentDistrict: String? = null
        var lastProvinceForDistrict: String? = null
        val distFilled = provFilled.map { raw ->
            if (raw.province != lastProvinceForDistrict) {
                currentDistrict = null
                lastProvinceForDistrict = raw.province
            }
            if (raw.district != null) currentDistrict = raw.district
            raw.copy(district = currentDistrict)
        }

        var currentLocal: String? = null
        var lastProvinceForLocal: String? = null
        var lastDistrictForLocal: String? = null
        val localFilled = distFilled.map { raw ->
            if (raw.province != lastProvinceForLocal || raw.district != lastDistrictForLocal) {
                currentLocal = null
                lastProvinceForLocal = raw.province
                lastDistrictForLocal = raw.district
            }
            if (raw.locality != null) currentLocal = raw.locality
            raw.copy(locality = currentLocal)
        }

        val withGeo = localFilled.filter { it.province != null && it.district != null && it.locality != null }

        var currentSex: String? = null
        var lastLocalKey: String? = null
        val sexFilled = withGeo.map { raw ->
            val key = "${raw.province}|${raw.district}|${raw.locality}"
            if (key != lastLocalKey) {
                currentSex = null
                lastLocalKey = key
            }
            if (raw.sex != null) currentSex = raw.sex
            raw.copy(sex = currentSex)
        }

        val finalized = sexFilled.filter { it.sex != null && it.caste != null && it.value != null }
            .filter { it.locality?.trim()?.equals("INSTITUTIONAL", ignoreCase = true) != true }.map {
                CensusRow(
                    province = it.province!!,
                    district = it.district!!,
                    locality = it.locality!!,
                    sex = it.sex!!,
                    casteEthnicity = it.caste!!,
                    value = it.value!!
                )
            }

        wb.close()
        return finalized
    }
}


fun censusDataToSqlite(censusXlsxPath: String) {
    val sqlitePath: String = "res/census.db"
    val tableName = "census"
    val rows = censusWrangle(censusXlsxPath)
    val url = "jdbc:sqlite:$sqlitePath"
    DriverManager.getConnection(url).use { conn ->
        conn.autoCommit = false
        ensureTable(conn, tableName)
        clearTable(conn, tableName)
        insertRows(conn, tableName, rows)
        conn.commit()
    }
}

private fun ensureTable(conn: Connection, table: String) {
    val sql = """
        CREATE TABLE IF NOT EXISTS $table (
            province TEXT NOT NULL,
            district TEXT NOT NULL,
            locality TEXT NOT NULL,
            sex TEXT NOT NULL,
            caste_ethnicity TEXT NOT NULL,
            value REAL NOT NULL
        );
    """.trimIndent()
    conn.createStatement().use { it.execute(sql) }
}

private fun clearTable(conn: Connection, table: String) {
    conn.createStatement().use { it.execute("DELETE FROM $table") }
}

private fun insertRows(conn: Connection, table: String, rows: List<CensusRow>) {
    val sql = "INSERT INTO $table (province, district, locality, sex, caste_ethnicity, value) VALUES (?, ?, ?, ?, ?, ?)"
    conn.prepareStatement(sql).use { ps ->
        for (r in rows) {
            ps.setString(1, r.province)
            ps.setString(2, r.district)
            ps.setString(3, r.locality)
            ps.setString(4, r.sex)
            ps.setString(5, r.casteEthnicity)
            ps.setDouble(6, r.value)
            ps.addBatch()
        }
        ps.executeBatch()
    }
}
