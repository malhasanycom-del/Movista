package com.example.moviesta

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection

data class Cat(val id: String, val name: String)
data class Item(val id: String, val name: String, val poster: String, val year: String, val rating: String, val ext: String)

private val Bg = Color(0xFF0B1226)
private val Card = Color(0xFF141C36)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("p", Context.MODE_PRIVATE)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card)) { Surface(color = Bg) { App(prefs) } } }
    }
}

fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Moviesta-Android/1.0")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(250)}")
        body
    } finally { connection.disconnect() }
}
fun start(ctx: Context, url: String) = ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra("url", url))

@Composable
fun App(prefs: SharedPreferences) {
    var host by remember { mutableStateOf(prefs.getString("h", "") ?: "") }
    var user by remember { mutableStateOf(prefs.getString("u", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("p", "") ?: "") }
    if (host.isBlank()) Login { h, u, p ->
        host = h.trim().trimEnd('/'); user = u.trim(); pass = p.trim()
        prefs.edit().putString("h", host).putString("u", user).putString("p", pass).apply()
    } else Home(host, user, pass, prefs) { prefs.edit().clear().apply(); host = "" }
}

@Composable
fun Login(onSave: (String, String, String) -> Unit) {
    var h by remember { mutableStateOf("http://") }; var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Moviesta", fontSize = 28.sp); Spacer(Modifier.height(16.dp))
        OutlinedTextField(h, { h = it }, label = { Text("Server URL (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(u, { u = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(p, { p = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button({
            if (h.trim().startsWith("http://") || h.trim().startsWith("https://")) {
                if (u.isNotBlank() && p.isNotBlank()) onSave(h, u, p)
            }
        }, Modifier.fillMaxWidth()) { Text("Login") }
    }
}

@Composable
fun Home(host: String, user: String, pass: String, prefs: SharedPreferences, onLogout: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var cats by remember { mutableStateOf(listOf<Cat>()) }
    var sel by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(listOf<Item>()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var favs by remember { mutableStateOf(prefs.getStringSet("f", emptySet()) ?: emptySet()) }
    var episodes by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    fun api(a: String, extra: String = "") =
        "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=${enc(a)}$extra"
    val kind = listOf("vod", "series", "live")[tab]

    LaunchedEffect(tab) {
        sel = null
        try {
            cats = JSONArray(fetch(api("get_${kind}_categories"))).objs().map { Cat(it.optString("category_id"), it.optString("category_name")) }
        } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(tab, sel) {
        if (sel == "fav") return@LaunchedEffect
        loading = true; error = null
        try {
            val action = if (tab == 1) "get_series" else "get_${kind}_streams"
            val extra = sel?.let { "&category_id=${enc(it)}" } ?: ""
            items = JSONArray(fetch(api(action, extra))).objs().map {
                Item(
                    id = it.optString(if (tab == 1) "series_id" else "stream_id"),
                    name = it.optString("name"),
                    poster = it.optString(if (tab == 1) "cover" else "stream_icon"),
                    year = it.optString("year", it.optString("releaseDate", "")).take(4),
                    rating = it.optString("rating", ""),
                    ext = it.optString("container_extension", "mp4")
                )
            }
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    fun toggleFav(i: Item) {
        val k = "$tab:${i.id}"
        favs = if (k in favs) favs - k else favs + k
        prefs.edit().putStringSet("f", HashSet(favs)).apply()
    }
    suspend fun open(i: Item) {
        when (tab) {
            0 -> start(ctx, "$host/movie/$user/$pass/${i.id}.${i.ext}")
            2 -> start(ctx, "$host/live/$user/$pass/${i.id}.m3u8")
            else -> try {
                val eps = JSONObject(fetch(api("get_series_info", "&series_id=${enc(i.id)}"))).optJSONObject("episodes")
                val out = mutableListOf<Pair<String, String>>()
                eps?.keys()?.forEach { s ->
                    eps.getJSONArray(s).objs().forEach { e ->
                        out += "S$s E${e.optString("episode_num")} ${e.optString("title")}" to
                            "$host/series/$user/$pass/${e.optString("id")}.${e.optString("container_extension", "mp4")}"
                    }
                }
                episodes = out
            } catch (e: Exception) { error = e.message }
        }
    }

    val scope = rememberCoroutineScope()
    val shown = items.filter { (sel != "fav" || "$tab:${it.id}" in favs) && it.name.contains(query, true) }
    val allFavs = remember(sel) { sel == "fav" }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search movies, series, channels") },
                singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onLogout) { Text("Exit") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Movies", "Series", "Live").forEachIndexed { i, t ->
                FilterChip(tab == i, { tab = i; items = emptyList() }, { Text(t) })
            }
        }
        LazyRow(Modifier.padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(sel == null, { sel = null }, { Text("All") }) }
            item { FilterChip(allFavs, { sel = "fav" }, { Text("★ Favorites") }) }
            items(cats) { c -> FilterChip(sel == c.id, { sel = c.id }, { Text(c.name) }) }
        }
        error?.let { Text("Error: $it", color = Color.Red, modifier = Modifier.padding(12.dp)) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shown, key = { it.id }) { i ->
                Column(Modifier.clickable { scope.launch { open(i) } }) {
                    Box {
                        AsyncImage(i.poster, i.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Card), contentScale = ContentScale.Crop)
                        Text(if ("$tab:${i.id}" in favs) "★" else "☆", fontSize = 22.sp, color = Color(0xFFFFC107),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { toggleFav(i) })
                    }
                    Text(i.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text("${i.year}  ★ ${i.rating}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }

    episodes?.let { eps ->
        AlertDialog(onDismissRequest = { episodes = null }, confirmButton = { TextButton({ episodes = null }) { Text("Close") } },
            title = { Text("Episodes") },
            text = { LazyColumn { items(eps) { (t, u) -> Text(t, Modifier.fillMaxWidth().clickable { start(ctx, u) }.padding(vertical = 10.dp)) } } })
    }
}
