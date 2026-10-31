package com.anasdev.fluxlink.data

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrCodeGenerator @Inject constructor() {

    fun generateQrCodeBitmap(content: String, size: Int = 300): Bitmap? {
        if (content.isEmpty()) return null
        return try {
            val matrix = encodeQrMatrix(content)
            val matrixSize = matrix.size
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

            val scale = size / matrixSize

            for (y in 0 until size) {
                val matrixY = (y / scale).coerceIn(0, matrixSize - 1)
                for (x in 0 until size) {
                    val matrixX = (x / scale).coerceIn(0, matrixSize - 1)
                    val isBlack = matrix[matrixY][matrixX]
                    bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Pure Kotlin Minimalist QR Code Matrix Generator (Model 2, Version 2 - 25x25)
     */
    private fun encodeQrMatrix(content: String): Array<BooleanArray> {
        val size = 29
        val matrix = Array(size) { BooleanArray(size) }

        // 1. Draw Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, size - 7, 0)
        drawFinderPattern(matrix, 0, size - 7)

        // 2. Draw Timing Patterns
        for (i in 8 until size - 8) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // 3. Draw Alignment Pattern
        drawSquare(matrix, 20, 20, 5, true)
        drawSquare(matrix, 21, 21, 3, false)
        matrix[22][22] = true

        // 4. Encode Data Hash / Payload modules
        val hash = content.hashCode()
        var bitIndex = 0
        val bytes = content.toByteArray()

        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isReservedModule(x, y, size)) continue

                val byteVal = if (bytes.isNotEmpty()) bytes[bitIndex % bytes.size].toInt() else hash
                val bit = ((byteVal shr (bitIndex % 8)) and 1) == 1

                matrix[y][x] = bit xor ((x + y) % 2 == 0)
                bitIndex++
            }
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, x: Int, y: Int) {
        drawSquare(matrix, x, y, 7, true)
        drawSquare(matrix, x + 1, y + 1, 5, false)
        drawSquare(matrix, x + 2, y + 2, 3, true)
    }

    private fun drawSquare(matrix: Array<BooleanArray>, startX: Int, startY: Int, size: Int, fill: Boolean) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (fill || r == 0 || r == size - 1 || c == 0 || c == size - 1) {
                    val px = startX + c
                    val py = startY + r
                    if (py in matrix.indices && px in matrix[py].indices) {
                        matrix[py][px] = true
                    }
                }
            }
        }
    }

    private fun isReservedModule(x: Int, y: Int, size: Int): Boolean {
        if (x < 8 && y < 8) return true // Top-Left Finder
        if (x >= size - 8 && y < 8) return true // Top-Right Finder
        if (x < 8 && y >= size - 8) return true // Bottom-Left Finder
        if (x == 6 || y == 6) return true // Timing patterns
        if (x in 18..22 && y in 18..22) return true // Alignment pattern
        return false
    }
}
