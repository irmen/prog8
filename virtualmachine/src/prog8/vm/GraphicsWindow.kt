package prog8.vm

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min


class GraphicsWindow(val pixelWidth: Int, val pixelHeight: Int, val pixelScaling: Int): JFrame("Prog8 VM Graphics Screen $pixelWidth × $pixelHeight"), AutoCloseable {
    private lateinit var repaintTimer: Timer

    fun start() {
        val refreshRate = optimalRefreshRate(this)
        repaintTimer = Timer(1000/refreshRate) {
            repaint()
        }
        repaintTimer.initialDelay = 0
        repaintTimer.start()
    }

    override fun close() {
        repaintTimer.stop()
        dispose()
    }

    val image: BufferedImage

    init {
        contentPane.layout = BorderLayout()
        background = Color.BLACK
        contentPane.background = Color.BLACK
        isResizable = false
        isLocationByPlatform = true
        defaultCloseOperation = EXIT_ON_CLOSE
        image = graphicsConfiguration.createCompatibleImage(pixelWidth, pixelHeight, Transparency.OPAQUE)
        contentPane.add(BitmapScreenPanel(image, pixelScaling), BorderLayout.CENTER)
        pack()
        requestFocusInWindow()
        isVisible = true
    }

    private fun optimalRefreshRate(frame: JFrame): Int {
        var rate = frame.graphicsConfiguration.device.displayMode.refreshRate
        if(rate==0)
            rate = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                .map { it.displayMode.refreshRate }
                .firstOrNull { it>0 } ?: 60
        return max(30, min(250, rate))
    }

    fun clear(color: Int) {
        val g2d = image.graphics as Graphics2D
        g2d.background = Color(color, color, color)
        g2d.clearRect(0,0, pixelWidth*pixelScaling, pixelHeight*pixelScaling)
        g2d.dispose()
    }

    fun plot(x: Int, y: Int, color: Int) {
        if(x !in 0..<pixelWidth)
            throw IllegalArgumentException("plot x outside of screen: $x")
        if(y !in 0..<pixelHeight)
            throw IllegalArgumentException("plot y outside of screen: $y")
        image.setRGB(x, y, Color(color, color, color, 255).rgb)
    }

    fun getpixel(x: Int, y: Int): Int {
        if(x !in 0..<pixelWidth)
            throw IllegalArgumentException("getpixel x outside of screen: $x")
        if(y !in 0..<pixelHeight)
            throw IllegalArgumentException("getpixel y outside of screen: $y")
        return image.getRGB(x, y)
    }
}


internal class BitmapScreenPanel(private val drawImage: BufferedImage, pixelScaling: Int) : JPanel() {
    init {
        val size = Dimension(drawImage.width * pixelScaling, drawImage.height * pixelScaling)
        minimumSize = size
        maximumSize = size
        preferredSize = size
        isFocusable = true
        isDoubleBuffered = false
        requestFocusInWindow()
    }

    override fun paint(graphics: Graphics) {
        val g2d = graphics as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.drawImage(drawImage, 0, 0, size.width, size.height, null)
        Toolkit.getDefaultToolkit().sync()
    }
}
