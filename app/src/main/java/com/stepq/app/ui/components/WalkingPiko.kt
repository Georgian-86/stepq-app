package com.stepq.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import com.stepq.app.R
import kotlin.math.sin

/**
 * Piko mid-stride. [phase] is the step cycle in radians (one left step and one right step per
 * 2π); [stride] scales the swing, so 0 is standing still. Legs swing from the hips and arms
 * swing the opposite way, from piko_walk.xml's named groups.
 */
@Composable
fun WalkingPiko(phase: Float, stride: Float, modifier: Modifier = Modifier, contentDescription: String? = "Piko") {
    val image = ImageVector.vectorResource(R.drawable.piko_walk)
    val s = sin(phase) * stride
    val rotations = mapOf(
        "legL" to s * 26f,
        "legR" to -s * 26f,
        "armL" to -s * 20f,
        "armR" to s * 16f,
        // A slight lean into each step.
        "body" to s * 2.5f,
    )
    val configs = rotations.mapValues { (_, degrees) ->
        object : VectorConfig {
            @Suppress("UNCHECKED_CAST")
            override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
                if (property == VectorProperty.Rotation) degrees as T else defaultValue
        }
    }
    val painter = rememberVectorPainter(
        defaultWidth = image.defaultWidth,
        defaultHeight = image.defaultHeight,
        viewportWidth = image.viewportWidth,
        viewportHeight = image.viewportHeight,
        name = image.name,
        tintColor = image.tintColor,
        tintBlendMode = image.tintBlendMode,
        autoMirror = image.autoMirror,
    ) { _, _ -> RenderVectorGroup(image.root, configs) }
    Image(painter, contentDescription, modifier)
}
