package com.ashmeet.hyperbackend.modloaders.modpacks.api

import com.google.gson.Gson

/** Only the HTTP boundary is replaced; tests compile the production ModrinthService. */
class ApiHandler(val baseUrl: String) {
    companion object {
        var response: String? = null
        var lastEndpoint = ""
        var lastQuery: Map<String, Any> = emptyMap()
    }
    fun <T> get(endpoint: String, type: Class<T>): T? = get(endpoint, hashMapOf(), type)
    fun <T> get(endpoint: String, query: HashMap<String, Any>, type: Class<T>): T? {
        check(baseUrl == "https://api.modrinth.com/v2")
        lastEndpoint = endpoint
        lastQuery = query.toMap()
        return response?.let { Gson().fromJson(it, type) }
    }
}
