package np.com.sudanchapagain.localElectionReport.unifiedData

import np.com.sudanchapagain.localElectionReport.census.censusDataToSqlite
import np.com.sudanchapagain.localElectionReport.election.electionDataToSqlite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.max
import kotlin.math.min

@Serializable
data class LocationMapping(
    val district_id: Int?,
    val province_id: Int?,
    val district_en: String?,
    val district_np: String?,
    val palika_en: String?,
    val palika_np: String?
)

data class CensusRecord(
    val province: String,
    val district: String,
    val locality: String,
    val sex: String,
    val caste_ethnicity: String,
    val value: Double
)

data class ElectionRecord(
    val candidateName: String,
    val gender: String,
    val age: Int?,
    val partyName: String,
    val totalVotes: Int,
    val postName: String,
    val ward: Int?,
    val vdcmunName: String,
    val districtName: String,
    val stateName: String
)

data class UnifiedRecord(
    val provinceId: Int?,
    val districtId: Int?,
    val provinceName: String,
    val districtName: String,
    val localityName: String,
    val censusData: CensusRecord?,
    val electionData: List<ElectionRecord>
)

class LocationMatcher(mapFilePath: String) {
    private val mappings: List<LocationMapping>
    
    private val provinceMap = mapOf(
        "KOSHI" to "कोशी प्रदेश",
        "MADHESH" to "मधेश प्रदेश", 
        "BAGMATI" to "बागमती प्रदेश",
        "GANDAKI" to "गण्डकी प्रदेश",
        "LUMBINI" to "लुम्बिनी प्रदेश",
        "KARNALI" to "कर्णाली प्रदेश",
        "SUDURPASCHIM" to "सुदूरपश्चिम प्रदेश"
    )
    
    private val reverseProvinceMap = provinceMap.entries.associate { (k, v) -> v to k }
    
    init {
        val jsonContent = File(mapFilePath).readText()
        val json = Json { 
            coerceInputValues = true
            ignoreUnknownKeys = true
        }
        mappings = json.decodeFromString<List<LocationMapping>>(jsonContent)
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
    
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace("municipality", "")
            .replace("नगरपालिका", "")
            .replace("गाउँपालिका", "")
            .replace("rural municipality", "")
            .replace("उपमहानगरपालिका", "")
            .replace("sub-metropolitan", "")
            .replace("metropolitan", "")
            .replace("महानगरपालिका", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun fuzzyMatch(str1: String?, str2: String?, threshold: Double = 0.7): Boolean {
        if (str1 == null || str2 == null) return false
        val norm1 = normalize(str1)
        val norm2 = normalize(str2)
        
        if (norm1 == norm2) return true
        
        if (norm1.contains(norm2) || norm2.contains(norm1)) return true

        val maxLen = max(norm1.length, norm2.length)
        if (maxLen == 0) return true
        
        val distance = levenshteinDistance(norm1, norm2)
        val similarity = 1.0 - (distance.toDouble() / maxLen)
        
        return similarity >= threshold
    }
    
    fun matchProvinces(censusProvince: String, electionProvince: String): Boolean {

        if (provinceMap[censusProvince] == electionProvince) return true
        if (reverseProvinceMap[electionProvince] == censusProvince) return true
        
        return fuzzyMatch(censusProvince, electionProvince)
    }
    
    fun findLocationMapping(
        censusProvince: String, 
        censusDistrict: String, 
        censusLocality: String,
        electionState: String,
        electionDistrict: String,
        electionVdcmun: String
    ): LocationMapping? {
        
        if (!matchProvinces(censusProvince, electionState)) {
            return null
        }
        
        val candidateMappings = mappings.filter { mapping ->
            fuzzyMatch(mapping.district_en, censusDistrict) || 
            fuzzyMatch(mapping.district_np, electionDistrict)
        }
        
        return candidateMappings.find { mapping ->
            fuzzyMatch(mapping.palika_en, censusLocality) || 
            fuzzyMatch(mapping.palika_np, electionVdcmun) ||
            fuzzyMatch(mapping.palika_en, electionVdcmun) ||
            fuzzyMatch(mapping.palika_np, censusLocality)
        }
    }
    
    fun getAllMappingsForProvince(provinceName: String): List<LocationMapping> {
        val nepaliProvince = provinceMap[provinceName] 
        return if (nepaliProvince != null) {
            mappings.filter { mapping ->
                matchProvinces(provinceName, nepaliProvince)
            }
        } else {
            mappings.filter { mapping ->
                matchProvinces(provinceName, provinceName)
            }
        }
    }
}

fun generateUnifiedData(mapFilePath: String = "res/map.json", outputDb: String = "res/unified.db") {
    println("Starting unified data generation...")
    
    val matcher = LocationMatcher(mapFilePath)
    
    File(outputDb).delete()
    
    val unifiedConn = DriverManager.getConnection("jdbc:sqlite:$outputDb")
    
    unifiedConn.createStatement().execute("""
        CREATE TABLE unified_data (
            province_id INTEGER,
            district_id INTEGER,
            province_name TEXT,
            district_name TEXT,
            locality_name TEXT,
            
            -- Census data
            census_province TEXT,
            census_district TEXT,  
            census_locality TEXT,
            total_population REAL,
            male_population REAL,
            female_population REAL,
            hill_castes REAL,
            mountain_hill_janajatis REAL,
            tarai_janajatis REAL,
            hill_dalits REAL,
            others REAL,
            
            -- Election data summary
            total_candidates INTEGER,
            total_votes INTEGER,
            winning_candidate TEXT,
            winning_party TEXT,
            winning_votes INTEGER,
            
            -- Matching confidence
            match_confidence TEXT
        )
    """)
    
    val censusConn = DriverManager.getConnection("jdbc:sqlite:res/census.db")
    val censusResults = censusConn.createStatement().executeQuery("""
        SELECT province, district, locality, sex, caste_ethnicity, SUM(value) as total_value
        FROM census 
        GROUP BY province, district, locality, sex, caste_ethnicity
    """)
    
    val censusData = mutableMapOf<String, MutableMap<String, Double>>()
    
    while (censusResults.next()) {
        val key = "${censusResults.getString("province")}|${censusResults.getString("district")}|${censusResults.getString("locality")}"
        val subKey = "${censusResults.getString("sex")}|${censusResults.getString("caste_ethnicity")}"
        val value = censusResults.getDouble("total_value")
        
        censusData.getOrPut(key) { mutableMapOf() }[subKey] = value
    }
    censusConn.close()
    
    val electionConn = DriverManager.getConnection("jdbc:sqlite:res/election.db")
    val electionResults = electionConn.createStatement().executeQuery("""
        SELECT 
            CandidateName, Gender, Age, PoliticalPartyName, TotalVoteReceived,
            post_name, Ward, vdcmun_name, district_name, state_name
        FROM election
        WHERE TotalVoteReceived IS NOT NULL
    """)
    
    val electionData = mutableMapOf<String, MutableList<ElectionRecord>>()
    
    while (electionResults.next()) {
        val key = "${electionResults.getString("state_name")}|${electionResults.getString("district_name")}|${electionResults.getString("vdcmun_name")}"
        val record = ElectionRecord(
            candidateName = electionResults.getString("CandidateName") ?: "",
            gender = electionResults.getString("Gender") ?: "",
            age = electionResults.getInt("Age").takeIf { !electionResults.wasNull() },
            partyName = electionResults.getString("PoliticalPartyName") ?: "",
            totalVotes = electionResults.getInt("TotalVoteReceived"),
            postName = electionResults.getString("post_name") ?: "",
            ward = electionResults.getInt("Ward").takeIf { !electionResults.wasNull() },
            vdcmunName = electionResults.getString("vdcmun_name") ?: "",
            districtName = electionResults.getString("district_name") ?: "",
            stateName = electionResults.getString("state_name") ?: ""
        )
        
        electionData.getOrPut(key) { mutableListOf() }.add(record)
    }
    electionConn.close()
    
    println("Loaded ${censusData.size} census localities and ${electionData.size} election localities")
    
    var matchedCount = 0
    var totalProcessed = 0
    
    val insertStmt = unifiedConn.prepareStatement("""
        INSERT INTO unified_data (
            province_id, district_id, province_name, district_name, locality_name,
            census_province, census_district, census_locality,
            total_population, male_population, female_population,
            hill_castes, mountain_hill_janajatis, tarai_janajatis, hill_dalits, others,
            total_candidates, total_votes, winning_candidate, winning_party, winning_votes,
            match_confidence
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """)
    
    for ((censusKey, censusStats) in censusData) {
        totalProcessed++
        val (censusProvince, censusDistrict, censusLocality) = censusKey.split("|")
        
        val totalPop = censusStats["Both sexes|All caste"] ?: 0.0
        val malePop = censusStats["Male|All caste"] ?: 0.0
        val femalePop = censusStats["Female|All caste"] ?: 0.0
        val hillCastes = censusStats["Both sexes|Hill Castes"] ?: 0.0
        val mountainHillJanajatis = censusStats["Both sexes|Mountain/Hill Janajatis"] ?: 0.0
        val taraiJanajatis = censusStats["Both sexes|Tarai Janajatis"] ?: 0.0
        val hillDalits = censusStats["Both sexes|Hill Dalits"] ?: 0.0
        val others = censusStats["Both sexes|Others, Foreigners & Not stated"] ?: 0.0
        
        var bestMatch: LocationMapping? = null
        var matchingElectionData: List<ElectionRecord>? = null
        var confidence = "NO_MATCH"
        
        for ((electionKey, elections) in electionData) {
            val (electionState, electionDistrict, electionVdcmun) = electionKey.split("|")
            
            val mapping = matcher.findLocationMapping(
                censusProvince, censusDistrict, censusLocality,
                electionState, electionDistrict, electionVdcmun
            )
            
            if (mapping != null) {
                bestMatch = mapping
                matchingElectionData = elections
                confidence = "MAPPED"
                matchedCount++
                break
            }
        }
        
        val totalCandidates = matchingElectionData?.size ?: 0
        val totalVotes = matchingElectionData?.sumOf { it.totalVotes } ?: 0
        val winner = matchingElectionData?.maxByOrNull { it.totalVotes }
        
        insertStmt.setObject(1, bestMatch?.province_id)
        insertStmt.setObject(2, bestMatch?.district_id)
        insertStmt.setString(3, censusProvince)
        insertStmt.setString(4, censusDistrict)
        insertStmt.setString(5, censusLocality)
        
        insertStmt.setString(6, censusProvince)
        insertStmt.setString(7, censusDistrict)
        insertStmt.setString(8, censusLocality)
        
        insertStmt.setDouble(9, totalPop)
        insertStmt.setDouble(10, malePop)
        insertStmt.setDouble(11, femalePop)
        insertStmt.setDouble(12, hillCastes)
        insertStmt.setDouble(13, mountainHillJanajatis)
        insertStmt.setDouble(14, taraiJanajatis)
        insertStmt.setDouble(15, hillDalits)
        insertStmt.setDouble(16, others)
        
        insertStmt.setInt(17, totalCandidates)
        insertStmt.setInt(18, totalVotes)
        insertStmt.setString(19, winner?.candidateName)
        insertStmt.setString(20, winner?.partyName)
        insertStmt.setObject(21, winner?.totalVotes)
        insertStmt.setString(22, confidence)
        
        insertStmt.executeUpdate()
        
        if (totalProcessed % 100 == 0) {
            println("Processed $totalProcessed localities, matched $matchedCount")
        }
    }
    
    unifiedConn.close()
    
    val matchRate = (matchedCount.toDouble() / totalProcessed * 100).format(2)
    println("Unified data generation complete!")
    println("Total localities processed: $totalProcessed")
    println("Successfully matched: $matchedCount ($matchRate%)")
    println("Output saved to: $outputDb")
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun getData(electionFilePath: String, censusFilePath: String): String {
    val censusDb = File("res/census.db")
    val electionDb = File("res/election.db")
    
    if (!censusDb.exists()) {
        println("Generating census.db from $censusFilePath")
        censusDataToSqlite(censusFilePath)
    } else {
        println("census.db already exists, skipping conversion")
    }
    
    if (!electionDb.exists()) {
        println("Generating election.db from $electionFilePath")
        electionDataToSqlite(electionFilePath)
    } else {
        println("election.db already exists, skipping conversion")
    }
    
    println("Generating unified database...")
    generateUnifiedData()
    
    return "res/unified.db"
}
