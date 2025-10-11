package np.com.sudanchapagain.localElectionReport

import np.com.sudanchapagain.localElectionReport.unifiedData.getData
import java.io.File

fun main() {
    val electionFilePath = "res/election.xlsx"
    val censusFilePath = "res/census.xlsx"

    println("=====================================\n")

    try {
        val censusDbExists = File("res/census.db").exists()
        val electionDbExists = File("res/election.db").exists()
        
        if (!censusDbExists && !File(censusFilePath).exists()) {
            println("ERROR: Neither census.db nor census.xlsx found")
            return
        }

        if (!electionDbExists && !File(electionFilePath).exists()) {
            println("ERROR: Neither election.db nor election.xlsx found")
            return
        }

        val unifiedSourceDataPath = getData(electionFilePath, censusFilePath)
    } catch (e: Exception) {
        println("ERROR: Error occured during data integration: ${e.message}")
        e.printStackTrace()
    }
}
