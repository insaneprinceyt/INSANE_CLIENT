package com.ashmeet.hyperlauncher.utils.installer

fun main() {
    val identities = listOf(
        "1.8.9", "1.20", "1.20.1", "1.21.1", "1.21.1-pre1", "1.21.1-rc1",
        "24w14a", "26.1", "26.1.1", "26.1.2", "26.2", "26.1-snapshot-1",
        "26.1-pre-1", "26.1-rc-1", "b1.7.3"
    )
    identities.forEach { version ->
        check(cleanMcVersion(version) == version) { "Corrupted Minecraft ID: $version -> ${cleanMcVersion(version)}" }
        check(isMcVersionCompatible(version, version)) { "Identical IDs must match: $version" }
    }
    val decorated = mapOf(
        "fabric-loader-0.18.3-26.1.1" to "26.1.1",
        "fabric-loader-0.16.10-1.21.1" to "1.21.1",
        "quilt-loader-0.27.1-1.21.1" to "1.21.1",
        "1.20.1-forge-47.4.0" to "1.20.1",
        "1.21.1-neoforge-21.1.2" to "1.21.1",
        "1.21.1-OptiFine_HD_U_J1" to "1.21.1",
        "1.21.1 (Fabric)" to "1.21.1"
    )
    decorated.forEach { (profile, version) ->
        check(cleanMcVersion(profile) == version) { "Incorrect profile version: $profile" }
        check(isMcVersionCompatible(profile, version))
    }
    listOf("26.1.1" to "1.1", "26.1.2" to "1.2", "1.20" to "1.20.1",
        "1.21.1-pre1" to "1.21.1", "1.21.1-rc1" to "1.21.1",
        "26.1-snapshot-1" to "26.1", "24w14a" to "24w14b").forEach { (target, release) ->
        check(!isMcVersionCompatible(target, release)) { "Unsafe match: $target and $release" }
    }
    check(cleanMcVersion(null).isEmpty())
    check(!isMcVersionCompatible("", ""))
    check(isLoaderCompatible(null, listOf("forge"))) // Any catalog loader
    check(isLoaderCompatible("any", listOf("fabric")))
    check(isLoaderCompatible("Fabric", listOf("fabric")))
    check(isLoaderCompatible("Forge", listOf("forge")))
    check(isLoaderCompatible("Quilt", listOf("quilt")))
    check(!isLoaderCompatible("fabric", listOf("forge")))
    check(!isLoaderCompatible("quilt", listOf("fabric")))
    println("Content catalog regression checks passed: exact Minecraft IDs, loader profiles and compatibility.")
}
