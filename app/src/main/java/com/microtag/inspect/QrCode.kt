package com.microtag.inspect

/**
 * A small QR code encoder: byte mode, error correction level M, versions 1 to 12 (enough for a UPI link).
 * Pure Kotlin, no dependencies. [modules] is a square grid, true = dark module.
 */
class QrCode private constructor(val size: Int, private val grid: Array<BooleanArray>, val version: Int, val mask: Int) {

    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && grid[y][x]

    companion object {
        private const val MAX_VERSION = 12
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22)
        private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8)

        /** Returns null when the text does not fit in version 12. */
        fun encode(text: String): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            var version = 1
            while (true) {
                val capBits = numDataCodewords(version) * 8
                val countBits = if (version < 10) 8 else 16
                if (4 + countBits + data.size * 8 <= capBits) break
                version++
                if (version > MAX_VERSION) return null
            }
            val bits = ArrayList<Int>()
            fun put(value: Int, len: Int) { for (i in len - 1 downTo 0) bits.add((value ushr i) and 1) }
            put(0b0100, 4)
            put(data.size, if (version < 10) 8 else 16)
            for (b in data) put(b.toInt() and 0xFF, 8)
            val capBits = numDataCodewords(version) * 8
            put(0, minOf(4, capBits - bits.size))
            while (bits.size % 8 != 0) bits.add(0)
            var pad = 0xEC
            while (bits.size < capBits) { put(pad, 8); pad = pad xor (0xEC xor 0x11) }
            val dataCw = ByteArray(bits.size / 8)
            for (i in bits.indices) dataCw[i / 8] = (dataCw[i / 8].toInt() or (bits[i] shl (7 - i % 8))).toByte()

            val all = addEccAndInterleave(dataCw, version)
            return build(version, all)
        }

        private fun rawModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        private fun numDataCodewords(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]

        private fun addEccAndInterleave(data: ByteArray, ver: Int): ByteArray {
            val nb = NUM_BLOCKS[ver]
            val eccLen = ECC_PER_BLOCK[ver]
            val raw = rawModules(ver) / 8
            val numShort = nb - raw % nb
            val shortLen = raw / nb
            val blocks = ArrayList<ByteArray>()
            val rs = rsDivisor(eccLen)
            var k = 0
            for (i in 0 until nb) {
                val dl = shortLen - eccLen + (if (i < numShort) 0 else 1)
                val dat = data.copyOfRange(k, k + dl)
                k += dl
                val ecc = rsRemainder(dat, rs)
                val block = ByteArray(shortLen + 1)
                System.arraycopy(dat, 0, block, 0, dat.size)
                System.arraycopy(ecc, 0, block, block.size - eccLen, eccLen)
                blocks.add(block)
            }
            val result = ByteArray(raw)
            var idx = 0
            for (i in 0..shortLen) {
                for (j in blocks.indices) {
                    if (i != shortLen - eccLen || j >= numShort) {
                        result[idx++] = blocks[j][i]
                    }
                }
            }
            return result
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun rsDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMul(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMul(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] = (result[i].toInt() xor gfMul(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }
            return result
        }

        private fun alignPositions(ver: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val size = ver * 4 + 17
            val step = if (ver == 32) 26 else (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val r = IntArray(n)
            r[0] = 6
            var pos = size - 7
            for (i in n - 1 downTo 1) { r[i] = pos; pos -= step }
            return r
        }

        private fun build(ver: Int, codewords: ByteArray): QrCode {
            val size = ver * 4 + 17
            val modules = Array(size) { BooleanArray(size) }
            val isFn = Array(size) { BooleanArray(size) }

            fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; isFn[y][x] = true }

            // Timing patterns.
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            // Finder patterns with separators.
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val dist = maxOf(Math.abs(dx), Math.abs(dy))
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) set(x, y, dist != 2 && dist != 4)
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            // Alignment patterns.
            val ap = alignPositions(ver)
            for (i in ap.indices) for (j in ap.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == ap.size - 1) || (i == ap.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(ap[i] + dx, ap[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }

            fun drawFormat(mask: Int) {
                val data = (0 shl 3) or mask        // error correction level M = 0
                var rem = data
                for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
                val bits = ((data shl 10) or rem) xor 0x5412
                fun bit(i: Int) = ((bits ushr i) and 1) != 0
                for (i in 0..5) set(8, i, bit(i))
                set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
                for (i in 9..14) set(14 - i, 8, bit(i))
                for (i in 0..7) set(size - 1 - i, 8, bit(i))
                for (i in 8..14) set(8, size - 15 + i, bit(i))
                set(8, size - 8, true)
            }
            drawFormat(0)

            if (ver >= 7) {
                var rem = ver
                for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
                val bits = (ver shl 12) or rem
                for (i in 0 until 18) {
                    val bit = ((bits ushr i) and 1) != 0
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }

            // Codewords in the zigzag order.
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0 until 2) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFn[y][x] && i < codewords.size * 8) {
                            modules[y][x] = ((codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                right -= 2
            }

            // Pick the mask with the lowest penalty.
            var bestMask = 0
            var bestPenalty = Int.MAX_VALUE
            for (m in 0 until 8) {
                applyMask(modules, isFn, m)
                drawFormat(m)
                val p = penalty(modules, size)
                if (p < bestPenalty) { bestPenalty = p; bestMask = m }
                applyMask(modules, isFn, m)   // undo (xor again)
            }
            applyMask(modules, isFn, bestMask)
            drawFormat(bestMask)
            return QrCode(size, modules, ver, bestMask)
        }

        private fun applyMask(m: Array<BooleanArray>, fn: Array<BooleanArray>, mask: Int) {
            val size = m.size
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (!fn[y][x] && invert) m[y][x] = !m[y][x]
            }
        }

        private fun penalty(m: Array<BooleanArray>, size: Int): Int {
            var result = 0
            // Runs of the same colour in rows and columns, and 2x2 blocks.
            for (pass in 0 until 2) {
                for (a in 0 until size) {
                    var run = 1
                    for (b in 1 until size) {
                        val cur = if (pass == 0) m[a][b] else m[b][a]
                        val prev = if (pass == 0) m[a][b - 1] else m[b - 1][a]
                        if (cur == prev) {
                            run++
                            if (run == 5) result += 3 else if (run > 5) result++
                        } else run = 1
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) result += 3
            }
            var dark = 0
            for (row in m) for (c in row) if (c) dark++
            val total = size * size
            val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += k * 10
            return result
        }
    }
}
