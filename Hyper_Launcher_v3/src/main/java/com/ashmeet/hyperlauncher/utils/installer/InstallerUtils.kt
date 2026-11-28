package com.ashmeet.hyperlauncher.utils.installer

private val LOADER_PREFIX_REGEX = Regex("""^(fabric|quilt|neoforge|forge)-loader-[0-9.]+-""", RegexOption.IGNORE_CASE)
private val LOADER_DASH_REGEX = Regex("""^(fabric|quilt|neoforge|forge)-loader-""", RegexOption.IGNORE_CASE)
private val LOADER_NO_DASH_REGEX = Regex("""^(fabric|quilt|neoforge|forge)loader""", RegexOption.IGNORE_CASE)
private val FORGE_SUFFIX_REGEX = Regex("""-forge-.*""", RegexOption.IGNORE_CASE)
private val FABRIC_SUFFIX_REGEX = Regex("""-fabric-.*""", RegexOption.IGNORE_CASE)
private val QUILT_SUFFIX_REGEX = Regex("""-quilt-.*""", RegexOption.IGNORE_CASE)
private val NEOFORGE_SUFFIX_REGEX = Regex("""-neoforge-.*""", RegexOption.IGNORE_CASE)
private val OPTIFINE_UNDERSCORE_REGEX = Regex("""-OptiFine_.*""", RegexOption.IGNORE_CASE)
private val OPTIFINE_SPACE_REGEX = Regex(""" OptiFine .*""", RegexOption.IGNORE_CASE)
private val PARENTHESES_REGEX = Regex(""" \(.*\)""")

fun isMcVersionCompatible(v1: String, v2: String): Boolean {
    val cv1 = cleanMcVersion(v1)
    val cv2 = cleanMcVersion(v2)
    // A project's declared game versions are authoritative. A release, patch,
    // prerelease and snapshot must never become interchangeable here.
    return cv1.isNotEmpty() && cv1 == cv2
}

fun cleanMcVersion(version: String?): String {
    if (version == null) return ""
    // Remove known launcher decorations only. Extracting a 1.x substring turns
    // 26.1.1 into 1.1 and strips -pre/-rc suffixes from real Minecraft IDs.
    return version
        .replace(LOADER_PREFIX_REGEX, "")
        .replace(LOADER_DASH_REGEX, "")
        .replace(LOADER_NO_DASH_REGEX, "")
        .replace(FORGE_SUFFIX_REGEX, "")
        .replace(FABRIC_SUFFIX_REGEX, "")
        .replace(QUILT_SUFFIX_REGEX, "")
        .replace(NEOFORGE_SUFFIX_REGEX, "")
        .replace(OPTIFINE_UNDERSCORE_REGEX, "")
        .replace(OPTIFINE_SPACE_REGEX, "")
        .replace(PARENTHESES_REGEX, "")
        .trim()
}

fun cleanLoaderName(loader: String): String {
    return when (loader.lowercase()) {
        "fabric", "fabricloader" -> "Fabric"
        "forge" -> "Forge"
        "quilt" -> "Quilt"
        "neoforge" -> "NeoForge"
        "optifine" -> "OptiFine"
        "iris" -> "Iris"
        "canvas" -> "Canvas"
        else -> loader.replaceFirstChar { it.uppercase() }
    }
}

fun cleanLoadersList(loaders: List<String>): String {
    return loaders.map { cleanLoaderName(it) }
        .distinct()
        .joinToString(", ")
}

fun isLoaderCompatible(targetLoader: String?, versionLoaders: List<String>): Boolean {
    if (targetLoader == null || targetLoader.equals("any", ignoreCase = true)) return true
    val cleanTarget = cleanLoaderName(targetLoader).lowercase()
    return versionLoaders.any { cleanLoaderName(it).lowercase() == cleanTarget }
}
