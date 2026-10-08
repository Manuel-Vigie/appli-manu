package fr.mesphotos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Quelques icônes de plus (tracés « Material », dessinés ici pour ne pas alourdir l'appli). */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    val Photos: ImageVector by lazy {
        icon("Photos", "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z")
    }

    val Copy: ImageVector by lazy {
        icon("Copy", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z")
    }
}

/** Barre du haut des écrans principaux : un grand titre, une ligne de détail, des actions à droite. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Action en texte, à droite de la barre du haut. */
@Composable
fun TopAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** Les trois onglets du bas. [selected] : 0 = Photos, 1 = Visages, 2 = Outils. */
@Composable
fun AppNavBar(selected: Int, onPhotos: () -> Unit, onFaces: () -> Unit, onTools: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = onPhotos,
            icon = { Icon(AppIcons.Photos, contentDescription = null) },
            label = { Text("Photos") },
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = onFaces,
            icon = { Icon(Icons.Default.Face, contentDescription = null) },
            label = { Text("Visages") },
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = onTools,
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text("Outils") },
        )
    }
}
