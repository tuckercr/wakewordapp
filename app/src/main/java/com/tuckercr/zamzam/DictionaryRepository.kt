package com.tuckercr.zamzam

import android.app.Application
import android.util.Log
import edu.cmu.pocketsphinx.Assets
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

internal object DictionaryRepository {
    private const val TAG = "DictionaryRepository"

    // Map<String, List<String>>
    fun loadList(application: Application): List<String> {
        Log.d(TAG, "loadList() called")
        val wordList: MutableList<String> = ArrayList()
        try {
            val assets = Assets(application)
            val assetsDir = assets.syncAssets()
            val open = application.assets.open(assetsDir.name + "/models/lm/words.dic")
            val reader = BufferedReader(InputStreamReader(open))

            // do reading, usually loop until end of file reading
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line == null) {
                    break
                }
                val word = line!!.substring(0, line!!.indexOf(' '))
                if (line!!.indexOf('(') > 0) {
                    // Log.i(TAG, "loadList: Ignoring secondary pronunciation: $line");
                    continue
                }

                wordList.add(word)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Caught: " + e.message, e)
        }

        // Log.i(TAG, "loadList: returning ${wordList.size} words")
        return wordList
    }

    fun loadMap(application: Application): Map<String, MutableList<String>> {
        Log.d(TAG, "loadMap() called with: application = [$application]")
        val wordMapList: MutableMap<String, MutableList<String>> = HashMap()
        try {
            val assets = Assets(application)
            val assetsDir = assets.syncAssets()
            val open = application.assets.open(assetsDir.name + "/models/lm/words.dic")
            val reader = BufferedReader(InputStreamReader(open))

            // do reading, usually loop until end of file reading
            var line: String
            while (reader.readLine().also { line = it } != null) {
                //process line
                val firstWord = line.substring(0, line.indexOf(' '))
                val indexOfParenthesis = line.indexOf('(')
                if (indexOfParenthesis > 0) {
                    Log.i(TAG, "Adding secondary pronunciation: $line")
                    val firstWordKey = line.substring(0, indexOfParenthesis)
                    val strings = wordMapList[firstWordKey]
                    if (strings != null) {
                        strings.add(firstWord)
                        wordMapList[firstWordKey] = strings
                    }
                    continue
                }
                val strings: MutableList<String> = ArrayList()
                strings.add(firstWord)
                wordMapList[firstWord] = strings
            }
        } catch (e: IOException) {
            Log.e(TAG, "Caught: " + e.message, e)
        }
        return wordMapList
    }
}