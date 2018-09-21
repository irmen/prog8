package prog8.stackvm

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer


class BitmapScreenPanel : JPanel() {

    private val image = BufferedImage(SCREENWIDTH, SCREENHEIGHT, BufferedImage.TYPE_INT_ARGB)
    private val g2d = image.graphics as Graphics2D


    init {
        val size = Dimension(image.width * SCALING, image.height * SCALING)
        minimumSize = size
        maximumSize = size
        preferredSize = size
        clearScreen(6)
    }

    override fun paint(graphics: Graphics?) {
        val g2d = graphics as Graphics2D?
        g2d!!.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.drawImage(image, 0, 0, image.width * 3, image.height * 3, null)
    }

    fun clearScreen(color: Int) {
        g2d.background = palette[color and 15]
        g2d.clearRect(0, 0, BitmapScreenPanel.SCREENWIDTH, BitmapScreenPanel.SCREENHEIGHT)
    }
    fun setPixel(x: Int, y: Int, color: Int) {
        image.setRGB(x, y, palette[color and 15].rgb)
    }
    fun writeText(x: Int, y: Int, text: String, color: Int) {
        g2d.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        g2d.color = palette[color and 15]
        g2d.drawString(text, x, y + g2d.font.size - 1)
    }

    companion object {
        const val SCREENWIDTH = 320
        const val SCREENHEIGHT = 256
        const val SCALING = 3
        val palette = listOf(         // this is Pepto's Commodore-64 palette  http://www.pepto.de/projects/colorvic/
                Color(0x000000),  // 0 = black
                Color(0xFFFFFF),  // 1 = white
                Color(0x813338),  // 2 = red
                Color(0x75cec8),  // 3 = cyan
                Color(0x8e3c97),  // 4 = purple
                Color(0x56ac4d),  // 5 = green
                Color(0x2e2c9b),  // 6 = blue
                Color(0xedf171),  // 7 = yellow
                Color(0x8e5029),  // 8 = orange
                Color(0x553800),  // 9 = brown
                Color(0xc46c71),  // 10 = light red
                Color(0x4a4a4a),  // 11 = dark grey
                Color(0x7b7b7b),  // 12 = medium grey
                Color(0xa9ff9f),  // 13 = light green
                Color(0x706deb),  // 14 = light blue
                Color(0xb2b2b2)   // 15 = light grey
        )
    }
}


class ScreenDialog : JFrame() {
    val canvas = BitmapScreenPanel()
    private val buttonQuit = JButton("Quit")

    init {
        val borderWidth = 16
        title = "StackVm graphics. Text I/O goes to console."
        layout = GridBagLayout()
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isResizable = false

        val buttonBar = JPanel()
        buttonBar.add(buttonQuit)

        var c = GridBagConstraints()
        // the button bar
        c.gridx = 0
        c.gridy = 0
        c.gridwidth = 3
        c.anchor = GridBagConstraints.LINE_END
        add(buttonBar, c)

        // the borders (top, left, right, bottom)
        val borderTop = JPanel().apply {
            preferredSize = Dimension(BitmapScreenPanel.SCALING * (BitmapScreenPanel.SCREENWIDTH+2*borderWidth), BitmapScreenPanel.SCALING * borderWidth)
            background = BitmapScreenPanel.palette[14]
        }
        val borderBottom = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * (BitmapScreenPanel.SCREENWIDTH+2*borderWidth), BitmapScreenPanel.SCALING * borderWidth)
            background = BitmapScreenPanel.palette[14]
        }
        val borderLeft = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * borderWidth, BitmapScreenPanel.SCALING * BitmapScreenPanel.SCREENHEIGHT)
            background = BitmapScreenPanel.palette[14]
        }
        val borderRight = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * borderWidth, BitmapScreenPanel.SCALING * BitmapScreenPanel.SCREENHEIGHT)
            background = BitmapScreenPanel.palette[14]
        }
        c = GridBagConstraints()
        c.gridx=0; c.gridy=1; c.gridwidth=3
        add(borderTop, c)
        c = GridBagConstraints()
        c.gridx=0; c.gridy=2
        add(borderLeft, c)
        c = GridBagConstraints()
        c.gridx=2; c.gridy=2
        add(borderRight, c)
        c = GridBagConstraints()
        c.gridx=0; c.gridy=3; c.gridwidth=3
        add(borderBottom, c)
        // the screen canvas(bitmap)
        c = GridBagConstraints()
        c.gridx = 1; c.gridy = 2
        add(canvas, c)

        getRootPane().defaultButton = buttonQuit
        buttonQuit.addActionListener { _ -> onOK() }

    }

    fun start() {
        val repaintTimer = Timer(1000 / 60) { _ -> repaint() }
        repaintTimer.start()
    }

    private fun onOK() {
        // add your code here
        dispose()
        System.exit(0)
    }
}
