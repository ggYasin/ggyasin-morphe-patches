package app.template.patches.ninegag.shared

/**
 * Raw obfuscated DEX descriptors for releases whose ad policy is the nullable
 * block-reason enum (null ENABLES ads). Each row was mapped by strings and
 * method structure, not by name; see docs/ninegag-8.26.1.md and
 * docs/ninegag-8.29.03.md. The patches still verify each target's validated
 * shape before editing it. 8.23.0 is absent: it keeps its Boolean-gate
 * fingerprints.
 */
internal data class NinegagSymbols(
    /** Feed DAO holding j(int,String,boolean) display and d(...) query builder. */
    val feedQueryClass: String,
    /** greenDAO QueryBuilder returned by the shared d(...) builder. */
    val feedQueryBuilder: String,
    /** Class holding the static bottom-banner initializer f(FrameLayout, callback). */
    val bannerClass: String,
    /** Kotlin Function0 type of the banner initializer's second parameter. */
    val bannerCallback: String,
    /** Generated R.id class holding adview_banner_binding. */
    val bannerIds: String,
    /** Banner binding object constructed by the initializer. */
    val bannerBinding: String,
    /** Class holding the static base ad policy b(Z, paidAccess, entitlement). */
    val adPolicyClass: String,
    /** Nullable ad-block-reason enum; field a is NotInitialized. */
    val blockReason: String,
    /** Sealed paid-access state parameter. */
    val paidAccess: String,
    /** AdEntitlement(isSubscriber=a, prefersAdsHidden=b). */
    val adEntitlement: String,
)

internal val NINEGAG_SYMBOLS = mapOf(
    "8.26.1" to NinegagSymbols(
        feedQueryClass = "Ld44;",
        feedQueryBuilder = "Lqy7;",
        bannerClass = "Lg3b;",
        bannerCallback = "Li04;",
        bannerIds = "Le38;",
        bannerBinding = "Lkl0;",
        adPolicyClass = "Lgb;",
        blockReason = "Lfa;",
        paidAccess = "Lo57;",
        adEntitlement = "Lib;",
    ),
    "8.29.03" to NinegagSymbols(
        feedQueryClass = "Lha4;",
        feedQueryBuilder = "Ljb8;",
        bannerClass = "Lj62;",
        bannerCallback = "Lk64;",
        bannerIds = "Lxf8;",
        bannerBinding = "Lem0;",
        adPolicyClass = "Ljb;",
        blockReason = "Lja;",
        paidAccess = "Lah7;",
        adEntitlement = "Llb;",
    ),
)
