package kh.com.custom.camera.capture.camera.model

import android.util.Log

class MrzHelper {

    fun process(textByBlock: String): Mrz? {
        val mrz = checkBlockCompatibility(textByBlock)
        if (mrz != null) {
            return Mrz(mrz)
        }
        return null
    }

    private fun checkBlockCompatibility(cipherText: String): String? {
        val block = stripWhiteSpace(cipherText)
        var mrzKey = ""

        // filter valid MRZ types
        if (block.startsWith("I") || block.startsWith("P")) {
            when (block.length) {
                // TD1
                90 -> {
                    val mrz = fixOCRInconsistenciesTD1(block)
                    return if (validateTD1Block(mrz)) mrz else null
                }
                // TD3
                88 -> {
                    val mrz = fixOCRInconsistenciesTD3(block)
                    return if (validateTD3Block(mrz)) mrz else null
                }
                // handle mrz fixer
                in 72..100 -> {
                    mrzKey = stripWhiteSpace(block)
                    if (mrzKey.length != 90 || mrzKey.length != 88 || mrzKey.length != 72) return null
                    return when (mrzKey.length) {
                        90 -> {
                            mrzKey = fixOCRInconsistenciesTD1(mrzKey)
                            if (validateTD1Block(mrzKey)) mrzKey else null
                        }

                        88 -> {
                            mrzKey = fixOCRInconsistenciesTD3(mrzKey)
                            if (validateTD3Block(mrzKey)) mrzKey else null
                        }

                        else -> null
                    }
                }
            }
        }

        return mrzKey
    }

    private fun containsDigit(string: String?): Boolean {
        var containsDigit = false
        if (!string.isNullOrEmpty()) {
            for (c in string.toCharArray()) {
                if (Character.isDigit(c).also { containsDigit = it }) break
            }
        }
        Log.w(TAG, "Checked If Digit in $string with result $containsDigit")
        return containsDigit
    }

    private fun stripWhiteSpace(mrzKey: String): String {
        var fixedString = mrzKey.replace("\n", "")
        fixedString = fixedString.replace(" ", "")
        return fixedString
    }

    private fun fixOCRInconsistenciesTD1(mrzKey: String): String {
        val type = mrzKey.substring(0, 5)
        val cutString = mrzKey.substring(5, 14).replace("O", "0")
        var fixedString = type + cutString + mrzKey.substring(14)
        fixedString = fixedString.replace(("([<])([a-z])([<])").toRegex(), "<<<")
        fixedString = fixedString.replace(("(F)(R)([O])(0)").toRegex(), "FR00")
        return fixedString
    }

    private fun fixOCRInconsistenciesTD3(mrzKey: String): String {
        val cutString = mrzKey.substring(44, 53).replace("O", "0")
        var fixedString = cutString + mrzKey.substring(53)
        fixedString = fixedString.replace(("([<])([a-z])([<])").toRegex(), "<<<")
        return fixedString
    }

    private fun validateTD1Block(mrzKey: String): Boolean {
        if (mrzKey.length != 90) return false
        if (!(mrzKey.startsWith("I") || mrzKey.startsWith("A") || mrzKey.startsWith("C"))) return false
        if (!containsDigit(mrzKey[59].toString())) return false
        return !containsDigit(mrzKey[60].toString())
    }

    private fun validateTD3Block(mrzKey: String): Boolean {
        if (mrzKey.length != 88) return false
        if (!mrzKey.startsWith("P")) return false
        if (!containsDigit(mrzKey[53].toString())) return false
        if (containsDigit(mrzKey[63].toString())) return false
        if (containsDigit(mrzKey[71].toString())) return false
        if (containsDigit(mrzKey[86].toString())) return false
        return !containsDigit(mrzKey[87].toString())
    }

    companion object {
        private const val TAG = "TEXT123"
    }
}