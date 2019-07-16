package prog8.vm.astvm

import prog8.compiler.target.c64.MachineDefinition
import prog8.compiler.target.c64.Petscii
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer


class BitmapScreenPanel : KeyListener, JPanel() {

    private val image = BufferedImage(SCREENWIDTH, SCREENHEIGHT, BufferedImage.TYPE_INT_ARGB)
    private val g2d = image.graphics as Graphics2D
    private var cursorX: Int=0
    private var cursorY: Int=0
    val keyboardBuffer: Deque<Char> = LinkedList()

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

    override fun keyTyped(p0: KeyEvent) {
        keyboardBuffer.add(p0.keyChar)
    }

    override fun keyPressed(p0: KeyEvent) {
    }

    override fun keyReleased(p0: KeyEvent?) {
    }

    override fun paint(graphics: Graphics?) {
        val g2d = graphics as Graphics2D?
        g2d!!.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.drawImage(image, 0, 0, image.width * 3, image.height * 3, null)
    }

    fun clearScreen(color: Short) {
        g2d.background = MachineDefinition.colorPalette[color % MachineDefinition.colorPalette.size]
        g2d.clearRect(0, 0, SCREENWIDTH, SCREENHEIGHT)
        cursorX = 0
        cursorY = 0
    }
    fun setPixel(x: Int, y: Int, color: Short) {
        image.setRGB(x, y, MachineDefinition.colorPalette[color % MachineDefinition.colorPalette.size].rgb)
    }
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, color: Short) {
        g2d.color = MachineDefinition.colorPalette[color % MachineDefinition.colorPalette.size]
        g2d.drawLine(x1, y1, x2, y2)
    }

    fun printText(text: String, lowercase: Boolean, inverseVideo: Boolean=false) {
        val t2 = text.substringBefore(0.toChar())
        val lines = t2.split('\n')
        for(line in lines.withIndex()) {
            val petscii = Petscii.encodePetscii(line.value, lowercase)
            petscii.forEach { printPetscii(it, inverseVideo) }
            if(line.index<lines.size-1) {
                printPetscii(13)    // newline
            }
        }
    }

    fun printPetscii(char: Short, inverseVideo: Boolean=false) {
        if(char==13.toShort() || char==141.toShort()) {
            cursorX=0
            cursorY++
        } else {
            setPetscii(cursorX, cursorY, char, 1, inverseVideo)
            cursorX++
            if (cursorX >= (SCREENWIDTH / 8)) {
                cursorY++
                cursorX = 0
            }
        }
        while(cursorY>=(SCREENHEIGHT/8)) {
            // scroll the screen up because the cursor went past the last line
            Thread.sleep(10)
            val screen = image.copy()
            val graphics = image.graphics as Graphics2D
            graphics.drawImage(screen, 0, -8, null)
            val color = graphics.color
            graphics.color = MachineDefinition.colorPalette[6]
            graphics.fillRect(0, 24*8, SCREENWIDTH, 25*8)
            graphics.color=color
            cursorY--
        }
    }

    fun writeTextAt(x: Int, y: Int, text: String, color: Short, lowercase: Boolean, inverseVideo: Boolean=false) {
        val colorIdx = (color % MachineDefinition.colorPalette.size).toShort()
        var xx=x
        for(clearx in xx until xx+text.length) {
            g2d.clearRect(8*clearx, 8*y, 8, 8)
        }
        for(sc in Petscii.encodePetscii(text, lowercase)) {
            if(sc==0.toShort())
                break
            setPetscii(xx++, y, sc, colorIdx, inverseVideo)
        }
    }

    fun setPetscii(x: Int, y: Int, petscii: Short, color: Short, inverseVideo: Boolean) {
        g2d.clearRect(8*x, 8*y, 8, 8)
        val colorIdx = (color % MachineDefinition.colorPalette.size).toShort()
        val screencode = Petscii.petscii2scr(petscii, inverseVideo)
        val coloredImage = MachineDefinition.Charset.getColoredChar(screencode, colorIdx)
        g2d.drawImage(coloredImage, 8*x, 8*y , null)
    }

    fun setChar(x: Int, y: Int, screencode: Short, color: Short) {
        g2d.clearRect(8*x, 8*y, 8, 8)
        val colorIdx = (color % MachineDefinition.colorPalette.size).toShort()
        val coloredImage = MachineDefinition.Charset.getColoredChar(screencode, colorIdx)
        g2d.drawImage(coloredImage, 8*x, 8*y , null)
    }

    fun setCursorPos(x: Int, y: Int) {
        cursorX = x
        cursorY = y
    }

    fun getCursorPos(): Pair<Int, Int> {
        return Pair(cursorX, cursorY)
    }

    companion object {
        const val SCREENWIDTH = 320
        const val SCREENHEIGHT = 200
        const val SCALING = 3
    }
}


class ScreenDialog(title: String) : JFrame(title) {
    val canvas = BitmapScreenPanel()
    val keyboardBuffer = canvas.keyboardBuffer

    init {
        val borderWidth = 16
        layout = GridBagLayout()
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false

        // the borders (top, left, right, bottom)
        val borderTop = JPanel().apply {
            preferredSize = Dimension(BitmapScreenPanel.SCALING * (BitmapScreenPanel.SCREENWIDTH +2*borderWidth), BitmapScreenPanel.SCALING * borderWidth)
            background = MachineDefinition.colorPalette[14]
        }
        val borderBottom = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * (BitmapScreenPanel.SCREENWIDTH +2*borderWidth), BitmapScreenPanel.SCALING * borderWidth)
            background = MachineDefinition.colorPalette[14]
        }
        val borderLeft = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * borderWidth, BitmapScreenPanel.SCALING * BitmapScreenPanel.SCREENHEIGHT)
            background = MachineDefinition.colorPalette[14]
        }
        val borderRight = JPanel().apply {
            preferredSize =Dimension(BitmapScreenPanel.SCALING * borderWidth, BitmapScreenPanel.SCALING * BitmapScreenPanel.SCREENHEIGHT)
            background = MachineDefinition.colorPalette[14]
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


private fun BufferedImage.copy(): BufferedImage {
    val bcopy = BufferedImage(this.width, this.height, this.type)
    val g = bcopy.graphics
    g.drawImage(this, 0, 0, null)
    g.dispose()
    return bcopy
}
