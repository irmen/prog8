package prog8.astvm

import prog8.compiler.target.c64.Charset
import prog8.compiler.target.c64.Petscii
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer


class BitmapScreenPanel : KeyListener, JPanel() {

    private val image = BufferedImage(SCREENWIDTH, SCREENHEIGHT, BufferedImage.TYPE_INT_ARGB)
    private val g2d = image.graphics as Graphics2D
    private var cursorX: Int=0
    private var cursorY: Int=0

    init {
        val size = Dimension(image.width * SCALING, image.height * SCALING)
        minimumSize = size
        maximumSize = size
        preferredSize = size
        clearScreen(6)
        isFocusable = true
        requestFocusInWindow()
        addKeyListener(this)
    }

    override fun keyTyped(p0: KeyEvent?) {}

    override fun keyPressed(p0: KeyEvent?) {
        println("pressed: $p0.k")
    }

    override fun keyReleased(p0: KeyEvent?) {
        println("released: $p0")
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
        cursorX = 0
        cursorY = 0
    }
    fun setPixel(x: Int, y: Int, color: Int) {
        image.setRGB(x, y, palette[color and 15].rgb)
    }
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        g2d.color = palette[color and 15]
        g2d.drawLine(x1, y1, x2, y2)
    }
    fun printText(text: String, color: Int, lowercase: Boolean) {
        val t2 = text.substringBefore(0.toChar())
        val lines = t2.split('\n')
        for(line in lines.withIndex()) {
            printTextSingleLine(line.value, color, lowercase)
            if(line.index<lines.size-1) {
                cursorX=0
                cursorY++
            }
        }
    }
    private fun printTextSingleLine(text: String, color: Int, lowercase: Boolean) {
        if(color!=1) {
            TODO("text can only be white for now")
        }
        for(clearx in cursorX until cursorX+text.length) {
            g2d.clearRect(8*clearx, 8*y, 8, 8)
        }
        for(sc in Petscii.encodeScreencode(text, lowercase)) {
            setChar(cursorX, cursorY, sc)
            cursorX++
            if(cursorX>=(SCREENWIDTH/8)) {
                cursorY++
                cursorX=0
            }
        }
    }

    fun printChar(char: Short) {
        if(char==13.toShort() || char==141.toShort()) {
            cursorX=0
            cursorY++
        } else {
            setChar(cursorX, cursorY, char)
            cursorX++
            if (cursorX >= (SCREENWIDTH / 8)) {
                cursorY++
                cursorX = 0
            }
        }
    }

    fun setChar(x: Int, y: Int, screenCode: Short) {
        g2d.clearRect(8*x, 8*y, 8, 8)
        g2d.drawImage(Charset.shiftedChars[screenCode.toInt()], 8*x, 8*y , null)
    }

    fun setCursorPos(x: Int, y: Int) {
        cursorX = x
        cursorY = y
    }

    fun getCursorPos(): Pair<Int, Int> {
        return Pair(cursorX, cursorY)
    }

    fun writeText(x: Int, y: Int, text: String, color: Int, lowercase: Boolean) {
        var xx=x
        if(color!=1) {
            TODO("text can only be white for now")
        }
        for(clearx in xx until xx+text.length) {
            g2d.clearRect(8*clearx, 8*y, 8, 8)
        }
        for(sc in Petscii.encodeScreencode(text, lowercase)) {
            if(sc==0.toShort())
                break
            setChar(xx++, y, sc)
        }
    }


    companion object {
        const val SCREENWIDTH = 320
        const val SCREENHEIGHT = 200
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

    init {
        val borderWidth = 16
        title = "AstVm graphics. Text I/O goes to console."
        layout = GridBagLayout()
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isResizable = false

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
        var c = GridBagConstraints()
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

        canvas.requestFocusInWindow()
    }

    fun start() {
        val repaintTimer = Timer(1000 / 60) { repaint() }
        repaintTimer.start()
    }
}
