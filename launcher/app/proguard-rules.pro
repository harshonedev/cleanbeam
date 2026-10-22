# Keep the app's own classes readable in stack traces; shrinking and optimisation still apply.
-keepattributes SourceFile,LineNumberTable
# BluetoothDevice.isConnected is reached by reflection in DeviceInfo; it is a framework method, R8 leaves it alone.
