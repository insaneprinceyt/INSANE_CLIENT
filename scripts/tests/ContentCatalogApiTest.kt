package com.ashmeet.hyperlauncher.utils.installer

import com.ashmeet.hyperbackend.modloaders.modpacks.api.ApiHandler
import com.ashmeet.hyperbackend.modloaders.modpacks.api.ModrinthService
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    var checks = 0
    suspend fun expectUnavailable(block: suspend () -> Any?) {
        try {
            block()
            error("A failed provider response must not look like an empty catalog")
        } catch (_: IOException) {
            checks++
        }
    }
    val searchResponse = """{"hits":[{"project_id":"project1","title":"Catalog entry","description":"A project","icon_url":null}]}"""
    val types = mapOf(ContentInstallerType.MODS to "mod", ContentInstallerType.RESOURCEPACKS to "resourcepack", ContentInstallerType.SHADERS to "shader")
    for ((type, facet) in types) {
        ApiHandler.response = searchResponse
        val projects = ModrinthService.search("", type)
        check(projects.single().title == "Catalog entry")
        check(ApiHandler.lastQuery["query"] == "")
        val facets = JsonParser.parseString(ApiHandler.lastQuery["facets"] as String).asJsonArray
        check(facets.size() == 1 && facets[0].asJsonArray[0].asString == "project_type:$facet")
        checks += 3
    }
    for (type in types.keys) {
        ApiHandler.response = searchResponse
        ModrinthService.search("", type, cleanMcVersion("fabric-loader-0.19.2-26.1.1"), "fabric")
        val facets = JsonParser.parseString(ApiHandler.lastQuery["facets"] as String).asJsonArray
            .map { it.asJsonArray[0].asString }
        check("versions:26.1.1" in facets && "versions:1.1" !in facets)
        check(("categories:fabric" in facets) == (type == ContentInstallerType.MODS))
        checks += 2
    }
    ApiHandler.response = null
    expectUnavailable { ModrinthService.search("", ContentInstallerType.MODS) }
    expectUnavailable { ModrinthService.getProjectVersions("project1") }
    expectUnavailable { ModrinthService.getProjectDetails("project1") }
    expectUnavailable { ModrinthService.getProjects(listOf("project1")) }
    ApiHandler.response = "{}"
    expectUnavailable { ModrinthService.search("", ContentInstallerType.SHADERS) }
    ApiHandler.response = """[{"id":"release1","name":"26.1.1 release","game_versions":["26.1.1"],"loaders":["fabric"],"version_type":"release","files":[{"primary":true,"url":"https://cdn.modrinth.com/data/project1/versions/release1/mod.jar"}],"dependencies":[{"project_id":"required1","version_id":"requiredVersion1","file_name":null,"dependency_type":"required"}]}]"""
    val version = ModrinthService.getProjectVersions("project1").single()
    check(version.gameVersions == listOf("26.1.1"))
    check(version.dependencies.single().versionId == "requiredVersion1")
    checks += 2
    println("Modrinth catalog API checks passed: $checks checks; blank discovery, category filters, exact versions, required references and explicit failures.")
}
