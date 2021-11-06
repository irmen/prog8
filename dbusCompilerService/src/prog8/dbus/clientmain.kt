package prog8.dbus


import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface

/*

Inspect the dbus services interactively via the qdbusviewer tool.


Command line access is also possible with:

$ dbus-send --session --dest=local.net.razorvine.prog8.dbus --type=method_call --print-reply /razorvine/TestService prog8.dbus.IrmenDbusTest.Status string:"hello world"

$ qdbus --literal  local.net.razorvine.prog8.dbus /razorvine/TestService Status string:"hello world"

$ gdbus call --session -d local.net.razorvine.prog8.dbus -o /razorvine/TestService --method prog8.dbus.IrmenDbusTest.Status string:"hello world"

Or with the dasbus python library:

    from dasbus.connection import SessionMessageBus
    from dasbus.identifier import DBusServiceIdentifier

    PROG8_SERVICE = DBusServiceIdentifier(
        namespace=("local", "net", "razorvine", "prog8", "dbus"),
        message_bus=SessionMessageBus()
    )

    proxy = PROG8_SERVICE.get_proxy("/razorvine/TestService")
    print(proxy.Status("hello world"))


 */

inline fun <reified I: DBusInterface> DBusConnection.getRemote(busname: String, objectpath: String): I =
    this.getRemoteObject(busname, objectpath, I::class.java)

inline fun <reified I: DBusInterface> DBusConnection.getPeerRemote(busname: String, objectpath: String): I =
    this.getPeerRemoteObject(busname, objectpath, I::class.java)


fun main() {
    DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION).use {

        try {

            val obj = it.getRemote<IrmenDbusTest>("local.net.razorvine.prog8.dbus", serviceObjectPath)
            println(obj.Status("irmen"))

        } catch (dx: DBusExecutionException) {
            println("DBUS ERROR! $dx")
        }
    }
}

