package com.lucasserafin94.iptvburo.domain.model

/**
 * Splits accented characters into a base letter and its combining marks, so the marks can be
 * stripped: `"Amélie"` becomes `"Ame´lie"`, which the caller then folds to `"amelie"`.
 *
 * Unicode calls this NFD. Every platform has it and none of them expose it the same way, so the
 * shared code declares what it needs and each target supplies its own.
 *
 * **This must not change what it returns.** It sits under [ContentIdentity.slugify], which is the
 * key every favourite, reminder, download and resume point is filed against on every installation
 * already out there. A stem that came out even slightly different would leave all of that pointing
 * at nothing, silently — the data would still be on disk and the app would never find it again.
 */
internal expect fun decomposeForFolding(value: String): String
