package prog8.dbus

import org.freedesktop.dbus.annotations.IntrospectionDescription
import org.freedesktop.dbus.interfaces.DBusInterface


const val serviceObjectPath = "/razorvine/TestService"


interface IrmenDbusTest: DBusInterface
{
    @IntrospectionDescription("return some sort of status")
    fun Status(address: String): Map<Int, String>
}


internal class TestService: IrmenDbusTest {

    override fun Status(address: String): Map<Int, String> {
        return mapOf(
            5 to "hello",
            42 to address
        )
    }

    override fun isRemote() = true
    override fun getObjectPath() = serviceObjectPath
}
