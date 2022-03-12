package prog8.codegen.experimental

import prog8.code.core.Position
import java.util.*
import javax.xml.stream.XMLStreamWriter

class IndentingXmlWriter(val xml: XMLStreamWriter): XMLStreamWriter by xml {
    private var indent = 0
    private var content = Stack<Boolean>()

    fun doc(version: String? = null) = if(version==null) writeStartDocument() else writeStartDocument(version)
    fun endDoc() = writeEndDocument()
    fun elt(name: String) = writeStartElement(name)
    fun attr(name: String, value: String) = writeAttribute(name, value)
    fun attrs(attributes: List<Pair<String, String>>) = attributes.forEach { writeAttribute(it.first, it.second) }
    fun startChildren() {
        xml.writeCharacters("\n")
        content.pop()
        content.push(true)
    }
    fun endElt(writeIndent: Boolean=true) = writeEndElement(writeIndent)
    fun pos(pos: Position) {
        elt("src")
        attr("pos", pos.toString())
        endElt()
    }
    fun comment(text: String) {
        writeComment(text)
        writeCharacters("\n")
    }

    override fun writeStartDocument() {
        xml.writeStartDocument()
        xml.writeCharacters("\n")
        content.push(true)
    }

    override fun writeStartDocument(version: String) {
        xml.writeStartDocument(version)
        xml.writeCharacters("\n")
        content.push(true)
    }

    override fun writeEndDocument() {
        xml.writeEndDocument()
        xml.writeCharacters("\n")
        require(indent==0)
        require(content.size==1)
    }

    override fun writeStartElement(name: String) {
        xml.writeCharacters("  ".repeat(indent))
        xml.writeStartElement(name)
        indent++
        content.push(false)
    }

    override fun writeStartElement(name: String, ns: String) {
        xml.writeCharacters("  ".repeat(indent))
        xml.writeStartElement(name, ns)
        indent++
        content.push(false)
    }

    fun writeEndElement(writeIndents: Boolean) {
        indent--
        if(content.pop() && writeIndents)
            xml.writeCharacters("  ".repeat(indent))
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    override fun writeEndElement() = writeEndElement(true)

    override fun writeStartElement(name: String, ns: String, p2: String) {
        xml.writeCharacters("  ".repeat(indent))
        xml.writeStartElement(name, ns, p2)
        indent++
        content.push(false)
    }

    fun writeTextNode(name: String, attrs: List<Pair<String, String>>, text: String) {
        xml.writeCharacters("  ".repeat(indent))
        xml.writeStartElement(name)
        attrs.forEach { (name, value) -> xml.writeAttribute(name, value) }
        xml.writeCData(text)
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }
}
