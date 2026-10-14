package id.orvyn.app

import id.orvyn.app.ui.qris.decodeQr
import id.orvyn.app.ui.qris.drawQr
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QrTest {
    @Test fun `a QR drawn for My QR reads back as the same payload`() {
        val payload = "00020101021126410015ID.CO.ORVYN.P2P0110406159453452044829530336058" + "02ID5914Sinta Maharani6007Jakarta63041A2B"
        assertEquals(payload, decodeQr(drawQr(payload)))
    }
}
