package prog8.compiler.target.c64

import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object Charset {
    private val normalImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-normal.png"))
    private val shiftedImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-shifted.png"))

    private fun scanChars(img: BufferedImage): Array<BufferedImage> {

        val transparent = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        transparent.createGraphics().drawImage(img, 0, 0, null)

        val black = Color(0,0,0).rgb
        val nopixel = Color(0,0,0,0).rgb
        for(y in 0 until transparent.height) {
            for(x in 0 until transparent.width) {
                val col = transparent.getRGB(x, y)
                if(col==black)
                    transparent.setRGB(x, y, nopixel)
            }
        }

        val numColumns = transparent.width / 8
        val charImages = (0..255).map {
            val charX = it % numColumns
            val charY = it/ numColumns
            transparent.getSubimage(charX*8, charY*8, 8, 8)
        }
        return charImages.toTypedArray()
    }

    val normalChars = scanChars(normalImg)
    val shiftedChars = scanChars(shiftedImg)
}
