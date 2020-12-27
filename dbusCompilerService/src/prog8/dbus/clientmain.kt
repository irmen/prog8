package prog8.dbus


import org.freedesktop.dbus.connections.impl.DBusConnection


fun main() {
    DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION).use {
        println(it.names.toList())
        println(it.uniqueName)
        println(it.address)
        println(it.machineId)
        val obj = it.getRemoteObject("local.net.razorvine.dbus.test", "/razorvine/TestService", IrmenDbusTest::class.java)
        println(obj.Status("irmen"))
    }
}

