package com.lucasserafin94.iptvburo.playback

import android.app.PictureInPictureParams

/**
 * The picture-in-picture window the player wants if the user leaves the app now, or null.
 *
 * Written by the player while something is actually playing on a phone and cleared when it stops,
 * read by the activity when the user presses Home. A process-wide holder rather than state passed
 * up through the composition, because the activity callback that needs it lives outside the
 * composition entirely, and there is only ever one player on screen.
 */
object PictureInPictureRequest {
    @Volatile
    var pending: PictureInPictureParams? = null
}
