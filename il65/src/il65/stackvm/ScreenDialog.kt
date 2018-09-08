package il65.stackvm

import javax.swing.*
import java.awt.*
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.Timer


class MyJPanel : JPanel() {

    var image = BufferedImage(BitmapWidth + BorderWidth * 2, BitmapHeight + BorderWidth * 2, BufferedImage.TYPE_INT_ARGB)

    override fun paint(graphics: Graphics?) {
        val g2d = graphics as Graphics2D?
        g2d!!.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.drawImage(image, 0, 0, image.width * 3, image.height * 3, null)
    }

    companion object {

        const val BitmapWidth = 320
        const val BitmapHeight = 200
        const val BorderWidth = 16
    }
}


class ScreenDialog : JFrame() {
    private val canvas = MyJPanel()
    private val buttonQuit = JButton("Quit")
    private val logicTimer: Timer
    private val repaintTimer: Timer

    init {

        val screenSize = Dimension(canvas.image.width * Visuals.Scaling, canvas.image.height * Visuals.Scaling)
        layout = GridBagLayout()
        canvas.minimumSize = screenSize
        canvas.maximumSize = screenSize
        canvas.preferredSize = screenSize

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isResizable = false

        val buttonBar = JPanel()
        buttonBar.add(buttonQuit)

        var c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH
        c.gridx = 0
        c.gridy = 0
        add(canvas, c)
        c = GridBagConstraints()
        c.gridx = 0
        c.gridy = 1
        c.anchor = GridBagConstraints.LINE_END
        add(buttonBar, c)

        getRootPane().defaultButton = buttonQuit
        buttonQuit.addActionListener { e -> onOK() }


        val logic = ScreenDialog.Logic()
        val visuals = Visuals(logic, canvas.image)

        logicTimer = Timer(1000 / 120) { actionEvent -> logic.update() }
        repaintTimer = Timer(1000 / 60) { actionEvent ->
            visuals.update()
            repaint()
        }

        logicTimer.start()
        repaintTimer.start()
    }

    private fun onOK() {
        // add your code here
        dispose()
        logicTimer.stop()
        repaintTimer.stop()
        System.exit(0)
    }

    internal class Logic {
        var last = System.currentTimeMillis()

        fun update() {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - last
            last = now
        }
    }

    internal class Visuals(private val logic: Logic, private val image: BufferedImage) {
        private val rnd = Random()
        companion object {
            const val Scaling = 3
        }

        init {
            val g2d = image.graphics as Graphics2D
            g2d.background = Color(0x5533ff)
            g2d.clearRect(0, 0, image.width, image.height)
            g2d.background = Color(0x222277)
            g2d.clearRect(MyJPanel.BorderWidth, MyJPanel.BorderWidth, MyJPanel.BitmapWidth, MyJPanel.BitmapHeight)
            g2d.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            g2d.drawString("HELLO world 12345", 100, 100)
        }

        fun update() {

            image.setRGB(rnd.nextInt(MyJPanel.BitmapWidth), rnd.nextInt(MyJPanel.BitmapHeight), rnd.nextInt())

            //            for(int x = 50; x < MyJPanel.BitmapWidth-50; x++) {
            //                for(int y = 50; y < MyJPanel.BitmapHeight-50; y++) {
            //                    image.setRGB(x+MyJPanel.BorderWidth, y+MyJPanel.BorderWidth, rnd.nextInt());
            //                }
            //            }
        }
    }
}


fun main(args: Array<String>) {
    EventQueue.invokeLater {
        val dialog = ScreenDialog()
        dialog.pack()
        dialog.isVisible = true
    }
}
