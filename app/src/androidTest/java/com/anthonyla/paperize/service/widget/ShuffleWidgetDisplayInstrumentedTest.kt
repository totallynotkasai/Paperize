package com.anthonyla.paperize.service.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.service.widget.ShuffleWidgetDisplay.Companion.OPAQUE
import com.anthonyla.paperize.service.widget.ShuffleWidgetDisplay.Companion.UNAVAILABLE_ALPHA
import com.anthonyla.paperize.service.widget.ShuffleWidgetDisplay.Companion.nameFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inflates the Shuffle widgets' real layouts as a launcher would, in every state (plan 4.1).
 * Builds views only: placed widgets are not redrawn and no wallpaper changes.
 */
@RunWith(AndroidJUnit4::class)
class ShuffleWidgetDisplayInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val display = ShuffleWidgetDisplay(context)

    private fun inflate(screen: ScreenType, ready: Boolean, wide: Boolean): View {
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = display.layout(screen, ready, wide).apply(context, FrameLayout(context))
        }
        return view
    }

    private val View.badge get() = findViewById<View>(R.id.widget_badge)
    private val View.glyph get() = findViewById<ImageView>(R.id.widget_glyph)

    @Test fun readyWidgetsShowTheirBadgeAndNameAndRespondToTaps() {
        SHUFFLE_TARGETS.forEach { screen ->
            val name = context.getString(nameFor(screen))
            listOf(false, true).forEach { wide ->
                val widget = inflate(screen, ready = true, wide = wide)
                // One screen-reader item, labelled with the widget's name.
                assertEquals(name, widget.contentDescription)
                assertEquals(View.VISIBLE, widget.badge.visibility)
                assertEquals(OPAQUE, widget.glyph.imageAlpha)
                assertTrue("$screen must react to a tap", widget.hasOnClickListeners())
            }
            val wide = inflate(screen, ready = true, wide = true)
            assertEquals(name, wide.findViewById<TextView>(R.id.widget_label).text.toString())
            assertEquals(View.GONE, wide.findViewById<View>(R.id.widget_status).visibility)
        }
    }

    @Test fun widgetsThatCantChangeAnythingAreGreyedOutAndSaySo() {
        SHUFFLE_TARGETS.forEach { screen ->
            val name = context.getString(nameFor(screen))
            val notSetUp = context.getString(R.string.widget_content_desc_not_set_up, name)
            listOf(false, true).forEach { wide ->
                val widget = inflate(screen, ready = false, wide = wide)
                assertEquals(notSetUp, widget.contentDescription)
                assertEquals(View.GONE, widget.badge.visibility)
                assertEquals(UNAVAILABLE_ALPHA, widget.glyph.imageAlpha)
                // A tap still explains what to set up.
                assertTrue(widget.hasOnClickListeners())
            }
            val status = inflate(screen, ready = false, wide = true).findViewById<TextView>(R.id.widget_status)
            assertEquals(View.VISIBLE, status.visibility)
            assertEquals(context.getString(R.string.widget_not_set_up), status.text.toString())
        }
    }

    @Test fun theWideWidgetsTextsAreLeftToTheContentDescription() {
        val wide = inflate(ScreenType.LOCK, ready = false, wide = true)
        listOf(R.id.widget_label, R.id.widget_status, R.id.widget_glyph).forEach { id ->
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, wide.findViewById<View>(id).importantForAccessibility)
        }
    }
}
