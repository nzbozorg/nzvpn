package nzvpn.nzbozorg.ir.data

/** حذف ساب مخزن دائمی است تا کاربر خودش «خواندن از مخزن» را بزند. */
object RepoVisibility {
    const val DIRECT_FILE = "nzvpn:repo-direct-file"

    fun visible(location: String, suppressed: Set<String>): Boolean =
        location !in suppressed
}
