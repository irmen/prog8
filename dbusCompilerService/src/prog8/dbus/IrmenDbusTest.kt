package prog8.dbus

import org.freedesktop.dbus.interfaces.DBusInterface


interface IrmenDbusTest: DBusInterface
{
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
    override fun getObjectPath() = "/razorvine/TestService"
}
