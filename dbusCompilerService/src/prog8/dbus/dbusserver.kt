package prog8.dbus

import org.freedesktop.dbus.connections.impl.DBusConnection


fun main() {
    val busname = "local.net.razorvine.prog8.dbus"

    DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION).use {
        it.requestBusName(busname)
        println("connection names        : ${it.names.toList()}")
        println("connection unique name  : ${it.uniqueName}")
        println("connection address      : ${it.address}")
        println("connection machine id   : ${it.machineId}")
        println("bus name for clients    : $busname")
        println("object path for clients : $serviceObjectPath")

        val service = TestService()
        it.exportObject(service.objectPath, service)

        Thread.sleep(100000)
        it.releaseBusName(busname)
    }
}
