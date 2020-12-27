package prog8.dbus

import org.freedesktop.dbus.connections.impl.DBusConnection


fun main() {
    DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION).use {
        it.requestBusName("local.net.razorvine.dbus.test")
        println(it.names.toList())
        println(it.uniqueName)
        println(it.address)
        println(it.machineId)
        val service = TestService()
        it.exportObject(service.objectPath, service)

        Thread.sleep(100000)
    }
}
