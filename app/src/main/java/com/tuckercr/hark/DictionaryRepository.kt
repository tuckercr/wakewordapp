package com.tuckercr.zamzam

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.cmu.pocketsphinx.Assets
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun loadList(): List<String> {
            val wordList: MutableList<String> = ArrayList()
            try {
                val assets = Assets(context)
                val assetsDir = assets.syncAssets()
                val open = context.assets.open(assetsDir.name + "/models/lm/words.dic")
                val reader = BufferedReader(InputStreamReader(open))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line == null) break
                    val word = line.substring(0, line.indexOf(' '))
                    if (line.indexOf('(') > 0) continue
                    wordList.add(word)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadList failed", e)
            }
            return wordList
        }

        companion object {
            private const val TAG = "DictionaryRepository"
        }
    }
