package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class BridgeContactDecoderTest {
    @Test fun tracksBothAndIgnoresPhysicalContacts() {
        val d = BridgeContactDecoder()
        d.accept("0003 002f 00000000")
        d.accept("0003 0039 00000005")
        assertNull(d.accept("0000 0000 00000000"))
        d.accept("0003 002f 0000000a")
        d.accept("0003 0039 0000ffff")
        assertEquals(true to false, d.accept("0000 0000 00000000"))
        d.accept("0003 002f 0000000b")
        d.accept("0003 0039 0000fffe")
        assertEquals(true to true, d.accept("0000 0000 00000000"))
        d.accept("0003 002f 0000000a")
        d.accept("0003 0039 ffffffff")
        assertEquals(false to true, d.accept("0000 0000 00000000"))
        d.accept("0003 002f 0000000b")
        d.accept("0003 0039 ffffffff")
        assertEquals(false to false, d.accept("0000 0000 00000000"))
        assertNull(d.accept("bad input"))
    }
}
