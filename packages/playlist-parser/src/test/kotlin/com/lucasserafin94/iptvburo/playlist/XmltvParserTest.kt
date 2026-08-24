package com.lucasserafin94.iptvburo.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading an XMLTV guide.
 *
 * The two places this can go quietly wrong are the clock and the cursor. A mishandled UTC offset
 * shifts a whole schedule by hours while every programme still looks plausible, and a stream reader
 * that miscounts its own nesting starts skipping programmes somewhere in the middle of a file
 * without ever failing. Most of what follows is aimed at those two.
 */
class XmltvParserTest {
    private fun parse(xml: String, maximum: Int = 1_000): List<XmltvProgramme> {
        val collected = mutableListOf<XmltvProgramme>()
        XmltvParser.parse(xml.byteInputStream(), maximum) { collected += it }
        return collected
    }

    private fun guide(body: String) = """<?xml version="1.0" encoding="UTF-8"?><tv>$body</tv>"""

    @Test
    fun `a programme becomes a schedule entry`() {
        val programmes =
            parse(
                guide(
                    """<programme channel="canal.1" start="20260824203000 +0000" stop="20260824213000 +0000">
                       <title>Jornal</title><desc>As noticias da noite.</desc></programme>""",
                ),
            )
        assertEquals(1, programmes.size)
        val only = programmes.single()
        assertEquals("canal.1", only.channelId)
        assertEquals("Jornal", only.title)
        assertEquals("As noticias da noite.", only.description)
        assertEquals(3_600L, only.endEpochSeconds - only.startEpochSeconds)
    }

    @Test
    fun `the offset is applied, not ignored`() {
        // The whole point of the offset. Brasilia is three hours behind UTC, so 20:30 there is
        // 23:30 UTC — ignoring the suffix would put every programme of the day three hours out
        // and still produce a schedule that looked entirely reasonable.
        val brasilia = XmltvParser.parseXmltvTime("20260824203000 -0300")
        val utc = XmltvParser.parseXmltvTime("20260824233000 +0000")
        assertEquals(utc, brasilia)
    }

    @Test
    fun `an eastern offset moves the other way`() {
        val tokyo = XmltvParser.parseXmltvTime("20260824203000 +0900")
        val utc = XmltvParser.parseXmltvTime("20260824113000 +0000")
        assertEquals(utc, tokyo)
    }

    @Test
    fun `a half-hour offset is not rounded to the hour`() {
        // India is +0530. Reading only the hours would be half an hour out on every entry.
        // 20:30 in Delhi happens five and a half hours before 20:30 in London, so the Delhi
        // instant is the smaller of the two.
        val delta =
            XmltvParser.parseXmltvTime("20260824203000 +0000")!! -
                XmltvParser.parseXmltvTime("20260824203000 +0530")!!
        assertEquals(5 * 3600L + 1800L, delta)
        assertEquals(
            XmltvParser.parseXmltvTime("20260824150000 +0000"),
            XmltvParser.parseXmltvTime("20260824203000 +0530"),
        )
    }

    @Test
    fun `a time without an offset is read as UTC`() {
        // Documented behaviour rather than a preference: falling back to the machine's own zone
        // would make one guide produce two different schedules on two computers.
        assertEquals(
            XmltvParser.parseXmltvTime("20260824203000 +0000"),
            XmltvParser.parseXmltvTime("20260824203000"),
        )
    }

    @Test
    fun `junk in a time is refused rather than guessed`() {
        listOf("", "   ", "2026-08-24", "202608", "nao-e-uma-data").forEach { bad ->
            assertNull("must not invent a time from $bad", XmltvParser.parseXmltvTime(bad))
        }
        assertNull(XmltvParser.parseXmltvTime(null))
    }

    @Test
    fun `an impossible date is refused`() {
        // A thirteenth month throws out of LocalDateTime rather than returning.
        assertNull(XmltvParser.parseXmltvTime("20261324203000 +0000"))
    }

    @Test
    fun `programmes after a rich one are still read`() {
        // The cursor hazard. Reading a title consumes through its end tag, so the nesting count
        // has to account for that or the reader believes it left <programme> early and loses its
        // place for the rest of the file. Here the extra elements of a real guide sit between two
        // programmes; if the count were wrong, the second would never arrive.
        val programmes =
            parse(
                guide(
                    """<programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>Primeiro</title>
                         <desc>Com muitos elementos.</desc>
                         <credits><director>Alguem</director><actor>Outro</actor></credits>
                         <category lang="pt">Filme</category>
                         <icon src="https://example.invalid/a.png"/>
                         <episode-num system="xmltv_ns">1.2.0/1</episode-num>
                         <rating system="BR"><value>14</value></rating>
                       </programme>
                       <programme channel="c2" start="20260824210000 +0000" stop="20260824220000 +0000">
                         <title>Segundo</title>
                       </programme>""",
                ),
            )
        assertEquals(listOf("Primeiro", "Segundo"), programmes.map { it.title })
        assertEquals("c2", programmes[1].channelId)
    }

    @Test
    fun `a run of programmes keeps its order and none are lost`() {
        val programmes =
            parse(
                guide(
                    """<programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>Primeiro</title></programme>
                       <programme channel="c2" start="20260824210000 +0000" stop="20260824220000 +0000">
                         <title>Segundo</title><desc>Depois.</desc></programme>
                       <programme channel="c3" start="20260824220000 +0000" stop="20260824230000 +0000">
                         <title>Terceiro</title></programme>""",
                ),
            )
        assertEquals(listOf("c1", "c2", "c3"), programmes.map { it.channelId })
    }

    @Test
    fun `channel elements before the schedule are skipped`() {
        // A real file lists every channel before the first programme.
        val programmes =
            parse(
                guide(
                    """<channel id="c1"><display-name>Canal Um</display-name></channel>
                       <channel id="c2"><display-name>Canal Dois</display-name></channel>
                       <programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>Depois dos canais</title></programme>""",
                ),
            )
        assertEquals(listOf("Depois dos canais"), programmes.map { it.title })
    }

    @Test
    fun `the first title wins when a guide repeats it per language`() {
        val programmes =
            parse(
                guide(
                    """<programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title lang="pt">Titulo</title><title lang="en">Title</title></programme>""",
                ),
            )
        assertEquals("Titulo", programmes.single().title)
    }

    @Test
    fun `an incomplete programme is dropped instead of guessed at`() {
        val programmes =
            parse(
                guide(
                    """<programme start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>Sem canal</title></programme>
                       <programme channel="c2" stop="20260824210000 +0000">
                         <title>Sem inicio</title></programme>
                       <programme channel="c3" start="20260824200000 +0000">
                         <title>Sem fim</title></programme>
                       <programme channel="c4" start="20260824200000 +0000" stop="20260824210000 +0000">
                       </programme>
                       <programme channel="c5" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>Completo</title></programme>""",
                ),
            )
        assertEquals(listOf("Completo"), programmes.map { it.title })
    }

    @Test
    fun `a programme that ends before it starts is dropped`() {
        // It would draw as a negative duration on the schedule.
        val programmes =
            parse(
                guide(
                    """<programme channel="c1" start="20260824210000 +0000" stop="20260824200000 +0000">
                         <title>Invertido</title></programme>""",
                ),
            )
        assertTrue(programmes.isEmpty())
    }

    @Test
    fun `a gzipped guide is read without being named as one`() {
        // Providers serve .xml.gz from a .xml address often enough that the bytes have to decide.
        val xml =
            guide(
                """<programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                     <title>Comprimido</title></programme>""",
            )
        val compressed =
            ByteArrayOutputStream()
                .also { sink -> GZIPOutputStream(sink).use { it.write(xml.toByteArray()) } }
                .toByteArray()

        val collected = mutableListOf<XmltvProgramme>()
        XmltvParser.parse(ByteArrayInputStream(compressed)) { collected += it }
        assertEquals(listOf("Comprimido"), collected.map { it.title })
    }

    @Test
    fun `the ceiling stops a file from filling memory`() {
        val many =
            (1..50).joinToString("") { index ->
                """<programme channel="c$index" start="20260824200000 +0000" stop="20260824210000 +0000">
                     <title>P$index</title></programme>"""
            }
        val collected = mutableListOf<XmltvProgramme>()
        val emitted =
            XmltvParser.parse(guide(many).byteInputStream(), maximumProgrammes = 10) {
                collected += it
            }
        assertEquals(10, emitted)
        assertEquals(10, collected.size)
    }

    @Test
    fun `an external entity is not fetched`() {
        // The guide is downloaded from whatever address a playlist names, so it is untrusted.
        // This is the shape that reads a local file through the parser: it must not resolve, and
        // must not take the guide load down either.
        val hostile =
            """<?xml version="1.0"?>
               <!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///c:/windows/win.ini">]>
               <tv><programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                 <title>&xxe;</title></programme></tv>"""
        val collected = mutableListOf<XmltvProgramme>()
        runCatching { XmltvParser.parse(hostile.byteInputStream()) { collected += it } }
        assertTrue(
            "no part of a local file may reach a programme",
            collected.none { it.title.contains("[") || it.title.length > 100 },
        )
    }

    @Test
    fun `an entity expansion bomb does not exhaust memory`() {
        // Billion laughs. With DTDs refused this cannot expand; what is asserted is that the load
        // ends quietly rather than hanging or bringing down the guide.
        val bomb =
            """<?xml version="1.0"?>
               <!DOCTYPE tv [
                 <!ENTITY a "aaaaaaaaaa">
                 <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
                 <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
                 <!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;">
               ]>
               <tv><programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                 <title>&d;</title></programme></tv>"""
        val collected = mutableListOf<XmltvProgramme>()
        runCatching { XmltvParser.parse(bomb.byteInputStream()) { collected += it } }
        assertTrue(collected.none { it.title.length > 1_000 })
    }

    @Test
    fun `an empty guide yields nothing rather than failing`() {
        assertTrue(parse(guide("")).isEmpty())
    }

    @Test
    fun `an oversized field is truncated`() {
        val programmes =
            parse(
                guide(
                    """<programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                         <title>${"t".repeat(5_000)}</title>
                         <desc>${"d".repeat(9_000)}</desc></programme>""",
                ),
            )
        val only = programmes.single()
        assertEquals(300, only.title.length)
        assertEquals(2_000, only.description?.length)
    }
}
