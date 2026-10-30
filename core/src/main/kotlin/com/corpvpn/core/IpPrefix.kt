package com.corpvpn.core

import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress

/** An IPv4 or IPv6 range in CIDR form, such as 10.0.0.0/8 or 2001:db8::/32. */
class IpPrefix private constructor(val isV6: Boolean, private val address: BigInteger, val length: Int) {
    private val width get() = if (isV6) 128 else 32

    fun contains(other: IpPrefix): Boolean =
        isV6 == other.isV6 && other.length >= length &&
            other.address.shiftRight(width - length) == address.shiftRight(width - length)

    private fun overlaps(other: IpPrefix) = contains(other) || other.contains(this)

    /** How many addresses the range covers. */
    val size: BigInteger get() = BigInteger.ONE.shiftLeft(width - length)

    private fun halves(): List<IpPrefix> = listOf(
        IpPrefix(isV6, address, length + 1),
        IpPrefix(isV6, address.setBit(width - length - 1), length + 1),
    )

    override fun toString(): String {
        val bytes = ByteArray(width / 8)
        val raw = address.toByteArray().takeLast(bytes.size)
        raw.toByteArray().copyInto(bytes, bytes.size - raw.size)
        return InetAddress.getByAddress(bytes).hostAddress + "/" + length
    }

    override fun equals(other: Any?) =
        other is IpPrefix && isV6 == other.isV6 && address == other.address && length == other.length

    override fun hashCode() = 31 * (31 * isV6.hashCode() + address.hashCode()) + length

    companion object {
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val IPV6 = Regex("""[0-9a-fA-F:.]+""")

        /** "1.2.3.4", "10.0.0.0/8", "2001:db8::/32"; null if the text isn't an address. */
        fun parse(text: String): IpPrefix? {
            val addressText = text.trim().substringBefore('/').substringBefore('%')
            val lengthText = text.trim().substringAfter('/', "")
            val bytes = when {
                IPV4.matches(addressText) -> addressText.split('.').map { it.toInt() }
                    .takeIf { parts -> parts.all { it in 0..255 } }
                    ?.map { it.toByte() }?.toByteArray()
                ':' in addressText && IPV6.matches(addressText) ->
                    // A literal, so this never does a DNS lookup.
                    runCatching { InetAddress.getByName(addressText) }.getOrNull()
                        ?.takeIf { it is Inet6Address }?.address
                else -> null
            } ?: return null
            val width = bytes.size * 8
            val length = if (lengthText.isEmpty()) width else lengthText.toIntOrNull() ?: return null
            if (length !in 0..width) return null
            // Clear the host bits, so 10.1.2.3/8 becomes 10.0.0.0/8.
            val value = BigInteger(1, bytes).shiftRight(width - length).shiftLeft(width - length)
            return IpPrefix(bytes.size == 16, value, length)
        }

        /** [from] with every address in [remove] taken out, as few ranges as the split allows. */
        fun subtract(from: List<IpPrefix>, remove: List<IpPrefix>): List<IpPrefix> =
            from.flatMap { subtract(it, remove) }

        private fun subtract(range: IpPrefix, remove: List<IpPrefix>): List<IpPrefix> {
            val hits = remove.filter { it.overlaps(range) }
            return when {
                hits.isEmpty() -> listOf(range)
                hits.any { it.contains(range) } -> emptyList()
                else -> range.halves().flatMap { subtract(it, hits) }
            }
        }

        /** The parts of [wanted] that fall inside [within]. */
        fun intersect(within: List<IpPrefix>, wanted: List<IpPrefix>): List<IpPrefix> {
            val parts = wanted.flatMap { w ->
                if (within.any { it.contains(w) }) listOf(w) else within.filter { w.contains(it) }
            }.distinct()
            return parts.filter { p -> parts.none { it != p && it.contains(p) } }
        }
    }
}
